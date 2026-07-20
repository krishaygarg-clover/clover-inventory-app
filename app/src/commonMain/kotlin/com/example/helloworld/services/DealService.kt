package com.example.helloworld.services

import com.example.helloworld.models.FlashDeal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DealService {
    private val _activeDeal = MutableStateFlow<FlashDeal?>(null)
    val activeDeal: StateFlow<FlashDeal?> = _activeDeal

    fun publishDeal(deal: FlashDeal?) {
        _activeDeal.value = deal
    }
}
