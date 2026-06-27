package ai.whatyousay.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Filesystem-backed ModelManager. Each pack lives in its own directory under
 * `rootDir`, holding the downloaded artifact plus a `.verified` marker recording the
 * sha256 that was confirmed at download time.
 *
 * Single-file packs (a GGUF for MT) are stored as-is. Archive packs (`.zip`/`.tar.gz`,
 * used for the multi-file sherpa-onnx voice models) are extracted into the pack
 * directory after verification, flattening a single top-level directory so the engines
 * find their files directly. The sha256 is always computed over the downloaded artifact.
 *
 * The only network use in the app is [download], which is user-initiated. It streams
 * the artifact to a temporary file while hashing it, refuses to install anything whose
 * hash does not match the manifest, and only then promotes it into place. Everything
 * else is local filesystem work, so "airplane mode works" stays literally true.
 */
class FileModelManager(private val rootDir: File) : ModelManager {

    override fun installed(): List<ModelPack> =
        ModelCatalog.packs.filter { isInstalled(it) }

    override fun isInstalled(pack: ModelPack): Boolean {
        val marker = markerFile(pack)
        if (!marker.isFile || !hasContent(pack)) return false
        if (pack.sha256.isBlank()) return true
        return marker.readText().trim().equals(pack.sha256, ignoreCase = true)
    }

    /** A single-file pack keeps its artifact; an archive pack leaves its extracted files. */
    private fun hasContent(pack: ModelPack): Boolean {
        if (artifactFile(pack).isFile) return true
        val files = packDir(pack).listFiles().orEmpty()
        return files.any { it.name != MARKER && it.name != STAGING }
    }

    override fun pathFor(pack: ModelPack): String? = when {
        !isInstalled(pack) -> null
        artifactFile(pack).isFile -> artifactFile(pack).absolutePath
        else -> packDir(pack).absolutePath
    }

    override suspend fun download(pack: ModelPack, onProgress: (Float) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            if (pack.url.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("pack ${pack.id} has no url"))
            }
            val connection = (URL(pack.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                connection.inputStream.use { input ->
                    ingest(pack, connection.contentLengthLong, input, onProgress)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Stream [input] to disk while hashing it, reject on sha256 mismatch, and only then
     * promote it into place with a `.verified` marker. Network-free and synchronous so it
     * can be unit-tested with an in-memory stream; [download] supplies the real connection.
     */
    internal fun ingest(
        pack: ModelPack,
        total: Long,
        input: InputStream,
        onProgress: (Float) -> Unit,
    ): Result<String> {
        val dir = packDir(pack).apply { mkdirs() }
        val artifact = artifactFile(pack)
        val part = File(dir, artifact.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            part.outputStream().use { output ->
                val buffer = ByteArray(1 shl 16)
                var read = input.read(buffer)
                var done = 0L
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    read = input.read(buffer)
                }
            }
        } catch (e: Exception) {
            part.delete()
            return Result.failure(e)
        }

        val computed = digest.digest().toHex()
        if (pack.sha256.isNotBlank() && !computed.equals(pack.sha256, ignoreCase = true)) {
            part.delete()
            return Result.failure(
                IllegalStateException("sha256 mismatch for ${pack.id}: expected ${pack.sha256}, got $computed"),
            )
        }

        val installed = if (ArchiveExtractor.isArchive(artifact.name)) {
            installArchive(pack, part, artifact.name, computed)
        } else {
            installFile(pack, part, artifact, computed)
        }
        if (installed.isSuccess) onProgress(1f)
        return installed
    }

    private fun installFile(pack: ModelPack, part: File, artifact: File, computed: String): Result<String> {
        artifact.delete()
        if (!part.renameTo(artifact)) {
            part.delete()
            return Result.failure(IllegalStateException("could not store ${pack.id}"))
        }
        markerFile(pack).writeText(computed)
        return Result.success(artifact.absolutePath)
    }

    private fun installArchive(pack: ModelPack, part: File, archiveName: String, computed: String): Result<String> {
        val dir = packDir(pack)
        return try {
            ArchiveExtractor.extract(part, dir, archiveName)
            part.delete()
            markerFile(pack).writeText(computed)
            Result.success(dir.absolutePath)
        } catch (e: Exception) {
            part.delete()
            Result.failure(e)
        }
    }

    override fun verify(pack: ModelPack): Boolean {
        val artifact = artifactFile(pack)
        if (!artifact.isFile || pack.sha256.isBlank()) return false
        return sha256Of(artifact).equals(pack.sha256, ignoreCase = true)
    }

    override fun remove(pack: ModelPack): Boolean =
        packDir(pack).deleteRecursively()

    private fun packDir(pack: ModelPack) = File(rootDir, pack.id)

    private fun artifactFile(pack: ModelPack) = File(packDir(pack), artifactName(pack.url, pack.id))

    private fun markerFile(pack: ModelPack) = File(packDir(pack), MARKER)

    companion object {
        private const val MARKER = ".verified"
        private const val STAGING = ".staging"

        /** Derive a stable on-disk file name from the pack url, falling back to the id. */
        fun artifactName(url: String, packId: String): String {
            val tail = url.substringAfterLast('/', "").substringBefore('?').trim()
            return if (tail.isNotEmpty()) tail else "$packId.bin"
        }

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                var read = input.read(buffer)
                while (read >= 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return digest.digest().toHex()
        }
    }
}

private fun ByteArray.toHex(): String {
    val hex = "0123456789abcdef".toCharArray()
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append(hex[v ushr 4])
        out.append(hex[v and 0x0f])
    }
    return out.toString()
}
