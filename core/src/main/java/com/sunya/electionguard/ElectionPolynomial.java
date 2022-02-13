package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ZERO_MOD_Q;
import static com.sunya.electionguard.Group.add_q;
import static com.sunya.electionguard.Group.g_pow_p;
import static com.sunya.electionguard.Group.mult_q;
import static com.sunya.electionguard.Group.pow_q;
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

  // LOOK test all sizes == quorum
  public ElectionPolynomial(List<Group.ElementModQ> coefficients, List<Group.ElementModP> coefficient_commitments,
                     List<SchnorrProof> coefficient_proofs) {
    this.coefficients = ImmutableList.copyOf(coefficients);
    this.coefficient_commitments = ImmutableList.copyOf(coefficient_commitments);
    this.coefficient_proofs = ImmutableList.copyOf(coefficient_proofs);
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

  /**
   * Generates a polynomial for sharing election keys.
   *
   * @param number_of_coefficients Number of coefficients of polynomial = the quorum, k.
   * @param nonce an optional nonce parameter that may be provided (only for testing), otherwise
   *               a random one is chosen.
   * @return ElectionPolynomial used to share election keys
   */
  public static ElectionPolynomial generate_polynomial(int number_of_coefficients, @Nullable Group.ElementModQ nonce) {
    ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
    ArrayList<Group.ElementModP> commitments = new ArrayList<>();
    ArrayList<SchnorrProof> proofs = new ArrayList<>();

    for (int i = 0; i < number_of_coefficients; i++) {
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
   * Computes the coordinate value of the election polynomial at exponent_modifier.
   *
   * @param exponent_modifier: Unique modifier (usually sequence order) for exponent
   * @param polynomial:        A Guardian's polynomial
   */
  public static ElementModQ compute_polynomial_coordinate(BigInteger exponent_modifier, ElectionPolynomial polynomial) {
    Preconditions.checkArgument(Group.between1andQ(exponent_modifier), "exponent_modifier is out of range");

    ElementModQ computed_value = ZERO_MOD_Q;
    int count = 0;
    for (ElementModQ coefficient : polynomial.coefficients) {
      ElementModQ exponent = pow_q(exponent_modifier, BigInteger.valueOf(count));
      ElementModQ factor = mult_q(coefficient, exponent);
      computed_value = add_q(computed_value, factor);
      count++;
    }
    return computed_value;
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
    ElementModQ numerator = Group.int_to_q_unchecked(BigInteger.valueOf(product).mod(Group.getPrimes().small_prime));
    // denominator = mult_q(*[(degree - coordinate) for degree in degrees])
    List<Integer> diff = degrees.stream().map(degree -> degree - coordinate).collect(Collectors.toList());
    int productDiff = diff.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ denominator = Group.int_to_q_unchecked(BigInteger.valueOf(productDiff).mod(Group.getPrimes().small_prime));
    return Group.div_q(numerator, denominator);
  }

  /**
   * Verify a polynomial coordinate value is in fact on the polynomial's curve.
   *
   * @param expected                Expected value
   * @param coordinate              Value to be checked
   * @param commitments Commitments for coefficients of polynomial
   */
  public static boolean verify_polynomial_coordinate(ElementModQ expected, BigInteger coordinate, List<ElementModP> commitments) {
    ElementModP commitment_output = compute_gPcoordinate(coordinate, commitments);

    ElementModP value_output = g_pow_p(expected);
    return value_output.equals(commitment_output);
  }

  /**
   * Compute g^P(coordinate).
   *
   * @param coordinate              Polynomial coordinate.
   * @param coefficient_commitments Commitments for coefficients of polynomial (K_ij)
   */
  public static ElementModP compute_gPcoordinate(BigInteger coordinate, List<ElementModP> coefficient_commitments) {
    ElementModP result = Group.ONE_MOD_P;
    int count = 0;
    for (ElementModP commitment : coefficient_commitments) {
      ElementModP exponent = Group.int_to_p_unchecked(Group.pow_pi(coordinate, BigInteger.valueOf(count)));
      ElementModP factor = Group.pow_p(commitment, exponent);
      result = Group.mult_p(result, factor);
      count++;
    }
    return result;
  }

}
