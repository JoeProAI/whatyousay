package ai.whatyousay

import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.FileModelManager
import ai.whatyousay.engine.ModelPack
import ai.whatyousay.engine.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileModelManagerTest {

    private fun tempRoot(): File = Files.createTempDirectory("wys-models").toFile()

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = "0123456789abcdef".toCharArray()
        val out = StringBuilder(d.size * 2)
        for (b in d) {
            val v = b.toInt() and 0xff
            out.append(hex[v ushr 4]); out.append(hex[v and 0x0f])
        }
        return out.toString()
    }

    private fun pack(id: String, url: String, sha: String, stage: Stage = Stage.MT) =
        ModelPack(
            id = id,
            stage = stage,
            displayName = id,
            approxBytes = 1_000L,
            languages = listOf("en"),
            quantization = "Q4_K_M",
            minTier = DeviceTier.MID,
            sha256 = sha,
            url = url,
        )

    /** Zip the given entries under a single top-level directory, as the real packs ship. */
    private fun zipWithRoot(root: String, entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("$root/"))
            zip.closeEntry()
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry("$root/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Minimal ustar tar.gz of flat entries, enough to exercise the untar path. */
    private fun tarGz(entries: Map<String, ByteArray>): ByteArray {
        val raw = ByteArrayOutputStream()
        for ((name, bytes) in entries) {
            val header = ByteArray(512)
            fun put(s: String, off: Int, len: Int) {
                val b = s.toByteArray()
                System.arraycopy(b, 0, header, off, minOf(b.size, len))
            }
            put(name, 0, 100)
            put("0000644", 100, 8)
            put("0000000", 108, 8)
            put("0000000", 116, 8)
            put(String.format("%011o", bytes.size), 124, 12)
            put(String.format("%011o", 0), 136, 12)
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            header[156] = '0'.code.toByte()
            put("ustar\u0000", 257, 6)
            put("00", 263, 2)
            var sum = 0
            for (b in header) sum += b.toInt() and 0xff
            put(String.format("%06o", sum) + "\u0000 ", 148, 8)
            raw.write(header)
            raw.write(bytes)
            val pad = (512 - bytes.size % 512) % 512
            raw.write(ByteArray(pad))
        }
        raw.write(ByteArray(1024))
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(raw.toByteArray()) }
        return gz.toByteArray()
    }

    @Test
    fun ingestExtractsZipArchiveAndReportsDirectory() {
        val files = mapOf(
            "tiny-encoder.int8.onnx" to ByteArray(64) { it.toByte() },
            "tiny-tokens.txt" to "a b c".toByteArray(),
            "espeak-ng-data/phontab" to ByteArray(8) { 9 },
        )
        val archive = zipWithRoot("sherpa-onnx-whisper-tiny", files)
        val mgr = FileModelManager(tempRoot())
        val p = pack("stt-zip", "https://cdn.example/stt-zip.zip", sha256(archive), Stage.STT)

        val result = mgr.ingest(p, archive.size.toLong(), ByteArrayInputStream(archive)) { }

        assertTrue(result.isSuccess)
        assertTrue(mgr.isInstalled(p))
        val dir = File(mgr.pathFor(p)!!)
        assertTrue(dir.isDirectory)
        // The single top-level directory is flattened away.
        assertTrue(File(dir, "tiny-encoder.int8.onnx").isFile)
        assertTrue(File(dir, "tiny-tokens.txt").isFile)
        assertTrue(File(dir, "espeak-ng-data/phontab").isFile)
    }

    @Test
    fun ingestExtractsTarGzArchive() {
        val archive = tarGz(mapOf("model.onnx" to ByteArray(40) { it.toByte() }, "tokens.txt" to "x".toByteArray()))
        val mgr = FileModelManager(tempRoot())
        val p = pack("tts-tgz", "https://cdn.example/tts-tgz.tar.gz", sha256(archive), Stage.TTS)

        val result = mgr.ingest(p, archive.size.toLong(), ByteArrayInputStream(archive)) { }

        assertTrue(result.isSuccess)
        val dir = File(mgr.pathFor(p)!!)
        assertTrue(File(dir, "model.onnx").isFile)
        assertEquals(40L, File(dir, "model.onnx").length())
        assertTrue(File(dir, "tokens.txt").isFile)
    }

    @Test
    fun verifyPassesForExtractedArchivePack() {
        val archive = zipWithRoot("pack", mapOf("model.onnx" to ByteArray(32) { it.toByte() }))
        val mgr = FileModelManager(tempRoot())
        val p = pack("stt-verify", "https://cdn.example/stt-verify.zip", sha256(archive), Stage.STT)

        assertTrue(mgr.ingest(p, archive.size.toLong(), ByteArrayInputStream(archive)) { }.isSuccess)
        // The source archive is deleted after extraction, but verify must still pass.
        assertTrue(mgr.verify(p))
    }

    @Test
    fun ingestRejectsArchiveHashMismatch() {
        val archive = zipWithRoot("pack", mapOf("f" to ByteArray(16)))
        val mgr = FileModelManager(tempRoot())
        val p = pack("stt-bad", "https://cdn.example/stt-bad.zip", "00".repeat(32), Stage.STT)

        val result = mgr.ingest(p, archive.size.toLong(), ByteArrayInputStream(archive)) { }

        assertTrue(result.isFailure)
        assertFalse(mgr.isInstalled(p))
        assertNull(mgr.pathFor(p))
    }

    @Test
    fun artifactNameFromUrlElseId() {
        assertEquals("model.gguf", FileModelManager.artifactName("https://cdn.example/x/model.gguf", "p"))
        assertEquals("model.gguf", FileModelManager.artifactName("https://cdn.example/model.gguf?token=abc", "p"))
        assertEquals("p.bin", FileModelManager.artifactName("", "p"))
    }

    @Test
    fun sha256MatchesKnownDigest() {
        val root = tempRoot()
        val f = File(root, "data.bin").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        assertEquals(sha256(f.readBytes()), FileModelManager.sha256Of(f))
    }

    @Test
    fun notInstalledWhenAbsent() {
        val mgr = FileModelManager(tempRoot())
        val p = pack("mt-x", "https://cdn.example/mt-x.gguf", "deadbeef")
        assertFalse(mgr.isInstalled(p))
        assertNull(mgr.pathFor(p))
        assertFalse(mgr.verify(p))
    }

    @Test
    fun ingestVerifiesStoresAndReports() {
        val payload = ByteArray(4096) { ((it * 7) % 251).toByte() }
        val expected = sha256(payload)
        val mgr = FileModelManager(tempRoot())
        val p = pack("mt-ok", "https://cdn.example/mt-ok.gguf", expected)

        var lastProgress = -1f
        val result = mgr.ingest(p, payload.size.toLong(), ByteArrayInputStream(payload)) { lastProgress = it }

        assertTrue(result.isSuccess)
        assertTrue(mgr.isInstalled(p))
        assertTrue(mgr.verify(p))
        assertEquals(1f, lastProgress, 0.0001f)
        assertEquals(payload.size.toLong(), File(mgr.pathFor(p)!!).length())
    }

    @Test
    fun ingestRejectsHashMismatch() {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val mgr = FileModelManager(tempRoot())
        val p = pack("mt-bad", "https://cdn.example/mt-bad.gguf", "00".repeat(32))

        val result = mgr.ingest(p, payload.size.toLong(), ByteArrayInputStream(payload)) { }

        assertTrue(result.isFailure)
        assertFalse(mgr.isInstalled(p))
        assertNull(mgr.pathFor(p))
    }

    @Test
    fun removeDeletesPack() {
        val payload = ByteArray(512) { 1 }
        val expected = sha256(payload)
        val mgr = FileModelManager(tempRoot())
        val p = pack("mt-rm", "https://cdn.example/mt-rm.gguf", expected)
        mgr.ingest(p, payload.size.toLong(), ByteArrayInputStream(payload)) { }
        assertTrue(mgr.isInstalled(p))
        assertTrue(mgr.remove(p))
        assertFalse(mgr.isInstalled(p))
    }
}
