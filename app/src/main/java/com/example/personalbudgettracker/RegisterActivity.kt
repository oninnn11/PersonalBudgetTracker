
package com.example.personalbudgettracker

import android.os.Bundle
import android.content.Intent
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        databaseHelper = DatabaseHelper(this)

        val fullName = findViewById<EditText>(R.id.etFullName)
        val email = findViewById<EditText>(R.id.etRegisterEmail)
        val password = findViewById<EditText>(R.id.etRegisterPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)

        val registerButton = findViewById<Button>(R.id.btnRegister)
        val backButton = findViewById<android.widget.ImageButton>(R.id.btnRegisterBack)

        registerButton.setOnClickListener {

            val nameValue = fullName.text.toString().trim()
            val emailValue = email.text.toString().trim()
            val passwordValue = password.text.toString()
            val confirmValue = confirmPassword.text.toString()

            when {

                nameValue.isEmpty() ||
                        emailValue.isEmpty() ||
                        passwordValue.isEmpty() ||
                        confirmValue.isEmpty() -> {

                    Toast.makeText(
                        this,
                        "Please complete all fields.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                !Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() -> {

                    email.error = "Enter a valid email address"
                }

                passwordValue.length < 6 -> {

                    password.error = "Password must be at least 6 characters"
                }

                passwordValue != confirmValue -> {

                    confirmPassword.error = "Passwords do not match"
                }

                else -> {

                    val result = databaseHelper.registerUser(
                        nameValue,
                        emailValue,
                        passwordValue
                    )

                    if (result != -1L) {
                        SessionManager.signOut(this)

                        Toast.makeText(
                            this,
                            "Account successfully registered!",
                            Toast.LENGTH_LONG
                        ).show()

                        // Return to Login Screen
                        val intent = Intent(
                            this,
                            LoginActivity::class.java
                        )

                        intent.putExtra(
                            "registered_email",
                            emailValue
                        )

                        intent.flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP

                        startActivity(intent)
                        finish()

                    } else {

                        Toast.makeText(
                            this,
                            "Email already registered. Please use another email.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseHelper.close()
    }
}
