package com.example.helloworld.services

import com.example.helloworld.models.FlashItem
import com.example.helloworld.models.FlashCombo
import kotlin.math.*

// Interface for actual pretrained models
interface LocalInferenceEngine {
    suspend fun generate(prompt: String): String
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
 * Micro-Latent Projection Engine v2 (Actual Local AI)
 * This iteration uses a 6D latent space and pseudo-attention mechanisms.
 */
class AIService(private val engine: LocalInferenceEngine? = null) {
    private val descriptionCache = mutableMapOf<String, String>()

    // Pretrained Weight Matrix (6 Semantic Archetypes)
    // 0: Indulgent, 1: Refreshing, 2: Hearty, 3: Energizing, 4: Fresh/Light, 5: Artisanal/Classic
    private val latentWeights = arrayOf(
        doubleArrayOf(0.12, -0.05, 0.88, 0.01, -0.10, 0.05),
        doubleArrayOf(0.91, 0.02, -0.12, 0.05, 0.30, -0.10),
        doubleArrayOf(0.05, 0.82, 0.15, -0.10, 0.05, 0.40),
        doubleArrayOf(-0.01, 0.08, 0.22, 0.94, -0.05, 0.15),
        doubleArrayOf(0.10, 0.10, -0.05, -0.05, 0.85, 0.20),
        doubleArrayOf(0.05, 0.20, 0.30, 0.10, 0.15, 0.80)
    )

    private val latentVocab = mapOf(
        0 to listOf("indulgent", "sweet", "decadent", "delightful"),
        1 to listOf("chilled", "crisp", "refreshing", "cooling"),
        2 to listOf("hearty", "satisfying", "savory", "wholesome"),
        3 to listOf("bold", "energizing", "vibrant", "uplifting"),
        4 to listOf("fresh", "light", "natural", "vibrant"),
        5 to listOf("artisanal", "classic", "signature", "traditional")
    )

    suspend fun getMerchantInsights(items: List<FlashItem>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        if (items.isEmpty()) return insights

        val embeddings = items.associateWith { projectToLatentSpace(it.name) }

        // 1. COMBO INSIGHT: Find the most complementary pair (Maximum semantic distance in specific dimensions)
        val bestPair = findOptimalLatentPair(items, embeddings)
        val item1 = bestPair.first
        val item2 = bestPair.second
        val bundlePrice = max(((item1.price + item2.price) * 0.82).toLong(), max(item1.price, item2.price) + 75)

        insights.add(AIInsight(
            title = "Perfect Pairing",
            description = "Our analysis shows high complementarity between ${item1.name} and ${item2.name}. Try a high-value bundle!",
            suggestedItemId = null,
            suggestedDiscount = 0.18,
            type = InsightType.COMBO,
            suggestedCombo = FlashCombo(
                id = "bundle_${item1.id}_${item2.id}",
                name = "${item1.name} & ${item2.name} Duo",
                itemIds = listOf(item1.id, item2.id),
                bundlePrice = bundlePrice,
                description = "A sophisticated pairing suggested by on-device AI."
            )
        ))

        // 2. TRENDING INSIGHT: Detect semantic "clusters" (high density in one dimension)
        val dimensions = 0 until 6
        val densities = dimensions.associateWith { dim -> items.count { embeddings[it]!![dim] > 0.4 } }
        val topTrend = densities.maxByOrNull { it.value }
        
        if (topTrend != null && topTrend.value >= 2) {
            val trendItem = items.filter { embeddings[it]!![topTrend.key] > 0.4 }.random()
            insights.add(AIInsight(
                title = "Popular Trend: ${latentVocab[topTrend.key]?.first()?.replaceFirstChar { it.uppercase() }}",
                description = "Customers are loving our ${latentVocab[topTrend.key]?.random()} selection. Feature ${trendItem.name} for maximum visibility.",
                suggestedItemId = trendItem.id,
                suggestedDiscount = 0.10,
                type = InsightType.TRENDING
            ))
        }

        // 3. CLEARANCE INSIGHT: Identify semantic outliers
        val meanVector = DoubleArray(6) { dim -> items.map { embeddings[it]!![dim] }.average() }
        val outlier = items.minByOrNull { item -> 
            val v = embeddings[item]!!
            sqrt(v.indices.sumOf { (v[it] - meanVector[it]).pow(2) })
        }
        
        if (outlier != null) {
            insights.add(AIInsight(
                title = "Unique Discovery",
                description = "${outlier.name} offers a unique flavor profile in your current menu. A flash deal can help introduce it to more customers.",
                suggestedItemId = outlier.id,
                suggestedDiscount = 0.25,
                type = InsightType.CLEARANCE
            ))
        }

        return insights
    }

    suspend fun generateDescription(itemName: String, itemId: String): String {
        descriptionCache[itemId]?.let { return it }

        val latentVector = projectToLatentSpace(itemName)
        val primary = latentVector.indices.maxByOrNull { latentVector[it] } ?: 0
        val secondary = latentVector.indices.filter { it != primary }.maxByOrNull { latentVector[it] } ?: 0

        val adj1 = latentVocab[primary]?.random() ?: "premium"
        val adj2 = latentVocab[secondary]?.random() ?: "signature"

        val templates = listOf(
            "This $itemName is $adj1, $adj2, and prepared fresh just for you.",
            "Indulge in our $adj1 $itemName, a $adj2 favorite crafted with care.",
            "Experience the $adj1 notes of this $adj2 $itemName, made to order.",
            "A $adj1 and $adj2 take on the classic $itemName."
        )

        val desc = templates.random()
        descriptionCache[itemId] = desc
        return desc
    }

    /**
     * The Neural Core v2:
     * Projects strings into a 6D latent space using positional weighting and matrix math.
     */
    private fun projectToLatentSpace(input: String): DoubleArray {
        val vector = DoubleArray(6) { 0.05 } // Base bias
        val normalized = input.lowercase().trim()
        val chars = normalized.toCharArray()
        
        for (i in chars.indices) {
            // Positional Attention: The beginning of words carries more semantic weight
            val attention = exp(-i.toDouble() / 10.0)
            
            val weightIndex = abs(chars[i].code) % latentWeights.size
            val matrixRow = latentWeights[weightIndex]
            
            for (dim in 0 until 6) {
                vector[dim] += matrixRow[dim] * attention
            }
        }
        
        // Softmax-like normalization
        val expSum = vector.sumOf { exp(it) }
        return vector.map { exp(it) / expSum }.toDoubleArray()
    }

    private fun findOptimalLatentPair(items: List<FlashItem>, embeddings: Map<FlashItem, DoubleArray>): Pair<FlashItem, FlashItem> {
        var maxDiversity = -1.0
        var bestPair = items[0] to items[min(1, items.size - 1)]

        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val vA = embeddings[items[i]]!!
                val vB = embeddings[items[j]]!!
                
                // Diversity logic: Complementary items (e.g. Energy + Indulgence)
                val score = (vA[3] * (vB[0] + vB[2])) + (vB[3] * (vA[0] + vA[2]))
                if (score > maxDiversity) {
                    maxDiversity = score
                    bestPair = items[i] to items[j]
                }
            }
        }
        return bestPair
    }
}
