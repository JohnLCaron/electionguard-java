package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Objects;

/** The constants for mathematical functions used for this election. */
@Immutable
public class ElectionConstants {
  /** large prime or p. */
  public final BigInteger large_prime;
  /** small prime or q. */
  public final BigInteger small_prime;
  /** cofactor or r. */
  public final BigInteger cofactor;
  /** generator or g. */
  public final BigInteger generator;

  public ElectionConstants() {
    this(Group.P, Group.Q, Group.R, Group.G);
  }

  public ElectionConstants(BigInteger large_prime, BigInteger small_prime, BigInteger cofactor, BigInteger generator) {
    this.large_prime = Preconditions.checkNotNull(large_prime);
    this.small_prime = Preconditions.checkNotNull(small_prime);
    this.cofactor = Preconditions.checkNotNull(cofactor);
    this.generator = Preconditions.checkNotNull(generator);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionConstants that = (ElectionConstants) o;
    return large_prime.equals(that.large_prime) &&
            small_prime.equals(that.small_prime) &&
            cofactor.equals(that.cofactor) &&
            generator.equals(that.generator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(large_prime, small_prime, cofactor, generator);
  }

  @Override
  public String toString() {
    return "ElectionConstants{" +
            "\n large_prime= " + large_prime +
            "\n small_prime= " + small_prime +
            "\n cofactor= " + cofactor +
            "\n generator= " + generator +
            "}";
  }
}
