package ai.whatyousay.engine

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Dependency-free extraction for the two pack formats the app ships: `.zip` and
 * `.tar.gz` (`.tgz`). Android has no bundled bzip2, and the app keeps zero runtime
 * dependencies, so model packs are distributed in these JDK-native formats rather
 * than the upstream `.tar.bz2`.
 *
 * Archives are flattened: when every entry sits under a single top-level directory
 * (the convention for the sherpa-onnx and GGUF packs), that directory is stripped so
 * the model files land directly in the pack directory that the engines read.
 */
object ArchiveExtractor {

    fun isArchive(name: String): Boolean = formatOf(name) != null

    private enum class Format { ZIP, TAR_GZ }

    private fun formatOf(name: String): Format? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".zip") -> Format.ZIP
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> Format.TAR_GZ
            else -> null
        }
    }

    /**
     * Extract [archive] into [destDir], flattening a single common root directory. The
     * format is taken from [name], which lets the caller pass a download's logical
     * filename even when the bytes live in a temp file.
     */
    fun extract(archive: File, destDir: File, name: String = archive.name) {
        val format = formatOf(name)
            ?: throw IllegalArgumentException("unsupported archive format: $name")
        val staging = File(destDir, ".staging").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            archive.inputStream().buffered().use { raw ->
                when (format) {
                    Format.ZIP -> unzip(raw, staging)
                    Format.TAR_GZ -> untar(GZIPInputStream(raw), staging)
                }
            }
            promote(staging, destDir)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Move the staged tree into [destDir], stripping a lone top-level directory. */
    private fun promote(staging: File, destDir: File) {
        val entries = staging.listFiles().orEmpty()
        val source = if (entries.size == 1 && entries[0].isDirectory) entries[0] else staging
        for (child in source.listFiles().orEmpty()) {
            val target = File(destDir, child.name)
            target.deleteRecursively()
            if (!child.renameTo(target)) {
                child.copyRecursively(target, overwrite = true)
            }
        }
    }

    private fun unzip(input: InputStream, destDir: File) {
        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        while (entry != null) {
            val out = safeChild(destDir, entry.name)
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    // Minimal tar reader: ustar regular files and directories, the ustar `prefix`
    // field for long paths, and GNU `L` long-name entries. Other entry types are
    // skipped, which is enough for the model packs the app consumes.
    private fun untar(input: InputStream, destDir: File) {
        val header = ByteArray(BLOCK)
        var pendingLongName: String? = null
        while (true) {
            if (!input.readFully(header)) break
            if (header.all { it.toInt() == 0 }) break

            val size = parseOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val name = pendingLongName ?: tarName(header)
            pendingLongName = null

            when (type) {
                'L' -> {
                    pendingLongName = String(input.readBlocks(size)).trimEnd('\u0000')
                    continue
                }
                '5' -> safeChild(destDir, name).mkdirs()
                '0', '\u0000' -> {
                    val out = safeChild(destDir, name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { it.write(input.readBlocks(size)) }
                }
                else -> input.skipFully(padded(size))
            }
        }
    }

    private fun tarName(header: ByteArray): String {
        val name = cString(header, 0, 100)
        val prefix = cString(header, 345, 155)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    /** Reject path-traversal entries so a malformed pack cannot escape its directory. */
    private fun safeChild(destDir: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/')
        val child = File(destDir, normalized).canonicalFile
        val root = destDir.canonicalFile
        require(child.path == root.path || child.path.startsWith(root.path + File.separator)) {
            "blocked path traversal in archive entry: $entryName"
        }
        return child
    }

    private const val BLOCK = 512

    private fun padded(size: Long): Long = ((size + BLOCK - 1) / BLOCK) * BLOCK

    private fun cString(buf: ByteArray, offset: Int, len: Int): String {
        var end = offset
        val limit = offset + len
        while (end < limit && buf[end].toInt() != 0) end++
        return String(buf, offset, end - offset)
    }

    private fun parseOctal(buf: ByteArray, offset: Int, len: Int): Long {
        var value = 0L
        for (i in offset until offset + len) {
            val c = buf[i].toInt()
            if (c == 0 || c == ' '.code) continue
            if (c < '0'.code || c > '7'.code) continue
            value = value * 8 + (c - '0'.code)
        }
        return value
    }

    private fun InputStream.readBlocks(size: Long): ByteArray {
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < data.size) {
            val n = read(data, read, data.size - read)
            if (n < 0) throw IllegalStateException("truncated archive")
            read += n
        }
        skipFully(padded(size) - size)
        return data
    }

    private fun InputStream.readFully(buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = read(buf, read, buf.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        val scratch = ByteArray(BLOCK)
        while (remaining > 0) {
            val n = read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (n < 0) break
            remaining -= n
        }
    }
}
