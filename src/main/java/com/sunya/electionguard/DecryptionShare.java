package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

public class DecryptionShare {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** owner object_id */
  final String owner_id; // LOOK can be removed

  /** guardian.object_id */
  final String guardian_id;

  /** The election public key for the guardian. */
  final ElementModP public_key;

  /** The collection of decryption shares for all contests in the election . */
  final ImmutableMap<String, CiphertextDecryptionContest> contests; // Map(CONTEST_ID, CiphertextDecryptionContest)

  public DecryptionShare(String owner_id, String guardian_id, ElementModP public_key, Map<String, CiphertextDecryptionContest> contests) {
    this.owner_id = owner_id;
    this.guardian_id = guardian_id;
    this.public_key = public_key;
    this.contests = ImmutableMap.copyOf(contests);
  }

  /** A Guardian's Partial Decryption of a contest. */
  @AutoValue
  public static abstract class CiphertextDecryptionContest implements ElectionObjectBaseIF {
    /** The Available Guardian that this share belongs to. */
    abstract String guardian_id();

    /** The ContestDescription Hash. */
    abstract ElementModQ description_hash();

    /** the collection of decryption shares for this contest's selections */
    abstract ImmutableMap<String, CiphertextDecryptionSelection> selections();

    public static CiphertextDecryptionContest create(
            String object_id,
            String guardian_id,
            ElementModQ description_hash,
            Map<String, CiphertextDecryptionSelection> selections) {
      return new AutoValue_DecryptionShare_CiphertextDecryptionContest(object_id, guardian_id, description_hash,
              ImmutableMap.copyOf(selections));
    }
  }

  /**
   * A Guardian's Partial Decryption of a selection.  A CiphertextDecryptionSelection
   * can be generated by a guardian directly, or it can be compensated for by a quorum of guardians
   * <p>
   * When the guardian generates this share directly, the `proof` field is populated with
   * a `chaumPedersen` proof that the decryption share was generated correctly.
   * <p>
   * When the share is generated on behalf of this guardian by other guardians, the `recovered_parts`
   * collection is populated with the `CiphertextCompensatedDecryptionSelection` objects generated
   * by each available guardian.
   */
  @AutoValue
  public static abstract class CiphertextDecryptionSelection implements ElectionObjectBaseIF {
    /** The Guardian that this share belongs to, available or missing. */
    public abstract String guardian_id();

    /** The Share of the decryption of a selection. `M_i` in the spec. */
    public abstract ElementModP share();

    // You either have a proof or a recovered_parts

    /** For available guardians, proof that the share was decrypted correctly. */
    public abstract Optional<ChaumPedersen.ChaumPedersenProof> proof();

    /** For missing guardians, keyed by available guardian_id. */
    public abstract Optional<ImmutableMap<String, CiphertextCompensatedDecryptionSelection>> recovered_parts();

    public static CiphertextDecryptionSelection create(
            String object_id,
            String guardian_id,
            ElementModP share,
            Optional<ChaumPedersen.ChaumPedersenProof> proof,
            Optional<Map<String, CiphertextCompensatedDecryptionSelection>> recovered_parts) {

      Preconditions.checkArgument(proof.isPresent() || recovered_parts.isPresent());
      Preconditions.checkArgument(!(proof.isPresent() && recovered_parts.isPresent()));

      return new AutoValue_DecryptionShare_CiphertextDecryptionSelection(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(guardian_id),
              Preconditions.checkNotNull(share),
              Preconditions.checkNotNull(proof),
              recovered_parts.map(ImmutableMap::copyOf));
    }

    /**
     * Verify that this CiphertextDecryptionSelection is valid for a
     * specific ElGamal key pair, public key, and election context.
     * <p>
     * @param message: the `ElGamalCiphertext` to compare
     * @param election_public_key: the `ElementModP Manifest Public Key for the Guardian
     * @param extended_base_hash: The `ElementModQ` election extended base hash.
     */
    boolean is_valid(ElGamal.Ciphertext message, ElementModP election_public_key, ElementModQ extended_base_hash) {
      if (this.proof().isPresent()) {
        ChaumPedersen.ChaumPedersenProof proof = this.proof().get();
        if (!proof.is_valid(message, election_public_key, this.share(), extended_base_hash)) {
          logger.atWarning().log("CiphertextDecryptionSelection is_valid failed for guardian: %s selection: %s with invalid proof",
                  this.guardian_id(), this.object_id());
          return false;
        }
      }

      if (this.recovered_parts().isPresent()) {
        Map<String, CiphertextCompensatedDecryptionSelection> recovered = this.recovered_parts().get();
        for (CiphertextCompensatedDecryptionSelection part : recovered.values()) {
          if (!part.proof().is_valid(message, part.recovery_key(), part.share(), extended_base_hash)) {
            logger.atWarning().log("CiphertextDecryptionSelection is_valid failed for guardian: %s selection: %s with invalid partial proof",
                    this.guardian_id(), this.object_id());
            return false;
          }
        }
      }
      return true;
    }
  }

  /** Create a ciphertext decryption selection */
  public static CiphertextDecryptionSelection create_ciphertext_decryption_selection(
          String object_id,
          String guardian_id,
          ElementModP share,
          Optional<ChaumPedersen.ChaumPedersenProof> proof,
          Optional<Map<String, CiphertextCompensatedDecryptionSelection>> recovered_parts) {

    return CiphertextDecryptionSelection.create(
            object_id, guardian_id, share, proof, recovered_parts);
  }

  //////////////////////////////////////////////////////////////////////////////////

  public static class CompensatedDecryptionShare {
    /** owner object_id */
    public final String owner_id;

    /** The Available Guardian that this share belongs to */
    public final String guardian_id;

    /** The Missing Guardian for whom this share is calculated on behalf of */
    public final String missing_guardian_id;

    /** The election public key for the guardian. */
    public final ElementModP public_key;

    /** The collection of decryption shares for all contests in the election. */
    public final ImmutableMap<String, CiphertextCompensatedDecryptionContest> contests; // Map(CONTEST_ID, CiphertextCompensatedDecryptionContest)

    public CompensatedDecryptionShare(String owner_id, String guardian_id, String missing_guardian_id, ElementModP public_key, Map<String, CiphertextCompensatedDecryptionContest> contests) {
      this.owner_id = owner_id;
      this.guardian_id = guardian_id;
      this.missing_guardian_id = missing_guardian_id;
      this.public_key = public_key;
      this.contests = ImmutableMap.copyOf(contests);
    }
  }

  /** A Guardian's Partial Decryption of a contest. */
  @AutoValue
  public static abstract class CiphertextCompensatedDecryptionContest implements ElectionObjectBaseIF {
    /** The Available Guardian that this share belongs to */
    public abstract String guardian_id();

    /** The Missing Guardian for whom this share is calculated on behalf of. */
    public abstract String missing_guardian_id();

    /** The ContestDescription Hash. */
    public abstract ElementModQ description_hash();

    /** the collection of decryption shares for this contest's selections. */
    public abstract ImmutableMap<String, CiphertextCompensatedDecryptionSelection> selections(); // Map(SELECTION_ID, CiphertextCompensatedDecryptionSelection)

    public static CiphertextCompensatedDecryptionContest create(
            String object_id,
            String guardian_id,
            String missing_guardian_id,
            ElementModQ description_hash,
            Map<String, CiphertextCompensatedDecryptionSelection> selections) {
      return new AutoValue_DecryptionShare_CiphertextCompensatedDecryptionContest(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(guardian_id),
              Preconditions.checkNotNull(missing_guardian_id),
              Preconditions.checkNotNull(description_hash),
              ImmutableMap.copyOf(selections));
    }
  }

    /** A compensated fragment of a Guardian's Partial Decryption of a selection generated by an available guardian. */
  @AutoValue
  public static abstract class CiphertextCompensatedDecryptionSelection implements ElectionObjectBaseIF {
    /** The available Guardian that this share belongs to. */
    public abstract String guardian_id();

    /** The missing Guardian for whom this share is calculated on behalf of. */
    public abstract String missing_guardian_id();

    /** The share of the decryption of a selection. M_il in the spec. */
    public abstract ElementModP share();

    /** The available guardian's share of the missing_guardian's public key. */
    public abstract ElementModP recovery_key();

    /** The proof that the share was decrypted correctly. */
    public abstract ChaumPedersen.ChaumPedersenProof proof();

    public static CiphertextCompensatedDecryptionSelection create(
            String object_id,
            String guardian_id,
            String missing_guardian_id,
            ElementModP share,
            ElementModP recovery_key,
            ChaumPedersen.ChaumPedersenProof proof) {
      return new AutoValue_DecryptionShare_CiphertextCompensatedDecryptionSelection(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(guardian_id),
              Preconditions.checkNotNull(missing_guardian_id),
              Preconditions.checkNotNull(share),
              Preconditions.checkNotNull(recovery_key),
              Preconditions.checkNotNull(proof));
    }
  } // CiphertextCompensatedDecryptionSelection

  @Immutable
  static class KeyAndSelection {
    final ElementModP public_key;
    final CiphertextDecryptionSelection decryption;

    public KeyAndSelection(ElementModP public_key, CiphertextDecryptionSelection decryption) {
      this.public_key = public_key;
      this.decryption = decryption;
    }
  }

  static Map<String, KeyAndSelection> get_tally_shares_for_selection2(
          String selection_id,
          Map<String, DecryptionShare> shares) { // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)

    HashMap<String, KeyAndSelection> cast_shares = new HashMap<>();
    for (DecryptionShare share : shares.values()) {
      for (CiphertextDecryptionContest contest : share.contests.values()) {
        for (CiphertextDecryptionSelection selection : contest.selections().values()) {
          if (selection.object_id().equals(selection_id)) {
            cast_shares.put(share.guardian_id, new KeyAndSelection(share.public_key, selection));
          }
        }
      }
    }
    return cast_shares;
  }

}
