package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_ElectionPublicKey extends KeyCeremony.ElectionPublicKey {

  private final String owner_id;

  private final SchnorrProof proof;

  private final Group.ElementModP key;

  AutoValue_KeyCeremony_ElectionPublicKey(
      String owner_id,
      SchnorrProof proof,
      Group.ElementModP key) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
    if (proof == null) {
      throw new NullPointerException("Null proof");
    }
    this.proof = proof;
    if (key == null) {
      throw new NullPointerException("Null key");
    }
    this.key = key;
  }

  @Override
  String owner_id() {
    return owner_id;
  }

  @Override
  SchnorrProof proof() {
    return proof;
  }

  @Override
  Group.ElementModP key() {
    return key;
  }

  @Override
  public String toString() {
    return "ElectionPublicKey{"
        + "owner_id=" + owner_id + ", "
        + "proof=" + proof + ", "
        + "key=" + key
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.ElectionPublicKey) {
      KeyCeremony.ElectionPublicKey that = (KeyCeremony.ElectionPublicKey) o;
      return this.owner_id.equals(that.owner_id())
          && this.proof.equals(that.proof())
          && this.key.equals(that.key());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= owner_id.hashCode();
    h$ *= 1000003;
    h$ ^= proof.hashCode();
    h$ *= 1000003;
    h$ ^= key.hashCode();
    return h$;
  }

}
