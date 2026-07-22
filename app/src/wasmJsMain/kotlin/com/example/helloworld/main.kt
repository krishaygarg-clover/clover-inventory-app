package com.example.helloworld

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.helloworld.services.AIService
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = document.getElementById("app-container") ?: document.body!!
    ComposeViewport(container) {
        // Now using the Micro-Vector engine by default
        App(AIService())
    }
}
