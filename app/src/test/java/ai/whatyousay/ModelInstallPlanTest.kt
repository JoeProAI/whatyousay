package ai.whatyousay

import ai.whatyousay.engine.DeviceTier
import ai.whatyousay.engine.ModelCatalog
import ai.whatyousay.engine.ModelManager
import ai.whatyousay.engine.ModelPack
import ai.whatyousay.engine.ModelInstallPlan
import ai.whatyousay.engine.PackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A manager where nothing is installed, so the plan starts every pack ABSENT. */
private class EmptyManager : ModelManager {
    override fun installed(): List<ModelPack> = emptyList()
    override fun isInstalled(pack: ModelPack): Boolean = false
    override fun pathFor(pack: ModelPack): String? = null
    override suspend fun download(pack: ModelPack, onProgress: (Float) -> Unit) = Result.success("")
    override fun verify(pack: ModelPack): Boolean = false
    override fun remove(pack: ModelPack): Boolean = false
}

class ModelInstallPlanTest {

    private val packs = ModelCatalog.defaultsFor(DeviceTier.MID, setOf("en", "fr"))
    private val first get() = packs.first().id

    private fun plan() = ModelInstallPlan(packs, EmptyManager())

    @Test
    fun startsAllAbsent() {
        val plan = plan()
        assertTrue(plan.statuses().all { it.state == PackState.ABSENT })
        assertEquals(0f, plan.overallProgress, 0.0001f)
        assertFalse(plan.allInstalled)
    }

    @Test
    fun lifecycleTransitions() {
        val plan = plan()
        plan.queued(first)
        assertEquals(PackState.QUEUED, plan.statusOf(first)!!.state)
        plan.downloading(first, 0.5f)
        assertEquals(PackState.DOWNLOADING, plan.statusOf(first)!!.state)
        assertEquals(0.5f, plan.statusOf(first)!!.progress, 0.0001f)
        plan.verifying(first)
        assertEquals(PackState.VERIFYING, plan.statusOf(first)!!.state)
        plan.installed(first, "abc123")
        assertEquals(PackState.INSTALLED, plan.statusOf(first)!!.state)
        assertEquals("abc123", plan.statusOf(first)!!.sha256)
    }

    @Test
    fun failureThenRetryClearsError() {
        val plan = plan()
        plan.failed(first, "boom")
        assertEquals(PackState.FAILED, plan.statusOf(first)!!.state)
        assertEquals("boom", plan.statusOf(first)!!.error)
        plan.queued(first)
        assertEquals(PackState.QUEUED, plan.statusOf(first)!!.state)
        assertEquals(null, plan.statusOf(first)!!.error)
    }

    @Test
    fun overallProgressAveragesAcrossPacks() {
        val plan = plan()
        packs.forEach { plan.installed(it.id, null) }
        assertEquals(1f, plan.overallProgress, 0.0001f)
        assertTrue(plan.allInstalled)
    }

    @Test
    fun absentResetsAnInstalledPack() {
        val plan = plan()
        plan.installed(first, "abc")
        plan.absent(first)
        assertEquals(PackState.ABSENT, plan.statusOf(first)!!.state)
        assertEquals(null, plan.statusOf(first)!!.sha256)
    }
}
