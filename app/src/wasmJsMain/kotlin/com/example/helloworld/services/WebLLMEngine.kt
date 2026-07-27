package com.example.helloworld.services

import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.js.JsAny

@JsFun("(modelName, progressCallback) => window.transformers.pipeline('text-generation', modelName, { device: 'webgpu', progress_callback: progressCallback })")
external fun createPipeline(modelName: String, progressCallback: (JsAny) -> Unit): Promise<JsAny>

@JsFun("(modelName, progressCallback) => window.transformers.pipeline('text-generation', modelName, { device: 'cpu', progress_callback: progressCallback })")
external fun createPipelineCpu(modelName: String, progressCallback: (JsAny) -> Unit): Promise<JsAny>

@JsFun("() => ({ max_new_tokens: 30, temperature: 0.7, do_sample: true, repetition_penalty: 1.2 })")
external fun createOptions(): JsAny

@JsFun("(gen, text, options) => gen(text, options)")
external fun callGenerator(gen: JsAny, text: String, options: JsAny): Promise<JsAny>

@JsFun("(result) => result[0].generated_text")
external fun parseResult(result: JsAny): String

@JsFun("(data) => data.progress || 0")
external fun getProgressFromData(data: JsAny): Double

class WebLLMEngine : LocalInferenceEngine {
    private var generator: JsAny? = null
    private var isInitializing = false
    private var currentProgress = 0.0

    suspend fun initialize(modelName: String = "Xenova/distilgpt2") {
        if (generator != null || isInitializing) return
        isInitializing = true
        currentProgress = 0.0
        
        val progressCallback: (JsAny) -> Unit = { data ->
            val p = getProgressFromData(data)
            println("AI Loading Progress: $p%")
            // Transformers.js sends progress per file, so this is an approximation
            if (p > currentProgress * 100.0) {
                currentProgress = p / 100.0
            }
        }

        try {
            println("Initializing local AI model: $modelName...")
            generator = createPipeline(modelName, progressCallback).await<JsAny>()
            currentProgress = 1.0
            println("Local AI Ready (WebGPU)!")
        } catch (e: Exception) {
            println("Failed to initialize with WebGPU: ${e.message}. Falling back to CPU...")
            try {
                generator = createPipelineCpu(modelName, progressCallback).await<JsAny>()
                currentProgress = 1.0
                println("Local AI Ready (CPU)!")
            } catch (e2: Exception) {
                println("Local AI initialization failed: ${e2.message}")
            }
        } finally {
            isInitializing = false
        }
    }

    override fun isReady(): Boolean = generator != null
    
    override fun getProgress(): Double = currentProgress

    override suspend fun generate(prompt: String): String {
        val gen = generator ?: return "AI not initialized"
        return try {
            val options = createOptions()
            val result = callGenerator(gen, prompt, options).await<JsAny>()
            val fullText = parseResult(result)
            fullText.substringAfter(prompt).trim()
        } catch (e: Exception) {
            "AI Error: ${e.message}"
        }
    }
}
