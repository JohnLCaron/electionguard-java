package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_GuardianPair extends KeyCeremony.GuardianPair {

  private final String owner_id;

  private final String designated_id;

  AutoValue_KeyCeremony_GuardianPair(
      String owner_id,
      String designated_id) {
    if (owner_id == null) {
      throw new NullPointerException("Null owner_id");
    }
    this.owner_id = owner_id;
    if (designated_id == null) {
      throw new NullPointerException("Null designated_id");
    }
    this.designated_id = designated_id;
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
  public String toString() {
    return "GuardianPair{"
        + "owner_id=" + owner_id + ", "
        + "designated_id=" + designated_id
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.GuardianPair) {
      KeyCeremony.GuardianPair that = (KeyCeremony.GuardianPair) o;
      return this.owner_id.equals(that.owner_id())
          && this.designated_id.equals(that.designated_id());
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
    return h$;
  }

}
