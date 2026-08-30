package com.reqlab.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.reqlab.core.model.McpCreateMessageResult
import com.reqlab.core.model.McpElicitRequest
import com.reqlab.ui.shared.components.McpElicitationForm
import com.reqlab.ui.shared.components.McpSamplingResultForm
import com.reqlab.ui.shared.state.AppState
import com.reqlab.ui.shared.theme.ReqLabTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class McpCallbackPaneUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sampling_result_form_approve_sends_typed_fields() {
        var approved: McpCreateMessageResult? = null
        composeRule.setContent {
            ReqLabTheme {
                Box(Modifier.size(800.dp, 600.dp)) {
                    McpSamplingResultForm(
                        initial = McpCreateMessageResult(),
                        onApprove = { approved = it },
                        onCancel = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("mcp-sampling-content", useUnmergedTree = true)
            .performTextReplacement("typed-from-test")
        composeRule.onNodeWithTag("mcp-sampling-role").performClick()
        composeRule.onNodeWithTag("mcp-sampling-role-user").performClick()
        composeRule.onNodeWithTag("mcp-sampling-model", useUnmergedTree = true)
            .performTextReplacement("test-model")
        composeRule.onNodeWithTag("mcp-sampling-stop-reason").performClick()
        composeRule.onNodeWithTag("mcp-sampling-stop-reason-maxTokens").performClick()
        composeRule.onNodeWithTag("mcp-sampling-approve-send").performClick()
        val result = assertNotNull(approved)
        assertEquals("typed-from-test", result.content.text)
        assertEquals("user", result.role)
        assertEquals("test-model", result.model)
        assertEquals("maxTokens", result.stopReason)
    }

    @Test
    fun elicitation_form_accept_sends_schema_field() {
        var acceptedArgs: String? = null
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        val state = AppState(openDefaultTab = false, withDemoData = false)
        composeRule.setContent {
            ReqLabTheme {
                Box(Modifier.size(800.dp, 600.dp)) {
                    var args by remember { mutableStateOf("""{"name":""}""") }
                    McpElicitationForm(
                        state = state,
                        request = McpElicitRequest(message = "What is your name?", requestedSchema = schema),
                        argsJson = args,
                        onArgsChange = { args = it },
                        onAccept = { acceptedArgs = args },
                        onDecline = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("mcp-elicit-args-name", useUnmergedTree = true)
            .performTextReplacement("typed-from-test")
        composeRule.onNodeWithTag("mcp-elicit-accept").performClick()
        assertNotNull(acceptedArgs)
        assertEquals(true, acceptedArgs!!.contains("typed-from-test"))
    }
}
