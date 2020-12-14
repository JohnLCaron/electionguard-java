package sunya.electionguard;

import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_KeyCeremony_ElectionKeyPair extends KeyCeremony.ElectionKeyPair {

  private final ElGamal.KeyPair key_pair;

  private final SchnorrProof proof;

  private final ElectionPolynomial polynomial;

  AutoValue_KeyCeremony_ElectionKeyPair(
      ElGamal.KeyPair key_pair,
      SchnorrProof proof,
      ElectionPolynomial polynomial) {
    if (key_pair == null) {
      throw new NullPointerException("Null key_pair");
    }
    this.key_pair = key_pair;
    if (proof == null) {
      throw new NullPointerException("Null proof");
    }
    this.proof = proof;
    if (polynomial == null) {
      throw new NullPointerException("Null polynomial");
    }
    this.polynomial = polynomial;
  }

  @Override
  ElGamal.KeyPair key_pair() {
    return key_pair;
  }

  @Override
  SchnorrProof proof() {
    return proof;
  }

  @Override
  ElectionPolynomial polynomial() {
    return polynomial;
  }

  @Override
  public String toString() {
    return "ElectionKeyPair{"
        + "key_pair=" + key_pair + ", "
        + "proof=" + proof + ", "
        + "polynomial=" + polynomial
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof KeyCeremony.ElectionKeyPair) {
      KeyCeremony.ElectionKeyPair that = (KeyCeremony.ElectionKeyPair) o;
      return this.key_pair.equals(that.key_pair())
          && this.proof.equals(that.proof())
          && this.polynomial.equals(that.polynomial());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= key_pair.hashCode();
    h$ *= 1000003;
    h$ ^= proof.hashCode();
    h$ *= 1000003;
    h$ ^= polynomial.hashCode();
    return h$;
  }

}
