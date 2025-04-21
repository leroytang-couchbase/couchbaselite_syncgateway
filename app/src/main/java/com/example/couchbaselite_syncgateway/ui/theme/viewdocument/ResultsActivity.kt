package com.example.couchbaselite_syncgateway.ui.theme.viewdocument

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.DataSource
import com.couchbase.lite.Expression
import com.couchbase.lite.Meta
import com.couchbase.lite.QueryBuilder
import com.couchbase.lite.SelectResult
import com.example.couchbaselite_syncgateway.R
import com.example.couchbaselite_syncgateway.data.database.DBManager
import com.couchbase.lite.Collection as CBLCollection

class ResultsActivity : AppCompatActivity() {

    private lateinit var queryButton: Button
    private lateinit var saveButton: Button
    private lateinit var resultsTable: TableLayout
    private lateinit var dbManager: DBManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // These IDs must match those declared in activity_results.xml
        queryButton = findViewById(R.id.queryButton)
        saveButton = findViewById(R.id.saveButton)
        resultsTable = findViewById(R.id.resultsTable)

        dbManager = DBManager.getInstance(applicationContext)

        queryButton.setOnClickListener { queryAndPopulateTable() }
        saveButton.setOnClickListener { saveUpdatedRecords() }
    }

    private fun queryAndPopulateTable() {
        resultsTable.removeAllViews()
        try {
            val coll: CBLCollection = dbManager.collection ?: run {
                Toast.makeText(this, "No collection available", Toast.LENGTH_SHORT).show()
                return
            }
            // Query all documents and include Meta.id as "docId"
            val query = QueryBuilder.select(
                SelectResult.expression(Meta.id).`as`("docId"),
                SelectResult.all()
            ).from(DataSource.collection(coll))
            val resultSet = query.execute()
            val results = resultSet.allResults()
            Log.d("ResultsActivity", "Query returned ${results.size} documents")

            if (results.isEmpty()) {
                Toast.makeText(this, "No documents found", Toast.LENGTH_SHORT).show()
            }

            // Prepare union of keys (excluding "docId")
            val unionKeys = mutableSetOf<String>()
            val documents = mutableListOf<Map<String, Any?>>()
            for (result in results) {
                val resultMap = result.toMap()
                val docData = resultMap[coll.name] as? Map<*, *>
                if (docData != null) {
                    // Convert keys to String and create a mutable copy.
                    val stringKeyMap = docData.mapKeys { it.key.toString() }.toMutableMap()
                    // Save the document ID using the key "docId"
                    stringKeyMap["docId"] = resultMap["docId"]
                    documents.add(stringKeyMap)
                    unionKeys.addAll(stringKeyMap.keys)
                }
            }
            // Remove "docId" from editable keys.
            unionKeys.remove("docId")
            val sortedKeys = unionKeys.toList().sorted()

            // Build header row:
            val headerRow = TableRow(this)
            // First cell: non-editable header for docId with smaller weight.
            val headerDocId = TextView(this)
            headerDocId.text = "docId"
            headerDocId.setPadding(8, 8, 8, 8)
            headerRow.addView(headerDocId, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f))
            // Headers for the rest of the keys:
            for (key in sortedKeys) {
                val headerText = TextView(this)
                headerText.text = key
                headerText.setPadding(8, 8, 8, 8)
                headerRow.addView(headerText, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f))
            }
            // Header for the delete button column:
            val headerDelete = TextView(this)
            headerDelete.text = "Delete"
            headerDelete.setPadding(8, 8, 8, 8)
            headerRow.addView(headerDelete, TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT))
            resultsTable.addView(headerRow)

            // Build data rows:
            for (doc in documents) {
                val row = TableRow(this)
                // Store document ID in row tag for future updates.
                val docId = doc["docId"] as? String ?: ""
                row.tag = docId

                // First cell: non-editable docId TextView.
                val docIdView = TextView(this)
                docIdView.text = docId
                docIdView.setPadding(8, 8, 8, 8)
                row.addView(docIdView, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f))

                // Create an editable field for each remaining key.
                for (key in sortedKeys) {
                    val cellEditText = EditText(this)
                    cellEditText.setPadding(8, 8, 8, 8)
                    cellEditText.setText(doc[key]?.toString() ?: "")
                    row.addView(cellEditText, TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 2f))
                }
                // Add a trash icon button to allow deleting this document.
                val deleteButton = ImageButton(this)
                deleteButton.setImageResource(R.drawable.ic_trash)
                deleteButton.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
                deleteButton.setOnClickListener {
                    try {
                        val currentDocId = row.tag as? String
                        if (currentDocId != null) {
                            val doc = dbManager.collection?.getDocument(currentDocId)
                            if (doc != null) {
                                dbManager.collection?.delete(doc)
                                Toast.makeText(this, "Deleted document $currentDocId", Toast.LENGTH_SHORT).show()
                                queryAndPopulateTable() // Refresh table after deletion.
                            }
                        }
                    } catch (e: CouchbaseLiteException) {
                        Toast.makeText(this, "Error deleting document: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                row.addView(deleteButton)
                resultsTable.addView(row)
            }
        } catch (e: CouchbaseLiteException) {
            Toast.makeText(this, "Query error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveUpdatedRecords() {
        // Iterate over each row (skip header row at index 0)
        val rowCount = resultsTable.childCount
        for (i in 1 until rowCount) {
            val row = resultsTable.getChildAt(i) as TableRow
            // Retrieve the document ID stored in the row's tag.
            val docId = row.tag as? String ?: continue
            val updatedData = mutableMapOf<String, Any?>()
            // Get header row to determine the keys.
            val headerRow = resultsTable.getChildAt(0) as TableRow
            // Skip the first cell (docId) when saving updates.
            val cellCount = row.childCount - 1
            for (j in 1 until cellCount) {
                val headerView = headerRow.getChildAt(j) as TextView
                val key = headerView.text.toString()
                val cellView = row.getChildAt(j)
                if (cellView is EditText) {
                    updatedData[key] = cellView.text.toString()
                }
            }
            try {
                if (dbManager.updateDoc(docId, updatedData)) {
                    Toast.makeText(this, "Document $docId updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to update document $docId", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error updating document $docId: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
