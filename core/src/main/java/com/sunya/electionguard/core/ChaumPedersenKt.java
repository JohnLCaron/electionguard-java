package com.sunya.electionguard.core;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Group;

import javax.annotation.concurrent.Immutable;
import javax.lang.model.element.Element;

import java.util.List;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

public class ChaumPedersenKt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Proof that the ciphertext is a given constant. */
  public record ConstantChaumPedersenProofKnownNonce(
          GenericChaumPedersenProof proof,
          int constant
  ) {

    boolean isValid(
            ElGamalKt.Ciphertext ciphertext,
            ElementModP publicKey,
            ElementModQ hashHeader,
            int expectedConstant) {

      return false;
      /* ElementModQ www = Group.int_to_q_unchecked(-this.constant)
      return false; /* proof.isValid(
              Group.G_MOD_P,
              ciphertext.pad(),
              publicKey,
              Group.mult_p(ciphertext.data(), Group.g_pow_p(-constant.toElementModQ(context)),
              List.of(ciphertext.pad(), ciphertext.data()),
              hashHeader,
              true
      ) and (if (expectedConstant != -1) constant == expectedConstant else true) */
    }
  }

  /** Proof that the ciphertext is a given constant. */
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
            List<Element> alsoHash,
            boolean checkC
            ) {
      return false; // expand(g, gx, h, hx).isValid(g, gx, h, hx, hashHeader, alsoHash, checkC);
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
          ElementModQ challenge) {

    // 2.0
    public DisjunctiveChaumPedersenProofKnownNonce {
      Preconditions.checkNotNull(proof0);
      Preconditions.checkNotNull(proof1);
      Preconditions.checkNotNull(challenge);
    }

    // 1.0
    public DisjunctiveChaumPedersenProofKnownNonce(
            ElementModQ proof_zero_challenge,
            ElementModQ proof_one_challenge, ElementModQ challenge, ElementModQ proof_zero_response,
            ElementModQ proof_one_response) {
      this(
              new GenericChaumPedersenProof(proof_zero_challenge, proof_zero_response),
              new GenericChaumPedersenProof(proof_one_challenge, proof_one_response),
              Preconditions.checkNotNull(challenge));
    }
  }
}