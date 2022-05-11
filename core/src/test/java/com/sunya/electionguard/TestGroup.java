package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

class TestGroup {
  @Example
  public void basicsLg() {
    //basics(ElectionConstants.LARGE_TEST_CONSTANTS);
    //basics(ElectionConstants.SMALL_TEST_CONSTANTS);
    basics(ElectionConstants.STANDARD_CONSTANTS); // make it the last one since the change is persistant across tests
  }

  private void basics(ElectionConstants context) {
    try {
      Group.setPrimes(context);
      Group.ElementModQ three = Group.int_to_q_unchecked(BigInteger.valueOf(3));
      Group.ElementModQ four = Group.int_to_q_unchecked(BigInteger.valueOf(4));
      Group.ElementModQ seven = Group.int_to_q_unchecked(BigInteger.valueOf(7));
      assertThat(Group.add_q(three, four)).isEqualTo(seven);
    } finally {
      Group.reset();
    }
  }

  /*

  @Test
  fun generatorsWorkLg() = generatorsWork { productionGroup() }

  @Test
  fun generatorsWorkSm() = generatorsWork { tinyGroup() }

  fun generatorsWork(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      forAll(propTestFastConfig, elementsModP(context)) { it.inBounds() }
      forAll(propTestFastConfig, elementsModQ(context)) { it.inBounds() }
    }
  }

  @Test
  fun validResiduesForGPowPLg() = validResiduesForGPowP { productionGroup() }

  @Test
  fun validResiduesForGPowPSm() = validResiduesForGPowP { tinyGroup() }

  fun validResiduesForGPowP(contextF: suspend () -> GroupContext) {
    runTest {
      forAll(propTestFastConfig, validElementsModP(contextF())) { it.isValidResidue() }
    }
  }

  @Test
  fun binaryArrayRoundTrip() {
    runTest {
      val context = productionGroup()
      forAll(propTestFastConfig, elementsModP(context)) {
        it == context.binaryToElementModP(it.byteArray())
      }
      forAll(propTestFastConfig, elementsModQ(context)) {
        it == context.binaryToElementModQ(it.byteArray())
      }
    }
  }

  @Test
  fun base64RoundTrip() {
    runTest {
      val context = productionGroup()
      forAll(propTestFastConfig, elementsModP(context)) {
        it == context.base64ToElementModP(it.base64())
      }
      forAll(propTestFastConfig, elementsModQ(context)) {
        it == context.base64ToElementModQ(it.base64())
      }
    }
  }

  @Test
  fun baseConversionFails() {
    runTest {
      val context = productionGroup()
      listOf("", "@@", "-10", "1234567890".repeat(1000))
              .forEach {
        assertNull(context.base64ToElementModP(it))
        assertNull(context.base64ToElementModQ(it))
      }
    }
  }

  @Test
  fun additionBasicsLg() = additionBasics { productionGroup() }

  @Test
  fun additionBasicsSm() = additionBasics { tinyGroup() }

  fun additionBasics(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              elementsModQ(context),
              elementsModQ(context),
              elementsModQ(context)
      ) { a, b, c ->
              assertEquals(a, a + context.ZERO_MOD_Q) // identity
        assertEquals(a + b, b + a) // commutative
        assertEquals(a + (b + c), (a + b) + c) // associative
      }
    }
  }

  @Test
  fun additionWrappingQLg() = additionWrappingQ { productionGroup() }

  @Test
  fun additionWrappingQSm() = additionWrappingQ { tinyGroup() }

  fun additionWrappingQ(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(propTestFastConfig, Arb.int(min=0, max= intTestQ - 1)) { i ->
              val iq = i.toElementModQ(context)
        val q = context.ZERO_MOD_Q - iq
        assertTrue(q.inBounds())
        assertEquals(context.ZERO_MOD_Q, q + iq)
      }
    }
  }

  @Test
  fun multiplicationBasicsPLg() = multiplicationBasicsP { productionGroup() }

  @Test
  fun multiplicationBasicsPSm() = multiplicationBasicsP { tinyGroup() }

  fun multiplicationBasicsP(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              elementsModPNoZero(context),
              elementsModPNoZero(context),
              elementsModPNoZero(context)
      ) { a, b, c ->
              assertEquals(a, a * context.ONE_MOD_P) // identity
        assertEquals(a * b, b * a) // commutative
        assertEquals(a * (b * c), (a * b) * c) // associative
      }
    }
  }

  @Test
  fun multiplicationBasicsQLg() = multiplicationBasicsQ { productionGroup() }

  @Test
  fun multiplicationBasicsQsm() = multiplicationBasicsQ { tinyGroup() }

  fun multiplicationBasicsQ(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              elementsModQNoZero(context),
              elementsModQNoZero(context),
              elementsModQNoZero(context)
      ) { a, b, c ->
              assertEquals(a, a * context.ONE_MOD_Q)
        assertEquals(a * b, b * a)
        assertEquals(a * (b * c), (a * b) * c)
      }
    }
  }

  @Test
  fun subtractionBasicsLg() = subtractionBasics { productionGroup() }

  @Test
  fun subtractionBasicsSm() = subtractionBasics { tinyGroup() }

  fun subtractionBasics(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              elementsModQNoZero(context),
              elementsModQNoZero(context),
              elementsModQNoZero(context)
      ) { a, b, c ->
              assertEquals(a, a - context.ZERO_MOD_Q, "identity")
        assertEquals(a - b, -(b - a), "commutativity-ish")
        assertEquals(a - (b - c), (a - b) + c, "associativity-ish")
      }
    }
  }

  @Test
  fun negationLg() = negation { productionGroup() }

  @Test
  fun negationSm() = negation { tinyGroup() }

  fun negation(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      forAll(propTestFastConfig, elementsModQ(context)) { context.ZERO_MOD_Q == (-it) + it }
    }
  }

  @Test
  fun multiplicativeInversesPLg() = multiplicativeInversesP { productionGroup() }

  @Test
  fun multiplicativeInversesPSm() = multiplicativeInversesP { tinyGroup() }

  fun multiplicativeInversesP(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      // our inverse code only works for elements in the subgroup, which makes it faster
      forAll(propTestFastConfig, validElementsModP(context)) {
        it.multInv() * it == context.ONE_MOD_P
      }
    }
  }

  @Test
  fun multiplicativeInversesQLg() = multiplicativeInversesQ { productionGroup() }

  @Test
  fun multiplicativeInversesQSm() = multiplicativeInversesQ { tinyGroup() }

  fun multiplicativeInversesQ(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(propTestFastConfig, elementsModQNoZero(context)) {
        assertEquals(context.ONE_MOD_Q, it.multInv() * it)
      }
    }
  }

  @Test
  fun divisionP() {
    runTest {
      val context = productionGroup()
      forAll(propTestFastConfig, validElementsModP(context), validElementsModP(context))
      { a, b -> (a * b) / b == a }
    }
  }

  @Test
  fun exponentiationQLg() {
    runTest {
      val context = productionGroup()
      val qMinus1 = context.ZERO_MOD_Q - context.ONE_MOD_Q

      checkAll(propTestFastConfig, elementsModQNoZero(context)) {
        assertEquals(it * it, it powQ context.TWO_MOD_Q)
        assertEquals(context.ONE_MOD_Q, it powQ qMinus1)
      }
    }
  }

  @Test
  fun exponentiationQSm() {
    runTest {
      val context = tinyGroup()
      val qMinus1 = context.ZERO_MOD_Q - context.ONE_MOD_Q

      // note: unlike the production group, here we're going to let the
      // checker search much harder for a counterexample
      checkAll(elementsModQNoZero(context)) {
        assertEquals(it * it, it powQ context.TWO_MOD_Q)
        assertEquals(context.ONE_MOD_Q, it powQ qMinus1)
      }
    }
  }

  @Test
  fun exponentiationLg() = exponentiation { productionGroup() }

  @Test
  fun exponentiationSm() = exponentiation { tinyGroup() }

  fun exponentiation(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      forAll(propTestFastConfig, elementsModQ(context), elementsModQ(context)) { a, b ->
              context.gPowP(a) * context.gPowP(b) == context.gPowP(a + b)
      }
    }
  }

  @Test
  fun acceleratedExponentiation() {
    runTest {
      val context = productionGroup()
      forAll(propTestFastConfig, elementsModQ(context), elementsModQ(context)) { a, b ->
              val ga = context.gPowP(a)
        val normal = ga powP b
        val gaAccelerated = ga.acceleratePow()
        val faster = gaAccelerated powP b
        normal == faster
      }
    }
  }

  @Test
  fun subgroupInversesLg() = subgroupInverses { productionGroup() }

  @Test
  fun subgroupInversesSm() = subgroupInverses { tinyGroup() }

  fun subgroupInverses(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      forAll(propTestFastConfig, elementsModQ(context)) {
        val p1 = context.gPowP(it)
        val p2 = p1 powP (context.ZERO_MOD_Q - context.ONE_MOD_Q)
        p1 * p2 == context.ONE_MOD_P
      }
    }
  }

  @Test
  fun iterableAdditionLg() = iterableAddition { productionGroup() }

  @Test
  fun iterableAdditionSm() = iterableAddition { tinyGroup() }

  fun iterableAddition(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              elementsModQ(context),
              elementsModQ(context),
              elementsModQ(context)
      ) { a, b, c ->
              val expected = a + b + c
        assertEquals(expected, context.addQ(a, b, c))
        assertEquals(expected, with(context) { listOf(a, b, c).addQ() })
      }
    }
  }

  @Test
  fun iterableMultiplicationLg() = iterableMultiplication { productionGroup() }

  @Test
  fun iterableMultiplicationSm() = iterableMultiplication { tinyGroup() }

  fun iterableMultiplication(contextF: suspend () -> GroupContext) {
    runTest {
      val context = contextF()
      checkAll(
              propTestFastConfig,
              validElementsModP(context),
              validElementsModP(context),
              validElementsModP(context)
      ) { a, b, c ->
              val expected = a * b * c
        assertEquals(expected, context.multP(a, b, c))
        assertEquals(expected, with(context) { listOf(a, b, c).multP() })
      }
    }
  }

  @Test
  fun groupCompatibility() {
    runTest {
      val ctxP = productionGroup(PowRadixOption.NO_ACCELERATION)
      val ctxP2 = productionGroup(PowRadixOption.NO_ACCELERATION)
      val ctxT = tinyGroup()

      assertTrue(ctxP.isCompatible(ctxP.constants))
      assertTrue(ctxP.isCompatible(ctxP2.constants))
      assertFalse(ctxT.constants.isCompatible(ctxP.constants))
    }
  }

   */
}
