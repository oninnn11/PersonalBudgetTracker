
package com.example.personalbudgettracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper
    private var selectedDate = ""
    private var editingTransactionId: Long = -1L
    private var userEmail: String = ""
    private var userId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        databaseHelper = DatabaseHelper(this)

        userEmail = SessionManager.currentEmail(this).orEmpty()
        userId = if (userEmail.isBlank()) -1L else databaseHelper.getUserId(userEmail)
        if (userId == -1L) {
            SessionManager.signOut(this)
            startActivity(android.content.Intent(this, LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        editingTransactionId = intent.getLongExtra(
            "transaction_id",
            -1L
        )

        val amountInput = findViewById<EditText>(R.id.etAmount)
        val descriptionInput = findViewById<EditText>(R.id.etDescription)

        val transactionTypeGroup =
            findViewById<RadioGroup>(R.id.rgTransactionType)

        val paymentMethodGroup =
            findViewById<RadioGroup>(R.id.rgPaymentMethod)

        val foodCheckBox = findViewById<CheckBox>(R.id.cbFood)
        val transportationCheckBox =
            findViewById<CheckBox>(R.id.cbTransportation)
        val educationCheckBox =
            findViewById<CheckBox>(R.id.cbEducation)
        val entertainmentCheckBox =
            findViewById<CheckBox>(R.id.cbEntertainment)

        val dateText = findViewById<TextView>(R.id.tvSelectedDate)
        val chooseDateButton = findViewById<Button>(R.id.btnChooseDate)
        val saveButton = findViewById<Button>(R.id.btnSaveTransaction)
        findViewById<android.widget.ImageButton>(R.id.btnTransactionBack)
            .setOnClickListener { finish() }
        if (editingTransactionId != -1L) {

            // Change the screen title and save button text.
            saveButton.text = "Update Transaction"

            // Load the saved transaction values.
            amountInput.setText(
                intent.getDoubleExtra("amount", 0.0).toString()
            )

            descriptionInput.setText(
                intent.getStringExtra("description") ?: ""
            )

            val savedType = intent.getStringExtra("transaction_type")

            transactionTypeGroup.check(
                if (savedType == "Income") R.id.rbIncome
                else R.id.rbExpense
            )

            val savedPayment = intent.getStringExtra("payment_method")

            val paymentRadioId = when (savedPayment) {
                "GCash" -> R.id.rbGCash
                "Maya" -> R.id.rbMaya
                "Bank Transfer" -> R.id.rbBank
                else -> R.id.rbCash
            }

            paymentMethodGroup.check(paymentRadioId)

            val savedCategories = (
                    intent.getStringExtra("category") ?: ""
                    ).split(",").map { it.trim() }

            foodCheckBox.isChecked = "Food" in savedCategories
            transportationCheckBox.isChecked =
                "Transportation" in savedCategories
            educationCheckBox.isChecked = "Education" in savedCategories
            entertainmentCheckBox.isChecked =
                "Entertainment" in savedCategories

            selectedDate = intent.getStringExtra("transaction_date")
                ?: selectedDate

            dateText.text = "Selected date: $selectedDate"
        }

        // Set today's date as the default date.
        val today = Calendar.getInstance()
        if (selectedDate.isBlank()) updateSelectedDate(today, dateText)

        chooseDateButton.setOnClickListener {
            val year = today.get(Calendar.YEAR)
            val month = today.get(Calendar.MONTH)
            val day = today.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val chosenDate = Calendar.getInstance()
                    chosenDate.set(
                        selectedYear,
                        selectedMonth,
                        selectedDay
                    )
                    updateSelectedDate(chosenDate, dateText)
                },
                year,
                month,
                day
            ).show()
        }

        saveButton.setOnClickListener {

            if (!saveButton.isEnabled) return@setOnClickListener

            if (userEmail.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "User account not found. Please log in again.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val amountText = amountInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()

            if (amountText.isEmpty()) {
                amountInput.error = "Please enter an amount."
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                amountInput.error = "Enter a valid amount greater than zero."
                return@setOnClickListener
            }

            val selectedTypeId = transactionTypeGroup.checkedRadioButtonId

            val transactionType = when (selectedTypeId) {
                R.id.rbIncome -> "Income"
                R.id.rbExpense -> "Expense"
                else -> {
                    Toast.makeText(
                        this,
                        "Please select a transaction type.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            val selectedCategories = mutableListOf<String>()

            if (foodCheckBox.isChecked) {
                selectedCategories.add("Food")
            }

            if (transportationCheckBox.isChecked) {
                selectedCategories.add("Transportation")
            }

            if (educationCheckBox.isChecked) {
                selectedCategories.add("Education")
            }

            if (entertainmentCheckBox.isChecked) {
                selectedCategories.add("Entertainment")
            }

            if (selectedCategories.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please select at least one category.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val category = selectedCategories.joinToString(", ")

            val selectedPaymentId = paymentMethodGroup.checkedRadioButtonId

            val paymentMethod = when (selectedPaymentId) {
                R.id.rbCash -> "Cash"
                R.id.rbGCash -> "GCash"
                R.id.rbMaya -> "Maya"
                R.id.rbBank -> "Bank Transfer"
                else -> {
                    Toast.makeText(
                        this,
                        "Please select a payment method.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            val result: Long

            saveButton.isEnabled = false

            if (editingTransactionId != -1L) {

                val updatedRows = databaseHelper.updateTransaction(
                    editingTransactionId,
                    userId,
                    amount,
                    transactionType,
                    category,
                    paymentMethod,
                    description,
                    selectedDate
                )

                result = if (updatedRows > 0) 1L else -1L

            } else {

                result = databaseHelper.insertTransaction(
                    userId,
                    amount,
                    transactionType,
                    category,
                    paymentMethod,
                    description,
                    selectedDate
                )
            }

            if (result != -1L) {
                val message = if (editingTransactionId != -1L) {
                    "Transaction updated successfully!"
                } else {
                    "Transaction saved successfully!"
                }
                Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } else {
                saveButton.isEnabled = true
                Toast.makeText(
                    this,
                    "Unable to save changes. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateSelectedDate(
        calendar: Calendar,
        dateText: TextView
    ) {
        val formatter = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        )

        selectedDate = formatter.format(calendar.time)
        dateText.text = "Selected date: $selectedDate"
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }
}
