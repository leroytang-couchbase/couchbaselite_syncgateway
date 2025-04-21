package com.example.couchbaselite_syncgateway.data.database

import android.content.Context
import android.util.Log
import androidx.lifecycle.asLiveData
import com.couchbase.lite.BasicAuthenticator
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.CouchbaseLite
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.DataSource
import com.couchbase.lite.Database
import com.couchbase.lite.DocumentFlag
import com.couchbase.lite.Expression
import com.couchbase.lite.ListenerToken
import com.couchbase.lite.Meta
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.QueryBuilder
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorActivityLevel
import com.couchbase.lite.ReplicatorConfiguration
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.SelectResult
import com.couchbase.lite.URLEndpoint
import com.couchbase.lite.replicatorChangesFlow
import kotlinx.coroutines.flow.map
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.atomic.AtomicReference
import com.couchbase.lite.Collection as CBLCollection

class DBManager private constructor() {
    var database: Database? = null
    var collection: CBLCollection? = null
    var syncGatewayEndpoint: String = "wss://xnjcgujv4ttuytrq.apps.cloud.couchbase.com:4984/demo"
    var dbToken: ListenerToken? = null

    // Add a replicator property to store the replicator instance.
    private var replicator: Replicator? = null

    private fun init(context: Context) {
        CouchbaseLite.init(context)
        Log.i(TAG, "CBL Initialized")
    }

    fun createDb(dbName: String) {
        database = Database(dbName)
        Log.i(TAG, "Database created: $dbName")
    }

    /**
     * Attempts to create a custom collection in the specified scope.
     * If creation returns null (because it already exists), retrieves the existing collection.
     */
    fun createCollection(collName: String, scopeName: String) {
        // Try to create a new collection in the custom scope.
        collection = database?.createCollection(collName, scopeName)
        if (collection == null) {
            // If creation returned null, the collection may already exist.
            collection = database?.getCollection(collName)
        }
        Log.i(TAG, "Collection created or retrieved: $collection")
    }

    fun retrieveDoc(docId: String) {
        collection?.getDocument(docId)
            ?.let { document ->
                Log.i(TAG, "Document ID :: $docId")
                Log.i(TAG, "Learning :: ${document.getString("language")}")
            }
            ?: Log.i(TAG, "No such document :: $docId")
    }

    fun updateDoc(docId: String, updatedData: Map<String, Any?>): Boolean {
        // Retrieve the existing document.
        val document = collection?.getDocument(docId) ?: return false
        // Create a mutable copy.
        val mutableDoc = document.toMutable()
        // Update all key-value pairs.
        for ((key, value) in updatedData) {
            mutableDoc.setValue(key, value)
        }
        return try {
            // Save the updated document.
            collection?.save(mutableDoc)
            true
        } catch (e: CouchbaseLiteException) {
            Log.e(TAG, "Error updating document: ${e.message}")
            false
        }
    }

    fun queryDocs() {
        val coll: CBLCollection = collection ?: return
        val query = QueryBuilder.select(SelectResult.all())
            .from(DataSource.collection(coll))
            .where(Expression.property("language").equalTo(Expression.string("Kotlin")))
        query.execute().use { rs ->
            Log.i(TAG, "Number of rows :: ${rs.allResults().size}")
        }
    }

    fun saveDocument(mutableDoc: MutableDocument): Boolean {
        val result = collection?.let { coll: CBLCollection ->
            try {
                coll.save(mutableDoc)
                true
            } catch (e: CouchbaseLiteException) {
                Log.e(TAG, "Error saving document: ${e.message}")
                false
            }
        } ?: false
        return result
    }

    companion object {
        private const val TAG = "DBManager"
        private val INSTANCE = AtomicReference<DBManager?>()

        @Synchronized
        fun getInstance(context: Context): DBManager {
            var mgr = INSTANCE.get()
            if (mgr == null) {
                mgr = DBManager()
                if (INSTANCE.compareAndSet(null, mgr)) {
                    mgr.init(context)
                }
            }
            return INSTANCE.get()!!
        }
    }

    fun deleteDoc(docId: String): Boolean {
        val doc = collection?.getDocument(docId) ?: return false
        return try {
            collection?.delete(doc)
            Log.i(TAG, "Document deleted: $docId")
            true
        } catch (e: CouchbaseLiteException) {
            Log.e(TAG, "Error deleting document: ${e.message}")
            false
        }
    }

    fun deleteAllDocuments(): Boolean {
        val coll = collection ?: return false
        return try {
            // Query all document IDs from the collection:
            val query = QueryBuilder.select(SelectResult.expression(Meta.id).`as`("docId"))
                .from(DataSource.collection(coll))
            val results = query.execute().allResults()
            for (result in results) {
                val docId = result.toMap()["docId"] as? String
                if (docId != null) {
                    val doc = coll.getDocument(docId)
                    if (doc != null) {
                        coll.delete(doc)
                        Log.i(TAG, "Deleted document: $docId")
                    }
                }
            }
            true
        } catch (e: CouchbaseLiteException) {
            Log.e(TAG, "Error deleting documents: ${e.message}")
            false
        }
    }

    // Start push-pull replication using the provided credentials.
    fun startPushAndPullReplicationForCurrentUser(
        username: String,
        password: String
    ): Replicator {
        var url: URI? = null
        try {
            url = URI(String.format("%s", syncGatewayEndpoint))
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }

        val coll: CBLCollection = collection ?: throw IllegalStateException("Collection is not initialized")
        val config = ReplicatorConfiguration(URLEndpoint(url!!))
            .setType(ReplicatorType.PUSH_AND_PULL)
            .setContinuous(true)
            .setAcceptOnlySelfSignedServerCertificate(false)
            .addCollection(coll, CollectionConfiguration())
            .setAuthenticator(BasicAuthenticator(username, password.toCharArray()))

        Log.i("config", "$config")

        // Create the replicator instance and assign it to our property.
        replicator = Replicator(config)

        // Add a change listener to log replication status.
        replicator?.addChangeListener { change ->
            Log.d(TAG, "Replication activity level: ${change.status.activityLevel}")
            if (change.status.activityLevel == ReplicatorActivityLevel.BUSY ||
                change.status.activityLevel == ReplicatorActivityLevel.IDLE
            ) {
                Log.i(TAG, "Replication started successfully.")
            }
            change.status.error?.let { error ->
                Log.e(TAG, "Replication error: ${error.message}", error)
            }
        }

        // Optional: observe replicator state with a flow.
        val replicatorState = replicator!!.replicatorChangesFlow()
            .map { it.status.activityLevel }
            .asLiveData()
        Log.i(TAG, "Replicator state: $replicatorState")

        val token = replicator!!.addDocumentReplicationListener { replication ->
            Log.d(TAG, "Replication type: " + if (replication.isPush()) "push" else "pull")
            for (document in replication.getDocuments()) {
                Log.e(TAG, "Doc ID: " + document.id)
                document.error?.let { err ->
                    Log.e(TAG, "Error replicating document: $err")
                    return@addDocumentReplicationListener
                }
                if (document.flags.contains(DocumentFlag.DELETED)) {
                    Log.d(TAG, "Successfully replicated a deleted document")
                }
            }
        }

        replicator!!.start()
        dbToken = token
        return replicator!!
    }

    // Stop replication.
    fun stopReplication() {
        replicator?.stop()
        replicator = null
        Log.i(TAG, "Replication stopped")
    }
}