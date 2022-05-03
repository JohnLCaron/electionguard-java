package com.sunya.electionguard.publish

data class ElectionRecordPath(val topDir : String) {
    private val electionRecordDir = "$topDir"

    companion object {
        const val PROTO_VERSION = "2.0.0"

        const val PROTO_SUFFIX = ".protobuf"
        const val DECRYPTING_TRUSTEE_PREFIX = "decryptingTrustee"
        const val ELECTION_CONFIG_FILE_NAME = "electionConfig" + PROTO_SUFFIX
        const val ELECTION_INITIALIZED_FILE_NAME = "electionInitialized" + PROTO_SUFFIX
        const val TALLY_RESULT_NAME = "tallyResult" + PROTO_SUFFIX
        const val DECRYPTION_RESULT_NAME = "decryptionResult" + PROTO_SUFFIX
        const val PLAINTEXT_BALLOT_PROTO = "plaintextBallots" + PROTO_SUFFIX
        const val SUBMITTED_BALLOT_PROTO = "encryptedBallots" + PROTO_SUFFIX
        const val SPOILED_BALLOT_FILE = "spoiledBallotsTally" + PROTO_SUFFIX
    }

    fun electionConfigPath(): String {
        return "$electionRecordDir/$ELECTION_CONFIG_FILE_NAME"
    }

    fun electionInitializedPath(): String {
        return "$electionRecordDir/$ELECTION_INITIALIZED_FILE_NAME"
    }

    fun tallyResultPath(): String {
        return "$electionRecordDir/$TALLY_RESULT_NAME"
    }

    fun decryptionResultPath(): String {
        return "$electionRecordDir/$DECRYPTION_RESULT_NAME"
    }

    fun plaintextBallotPath(ballotDir: String): String {
        return "$ballotDir/$PLAINTEXT_BALLOT_PROTO"
    }

    fun submittedBallotPath(): String {
        return "$electionRecordDir/$SUBMITTED_BALLOT_PROTO"
    }

    fun spoiledBallotPath(): String {
        return "$electionRecordDir/$SPOILED_BALLOT_FILE"
    }

    fun decryptingTrusteePath(trusteeDir: String, guardianId: String): String {
        return "$trusteeDir/$DECRYPTING_TRUSTEE_PREFIX-$guardianId$PROTO_SUFFIX"
    }
}