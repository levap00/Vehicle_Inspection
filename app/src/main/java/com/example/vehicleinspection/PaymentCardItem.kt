package com.example.vehicleinspection

data class PaymentCardItem(
    val name: String,
    val expiry: String,
    val imageRes: Int,
    val pin: String = "1234"
)
