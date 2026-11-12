
package com.example.personalbudgettracker

data class TransactionItem(
    val id: Long,
    val amount: Double,
    val transactionType: String,
    val category: String,
    val paymentMethod: String,
    val description: String,
    val transactionDate: String
)
