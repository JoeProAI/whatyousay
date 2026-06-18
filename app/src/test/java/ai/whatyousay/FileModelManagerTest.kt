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
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

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

    private fun pack(id: String, url: String, sha: String) =
        ModelPack(
            id = id,
            stage = Stage.MT,
            displayName = id,
            approxBytes = 1_000L,
            languages = listOf("en"),
            quantization = "Q4_K_M",
            minTier = DeviceTier.MID,
            sha256 = sha,
            url = url,
        )

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
