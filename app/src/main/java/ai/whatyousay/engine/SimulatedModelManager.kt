package ai.whatyousay.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * A network-free [ModelManager] that demonstrates the full download and verify
 * lifecycle without any model files or real pack URLs.
 *
 * The shipping [ModelCatalog] carries no real URLs (they are filled at release from
 * the pack CDN), so until a real source is configured the onboarding flow runs
 * against this manager. It streams deterministic bytes per pack, reports progress,
 * computes a real sha256 over those bytes, and records the pack as installed and
 * verified. It writes nothing to disk and never touches the network, so the
 * "airplane mode works" guarantee is never violated even in the demo. The UI labels
 * packs provisioned this way as simulated.
 */
class SimulatedModelManager(
    private val steps: Int = 24,
    private val stepDelayMs: Long = 55L,
    private val verifyDelayMs: Long = 450L,
) : ModelManager {

    private data class Installed(val sha: String, val path: String)

    private val installed = ConcurrentHashMap<String, Installed>()

    override fun installed(): List<ModelPack> = ModelCatalog.packs.filter { isInstalled(it) }

    override fun isInstalled(pack: ModelPack): Boolean = installed.containsKey(pack.id)

    override fun pathFor(pack: ModelPack): String? = installed[pack.id]?.path

    override fun installedSha(pack: ModelPack): String? = installed[pack.id]?.sha

    override suspend fun download(pack: ModelPack, onProgress: (Float) -> Unit): Result<String> =
        withContext(Dispatchers.Default) {
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                for (i in 1..steps) {
                    currentCoroutineContext().ensureActive()
                    digest.update("${pack.id}:chunk:$i".toByteArray())
                    onProgress(i.toFloat() / steps)
                    delay(stepDelayMs)
                }
                onProgress(1f)
                // The verify phase: a real download hashes on the wire, so the demo
                // spends a beat here too and the UI can surface the verifying state.
                delay(verifyDelayMs)
                currentCoroutineContext().ensureActive()
                val sha = digest.digest().toHexLower()
                val path = "(simulated)/${pack.id}.bin"
                installed[pack.id] = Installed(sha, path)
                Result.success(path)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun verify(pack: ModelPack): Boolean = isInstalled(pack)

    override fun remove(pack: ModelPack): Boolean = installed.remove(pack.id) != null
}

private fun ByteArray.toHexLower(): String {
    val hex = "0123456789abcdef".toCharArray()
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append(hex[v ushr 4])
        out.append(hex[v and 0x0f])
    }
    return out.toString()
}
