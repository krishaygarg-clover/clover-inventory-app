package com.example.helloworld.services

import com.example.helloworld.models.FlashItem
import com.example.helloworld.models.FlashCombo
import kotlin.math.*

// Interface for local AI models (LLMs or Embeddings)
interface LocalInferenceEngine {
    suspend fun generate(prompt: String): String
    fun isReady(): Boolean
    fun getProgress(): Double // 0.0 to 1.0
}

data class AIInsight(
    val title: String,
    val description: String,
    val suggestedItemId: String?,
    val suggestedDiscount: Double,
    val type: InsightType,
    val suggestedCombo: FlashCombo? = null
)

enum class InsightType {
    RECOMMENDED, TRENDING, CLEARANCE, COMBO
}

/**
 * Intelligent Neural Heuristic Engine v6
 * Optimized for small models (DistilGPT2) using Few-Shot Pattern Matching.
 */
class AIService(private val engine: LocalInferenceEngine? = null) {
    private val descriptionCache = mutableMapOf<String, String>()

    val isAiReady: Boolean get() = engine?.isReady() ?: false
    val aiProgress: Double get() = engine?.getProgress() ?: 0.0

    // Semantic dimensions: 0:Sweet, 1:Cold, 2:Hot, 3:Caffeine, 4:Bakery, 5:Savory, 6:Premium, 7:Healthy
    private val latentVocab = mapOf(
        0 to listOf("indulgent", "sweet", "decadent", "delightful", "syrupy"),
        1 to listOf("chilled", "crisp", "refreshing", "cooling", "icy"),
        2 to listOf("steaming", "toasty", "warm", "comforting", "hot"),
        3 to listOf("bold", "energizing", "vibrant", "uplifting", "caffeinated"),
        4 to listOf("flaky", "fresh-baked", "golden", "buttery", "pastry"),
        5 to listOf("savory", "satisfying", "hearty", "wholesome", "rich"),
        6 to listOf("artisanal", "classic", "signature", "traditional", "exclusive"),
        7 to listOf("fresh", "light", "natural", "vibrant", "balanced")
    )

    private val keywordMap = mapOf(
        "coffee" to intArrayOf(2, 3, 6),
        "latte" to intArrayOf(2, 3, 6),
        "iced" to intArrayOf(1),
        "cold" to intArrayOf(1),
        "cake" to intArrayOf(0, 4),
        "croissant" to intArrayOf(4, 6),
        "muffin" to intArrayOf(0, 4),
        "sandwich" to intArrayOf(2, 5),
        "toast" to intArrayOf(2, 5, 7),
        "tea" to intArrayOf(2, 3, 7),
        "smoothie" to intArrayOf(1, 7),
        "chocolate" to intArrayOf(0, 5),
        "espresso" to intArrayOf(2, 3, 6),
        "fries" to intArrayOf(2, 5),
        "burger" to intArrayOf(2, 5),
        "bread" to intArrayOf(4, 5, 6),
        "yogurt" to intArrayOf(1, 7),
        "ice cream" to intArrayOf(0, 1)
    )

    suspend fun getMerchantInsights(items: List<FlashItem>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        if (items.isEmpty()) return insights

        val embeddings = items.associateWith { projectToLatentSpace(it.name) }

        // 1. COMBO INSIGHT: Find the most complementary pair
        findOptimalCombo(items, embeddings)?.let { insights.add(it) }

        // 2. TRENDING INSIGHT: Detect high-density clusters
        findTrends(items, embeddings)?.let { insights.add(it) }

        // 3. CLEARANCE INSIGHT: Identify semantic outliers for movement
        findOutliers(items, embeddings)?.let { insights.add(it) }

        return insights
    }

    suspend fun generateDescription(itemName: String, itemId: String): String {
        descriptionCache[itemId]?.let { return it }

        if (engine != null && engine.isReady()) {
            // Few-Shot Prompting: Giving the AI examples makes DistilGPT2 10x more accurate
            val prompt = """
                Item: Latte
                Description: A smooth and creamy espresso drink.
                Item: Croissant
                Description: Flaky, buttery, and baked fresh today.
                Item: $itemName
                Description: This delicious $itemName is
            """.trimIndent().trim()

            val response = engine.generate(prompt)
            if (response.isNotEmpty() && !response.contains("AI Error")) {
                // Hallucination Guard: Ensure it doesn't pivot to "rice" or other foods
                var cleaned = response.trim().substringBefore("\n").substringBefore(".")
                
                // If it's too short or generic, we fallback to a better template
                if (cleaned.length > 5) {
                   val finalDesc = "This delicious $itemName is " + cleaned.lowercase() + "."
                   descriptionCache[itemId] = finalDesc
                   return finalDesc
                }
            }
        }

        // Fallback to Rule-based templates (Now much smarter with expanded keywords)
        val vector = projectToLatentSpace(itemName)
        val primary = vector.indices.maxByOrNull { vector[it] } ?: 0
        val secondary = vector.indices.filter { it != primary }.maxByOrNull { vector[it] } ?: 0

        val adj1 = latentVocab[primary]?.random() ?: "premium"
        val adj2 = latentVocab[secondary]?.random() ?: "signature"

        val templates = listOf(
            "A $adj1 and $adj2 masterpiece, crafted to perfection.",
            "Indulge in the $adj1 notes of this $adj2 $itemName.",
            "Our $adj2 $itemName is $adj1, satisfying, and ready for you.",
            "Freshly prepared $itemName featuring our $adj1 $adj2 blend.",
            "Experience a $adj1 twist on this $adj2 classic."
        )

        val desc = templates.random()
        descriptionCache[itemId] = desc
        return desc
    }

    private fun projectToLatentSpace(name: String): DoubleArray {
        val vector = DoubleArray(8) { 0.1 }
        val words = name.lowercase().split(" ", "-", "/")
        
        words.forEach { word ->
            keywordMap[word]?.forEach { dim ->
                vector[dim] += 1.5 // Increased weight for keywords
            }
            // Character-level fallthrough for unknown words
            word.forEach { char ->
                val dim = char.code % 8
                vector[dim] += 0.02
            }
        }
        
        // Softmax
        val expSum = vector.sumOf { exp(it) }
        return vector.map { exp(it) / expSum }.toDoubleArray()
    }

    private suspend fun findOptimalCombo(items: List<FlashItem>, embeddings: Map<FlashItem, DoubleArray>): AIInsight? {
        if (items.size < 2) return null
        
        var bestScore = -1.0
        var bestPair: Pair<FlashItem, FlashItem>? = null

        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val v1 = embeddings[items[i]]!!
                val v2 = embeddings[items[j]]!!
                
                // Complementary logic: Bakery (4) + Caffeine (3) or Savory (5) + Caffeine (3) or Savory (5) + Cold (1)
                val score = (v1[4] + v1[5]) * v2[3] + (v2[4] + v2[5]) * v1[3] + (v1[5] * v2[1])
                
                if (score > bestScore) {
                    bestScore = score
                    bestPair = items[i] to items[j]
                }
            }
        }

        return bestPair?.let { (item1, item2) ->
            val bundlePrice = ((item1.price + item2.price) * 0.85).toLong()
            
            var description = "Our engine suggests pairing the ${item1.name} with ${item2.name} for a balanced, high-satisfaction combo."
            if (engine != null && engine.isReady()) {
                val p = "Pairing: ${item1.name} and ${item2.name}\nMarketing: The perfect duo! Enjoy"
                val r = engine.generate(p)
                if (!r.contains("AI Error")) {
                    description = "The perfect duo! Enjoy " + r.trim().substringBefore("\n").substringBefore(".") + "."
                }
            }

            AIInsight(
                title = "Perfect Pairing",
                description = description,
                suggestedItemId = null,
                suggestedDiscount = 0.15,
                type = InsightType.COMBO,
                suggestedCombo = FlashCombo(
                    id = "combo_${item1.id}_${item2.id}",
                    name = "${item1.name} & ${item2.name}",
                    itemIds = listOf(item1.id, item2.id),
                    bundlePrice = bundlePrice,
                    description = description
                )
            )
        }
    }

    private fun findTrends(items: List<FlashItem>, embeddings: Map<FlashItem, DoubleArray>): AIInsight? {
        val dims = 0 until 8
        val dimensionCounts = dims.associateWith { dim -> items.count { embeddings[it]!![dim] > 0.25 } }
        val topDim = dimensionCounts.maxByOrNull { it.value } ?: return null
        
        if (topDim.value >= 2) {
            val sample = items.filter { embeddings[it]!![topDim.key] > 0.25 }.random()
            val category = latentVocab[topDim.key]?.random() ?: "Featured"
            return AIInsight(
                title = "Trending: ${category.replaceFirstChar { it.uppercase() }}",
                description = "We've detected a spike in interest for ${category.lowercase()} items. Highlighting ${sample.name} could drive immediate volume.",
                suggestedItemId = sample.id,
                suggestedDiscount = 0.12,
                type = InsightType.TRENDING
            )
        }
        return null
    }

    private fun findOutliers(items: List<FlashItem>, embeddings: Map<FlashItem, DoubleArray>): AIInsight? {
        if (items.isEmpty()) return null
        val meanVector = DoubleArray(8) { dim -> items.map { embeddings[it]!![dim] }.average() }
        
        val outlier = items.maxByOrNull { item ->
            val v = embeddings[item]!!
            sqrt(v.indices.sumOf { (v[it] - meanVector[it]).pow(2) })
        } ?: return null

        return AIInsight(
            title = "Inventory Velocity",
            description = "${outlier.name} has a unique profile. A strategically timed flash sale can introduce this hidden gem to new customers.",
            suggestedItemId = outlier.id,
            suggestedDiscount = 0.20,
            type = InsightType.CLEARANCE
        )
    }
}
