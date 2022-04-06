package com.sunya.electionguard.core;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

public class ChaumPedersenKt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Proof that the ciphertext is a given constant.
   */
  public record ConstantChaumPedersenProofKnownNonce(
          GenericChaumPedersenProof proof,
          int constant
  ) {

    public boolean isValid(
            ElGamal.Ciphertext ciphertext,
            ElementModP publicKey,
            ElementModQ hashHeader,
            int expectedConstant) {

      ElementModQ constantQ = Group.int_to_q_unchecked(BigInteger.valueOf(-this.constant));
      return proof.isValid(
              Group.getPrimes().generatorP,
              ciphertext.pad(),
              publicKey,
              Group.mult_p(ciphertext.data(), Group.pow_p(publicKey, constantQ)),
              hashHeader,
              new Object[] {ciphertext.pad(), ciphertext.data()},
              true
      ) && (constant == expectedConstant);
    }
  }

  /**
   * Proof that the ciphertext is a given constant.
   */
  public record ConstantChaumPedersenProofKnownSecretKey(
          GenericChaumPedersenProof proof,
          int constant
  ) {
  }

  /**
   * General-purpose Chaum-Pedersen proof object, demonstrating that the prover knows the exponent `x`
   * for two tuples `(g, g^x)` and `(h, h^x)`, without revealing anything about `x`. This is used as a
   * component in other proofs.
   * (See [Chaum-Pedersen 1992](https://link.springer.com/chapter/10.1007/3-540-48071-4_7))
   *
   * @param c hash(a, b, and possibly other state) (aka challenge)
   * @param r w + xc (aka response)
   */
  public record GenericChaumPedersenProof(ElementModQ c, ElementModQ r) {

    public boolean isValid(
            ElementModP g,
            ElementModP gx,
            ElementModP h,
            ElementModP hx,
            ElementModQ hashHeader,
            Object[] alsoHash,
            boolean checkC
    ) {
      return expand(g, gx, h, hx).isValid(g, gx, h, hx, hashHeader, alsoHash, checkC);
    }

    public ExpandedGenericChaumPedersenProof expand(
            ElementModP g,
            ElementModP gx,
            ElementModP h,
            ElementModP hx) {

      ElementModQ negC = Group.negate_q(c);
      ElementModP gr = Group.pow_p(g, r); // g^r = g^{w + xc}
      ElementModP hr = Group.pow_p(h, r); // h^r = h^{w + xc}
      ElementModP a = Group.mult_p(gr, Group.pow_p(gx, negC)); // cancelling out the xc, getting g^w
      ElementModP b = Group.mult_p(hr, Group.pow_p(hx, negC)); // cancelling out the xc, getting h^w
      return new ExpandedGenericChaumPedersenProof(a, b, c, r);
    }
  }

  public record ExpandedGenericChaumPedersenProof(
          ElementModP a,
          ElementModP b,
          ElementModQ c,
          ElementModQ r) {

    public boolean isValid(
            ElementModP g,
            ElementModP gx,
            ElementModP h,
            ElementModP hx,
            ElementModQ hashHeader,
            Object[] alsoHash,
            boolean checkC
    ) {

      boolean inBoundsG = g.is_valid_residue();
      boolean inBoundsGx = gx.is_valid_residue();
      boolean inBoundsH = h.is_valid_residue();
      boolean inBoundsHx = hx.is_valid_residue();

      // wrong should be *alsoHash
      Object[] hashElems = new Object[3 + alsoHash.length];
      hashElems[0] = hashHeader;
      hashElems[1] = a;
      hashElems[2] = b;
      System.arraycopy(alsoHash, 0, hashElems, 3, alsoHash.length);
      boolean hashGood = !checkC || this.c.equals(Hash.hash_elems(hashElems));

      boolean success = (hashGood && inBoundsG && inBoundsGx && inBoundsH && inBoundsHx);

      if (!success) {
        logger.atWarning().log(
                "Invalid generic Chaum-Pedersen proof: " +
                        " hashGood " + hashGood +
                        " inBoundsG " + inBoundsG +
                        " inBoundsGx " + inBoundsGx +
                        " inBoundsH " + inBoundsH +
                        " inBoundsHx " + inBoundsHx +
                        " %n");
      }
      return success;
    }
  }

  /**
   * Proof that the ciphertext is either zero or one. (See
   * [Cramer-Damgård-Schoenmakers 1994](https://www.iacr.org/cryptodb/data/paper.php?pubkey=1194))
   */
  @Immutable
  public record DisjunctiveChaumPedersenProofKnownNonce(
          GenericChaumPedersenProof proof0,
          GenericChaumPedersenProof proof1,
          ElementModQ c) {

    // 2.0
    public DisjunctiveChaumPedersenProofKnownNonce {
      Preconditions.checkNotNull(proof0);
      Preconditions.checkNotNull(proof1);
      Preconditions.checkNotNull(c);
    }

    // 1.0
    public DisjunctiveChaumPedersenProofKnownNonce(
            ChaumPedersen.DisjunctiveChaumPedersenProof ver1) {
      this(
              new GenericChaumPedersenProof(ver1.proof0.challenge, ver1.proof0.response),
              new GenericChaumPedersenProof(ver1.proof1.challenge, ver1.proof1.response),
              Preconditions.checkNotNull(ver1.challenge));
    }

    public boolean isValid(
            ElGamal.Ciphertext ciphertext,
            ElementModP publicKey,
            ElementModQ hashHeader) {

      ElementModP alpha = ciphertext.pad();
      ElementModP beta = ciphertext.data();

      boolean consistentC = Group.add_q(proof0.c, proof1.c).equals(c);
      ExpandedGenericChaumPedersenProof eproof0 =
              proof0.expand(
                      Group.getPrimes().generatorP,
                      ciphertext.pad(),
                      publicKey,
                      ciphertext.data()
              );
      ExpandedGenericChaumPedersenProof eproof1 =
              proof1.expand(
                      Group.getPrimes().generatorP,
                      ciphertext.pad(),
                      publicKey,
                      Group.div_p(ciphertext.data(), publicKey)
              );

      boolean validHash = c.equals(
              Hash.hash_elems(hashHeader, alpha, beta, eproof0.a(), eproof0.b(), eproof1.a(), eproof1.b()));

      boolean valid0 =
              eproof0.isValid(
                      Group.getPrimes().generatorP,
                      ciphertext.pad(),
                      publicKey,
                      ciphertext.data(),
                      hashHeader,
                      new Object[0],
                      false
                      );

      boolean valid1 =
              eproof1.isValid(
                      Group.getPrimes().generatorP,
                      ciphertext.pad(),
                      publicKey,
                      Group.div_p(ciphertext.data(), publicKey),
                      hashHeader,
                      new Object[0],
                      false
                      );

      // If valid0 or valid1 is false, this will already have been logged,
      // so we don't have to repeat it here.
      if (!consistentC || !validHash) {
        logger.atWarning().log(
        "Invalid commitments for disjunctive Chaum-Pedersen proof: " +
                        " consistentC " + consistentC +
                        " validHash " + validHash +
                        " valid0 " + valid0 +
                        " valid1 " + valid1);
      }

      return valid0 && valid1 && consistentC && validHash;
    }
  }

}