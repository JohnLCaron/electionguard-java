package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_PublicKeySet extends KeyCeremony.PublicKeySet {

  private final String owner_id;

  private final int sequence_order;

  private final String auxiliary_public_key;

  private final Group.ElementModP election_public_key;

  private final SchnorrProof election_public_key_proof;

  AutoValue_KeyCeremony_PublicKeySet(
      String owner_id,
      int sequence_order,
      String auxiliary_public_key,
      Group.ElementModP election_public_key,
      SchnorrProof election_public_key_proof) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
    this.sequence_order = sequence_order;
    if (auxiliary_public_key == null) {
      throw new NullPointerException("Null auxiliary_public_key");
    }
    this.auxiliary_public_key = auxiliary_public_key;
    if (election_public_key == null) {
      throw new NullPointerException("Null election_public_key");
    }
    this.election_public_key = election_public_key;
    if (election_public_key_proof == null) {
      throw new NullPointerException("Null election_public_key_proof");
    }
    this.election_public_key_proof = election_public_key_proof;
  }

  @Override
  String owner_id() {
    return owner_id;
  }

  @Override
  int sequence_order() {
    return sequence_order;
  }

  @Override
  String auxiliary_public_key() {
    return auxiliary_public_key;
  }

  @Override
  Group.ElementModP election_public_key() {
    return election_public_key;
  }

  @Override
  SchnorrProof election_public_key_proof() {
    return election_public_key_proof;
  }

  @Override
  public String toString() {
    return "PublicKeySet{"
        + "owner_id=" + owner_id + ", "
        + "sequence_order=" + sequence_order + ", "
        + "auxiliary_public_key=" + auxiliary_public_key + ", "
        + "election_public_key=" + election_public_key + ", "
        + "election_public_key_proof=" + election_public_key_proof
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.PublicKeySet) {
      KeyCeremony.PublicKeySet that = (KeyCeremony.PublicKeySet) o;
      return this.owner_id.equals(that.owner_id())
          && this.sequence_order == that.sequence_order()
          && this.auxiliary_public_key.equals(that.auxiliary_public_key())
          && this.election_public_key.equals(that.election_public_key())
          && this.election_public_key_proof.equals(that.election_public_key_proof());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= owner_id.hashCode();
    h$ *= 1000003;
    h$ ^= sequence_order;
    h$ *= 1000003;
    h$ ^= auxiliary_public_key.hashCode();
    h$ *= 1000003;
    h$ ^= election_public_key.hashCode();
    h$ *= 1000003;
    h$ ^= election_public_key_proof.hashCode();
    return h$;
  }

}
