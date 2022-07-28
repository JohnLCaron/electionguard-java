package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;
import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ElectionPolynomial.*;
import static com.sunya.electionguard.Group.*;

public class TestElectionPolynomial {
  private static final int TEST_POLYNOMIAL_DEGREE = 5;
  private static final int VALUE = 1;
  private static final BigInteger TEST_EXPONENT_MODIFIER = BigInteger.ONE;

  @Example
  public void test_contest_description_valid_input_succeeds() {
    ElectionPolynomial polynomial = generate_polynomial(TEST_POLYNOMIAL_DEGREE, null);
    assertThat(polynomial).isNotNull();
  }

  @Example
  public void test_compute_polynomial_coordinate() {
    ElectionPolynomial polynomial = new ElectionPolynomial(
            ImmutableList.of(ONE_MOD_Q, TWO_MOD_Q),
            ImmutableList.of(ONE_MOD_P, TWO_MOD_P),
    ImmutableList.of());

    ElementModQ value = polynomial.valueAt(VALUE);
    assertThat(value).isNotNull();
  }

  @Example
  public void test_verify_polynomial_coordinate() {
    ElectionPolynomial polynomial = generate_polynomial(TEST_POLYNOMIAL_DEGREE, null);

    ElementModQ value = polynomial.valueAt(VALUE);
    assertThat(verifyPolynomialCoordinate(
            value, VALUE, polynomial.coefficient_commitments)).isTrue();
  }

  @Example
  public void test_verify_polynomial_coordinate_zero() {
    ElectionPolynomial polynomial = generate_polynomial(TEST_POLYNOMIAL_DEGREE, null);
    Group.ElementModP value = ElectionPolynomial.calculateGexpPiAtL(0, polynomial.coefficient_commitments);
    assertThat(value).isEqualTo(polynomial.coefficient_commitments.get(0));
  }
}
