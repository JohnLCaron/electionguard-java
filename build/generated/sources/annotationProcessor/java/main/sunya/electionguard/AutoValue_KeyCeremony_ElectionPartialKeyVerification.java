package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_ElectionPartialKeyVerification extends KeyCeremony.ElectionPartialKeyVerification {

  private final String owner_id;

  private final String designated_id;

  private final String verifier_id;

  private final boolean verified;

  AutoValue_KeyCeremony_ElectionPartialKeyVerification(
      String owner_id,
      String designated_id,
      String verifier_id,
      boolean verified) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
    if (designated_id == null) {
      throw new NullPointerException("Null designated_id");
    }
    this.designated_id = designated_id;
    if (verifier_id == null) {
      throw new NullPointerException("Null verifier_id");
    }
    this.verifier_id = verifier_id;
    this.verified = verified;
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
  String verifier_id() {
    return verifier_id;
  }

  @Override
  boolean verified() {
    return verified;
  }

  @Override
  public String toString() {
    return "ElectionPartialKeyVerification{"
        + "owner_id=" + owner_id + ", "
        + "designated_id=" + designated_id + ", "
        + "verifier_id=" + verifier_id + ", "
        + "verified=" + verified
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.ElectionPartialKeyVerification) {
      KeyCeremony.ElectionPartialKeyVerification that = (KeyCeremony.ElectionPartialKeyVerification) o;
      return this.owner_id.equals(that.owner_id())
          && this.designated_id.equals(that.designated_id())
          && this.verifier_id.equals(that.verifier_id())
          && this.verified == that.verified();
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
    h$ ^= verifier_id.hashCode();
    h$ *= 1000003;
    h$ ^= verified ? 1231 : 1237;
    return h$;
  }

}
