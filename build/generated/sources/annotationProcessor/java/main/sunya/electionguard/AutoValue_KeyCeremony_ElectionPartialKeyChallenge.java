package sunya.electionguard;

import java.util.List;
import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_ElectionPartialKeyChallenge extends KeyCeremony.ElectionPartialKeyChallenge {

  private final String owner_id;

  private final String designated_id;

  private final int designated_sequence_order;

  private final Group.ElementModQ value;

  private final List<Group.ElementModP> coefficient_commitments;

  private final List<SchnorrProof> coefficient_proofs;

  AutoValue_KeyCeremony_ElectionPartialKeyChallenge(
      String owner_id,
      String designated_id,
      int designated_sequence_order,
      Group.ElementModQ value,
      List<Group.ElementModP> coefficient_commitments,
      List<SchnorrProof> coefficient_proofs) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
    if (designated_id == null) {
      throw new NullPointerException("Null designated_id");
    }
    this.designated_id = designated_id;
    this.designated_sequence_order = designated_sequence_order;
    if (value == null) {
      throw new NullPointerException("Null value");
    }
    this.value = value;
    if (coefficient_commitments == null) {
      throw new NullPointerException("Null coefficient_commitments");
    }
    this.coefficient_commitments = coefficient_commitments;
    if (coefficient_proofs == null) {
      throw new NullPointerException("Null coefficient_proofs");
    }
    this.coefficient_proofs = coefficient_proofs;
  }

  @Override
  String owner_id() {
    return owner_id;
  }

  @Override
  String designated_id() {
    return designated_id;
  }

  @Override
  int designated_sequence_order() {
    return designated_sequence_order;
  }

  @Override
  Group.ElementModQ value() {
    return value;
  }

  @Override
  List<Group.ElementModP> coefficient_commitments() {
    return coefficient_commitments;
  }

  @Override
  List<SchnorrProof> coefficient_proofs() {
    return coefficient_proofs;
  }

  @Override
  public String toString() {
    return "ElectionPartialKeyChallenge{"
        + "owner_id=" + owner_id + ", "
        + "designated_id=" + designated_id + ", "
        + "designated_sequence_order=" + designated_sequence_order + ", "
        + "value=" + value + ", "
        + "coefficient_commitments=" + coefficient_commitments + ", "
        + "coefficient_proofs=" + coefficient_proofs
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.ElectionPartialKeyChallenge) {
      KeyCeremony.ElectionPartialKeyChallenge that = (KeyCeremony.ElectionPartialKeyChallenge) o;
      return this.owner_id.equals(that.owner_id())
          && this.designated_id.equals(that.designated_id())
          && this.designated_sequence_order == that.designated_sequence_order()
          && this.value.equals(that.value())
          && this.coefficient_commitments.equals(that.coefficient_commitments())
          && this.coefficient_proofs.equals(that.coefficient_proofs());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= owner_id.hashCode();
    h$ *= 1000003;
    h$ ^= designated_id.hashCode();
    h$ *= 1000003;
    h$ ^= designated_sequence_order;
    h$ *= 1000003;
    h$ ^= value.hashCode();
    h$ *= 1000003;
    h$ ^= coefficient_commitments.hashCode();
    h$ *= 1000003;
    h$ ^= coefficient_proofs.hashCode();
    return h$;
  }

}
