package com.example.helloworld.services

import kotlin.js.JsAny

@JsFun("(apiKey) => new Clover(apiKey)")
external fun createClover(apiKey: String): JsAny

actual fun initiatePayment(amountCents: Long) {
    println("Web: Initiating Clover.js checkout for $amountCents cents")
    try {
        // Use a test API key for demonstration
        val clover = createClover("44538e081039a4572ab654cc76df6888")
        println("Clover.js initialized successfully")
    } catch (e: Exception) {
        println("Error initializing Clover.js: ${e.message}")
    }
}
