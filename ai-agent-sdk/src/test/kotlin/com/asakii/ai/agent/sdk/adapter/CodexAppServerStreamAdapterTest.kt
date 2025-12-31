import com.asakii.ai.agent.sdk.adapter.CodexAppServerStreamAdapter
import com.asakii.ai.agent.sdk.model.ContentCompletedEvent
import com.asakii.ai.agent.sdk.model.ContentStartedEvent
import com.asakii.ai.agent.sdk.model.ToolResultContent
import com.asakii.ai.agent.sdk.model.ToolUseContent
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.CommandExecutionStatus
import com.asakii.codex.agent.sdk.appserver.FileUpdateChange
import com.asakii.codex.agent.sdk.appserver.PatchApplyStatus
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ThreadItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CodexAppServerStreamAdapterTest {
    @Test
    fun `command execution maps to tool use and tool result`() {
        val adapter = CodexAppServerStreamAdapter { "thread-1" }
        val startItem = ThreadItem.CommandExecution(
            id = "cmd-1",
            command = "ls",
            cwd = "/tmp",
            status = CommandExecutionStatus.InProgress
        )

        val startEvents = adapter.convert(AppServerEvent.ItemStarted("thread-1", "turn-1", startItem))
        val started = startEvents.filterIsInstance<ContentStartedEvent>().single()
        val toolUse = started.content as ToolUseContent

        assertEquals("Bash", toolUse.name)
        val input = toolUse.input?.jsonObject
        assertNotNull(input)
        assertEquals("CommandExecution", input["type"]?.jsonPrimitive?.content)
        assertEquals("ls", input["command"]?.jsonPrimitive?.content)

        val completedItem = startItem.copy(
            status = CommandExecutionStatus.Completed,
            aggregatedOutput = "ok",
            exitCode = 0
        )
        val completedEvents = adapter.convert(AppServerEvent.ItemCompleted("thread-1", "turn-1", completedItem))
        val completed = completedEvents.filterIsInstance<ContentCompletedEvent>().single()
        val result = completed.content as ToolResultContent

        assertEquals("cmd-1", result.toolUseId)
        assertEquals("ok", result.content?.jsonPrimitive?.content)
        assertEquals(false, result.isError)
    }

    @Test
    fun `file change maps to edit tool input`() {
        val adapter = CodexAppServerStreamAdapter { "thread-1" }
        val change = FileUpdateChange(
            path = "src/main.kt",
            kind = PatchChangeKind.Update(),
            diff = "@@ -1,1 +1,1 @@\n-old\n+new\n"
        )
        val item = ThreadItem.FileChange(
            id = "file-1",
            changes = listOf(change),
            status = PatchApplyStatus.Completed
        )

        val startEvents = adapter.convert(AppServerEvent.ItemStarted("thread-1", "turn-1", item))
        val started = startEvents.filterIsInstance<ContentStartedEvent>().single()
        val toolUse = started.content as ToolUseContent

        assertEquals("Edit", toolUse.name)
        val input = toolUse.input?.jsonObject
        assertNotNull(input)
        assertEquals("FileChange", input["type"]?.jsonPrimitive?.content)
        assertEquals("edit", input["operation"]?.jsonPrimitive?.content)
        assertEquals("src/main.kt", input["path"]?.jsonPrimitive?.content)
        assertTrue(input["oldContent"]?.jsonPrimitive?.content?.contains("old") == true)
        assertTrue(input["newContent"]?.jsonPrimitive?.content?.contains("new") == true)
    }
}
