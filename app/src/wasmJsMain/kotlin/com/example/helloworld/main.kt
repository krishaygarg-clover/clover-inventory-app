package com.example.helloworld

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.helloworld.services.AIService
import com.example.helloworld.services.WebLLMEngine
import kotlinx.browser.document
import kotlinx.coroutines.*

@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class)
fun main() {
    val container = document.getElementById("app-container") ?: document.body!!
    
    val engine = WebLLMEngine()
    val aiService = AIService(engine)
    
    // Initialize LLM in background
    GlobalScope.launch {
        // Using a very small model (~80MB) that fits in GitHub
        engine.initialize("Xenova/distilgpt2")
    }

    ComposeViewport(container) {
        App(aiService)
    }
}
