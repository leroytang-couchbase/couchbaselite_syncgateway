package com.example.couchbaselite_syncgateway.ui.theme.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorActivityLevel
import com.example.couchbaselite_syncgateway.R
import com.example.couchbaselite_syncgateway.data.database.DBManager
import com.example.couchbaselite_syncgateway.ui.theme.mainscreen.MainActivity

class LoginScreen : AppCompatActivity() {

    private lateinit var dbManager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize DBManager and ensure database and collection are created.
        dbManager = DBManager.getInstance(applicationContext)
        dbManager.createDb("demo")
        dbManager.createCollection("_default", "_default")

        val usernameEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
            } else {
                attemptLogin(username, password)
            }
        }
    }

    private fun attemptLogin(username: String, password: String) {
        try {
            // Start replication using your DBManager method.
            // This function must return a Replicator instance.
            val replicator: Replicator = dbManager.startPushAndPullReplicationForCurrentUser(username, password)

            // Add a change listener to monitor the replicator status.
            replicator.addChangeListener { change ->
                Log.d("LoginScreen", "Replication activity level: ${change.status.activityLevel}")
                if (change.status.error != null) {
                    Log.e("LoginScreen", "Replication error: ${change.status.error?.message}", change.status.error)
                    runOnUiThread {
                        Toast.makeText(this, "Login failed: ${change.status.error?.message}", Toast.LENGTH_SHORT).show()
                    }
                } else if (change.status.activityLevel == ReplicatorActivityLevel.BUSY) {
                    // Successful login; navigate to MainActivity.
                    runOnUiThread {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LoginScreen", "Error during login: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}