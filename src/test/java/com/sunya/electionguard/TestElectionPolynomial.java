package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

import static com.sunya.electionguard.ElectionPolynomial.*;
import static com.sunya.electionguard.Group.*;


public class TestElectionPolynomial {
  private static final int TEST_POLYNOMIAL_DEGREE = 3;
  private static final BigInteger TEST_EXPONENT_MODIFIER = BigInteger.ONE;

  @Test
  public void test_contest_description_valid_input_succeeds() {
    ElectionPolynomial polynomial = generate_polynomial(TEST_POLYNOMIAL_DEGREE, null);
    assertThat(polynomial).isNotNull();
  }

  @Test
  public void test_compute_polynomial_coordinate() {
    ElectionPolynomial polynomial =  polynomial = new ElectionPolynomial(
            ImmutableList.of(ONE_MOD_Q, TWO_MOD_Q),
            ImmutableList.of(ONE_MOD_P, TWO_MOD_P),
    ImmutableList.of());

    ElementModQ value = compute_polynomial_coordinate(TEST_EXPONENT_MODIFIER, polynomial);

    assertThat(value).isNotNull();
  }

    @Test
    public void test_verify_polynomial_coordinate() {
      ElectionPolynomial polynomial = generate_polynomial(TEST_POLYNOMIAL_DEGREE, null);

      Group.ElementModQ value = compute_polynomial_coordinate(TEST_EXPONENT_MODIFIER, polynomial);

      assertThat(verify_polynomial_coordinate(
              value, TEST_EXPONENT_MODIFIER, polynomial.coefficient_commitments)).isTrue();
    }
}
