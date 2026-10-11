package com.bu.paperclip

import com.bu.paperclip.data.*
import com.bu.paperclip.net.AppJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decodes real API responses with the app's models. Fixtures are captured from a live server and kept
 * out of the repo; point PAPERCLIP_FIXTURES at the directory to run (skipped otherwise).
 */
class FixtureDecodeTest {
    private val dir = System.getenv("PAPERCLIP_FIXTURES")?.let(::File)

    private fun <T> decode(name: String, ser: KSerializer<T>): T? {
        val f = dir?.resolve("$name.json") ?: return null
        if (!f.exists()) return null
        return AppJson.decodeFromString(ser, f.readText())
    }

    @Test
    fun decodesAllFixtures() {
        assumeTrue(dir?.isDirectory == true)
        decode("session", AuthSession.serializer())
        decode("companies", ListSerializer(Company.serializer()))
        decode("users", UserDirectory.serializer())
        decode("dashboard", DashboardSummary.serializer())
        decode("badges", SidebarBadges.serializer())
        decode("activity", ListSerializer(ActivityEvent.serializer()))
        decode("liveruns", ListSerializer(LiveRun.serializer()))
        val att = decode("attention", AttentionFeed.serializer())
        decode("issues", ListSerializer(Issue.serializer()))
        decode("agents", ListSerializer(Agent.serializer()))
        decode("runs", ListSerializer(HeartbeatRun.serializer()))
        decode("approvals", ListSerializer(Approval.serializer()))
        decode("costsummary", CostSummary.serializer())
        decode("costagent", ListSerializer(CostByAgent.serializer()))
        decode("costprovider", ListSerializer(CostByProvider.serializer()))
        decode("quotas", CachedQuota.serializer())
        decode("budgets", BudgetOverview.serializer())
        decode("projects", ListSerializer(Project.serializer()))
        decode("goals", ListSerializer(Goal.serializer()))
        decode("routines", ListSerializer(Routine.serializer()))
        decode("search", SearchResponse.serializer())
        decode("labels", ListSerializer(Label.serializer()))
        val issue = decode("issue", Issue.serializer())
        decode("comments", ListSerializer(IssueComment.serializer()))
        val ints = decode("interactions", ListSerializer(Interaction.serializer()))
        decode("documents", ListSerializer(IssueDocument.serializer()))
        decode("attachments", ListSerializer(Attachment.serializer()))
        decode("workproducts", ListSerializer(WorkProduct.serializer()))
        decode("issueactivity", ListSerializer(ActivityEvent.serializer()))
        decode("issueruns", ListSerializer(JsonObject.serializer()))
        decode("issuecost", IssueCostSummary.serializer())
        decode("agent", Agent.serializer())
        decode("runtime", RuntimeState.serializer())
        decode("instructions", InstructionsBundle.serializer())
        decode("run", HeartbeatRun.serializer())
        decode("runevents", ListSerializer(RunEvent.serializer()))
        val log = decode("runlog", RunLogPage.serializer())
        println("attention items=${att?.items?.size} issue=${issue?.identifier} interactions=${ints?.size}")
        if (log != null) {
            val b = TranscriptBuilder("claude_local")
            b.feedLogContent(log.content)
            println("transcript entries=${b.entries.size} kinds=${b.entries.groupingBy { it::class.simpleName }.eachCount()}")
        }
    }

    @Test
    fun parsesEveryRunLogFixture() {
        assumeTrue(dir?.isDirectory == true)
        dir!!.listFiles { f -> f.name.startsWith("log-") && f.name.endsWith(".json") }?.forEach { f ->
            val page = AppJson.decodeFromString(RunLogPage.serializer(), f.readText())
            val adapter = f.name.removePrefix("log-").substringBefore("__")
            val b = TranscriptBuilder(adapter)
            b.feedLogContent(page.content)
            val counts = b.entries.groupingBy { it::class.simpleName }.eachCount()
            println("${f.name}: ${b.entries.size} entries $counts")
            val raw = counts["Line"] ?: 0
            assertTrue("${f.name} produced only raw lines", b.entries.isEmpty() || raw < b.entries.size)
        }
    }
}
