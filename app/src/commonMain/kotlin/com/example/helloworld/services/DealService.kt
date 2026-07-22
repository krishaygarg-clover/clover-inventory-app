package com.example.helloworld.services

import com.example.helloworld.models.FlashDeal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DealService {
    private val _activeDeals = MutableStateFlow<List<FlashDeal>>(emptyList())
    val activeDeals: StateFlow<List<FlashDeal>> = _activeDeals.asStateFlow()

    fun publishDeal(deal: FlashDeal) {
        val current = _activeDeals.value.toMutableList()
        // Replace if already exists for same item, otherwise add
        val index = current.indexOfFirst { it.itemId == deal.itemId }
        if (index != -1) {
            current[index] = deal
        } else {
            current.add(deal)
        }
        _activeDeals.value = current
    }

    fun removeDeal(itemId: String) {
        _activeDeals.value = _activeDeals.value.filterNot { it.itemId == itemId }
    }

    fun clearAllDeals() {
        _activeDeals.value = emptyList()
    }
}
