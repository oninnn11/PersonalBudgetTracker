
package com.example.personalbudgettracker

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale
import android.app.AlertDialog


class HistoryActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var transactionList: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var countText: TextView

    private var userId: Long = -1L
    private var userEmail: String? = null
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        databaseHelper = DatabaseHelper(this)

        userEmail = SessionManager.currentEmail(this)
        if (userEmail.isNullOrBlank()) {
            returnToLogin()
            return
        }
        userId = databaseHelper.getUserId(userEmail!!)
        if (userId == -1L) {
            SessionManager.signOut(this)
            returnToLogin()
            return
        }

        transactionList = findViewById(R.id.layoutTransactionList)
        emptyMessage = findViewById(R.id.tvEmptyHistory)
        countText = findViewById(R.id.tvHistoryCount)

        val filterGroup = findViewById<RadioGroup>(
            R.id.rgHistoryFilter
        )

        val backButton = findViewById<android.widget.ImageButton>(R.id.btnHistoryBack)

        filterGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.rbFilterIncome -> "Income"
                R.id.rbFilterExpense -> "Expense"
                else -> "All"
            }

            loadTransactions()
        }

        backButton.setOnClickListener {
            finish()
        }

        if (userId == -1L) {
            emptyMessage.text = "Unable to identify your account. Please log in again."
            emptyMessage.visibility = View.VISIBLE
            countText.text = "Transactions: 0"
        } else {
            loadTransactions()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::databaseHelper.isInitialized && userId != -1L) {
            loadTransactions()
        }
    }

    private fun loadTransactions() {

        transactionList.removeAllViews()

        val records = databaseHelper.getTransactions(
            userId,
            currentFilter
        )

        countText.text = "Transactions: ${records.size}"

        if (records.isEmpty()) {
            emptyMessage.text = when (currentFilter) {
                "Income" -> "No income transactions found."
                "Expense" -> "No expense transactions found."
                else -> "No transactions found. Add your first transaction!"
            }

            emptyMessage.visibility = View.VISIBLE
            return
        }

        emptyMessage.visibility = View.GONE

        for (record in records) {
            addTransactionCard(record)
        }
    }

    private fun addTransactionCard(record: TransactionItem) {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20f
                setStroke(1, Color.LTGRAY)
            }
        }

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 14
        }

        val amountFormat = NumberFormat.getCurrencyInstance(
            Locale("en", "PH")
        )

        val amountText = TextView(this).apply {
            text = "${record.transactionType}: ${amountFormat.format(record.amount)}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (record.transactionType == "Income") {
                    Color.rgb(22, 163, 74)
                } else {
                    Color.rgb(220, 38, 38)
                }
            )
        }

        val categoryText = TextView(this).apply {
            text = "Category: ${record.category}"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 0)
        }

        val paymentText = TextView(this).apply {
            text = "Payment: ${record.paymentMethod}"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 6, 0, 0)
        }

        val descriptionText = TextView(this).apply {
            text = "Description: ${
                record.description.ifBlank { "No description" }
            }"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 6, 0, 0)
        }

        val dateText = TextView(this).apply {
            text = "Date: ${record.transactionDate}"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, 6, 0, 0)
        }


        card.addView(amountText)
        card.addView(categoryText)
        card.addView(paymentText)
        card.addView(descriptionText)
        card.addView(dateText)

// Create a row for Edit and Delete buttons.
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }

        val editButton = Button(this).apply {
            text = "Edit"
            isAllCaps = false
        }

        val deleteButton = Button(this).apply {
            text = "Delete"
            isAllCaps = false
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = 8
        }

        actionRow.addView(editButton, buttonParams)

        val deleteParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )

        actionRow.addView(deleteButton, deleteParams)

        card.addView(actionRow)

// Open the existing Add Transaction screen in edit mode.
        editButton.setOnClickListener {
            openEditScreen(record)
        }

// Ask for confirmation before deleting.
        deleteButton.setOnClickListener {
            confirmDelete(record)
        }

        transactionList.addView(card, cardParams)

    }

    private fun openEditScreen(record: TransactionItem) {

        val editIntent = android.content.Intent(
            this,
            AddTransactionActivity::class.java
        )

        editIntent.putExtra("user_email", userEmail)
        editIntent.putExtra("user_id", userId)
        editIntent.putExtra("transaction_id", record.id)
        editIntent.putExtra("amount", record.amount)
        editIntent.putExtra("transaction_type", record.transactionType)
        editIntent.putExtra("category", record.category)
        editIntent.putExtra("payment_method", record.paymentMethod)
        editIntent.putExtra("description", record.description)
        editIntent.putExtra("transaction_date", record.transactionDate)

        startActivity(editIntent)
    }

    private fun confirmDelete(record: TransactionItem) {

        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage(
                "Are you sure you want to delete this transaction?"
            )
            .setPositiveButton("Delete") { _, _ ->

                val result = databaseHelper.deleteTransaction(
                    record.id,
                    userId
                )

                if (result > 0) {

                    android.widget.Toast.makeText(
                        this,
                        "Transaction deleted successfully!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    loadTransactions()

                } else {

                    android.widget.Toast.makeText(
                        this,
                        "Unable to delete transaction.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun returnToLogin() {
        startActivity(android.content.Intent(this, LoginActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }


    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }
}
