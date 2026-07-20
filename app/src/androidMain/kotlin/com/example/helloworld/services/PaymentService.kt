package com.example.helloworld.services

actual fun initiatePayment(amountCents: Long) {
    // In a real app, this would use the Clover Android SDK to start a transaction
    println("Android: Initiating sale for $amountCents cents via Clover SDK")
}
