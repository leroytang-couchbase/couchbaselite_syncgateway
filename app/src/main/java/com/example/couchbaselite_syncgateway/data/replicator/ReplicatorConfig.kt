package com.example.couchbaselite_syncgateway.data.replicator

data class ReplicatorConfig (
    var username: String,
    var password: CharArray,
    var endpointUrl: String,
    var replicatorType: String,
    var heartBeat: Long,
    var continuous: Boolean,
    var selfSignedCert: Boolean
)