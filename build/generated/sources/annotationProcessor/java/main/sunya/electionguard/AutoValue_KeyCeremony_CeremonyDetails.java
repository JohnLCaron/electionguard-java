package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_CeremonyDetails extends KeyCeremony.CeremonyDetails {

  private final int number_of_guardians;

  private final int quorum;

  AutoValue_KeyCeremony_CeremonyDetails(
      int number_of_guardians,
      int quorum) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
  }

  @Override
  int number_of_guardians() {
    return number_of_guardians;
  }

  @Override
  int quorum() {
    return quorum;
  }

  @Override
  public String toString() {
    return "CeremonyDetails{"
        + "number_of_guardians=" + number_of_guardians + ", "
        + "quorum=" + quorum
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.CeremonyDetails) {
      KeyCeremony.CeremonyDetails that = (KeyCeremony.CeremonyDetails) o;
      return this.number_of_guardians == that.number_of_guardians()
          && this.quorum == that.quorum();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= number_of_guardians;
    h$ *= 1000003;
    h$ ^= quorum;
    return h$;
  }

}
