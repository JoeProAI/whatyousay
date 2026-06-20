package ai.whatyousay.engine

/** Lifecycle of a single model pack as the onboarding flow installs it. */
enum class PackState { ABSENT, QUEUED, DOWNLOADING, VERIFYING, INSTALLED, FAILED }

/** A pack plus its live install state, ready to render. */
data class PackStatus(
    val pack: ModelPack,
    val state: PackState,
    val progress: Float = 0f,
    val sha256: String? = null,
    val error: String? = null,
) {
    val installed: Boolean get() = state == PackState.INSTALLED
    val busy: Boolean get() = state == PackState.QUEUED || state == PackState.DOWNLOADING || state == PackState.VERIFYING
}

/**
 * Tracks the install state of a set of packs. Pure and Android-free, so the state
 * transitions the onboarding ViewModel drives are unit tested on the JVM. The
 * ViewModel feeds it progress from the [ModelManager]; this holds no IO of its own.
 */
class ModelInstallPlan(packs: List<ModelPack>, manager: ModelManager) {

    private val order: List<String> = packs.map { it.id }
    private val byId: Map<String, ModelPack> = packs.associateBy { it.id }
    private val states: MutableMap<String, PackStatus> = packs.associate { pack ->
        val installed = manager.isInstalled(pack)
        pack.id to PackStatus(
            pack = pack,
            state = if (installed) PackState.INSTALLED else PackState.ABSENT,
            progress = if (installed) 1f else 0f,
            sha256 = manager.installedSha(pack),
        )
    }.toMutableMap()

    fun statuses(): List<PackStatus> = order.mapNotNull { states[it] }

    fun statusOf(packId: String): PackStatus? = states[packId]

    fun queued(packId: String) = update(packId) { it.copy(state = PackState.QUEUED, progress = 0f, error = null) }

    fun downloading(packId: String, progress: Float) =
        update(packId) { it.copy(state = PackState.DOWNLOADING, progress = progress.coerceIn(0f, 1f), error = null) }

    fun verifying(packId: String) =
        update(packId) { it.copy(state = PackState.VERIFYING, progress = 1f, error = null) }

    fun installed(packId: String, sha256: String?) =
        update(packId) { it.copy(state = PackState.INSTALLED, progress = 1f, sha256 = sha256, error = null) }

    fun failed(packId: String, error: String?) =
        update(packId) { it.copy(state = PackState.FAILED, error = error) }

    fun absent(packId: String) =
        update(packId) { it.copy(state = PackState.ABSENT, progress = 0f, sha256 = null, error = null) }

    /** Mean completion across all packs, counting an installed pack as fully done. */
    val overallProgress: Float
        get() {
            if (states.isEmpty()) return 0f
            val total = states.values.sumOf { status ->
                when (status.state) {
                    PackState.INSTALLED -> 1.0
                    PackState.VERIFYING -> 1.0
                    PackState.DOWNLOADING -> status.progress.toDouble()
                    else -> 0.0
                }
            }
            return (total / states.size).toFloat()
        }

    val allInstalled: Boolean get() = states.values.all { it.state == PackState.INSTALLED }

    val anyInstalled: Boolean get() = states.values.any { it.state == PackState.INSTALLED }

    private fun update(packId: String, transform: (PackStatus) -> PackStatus) {
        val current = states[packId] ?: byId[packId]?.let { PackStatus(it, PackState.ABSENT) } ?: return
        states[packId] = transform(current)
    }
}
