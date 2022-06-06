package electionguard.ballot

import com.sunya.electionguard.Group
import com.sunya.electionguard.GuardianRecord
import com.sunya.electionguard.Manifest
import com.sunya.electionguard.SchnorrProof
import com.sunya.electionguard.core.UInt256
import java.util.Collections.emptyMap

/** Results of KeyCeremony, for Encryption. */
data class ElectionInitialized(
    val config: ElectionConfig,
    /** The joint public key (K) in the ElectionGuard Spec. */
    val jointPublicKey: Group.ElementModP,
    val manifestHash: UInt256, // matches Manifest.cryptoHash
    val cryptoBaseHash: UInt256, // aka Q
    val cryptoExtendedBaseHash: UInt256, // aka Qbar
    val guardians: List<Guardian>,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun manifest(): Manifest {
        return config.manifest
    }
}

/** Public info for Guardian. */
data class Guardian(
    val guardianId: String,
    val xCoordinate: Int, // > 0 for lagrange interpolation to work correctly
    val coefficientCommitments: List<Group.ElementModP>,
    val coefficientProofs: List<SchnorrProof>
) {
    constructor(old: GuardianRecord) :
            this(old.guardianId, old.xCoordinate, old.coefficientCommitments, old.coefficientProofs)

    fun publicKey() : Group.ElementModP = coefficientCommitments[0]
}