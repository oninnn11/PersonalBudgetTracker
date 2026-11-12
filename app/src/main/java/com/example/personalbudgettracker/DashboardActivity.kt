
package com.example.personalbudgettracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper

    private var userId: Long = -1L
    private lateinit var userEmail: String

    private lateinit var welcomeText: TextView
    private lateinit var balanceText: TextView
    private lateinit var incomeText: TextView
    private lateinit var expenseText: TextView
    private lateinit var budgetText: TextView
    private lateinit var recentTransactionsText: TextView
    private lateinit var monthlyBudgetInput: EditText

    private val currencyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    private val currentMonth: String
        get() = SimpleDateFormat("yyyy-MM", Locale.US)
            .format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        databaseHelper = DatabaseHelper(this)

        userEmail = SessionManager.currentEmail(this).orEmpty()
        if (userEmail.isBlank()) {
            returnToLogin()
            return
        }
        userId = databaseHelper.getUserId(userEmail)
        if (userId == -1L) {
            SessionManager.signOut(this)
            returnToLogin()
            return
        }

        welcomeText = findViewById(R.id.tvWelcome)
        balanceText = findViewById(R.id.tvTotalBalance)
        incomeText = findViewById(R.id.tvMonthlyIncome)
        expenseText = findViewById(R.id.tvMonthlyExpenses)
        budgetText = findViewById(R.id.tvRemainingBudget)
        recentTransactionsText =
            findViewById(R.id.tvRecentTransactions)

        monthlyBudgetInput = findViewById(R.id.etMonthlyBudget)

        val addTransactionButton =
            findViewById<Button>(R.id.btnAddTransaction)

        val historyButton =
            findViewById<Button>(R.id.btnViewHistory)

        val logoutButton =
            findViewById<Button>(R.id.btnLogout)

        findViewById<android.widget.ImageButton>(R.id.btnDashboardBack)
            .setOnClickListener { finish() }

        val saveBudgetButton =
            findViewById<Button>(R.id.btnSaveBudget)

        welcomeText.text = "Hello, ${databaseHelper.getUserName(userEmail) ?: "User"}!"

        addTransactionButton.setOnClickListener {
            if (userId == -1L) {
                showLoginError()
                return@setOnClickListener
            }

            val addIntent = Intent(
                this,
                AddTransactionActivity::class.java
            )

            addIntent.putExtra("user_email", userEmail)
            addIntent.putExtra("user_id", userId)
            startActivity(addIntent)
        }

        historyButton.setOnClickListener {
            if (userId == -1L) {
                showLoginError()
                return@setOnClickListener
            }

            val historyIntent = Intent(
                this,
                HistoryActivity::class.java
            )

            historyIntent.putExtra("user_id", userId)
            historyIntent.putExtra(
                "user_email",
                intent.getStringExtra("user_email")
            )
            startActivity(historyIntent)
        }

        saveBudgetButton.setOnClickListener {
            saveBudget()
        }

        logoutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out") { _, _ -> logOut() }
                .show()
        }

        if (userId != -1L) {
            loadDashboard()
        } else {
            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::databaseHelper.isInitialized && userId != -1L) {
            loadDashboard()
        }
    }

    private fun loadDashboard() {

        val balance = databaseHelper.getTotalBalance(userId, currentMonth)

        val monthlyIncome =
            databaseHelper.getMonthlyIncome(userId, currentMonth)

        val monthlyExpenses =
            databaseHelper.getMonthlyExpenses(userId, currentMonth)

        val monthlyBudget =
            databaseHelper.getMonthlyBudget(userId, currentMonth)

        val remainingBudget = monthlyBudget - monthlyExpenses

        balanceText.text = currencyFormat.format(balance)
        incomeText.text = currencyFormat.format(monthlyIncome)
        expenseText.text = currencyFormat.format(monthlyExpenses)

        budgetText.text = if (remainingBudget < 0) {
            "Over budget by ${currencyFormat.format(-remainingBudget)}"
        } else {
            currencyFormat.format(remainingBudget)
        }

        if (monthlyBudget > 0) {
            monthlyBudgetInput.setText(
                String.format(Locale.US, "%.2f", monthlyBudget)
            )
        } else {
            monthlyBudgetInput.setText("")
        }

        loadRecentTransactions()
    }

    private fun saveBudget() {

        val budgetTextValue =
            monthlyBudgetInput.text.toString().trim()

        if (budgetTextValue.isEmpty()) {
            monthlyBudgetInput.error = "Please enter your monthly budget."
            return
        }

        val amount = budgetTextValue.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            monthlyBudgetInput.error =
                "Enter a valid budget greater than zero."
            return
        }

        val result = databaseHelper.saveMonthlyBudget(
            userId,
            currentMonth,
            amount
        )

        if (result != -1L) {
            Toast.makeText(
                this,
                "Monthly budget saved successfully!",
                Toast.LENGTH_SHORT
            ).show()

            loadDashboard()
        } else {
            Toast.makeText(
                this,
                "Unable to save budget. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadRecentTransactions() {

        val records = databaseHelper.getTransactions(userId, "All")
            .take(5)

        if (records.isEmpty()) {
            recentTransactionsText.text =
                "No transactions yet. Start by adding one!"
            return
        }

        val recentText = StringBuilder()

        for (record in records) {

            val sign = if (record.transactionType == "Income") {
                "+"
            } else {
                "-"
            }

            recentText.append(
                "$sign${currencyFormat.format(record.amount)}\n"
            )

            recentText.append(
                "${record.transactionType} • ${record.category}\n"
            )

            recentText.append(
                "${record.description.ifBlank { "No description" }}\n"
            )

            recentText.append(
                "${record.transactionDate}\n\n"
            )
        }

        recentTransactionsText.text = recentText.toString().trim()
    }

    private fun showLoginError() {
        Toast.makeText(
            this,
            "Please log in again.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun returnToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun logOut() {
        SessionManager.signOut(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onDestroy() {
        databaseHelper.close()
        super.onDestroy()
    }
}
