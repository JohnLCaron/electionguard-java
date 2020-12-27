package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;

/**
 *     A polynomial defined by coefficients
 *
 *     The 0-index coefficient is used for a secret key which can
 *     be discovered by a quorum of n guardians corresponding to n coefficients.
 */
@Immutable
public class ElectionPolynomial {

  /** The secret coefficients `a_ij`. */
  final ImmutableList<Group.ElementModQ> coefficients;

  /** The public keys `K_ij`generated from secret coefficients. */
  final ImmutableList<Group.ElementModP> coefficient_commitments;

  /** A proof of posession of the private key for each secret coefficient. */
  final ImmutableList<SchnorrProof> coefficient_proofs;

  ElectionPolynomial(List<Group.ElementModQ> coefficients, List<Group.ElementModP> coefficient_commitments, List<SchnorrProof> coefficient_proofs) {
    this.coefficients = ImmutableList.copyOf(coefficients);
    this.coefficient_commitments = ImmutableList.copyOf(coefficient_commitments);
    this.coefficient_proofs = ImmutableList.copyOf(coefficient_proofs);
  }

  /**
   *     Generates a polynomial for sharing election keys
   *
   *     @param number_of_coefficients: Number of coefficients of polynomial
   *     @param nonce: an optional nonce parameter that may be provided (useful for testing)
   *     @return Polynomial used to share election keys
   */
  static ElectionPolynomial generate_polynomial(int number_of_coefficients, @Nullable Group.ElementModQ nonce) {
    ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
    ArrayList<Group.ElementModP> commitments = new ArrayList<>();
    ArrayList<SchnorrProof> proofs = new ArrayList<>();

    for (int i = 0; i < number_of_coefficients; i++) {
      // Note the nonce value is not safe.it is designed for testing only.
      // this method should be called without the nonce in production.
      Group.ElementModQ coefficient = (nonce == null) ? rand_q() : add_qi(nonce.elem, BigInteger.valueOf(i));
      Group.ElementModP commitment = g_pow_p(coefficient);
      // TODO Alternate schnoor proof method that doesn 't need KeyPair
      SchnorrProof proof = SchnorrProof.make_schnorr_proof(new ElGamal.KeyPair(coefficient, commitment), rand_q());

      coefficients.add(coefficient);
      commitments.add(commitment);
      proofs.add(proof);
    }

    return new ElectionPolynomial(coefficients, commitments, proofs);
  }

  /**
   * Computes a single coordinate value of the election polynomial used for sharing
   *
   * @param exponent_modifier: Unique modifier (usually sequence order) for exponent
   * @param polynomial:        Election polynomial
   * @param exponent_modifier
   * @param polynomial
   * @return Polynomial used to share election keys
   */
  static ElementModQ compute_polynomial_coordinate(BigInteger exponent_modifier, ElectionPolynomial polynomial) {
    Preconditions.checkArgument(between(BigInteger.ZERO, exponent_modifier, Q), "exponent_modifier is out of range");

    ElementModQ computed_value = ZERO_MOD_Q;
    int count = 0;
    for (ElementModQ coefficient : polynomial.coefficients) {
      ElementModQ exponent = pow_q(exponent_modifier, BigInteger.valueOf(count));
      ElementModQ factor = mult_q(coefficient, exponent);
      computed_value = add_qi(computed_value.elem, factor.elem);
      count++;
    }
    return computed_value;
  }

  /**
   *     Compute the lagrange coefficient for a specific coordinate against N degrees.
   *     @param coordinate: the coordinate to plot, usually a Guardian's Sequence Order
   *     @param degrees: the degrees across which to plot, usually the collection of
   *                     available Guardians' Sequence Orders
   */
  static ElementModQ compute_lagrange_coefficient(BigInteger coordinate, List<BigInteger> degrees) {
    ElementModQ numerator = mult_q(Iterables.toArray(degrees, BigInteger.class));
    // denominator = mult_q(*[(degree - coordinate) for degree in degrees])
    List<BigInteger> diff = degrees.stream().map(degree -> degree.subtract(coordinate)).collect(Collectors.toList());
    ElementModQ denominator = mult_q(Iterables.toArray(diff, BigInteger.class));
    return div_q(numerator, denominator);
  }

  /**
   *     Verify a polynomial coordinate value is in fact on the polynomial's curve
   *
   *     @param coordinate Value to be checked
   *     @param exponent_modifier Unique modifier (usually sequence order) for exponent
   *     @param coefficient_commitments Commitments for coefficients of polynomial
   *     @return True if verified on polynomial
   */
  static boolean verify_polynomial_coordinate(ElementModQ coordinate, BigInteger exponent_modifier, List<ElementModP> coefficient_commitments) {
    BigInteger commitment_output = BigInteger.ONE;
    int count = 0;
    for (ElementModP commitment : coefficient_commitments) {
      BigInteger exponent = pow_p(exponent_modifier, BigInteger.valueOf(count));
      BigInteger factor = pow_p(commitment.getBigInt(), exponent);
      commitment_output = mult_p(ImmutableList.of(commitment_output, factor));
      count++;
    }

    ElementModP value_output = g_pow_p(coordinate);
    return value_output.elem.equals(commitment_output);
  }

}
