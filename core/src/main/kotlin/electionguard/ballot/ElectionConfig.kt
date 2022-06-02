package electionguard.ballot

import com.google.common.base.Preconditions
import com.sunya.electionguard.Manifest
import java.util.Collections.emptyMap

/** Configuration for KeyCeremony. */
data class ElectionConfig(
    val protoVersion: String,
    val constants: ElectionConstants,
    val manifest: Manifest,
    /** The number of guardians necessary to generate the public key. */
    val numberOfGuardians: Int,
    /** The quorum of guardians necessary to decrypt an election. Must be <= number_of_guardians. */
    val quorum: Int,
    /** arbitrary key/value metadata. */
    val metadata: Map<String, String> = emptyMap(),
) {

    init {
        Preconditions.checkArgument(numberOfGuardians > 0);
        Preconditions.checkArgument(quorum > 0);
        Preconditions.checkArgument(quorum <= numberOfGuardians);
    }

    constructor(manifest: Manifest, numberOfGuardians: Int, quorum: Int) :
            this("2.0.0", ElectionConstants(com.sunya.electionguard.ElectionConstants.STANDARD_CONSTANTS),
                 manifest, numberOfGuardians, quorum)
}

/**
 * A public description of the mathematical group used for the encryption and processing of ballots.
 * The byte arrays are defined to be big-endian.
 */
data class ElectionConstants(
    val name: String,
    /** large prime or P. */
    val largePrime: ByteArray,
    /** small prime or Q. */
    val smallPrime: ByteArray,
    /** cofactor or R. */
    val cofactor: ByteArray,
    /** generator or G. */
    val generator: ByteArray,
) {
    constructor(old: com.sunya.electionguard.ElectionConstants) :
            this(old.name, old.largePrime.toByteArray(), old.smallPrime.toByteArray(), old.cofactor.toByteArray(), old.generator.toByteArray())

    // default equals is failing 5/3/2022
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ElectionConstants

        if (name != other.name) return false
        if (!largePrime.contentEquals(other.largePrime)) return false
        if (!smallPrime.contentEquals(other.smallPrime)) return false
        if (!cofactor.contentEquals(other.cofactor)) return false
        if (!generator.contentEquals(other.generator)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + largePrime.contentHashCode()
        result = 31 * result + smallPrime.contentHashCode()
        result = 31 * result + cofactor.contentHashCode()
        result = 31 * result + generator.contentHashCode()
        return result
    }

}