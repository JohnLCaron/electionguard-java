package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.add_q;
import static com.sunya.electionguard.Group.g_pow_p;
import static com.sunya.electionguard.Group.rand_q;

/**
 * The polynomial that each Guardian defines to solve for their private key.
 * A different point associated with the polynomial is shared with each of the other guardians so that the guardians
 * can come together to derive the polynomial function and solve for the private key.
 * <p>
 * The 0-index coefficient is used for a secret key which can be discovered by a quorum of guardians.
 */
@Immutable
public class ElectionPolynomial {

  /** The secret coefficients `a_ij`. */
  public final ImmutableList<Group.ElementModQ> coefficients;

  /** The public keys `K_ij` generated from secret coefficients. (not secret) */
  public final ImmutableList<Group.ElementModP> coefficient_commitments;

  /** A proof of possession of the private key for each secret coefficient. (not secret) */
  public final ImmutableList<SchnorrProof> coefficient_proofs;

  public ElectionPolynomial(List<Group.ElementModQ> coefficients, List<Group.ElementModP> coefficient_commitments,
                     List<SchnorrProof> coefficient_proofs) {
    this.coefficients = ImmutableList.copyOf(coefficients);
    this.coefficient_commitments = ImmutableList.copyOf(coefficient_commitments);
    this.coefficient_proofs = ImmutableList.copyOf(coefficient_proofs);
  }

  // The value of the polynomial at xcoord
  public ElementModQ valueAt(int xcoord) {
    ElementModQ xcoordQ = Group.int_to_q_unchecked(xcoord);
    ElementModQ computedValue = Group.ZERO_MOD_Q;
    ElementModQ xcoordPower = Group.ONE_MOD_Q;

    for (ElementModQ coefficient : this.coefficients) {
      ElementModQ term = Group.mult_q(coefficient, xcoordPower);
      computedValue = Group.add_q(computedValue, term);
      xcoordPower = Group.mult_q(xcoordPower, xcoordQ);
    }
    return computedValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionPolynomial that = (ElectionPolynomial) o;
    return coefficients.equals(that.coefficients) &&
            coefficient_commitments.equals(that.coefficient_commitments) &&
            coefficient_proofs.equals(that.coefficient_proofs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coefficients, coefficient_commitments, coefficient_proofs);
  }

  @Override
  public String toString() {
    return "ElectionPolynomial{" +
            "coefficients=" + coefficients +
            ", coefficient_commitments=" + coefficient_commitments +
            ", coefficient_proofs=" + coefficient_proofs +
            '}';
  }

  /**
   * Generates a polynomial for sharing election keys.
   *
   * @param quorum Number of coefficients of polynomial = the quorum, k.
   * @param nonce an optional nonce parameter that may be provided (only for testing), otherwise
   *               a random one is chosen.
   * @return ElectionPolynomial used to share election keys
   */
  public static ElectionPolynomial generate_polynomial(int quorum, @Nullable Group.ElementModQ nonce) {
    ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
    ArrayList<Group.ElementModP> commitments = new ArrayList<>();
    ArrayList<SchnorrProof> proofs = new ArrayList<>();

    for (int i = 0; i < quorum; i++) {
      // Note the nonce value is not safe. it is designed for testing only.
      // this method should be called without the nonce in production.
      ElementModQ coeff_value = Group.int_to_q_unchecked(BigInteger.valueOf(i));
      Group.ElementModQ coefficient = (nonce == null) ? rand_q() : add_q(nonce, coeff_value);
      Group.ElementModP commitment = g_pow_p(coefficient);
      // TODO Alternate schnorr proof method that doesn't need KeyPair
      SchnorrProof proof = SchnorrProof.make_schnorr_proof(new ElGamal.KeyPair(coefficient, commitment), rand_q());

      coefficients.add(coefficient);
      commitments.add(commitment);
      proofs.add(proof);
    }

    return new ElectionPolynomial(coefficients, commitments, proofs);
  }

  /**
   * Compute the lagrange coefficient for a specific coordinate against N degrees.
   *
   * @param coordinate: the coordinate to plot, usually a Guardian's Sequence Order
   * @param degrees:    the degrees across which to plot, usually the collection of
   *                    other Guardians' Sequence Orders
   */
  public static ElementModQ compute_lagrange_coefficient(Integer coordinate, List<Integer> degrees) {
    int product = degrees.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ numerator = Group.int_to_q_unchecked(BigInteger.valueOf(product).mod(Group.getPrimes().smallPrime));
    // denominator = mult_q(*[(degree - coordinate) for degree in degrees])
    List<Integer> diff = degrees.stream().map(degree -> degree - coordinate).toList();
    int productDiff = diff.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ denominator = Group.int_to_q_unchecked(BigInteger.valueOf(productDiff).mod(Group.getPrimes().smallPrime));
    return Group.div_q(numerator, denominator);
  }

  public static Integer computeLagrangeCoefficient(Integer coordinate, List<Integer> degrees) {
    int product = degrees.stream().reduce(1, (a, b)  -> a * b);
    // denominator = mult_q(*[(degree - coordinate) for degree in degrees])
    List<Integer> diff = degrees.stream().map(degree -> degree - coordinate).toList();
    int productDiff = diff.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ denominator = Group.int_to_q_unchecked(BigInteger.valueOf(productDiff).mod(Group.getPrimes().smallPrime));
    return product / productDiff;
  }

  /**
   * Verify a polynomial coordinate value is in fact on the polynomial's curve.
   *
   * @param expected                Expected value
   * @param xcoord              Value to be checked
   * @param commitments Commitments for coefficients of polynomial
   */
  public static boolean verifyPolynomialCoordinate(ElementModQ expected, int xcoord, List<ElementModP> commitments) {
    ElementModP calculated = calculateGexpPiAtL(xcoord, commitments);
    ElementModP gexpected = g_pow_p(expected);
    return gexpected.equals(calculated);
  }

  // Used in KeyCeremonyTrustee and DecryptingTrustee
  // g^Pi(ℓ) mod p = Product ((K_i,j)^ℓ^j) mod p, j = 0, k-1 because there are always k coefficients
  public static ElementModP calculateGexpPiAtL(int xcoord, List<ElementModP> coefficientCommitments) {
    ElementModQ xcoordQ = Group.int_to_q_unchecked(xcoord);
    ElementModP result = Group.ONE_MOD_P;
    ElementModQ xcoordPower = Group.ONE_MOD_Q; // ℓ^j

    for (ElementModP commitment : coefficientCommitments) {
      ElementModP term = Group.pow_p(commitment, xcoordPower); // (K_i,j)^ℓ^j
      result = Group.mult_p(result, term);
      xcoordPower = Group.mult_q(xcoordPower, xcoordQ);
    }
    return result;
  }

}
