package com.sunya.electionguard.publish

import java.nio.file.Path

data class ElectionRecordPath(val topDir : String) {

    companion object {
        const val PROTO_VERSION = "2.0.0"

        const val PROTO_SUFFIX = ".protobuf"
        const val DECRYPTING_TRUSTEE_PREFIX = "decryptingTrustee-"
        const val ELECTION_CONFIG_FILE_NAME = "electionConfig" + PROTO_SUFFIX
        const val ELECTION_INITIALIZED_FILE_NAME = "electionInitialized" + PROTO_SUFFIX
        const val TALLY_RESULT_NAME = "tallyResult" + PROTO_SUFFIX
        const val DECRYPTION_RESULT_NAME = "decryptionResult" + PROTO_SUFFIX
        const val SUBMITTED_BALLOT_PROTO = "encryptedBallots" + PROTO_SUFFIX
        const val SPOILED_BALLOT_FILE = "spoiledBallotsTally" + PROTO_SUFFIX

        const val INPUT_BALLOTS_FILE = "inputBallots" + PROTO_SUFFIX
        const val INVALID_BALLOTS_FILE = "invalidBallots" + PROTO_SUFFIX
    }

    fun topDirPath() = Path.of(topDir)

    fun electionConfigPath(): Path {
        return Path.of("$topDir/$ELECTION_CONFIG_FILE_NAME")
    }

    fun electionInitializedPath(): Path {
        return Path.of("$topDir/$ELECTION_INITIALIZED_FILE_NAME")
    }

    fun tallyResultPath(): Path {
        return Path.of("$topDir/$TALLY_RESULT_NAME")
    }

    fun decryptionResultPath(): Path {
        return Path.of("$topDir/$DECRYPTION_RESULT_NAME")
    }

    fun submittedBallotPath(): Path {
        return Path.of("$topDir/$SUBMITTED_BALLOT_PROTO")
    }

    fun spoiledBallotPath(): Path {
        return Path.of("$topDir/$SPOILED_BALLOT_FILE")
    }

    fun decryptingTrusteeName(guardianId: String): String {
        return "$DECRYPTING_TRUSTEE_PREFIX$guardianId$PROTO_SUFFIX"
    }

    fun decryptingTrusteePath(trusteeDir: String, guardianId: String): String {
        return Path.of(trusteeDir).resolve(decryptingTrusteeName(guardianId)).toString()
    }

    fun inputBallotsFilePath(dir: String): Path {
        return Path.of(dir).resolve(INPUT_BALLOTS_FILE)
    }

    fun invalidBallotsFilePath(dir: String): Path {
        return Path.of(dir).resolve(INVALID_BALLOTS_FILE)
    }
}