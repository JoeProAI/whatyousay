package ai.whatyousay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ai.whatyousay.design.WhatYouSayTheme
import ai.whatyousay.feature.conversation.ConversationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
private fun App() {
    WhatYouSayTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ConversationScreen()
        }
    }
}
