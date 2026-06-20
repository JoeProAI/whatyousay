package ai.whatyousay

import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.ModelPack
import ai.whatyousay.engine.SimulatedModelManager
import ai.whatyousay.engine.Stage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedModelManagerTest {

    private fun manager() = SimulatedModelManager(steps = 4, stepDelayMs = 0L, verifyDelayMs = 0L)

    private val pack: ModelPack = ModelCatalog.forStage(Stage.MT, DeviceTier.LOW)!!

    @Test
    fun downloadInstallsAndVerifies() = runTest {
        val mgr = manager()
        assertFalse(mgr.isInstalled(pack))

        var last = -1f
        val result = mgr.download(pack) { last = it }

        assertTrue(result.isSuccess)
        assertTrue(mgr.isInstalled(pack))
        assertEquals(1f, last, 0.0001f)
        assertNotNull(mgr.pathFor(pack))
    }

    @Test
    fun installedShaIsRealHexDigest() = runTest {
        val mgr = manager()
        mgr.download(pack) {}
        val sha = mgr.installedSha(pack)
        assertNotNull(sha)
        assertEquals(64, sha!!.length)
        assertTrue(sha.all { it in "0123456789abcdef" })
    }

    @Test
    fun shaIsDeterministicPerPack() = runTest {
        val a = manager().also { it.download(pack) {} }.installedSha(pack)
        val b = manager().also { it.download(pack) {} }.installedSha(pack)
        assertEquals(a, b)
    }

    @Test
    fun progressIsMonotonicAndReachesOne() = runTest {
        val mgr = manager()
        val seen = mutableListOf<Float>()
        mgr.download(pack) { seen.add(it) }
        assertEquals(1f, seen.last(), 0.0001f)
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun removeUninstalls() = runTest {
        val mgr = manager()
        mgr.download(pack) {}
        assertTrue(mgr.isInstalled(pack))
        assertTrue(mgr.remove(pack))
        assertFalse(mgr.isInstalled(pack))
    }
}
