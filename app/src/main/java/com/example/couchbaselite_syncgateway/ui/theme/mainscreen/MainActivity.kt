package com.example.couchbaselite_syncgateway.ui.theme.mainscreen

import android.content.Intent
import android.os.Bundle
import android.text.LoginFilter.UsernameFilterGMail
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.couchbaselite_syncgateway.R
import com.example.couchbaselite_syncgateway.data.database.DBManager
import com.example.couchbaselite_syncgateway.ui.theme.createdocument.CreateActivity
import com.example.couchbaselite_syncgateway.ui.theme.viewdocument.ResultsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var insertButton: Button
    private lateinit var viewButton: Button
    private lateinit var dbManager: DBManager
    private lateinit var deleteAllButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        insertButton = findViewById(R.id.insertButton)
        viewButton = findViewById(R.id.viewButton)
        deleteAllButton = findViewById(R.id.deleteAllButton)

        // Initialize the database manager and create the database and collection.
        dbManager = DBManager.getInstance(applicationContext)
        dbManager.createDb("demo")
        // If you plan on using custom scopes, verify your Sync Gateway supports them.
        dbManager.createCollection("_default", "_default")

        if (dbManager.collection == null) {
            Toast.makeText(this, "Collection not initialized!", Toast.LENGTH_LONG).show()
            return
        }
        var username = "";
        var password = "";
        // Start replication with detailed logging.
        try {
            dbManager.startPushAndPullReplicationForCurrentUser(username, password)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting replication: ${e.message}")
        }

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
    }
}
