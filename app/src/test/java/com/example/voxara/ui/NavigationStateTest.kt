package com.example.voxara.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import com.example.voxara.ai.CoachUi
import com.example.voxara.ai.ReplySource
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.data.ExposureState
import com.example.voxara.ui.theme.VoxaraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens inside the nav host must follow the live state. They used to keep the env they were
 * first composed with, so taps worked but the screen never changed (the "dead buttons" bug).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w240dp-h240dp-round-xhdpi")
class NavigationStateTest {

    @get:Rule val compose = createComposeRule()

    private fun reply(status: String) = CoachUi(
        reply = CoachReply(status = status, insight = "i", why = "", suggestion = "s", chips = emptyList()),
        source = ReplySource.CLOUD_AI,
    )

    @Test
    fun `the home screen shows a reply that arrives after it was composed`() {
        val env = mutableStateOf(UiEnv(state = ExposureState(), coach = reply("First status")))
        compose.setContent {
            val current by env
            VoxaraTheme(ambient = false, reduceMotion = true) {
                AppScaffold { VoxaraApp(env = current, actions = VoxaraActions(), ambient = false) }
            }
        }
        compose.onNodeWithText("First status").assertExists()

        env.value = env.value.copy(coach = reply("Second status"))
        compose.waitForIdle()

        compose.onNodeWithText("Second status").assertExists()
        compose.onNodeWithText("First status").assertDoesNotExist()
    }
}
