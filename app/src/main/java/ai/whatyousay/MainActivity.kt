package ai.whatyousay

import ai.whatyousay.design.WhatYouSayTheme
import ai.whatyousay.feature.conversation.ConversationScreen
import ai.whatyousay.feature.onboarding.OnboardingScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private enum class Screen { ONBOARDING, CONVERSATION }

@Composable
private fun App() {
    val container = (LocalContext.current.applicationContext as WhatYouSayApp).container
    WhatYouSayTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var screen by remember {
                mutableStateOf(if (container.settings.onboardingComplete) Screen.CONVERSATION else Screen.ONBOARDING)
            }
            when (screen) {
                Screen.ONBOARDING -> OnboardingScreen(onFinish = { screen = Screen.CONVERSATION })
                Screen.CONVERSATION -> ConversationScreen(onOpenModels = { screen = Screen.ONBOARDING })
            }
        }
    }
}
