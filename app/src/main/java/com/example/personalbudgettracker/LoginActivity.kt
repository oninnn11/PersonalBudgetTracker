
package com.example.personalbudgettracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        databaseHelper = DatabaseHelper(this)

        val activeEmail = SessionManager.currentEmail(this)
        if (!activeEmail.isNullOrBlank()) {
            if (databaseHelper.getUserId(activeEmail) != -1L) {
                openDashboard(activeEmail, showWelcome = false)
                return
            }
            SessionManager.signOut(this)
        }

        val email = findViewById<EditText>(R.id.etLoginEmail)
        val password = findViewById<EditText>(R.id.etLoginPassword)

        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnGoRegister)

        // Automatically fill email after registration
        applyRegisteredEmail(intent, email, password)

        loginButton.setOnClickListener {

            val userEmail = email.text.toString().trim()
            val userPassword = password.text.toString()

            when {

                userEmail.isEmpty() || userPassword.isEmpty() -> {

                    Toast.makeText(
                        this,
                        "Please enter your email and password.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> {

                    email.error = "Enter a valid email address"
                }

                else -> {

                    val result = databaseHelper.authenticateUser(
                        userEmail,
                        userPassword
                    )

                    when (result) {
                        AuthenticationResult.SUCCESS -> openDashboard(userEmail)
                        AuthenticationResult.ACCOUNT_NOT_FOUND -> email.error =
                            "No account found. Please register first."
                        AuthenticationResult.INVALID_PASSWORD -> password.error =
                            "Incorrect password. Please try again."
                    }
                }
            }
        }


        registerButton.setOnClickListener {

            val intent = Intent(
                this,
                RegisterActivity::class.java
            )

            startActivity(intent)
        }
    }

    private fun applyRegisteredEmail(
        source: Intent?,
        email: EditText,
        password: EditText
    ) {
        val registeredEmail = source?.getStringExtra("registered_email")
        if (!registeredEmail.isNullOrBlank()) {
            email.setText(registeredEmail)
            password.requestFocus()
        }
    }

    private fun openDashboard(email: String, showWelcome: Boolean = true) {
        SessionManager.signIn(this, email)
        val name = databaseHelper.getUserName(email) ?: "User"
        if (showWelcome) {
            Toast.makeText(this, "Login successful! Welcome back.", Toast.LENGTH_LONG).show()
        }
        startActivity(Intent(this, DashboardActivity::class.java).apply {
            putExtra("user_name", name)
            putExtra("user_email", email)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val email = findViewById<EditText>(R.id.etLoginEmail)
        val password = findViewById<EditText>(R.id.etLoginPassword)
        applyRegisteredEmail(intent, email, password)
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }
}
