package sunya.electionguard;

import java.util.List;
import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_CoefficientValidationSet extends KeyCeremony.CoefficientValidationSet {

  private final String owner_id;

  private final List<Group.ElementModP> coefficient_commitments;

  private final List<SchnorrProof> coefficient_proofs;

  AutoValue_KeyCeremony_CoefficientValidationSet(
      String owner_id,
      List<Group.ElementModP> coefficient_commitments,
      List<SchnorrProof> coefficient_proofs) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
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
  List<Group.ElementModP> coefficient_commitments() {
    return coefficient_commitments;
  }

  @Override
  List<SchnorrProof> coefficient_proofs() {
    return coefficient_proofs;
  }

  @Override
  public String toString() {
    return "CoefficientValidationSet{"
        + "owner_id=" + owner_id + ", "
        + "coefficient_commitments=" + coefficient_commitments + ", "
        + "coefficient_proofs=" + coefficient_proofs
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.CoefficientValidationSet) {
      KeyCeremony.CoefficientValidationSet that = (KeyCeremony.CoefficientValidationSet) o;
      return this.owner_id.equals(that.owner_id())
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
    h$ ^= coefficient_commitments.hashCode();
    h$ *= 1000003;
    h$ ^= coefficient_proofs.hashCode();
    return h$;
  }

}
