package com.example.helloworld.services

import com.example.helloworld.models.CloverItemResponse
import com.example.helloworld.models.FlashItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class InventoryService(
    private val merchantId: String,
    private val apiToken: String
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun getInventory(): List<FlashItem> {
        return try {
            val response = client.get("https://apisandbox.dev.clover.com/v3/merchants/$merchantId/items") {
                header("Authorization", "Bearer $apiToken")
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "FlashSaleApp/1.0")
            }

            if (response.status == HttpStatusCode.OK) {
                val itemResponse: CloverItemResponse = response.body()
                itemResponse.elements.map {
                    FlashItem(
                        id = it.id ?: "",
                        name = it.name ?: "Unknown",
                        price = it.price ?: 0L
                    )
                }
            } else {
                println("Clover API Error ${response.status}")
                getDemoItems()
            }
        } catch (e: Exception) {
            println("Exception during API call: ${e.message}. Using demo data.")
            getDemoItems()
        }
    }

    suspend fun addItem(name: String, priceCents: Long): Boolean {
        try {
            println("Attempting to add item: $name, $priceCents cents")
            val request = com.example.helloworld.models.CloverAddItemRequest(name, priceCents)
            val response = client.post("https://apisandbox.dev.clover.com/v3/merchants/$merchantId/items") {
                header("Authorization", "Bearer $apiToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "FlashSaleApp/1.0")
                setBody(request)
            }

            return if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                println("Successfully added item!")
                true
            } else {
                val errorBody = response.bodyAsText()
                println("Add item failed. Status: ${response.status}")
                println("Error body: $errorBody")
                false
            }
        } catch (e: Exception) {
            println("Exception during addItem: ${e.message}")
            return false
        }
    }

    suspend fun deleteItem(itemId: String): Boolean {
        return try {
            val response = client.delete("https://apisandbox.dev.clover.com/v3/merchants/$merchantId/items/$itemId") {
                header("Authorization", "Bearer $apiToken")
                header(HttpHeaders.UserAgent, "FlashSaleApp/1.0")
            }
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent
        } catch (e: Exception) {
            println("Delete item failed: ${e.message}")
            false
        }
    }

    private fun getDemoItems(): List<FlashItem> {
        return listOf(
            FlashItem("1", "Chocolate Croissant", 350),
            FlashItem("2", "Blueberry Muffin", 275),
            FlashItem("3", "Flat White", 450),
            FlashItem("4", "Iced Latte", 500),
            FlashItem("5", "Avocado Toast", 1200)
        )
    }
}
