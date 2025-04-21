package com.example.couchbaselite_syncgateway.ui.theme.createdocument

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.couchbaselite_syncgateway.R
import com.example.couchbaselite_syncgateway.data.database.DBManager
import com.couchbase.lite.MutableDocument
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class CreateActivity : AppCompatActivity() {

    private lateinit var editTextId: EditText
    private lateinit var editTextJson: EditText
    private lateinit var submitButton: Button
    private lateinit var dbManager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editTextId = findViewById(R.id.editTextId)
        editTextJson = findViewById(R.id.editTextJson)
        submitButton = findViewById(R.id.submitButton)

        val rootView: View = findViewById(R.id.createConstraintLayout)
        if (!rootView.isInEditMode) {
            try {
                dbManager = DBManager.getInstance(applicationContext)
                dbManager.createDb("demo")
                dbManager.createCollection("_default", "_default")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        submitButton.setOnClickListener {
            val docIdInput = editTextId.text.toString().trim()
            val jsonInput = editTextJson.text.toString().trim()

            if (jsonInput.isEmpty()) {
                Toast.makeText(this, "JSON input is required", Toast.LENGTH_SHORT).show()
            }

            // Check if user-entered ID already exists (only if an ID was provided)
            if (docIdInput.isNotEmpty()) {
                if (dbManager.collection?.getDocument(docIdInput) != null) {
                    Toast.makeText(
                        this,
                        "Document with ID '$docIdInput' already exists.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            try {
                val mutableDocument = if (docIdInput.isNotEmpty()) {
                    MutableDocument(docIdInput)
                } else {
                    MutableDocument()
                }

                val trimmedInput = jsonInput.trim()
                if (trimmedInput.startsWith("[")) {
                    // Input is a JSON array; convert it to a Kotlin List.
                    val jsonArray = JSONArray(trimmedInput)
                    val list = mutableListOf<Any>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.get(i))
                    }
                    // Store the list under a default key "data".
                    mutableDocument.setValue("data", list)
                } else if (trimmedInput.startsWith("{")) {
                    // Input is a JSON object.
                    val jsonObject = JSONObject(trimmedInput)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = jsonObject.get(key)
                        if (value is JSONArray) {
                            // Convert JSONArray to a Kotlin List.
                            val list = mutableListOf<Any>()
                            for (i in 0 until value.length()) {
                                list.add(value.get(i))
                            }
                            mutableDocument.setValue(key, list)
                        } else {
                            mutableDocument.setValue(key, value)
                        }
                    }
                } else {
                    Toast.makeText(this, "Invalid JSON input", Toast.LENGTH_SHORT).show()
                }

                if (dbManager.saveDocument(mutableDocument)) {
                    Toast.makeText(
                        this,
                        "Document saved: ${mutableDocument.id}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, "Failed to save document", Toast.LENGTH_SHORT).show()
                }

                editTextId.text.clear()
                editTextJson.text.clear()
            } catch (e: JSONException) {
                Toast.makeText(this, "Invalid JSON input: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving document: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}