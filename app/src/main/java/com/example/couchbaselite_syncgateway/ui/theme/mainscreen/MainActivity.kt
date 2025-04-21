package com.example.couchbaselite_syncgateway.ui.theme.mainscreen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.couchbaselite_syncgateway.R
import com.example.couchbaselite_syncgateway.data.database.DBManager
import com.example.couchbaselite_syncgateway.ui.theme.createdocument.CreateActivity
import com.example.couchbaselite_syncgateway.ui.theme.login.LoginScreen
import com.example.couchbaselite_syncgateway.ui.theme.viewdocument.ResultsActivity

class MainActivity : AppCompatActivity() {
    private lateinit var insertButton: Button
    private lateinit var viewButton: Button
    private lateinit var deleteAllButton: Button
    private lateinit var logoutButton: Button
    private lateinit var dbManager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Bind buttons from layout
        insertButton = findViewById(R.id.insertButton)
        viewButton = findViewById(R.id.viewButton)
        deleteAllButton = findViewById(R.id.deleteAllButton)
        logoutButton = findViewById(R.id.logoutButton)
        dbManager = DBManager.getInstance(applicationContext)

        // Set up navigation buttons.
        insertButton.setOnClickListener {
            startActivity(Intent(this, CreateActivity::class.java))
        }
        viewButton.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        deleteAllButton.setOnClickListener {
            if (dbManager.deleteAllDocuments()) {
                Toast.makeText(this, "All documents deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to delete documents", Toast.LENGTH_SHORT).show()
            }
        }
        logoutButton.setOnClickListener {
            // Stop replication
            dbManager.stopReplication()
            // Navigate back to the LoginScreen
            val intent = Intent(this, LoginScreen::class.java)
            startActivity(intent)
        }
    }
}