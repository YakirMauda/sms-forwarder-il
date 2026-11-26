package com.qrcommunication.smsforwarder.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrcommunication.smsforwarder.ui.common.ConditionTag
import com.qrcommunication.smsforwarder.ui.common.ResultBanner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the shared UI pieces on the JVM (Robolectric) and checks what the user sees.
 * A plain Application is used so the test doesn't need Hilt / WorkManager.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ComponentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun resultBanner_success_showsTitleAndTargets() {
        compose.setContent { ResultBanner(success = true, title = "ההודעה תועבר", subtitle = "אל 052-1234567") }

        compose.onNodeWithText("ההודעה תועבר").assertIsDisplayed()
        compose.onNodeWithText("אל 052-1234567").assertIsDisplayed()
    }

    @Test
    fun resultBanner_failure_showsReason_inRtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ResultBanner(success = false, title = "ההודעה לא תועבר", subtitle = "השולח מתאים, אבל התוכן לא")
            }
        }

        compose.onNodeWithText("ההודעה לא תועבר").assertIsDisplayed()
        compose.onNodeWithText("השולח מתאים, אבל התוכן לא").assertIsDisplayed()
    }

    @Test
    fun resultBanner_withoutSubtitle_showsOnlyTitle() {
        compose.setContent { ResultBanner(success = true, title = "ההודעה תועבר", subtitle = null) }

        compose.onNodeWithText("ההודעה תועבר").assertIsDisplayed()
    }

    @Test
    fun conditionTag_showsOperatorAndValue() {
        compose.setContent { ConditionTag(operator = "שווה ל", value = "Leumi") }

        compose.onNodeWithText("שווה ל").assertIsDisplayed()
        compose.onNodeWithText("Leumi").assertIsDisplayed()
    }
}
