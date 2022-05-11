package electionguard.ballot

import com.sunya.electionguard.*
import com.sunya.electionguard.core.UInt256
import java.util.Collections.emptyMap

data class TallyResult(
    val electionIntialized: ElectionInitialized,
    val ciphertextTally: CiphertextTally,
    val ballotIds: List<String>,
    val tallyIds: List<String>,
) {
    fun manifestHash(): UInt256 {
        return electionIntialized.manifestHash;
    }
    fun jointPublicKey(): Group.ElementModP {
        return electionIntialized.jointPublicKey
    }
    fun cryptoExtendedBaseHash(): Group.ElementModQ {
        return electionIntialized.cryptoExtendedBaseHash.toModQ();
    }
}

data class DecryptionResult(
    val tallyResult: TallyResult,
    val decryptedTally: PlaintextTally,
    val availableGuardians: List<AvailableGuardian>,
    val metadata: Map<String, String> = emptyMap(),
)