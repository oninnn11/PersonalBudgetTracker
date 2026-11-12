
package com.example.personalbudgettracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteConstraintException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION

) {

    companion object {
        private const val DATABASE_NAME = "personal_budget.db"
        private const val DATABASE_VERSION = 2

        // Users table
        private const val TABLE_USERS = "users"

        private const val COL_USER_ID = "user_id"
        private const val COL_FULL_NAME = "full_name"
        private const val COL_EMAIL = "email"
        private const val COL_PASSWORD_HASH = "password_hash"
        private const val COL_PASSWORD_SALT = "password_salt"
        private const val COL_CREATED_AT = "created_at"

        // Transactions table
        private const val TABLE_TRANSACTIONS = "transactions"

        private const val COL_TRANSACTION_ID = "transaction_id"
        private const val COL_AMOUNT = "amount"
        private const val COL_TRANSACTION_TYPE = "transaction_type"
        private const val COL_CATEGORY = "category"
        private const val COL_PAYMENT_METHOD = "payment_method"
        private const val COL_DESCRIPTION = "description"
        private const val COL_TRANSACTION_DATE = "transaction_date"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {

        // Create Users Table
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FULL_NAME TEXT NOT NULL,
                $COL_EMAIL TEXT NOT NULL COLLATE NOCASE UNIQUE,
                $COL_PASSWORD_HASH TEXT NOT NULL,
                $COL_PASSWORD_SALT TEXT NOT NULL,
                $COL_CREATED_AT TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        db.execSQL(createUsersTable)

        // Create Transactions Table
        val createTransactionsTable = """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TRANSACTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USER_ID INTEGER NOT NULL,
                $COL_AMOUNT REAL NOT NULL,
                $COL_TRANSACTION_TYPE TEXT NOT NULL
                    CHECK ($COL_TRANSACTION_TYPE IN ('Income', 'Expense')),
                $COL_CATEGORY TEXT NOT NULL,
                $COL_PAYMENT_METHOD TEXT NOT NULL,
                $COL_DESCRIPTION TEXT,
                $COL_TRANSACTION_DATE TEXT NOT NULL,
                $COL_CREATED_AT TEXT DEFAULT CURRENT_TIMESTAMP,

                FOREIGN KEY ($COL_USER_ID)
                REFERENCES $TABLE_USERS($COL_USER_ID)
                ON DELETE CASCADE
            )
        """.trimIndent()

        db.execSQL(createTransactionsTable)

        db.execSQL("""
    CREATE TABLE IF NOT EXISTS budgets (
        budget_id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        month_key TEXT NOT NULL,
        budget_amount REAL NOT NULL,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(user_id, month_key),
        FOREIGN KEY(user_id) REFERENCES users(user_id)
            ON DELETE CASCADE
    )
""".trimIndent())
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS budgets (
                budget_id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                month_key TEXT NOT NULL,
                budget_amount REAL NOT NULL,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(user_id, month_key),
                FOREIGN KEY(user_id) REFERENCES users(user_id)
                    ON DELETE CASCADE
            )
        """.trimIndent())
        }
    }

    // Generate a secure password hash
    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): String {

        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            120000,
            256
        )

        val factory = SecretKeyFactory.getInstance(
            "PBKDF2WithHmacSHA1"
        )

        val hash = factory.generateSecret(spec).encoded

        spec.clearPassword()

        return Base64.encodeToString(
            hash,
            Base64.NO_WRAP
        )
    }

    // Register a new user
    // Returns user ID, or -1 if email already exists
    fun registerUser(
        fullName: String,
        email: String,
        password: String
    ): Long {

        val db = writableDatabase

        return try {

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            val passwordHash = hashPassword(password, salt)

            val values = ContentValues().apply {
                put(COL_FULL_NAME, fullName.trim())
                put(COL_EMAIL, email.trim().lowercase())
                put(COL_PASSWORD_HASH, passwordHash)
                put(
                    COL_PASSWORD_SALT,
                    Base64.encodeToString(salt, Base64.NO_WRAP)
                )
            }

            db.insertOrThrow(
                TABLE_USERS,
                null,
                values
            )

        } catch (e: SQLiteConstraintException) {

            -1L

        } finally {

            db.close()
        }
    }

    // Verify login credentials
    fun loginUser(
        email: String,
        password: String
    ): Boolean = authenticateUser(email, password) == AuthenticationResult.SUCCESS

    fun authenticateUser(
        email: String,
        password: String
    ): AuthenticationResult {

        val db = readableDatabase

        return try {

            val cursor = db.query(
                TABLE_USERS,
                arrayOf(
                    COL_PASSWORD_HASH,
                    COL_PASSWORD_SALT
                ),
                "$COL_EMAIL = ?",
                arrayOf(email.trim().lowercase()),
                null,
                null,
                null
            )

            cursor.use {

                if (!it.moveToFirst()) {
                    return@use AuthenticationResult.ACCOUNT_NOT_FOUND
                }

                val savedHash = it.getString(
                    it.getColumnIndexOrThrow(COL_PASSWORD_HASH)
                )

                val savedSalt = it.getString(
                    it.getColumnIndexOrThrow(COL_PASSWORD_SALT)
                )

                val salt = Base64.decode(
                    savedSalt,
                    Base64.NO_WRAP
                )

                val enteredHash = hashPassword(
                    password,
                    salt
                )

                if (java.security.MessageDigest.isEqual(
                    savedHash.toByteArray(Charsets.UTF_8),
                    enteredHash.toByteArray(Charsets.UTF_8)
                )) AuthenticationResult.SUCCESS else AuthenticationResult.INVALID_PASSWORD
            }

        } finally {

            db.close()
        }
    }

    fun getUserName(email: String): String? {

        val db = readableDatabase

        return try {

            val cursor = db.query(
                TABLE_USERS,
                arrayOf(COL_FULL_NAME),
                "$COL_EMAIL = ?",
                arrayOf(email.trim().lowercase()),
                null,
                null,
                null
            )

            cursor.use {

                if (it.moveToFirst()) {
                    it.getString(
                        it.getColumnIndexOrThrow(COL_FULL_NAME)
                    )
                } else {
                    null
                }
            }

        } finally {
            db.close()
        }
    }
    fun getUserId(email: String): Long {
        val db = readableDatabase

        val cursor = db.query(
            "users",
            arrayOf("user_id"),
            "email = ?",
            arrayOf(email.trim().lowercase()),
            null,
            null,
            null
        )

        cursor.use {
            return if (it.moveToFirst()) {
                it.getLong(it.getColumnIndexOrThrow("user_id"))
            } else {
                -1L
            }
        }
    }

    fun insertTransaction(
        userId: Long,
        amount: Double,
        transactionType: String,
        category: String,
        paymentMethod: String,
        description: String,
        transactionDate: String
    ): Long {
        val db = writableDatabase

        val values = android.content.ContentValues().apply {
            put("user_id", userId)
            put("amount", amount)
            put("transaction_type", transactionType)
            put("category", category)
            put("payment_method", paymentMethod)
            put("description", description)
            put("transaction_date", transactionDate)
        }

        return db.insert("transactions", null, values)
    }


    fun getTransactions(
        userId: Long,
        filter: String = "All"
    ): List<TransactionItem> {

        val db = readableDatabase
        val transactions = mutableListOf<TransactionItem>()

        val selection: String
        val selectionArgs: Array<String>

        if (filter == "All") {
            selection = "user_id = ?"
            selectionArgs = arrayOf(userId.toString())
        } else {
            selection = "user_id = ? AND transaction_type = ?"
            selectionArgs = arrayOf(userId.toString(), filter)
        }

        val cursor = db.query(
            "transactions",
            arrayOf(
                "transaction_id",
                "amount",
                "transaction_type",
                "category",
                "payment_method",
                "description",
                "transaction_date"
            ),
            selection,
            selectionArgs,
            null,
            null,
            "transaction_date DESC, transaction_id DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                transactions.add(
                    TransactionItem(
                        id = it.getLong(
                            it.getColumnIndexOrThrow("transaction_id")
                        ),
                        amount = it.getDouble(
                            it.getColumnIndexOrThrow("amount")
                        ),
                        transactionType = it.getString(
                            it.getColumnIndexOrThrow("transaction_type")
                        ),
                        category = it.getString(
                            it.getColumnIndexOrThrow("category")
                        ),
                        paymentMethod = it.getString(
                            it.getColumnIndexOrThrow("payment_method")
                        ),
                        description = it.getString(
                            it.getColumnIndexOrThrow("description")
                        ) ?: "",
                        transactionDate = it.getString(
                            it.getColumnIndexOrThrow("transaction_date")
                        )
                    )
                )
            }
        }

        return transactions
    }

    /** Current month's starting budget plus the existing all-time transaction balance. */
    fun getTotalBalance(userId: Long, monthKey: String): Double {
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT
            COALESCE((
                SELECT budget_amount
                FROM budgets
                WHERE user_id = ? AND month_key = ?
            ), 0) + COALESCE(SUM(
                CASE
                    WHEN transaction_type = 'Income' THEN amount
                    WHEN transaction_type = 'Expense' THEN -amount
                    ELSE 0
                END
            ), 0)
        FROM transactions
        WHERE user_id = ?
        """.trimIndent(),
            arrayOf(userId.toString(), monthKey, userId.toString())
        )

        cursor.use {
            return if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
    }

    fun getMonthlyIncome(userId: Long, monthKey: String): Double {
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE user_id = ?
        AND transaction_type = 'Income'
        AND transaction_date LIKE ?
        """.trimIndent(),
            arrayOf(userId.toString(), "$monthKey%")
        )

        cursor.use {
            return if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
    }

    fun getMonthlyExpenses(userId: Long, monthKey: String): Double {
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE user_id = ?
        AND transaction_type = 'Expense'
        AND transaction_date LIKE ?
        """.trimIndent(),
            arrayOf(userId.toString(), "$monthKey%")
        )

        cursor.use {
            return if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
    }

    fun getMonthlyBudget(userId: Long, monthKey: String): Double {
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT budget_amount
        FROM budgets
        WHERE user_id = ? AND month_key = ?
        """.trimIndent(),
            arrayOf(userId.toString(), monthKey)
        )

        cursor.use {
            return if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
    }

    fun saveMonthlyBudget(
        userId: Long,
        monthKey: String,
        budgetAmount: Double
    ): Long {
        val db = writableDatabase

        val values = android.content.ContentValues().apply {
            put("user_id", userId)
            put("month_key", monthKey)
            put("budget_amount", budgetAmount)
        }

        return try {
            db.insertWithOnConflict(
                "budgets",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } finally {
            db.close()
        }
    }

    fun updateTransaction(
        transactionId: Long,
        userId: Long,
        amount: Double,
        transactionType: String,
        category: String,
        paymentMethod: String,
        description: String,
        transactionDate: String
    ): Int {

        val db = writableDatabase

        val values = android.content.ContentValues().apply {
            put("amount", amount)
            put("transaction_type", transactionType)
            put("category", category)
            put("payment_method", paymentMethod)
            put("description", description)
            put("transaction_date", transactionDate)
        }

        return db.update(
            "transactions",
            values,
            "transaction_id = ? AND user_id = ?",
            arrayOf(transactionId.toString(), userId.toString())
        )
    }

    fun deleteTransaction(
        transactionId: Long,
        userId: Long
    ): Int {

        val db = writableDatabase

        return db.delete(
            "transactions",
            "transaction_id = ? AND user_id = ?",
            arrayOf(transactionId.toString(), userId.toString())
        )
    }




}
