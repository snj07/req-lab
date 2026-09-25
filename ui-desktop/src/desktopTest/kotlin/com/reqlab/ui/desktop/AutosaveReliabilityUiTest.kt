package com.reqlab.ui.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import com.reqlab.core.model.BodyType
import com.reqlab.ui.shared.MainScreen
import com.reqlab.ui.shared.persistence.TabsRepository
import com.reqlab.ui.shared.platform.PlatformStorage
import com.reqlab.ui.shared.state.AppState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AutosaveReliabilityUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearStorage() {
        PlatformStorage.remove("reqlab.tabs")
    }

    @After
    fun cleanupStorage() {
        PlatformStorage.remove("reqlab.tabs")
    }

    @Test
    fun same_length_edit_triggers_real_main_screen_autosave_and_reload() {
        val state = AppState()
        state.settings.autoSaveRequests = true
        val tab = state.activeTab!!
        tab.bodyType = BodyType.JSON
        tab.bodyContent = "before"
        tab.markSaved()
        TabsRepository.save(state)

        composeRule.setContent { MainScreen(state) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            tab.bodyContent = "after!"
            tab.markDirty()
        }
        composeRule.waitUntil(5_000) {
            PlatformStorage.getString("reqlab.tabs")?.contains("after!") == true
        }

        val restored = AppState(openDefaultTab = false)
        TabsRepository.load(restored)
        assertEquals("after!", restored.activeTab?.bodyContent)
        restored.dispose()
    }
}
