package ai.whatyousay.engine

/**
 * Downloads, verifies, stores, and locates model packs.
 *
 * The contract is deliberately small. The only network use in the whole app lives
 * behind `download`, and it is always user-initiated. Everything else is local
 * filesystem work. A real implementation hashes each pack against the manifest
 * sha256 before registering it, so a corrupted or swapped download is rejected.
 */
interface ModelManager {
    fun installed(): List<ModelPack>

    fun isInstalled(pack: ModelPack): Boolean

    /** Absolute path to the local pack file, or null if not installed. */
    fun pathFor(pack: ModelPack): String?

    /** The sha256 confirmed when the pack was installed, or null if not installed. */
    fun installedSha(pack: ModelPack): String? = null

    /** Fetch a pack over the network (wifi-only by policy). Verifies sha256 on completion. */
    suspend fun download(pack: ModelPack, onProgress: (Float) -> Unit): Result<String>

    /** Recompute the on-disk hash and compare to the manifest. */
    fun verify(pack: ModelPack): Boolean

    fun remove(pack: ModelPack): Boolean
}

/** Picks the right packs for a device. Pure, so it is unit-testable without a filesystem. */
object ManifestSelector {
    fun recommended(ramBytes: Long, hasNpu: Boolean, languages: Collection<String>): List<ModelPack> =
        ModelCatalog.defaultsFor(tierFor(ramBytes, hasNpu), languages)

    fun canRun(pack: ModelPack, ramBytes: Long, hasNpu: Boolean): Boolean =
        pack.minTier.ordinal <= tierFor(ramBytes, hasNpu).ordinal
}
