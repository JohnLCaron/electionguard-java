package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElGamal;

import javax.annotation.Nullable;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion between SubmittedBallot and Json, using python's object model. */
public class SubmittedBallotPojo {
  public String object_id;
  public String ballot_style;
  public ElementModQ description_hash;
  public ElementModQ tracking_hash;
  public ElementModQ previous_tracking_hash;
  public List<CiphertextBallotContestPojo> contests;
  public long timestamp;
  public ElementModQ crypto_hash;
  // public ElementModQ nonce; LOOK temporary kludge until Optional serialization gets fixed
  public BallotBox.State state;

  public static class CiphertextBallotContestPojo {
    public String object_id;
    public ElementModQ description_hash;
    public List<CiphertextBallotSelectionPojo> ballot_selections;
    public ElementModQ crypto_hash;
    public ElGamalCiphertextPojo ciphertext_accumulation;
    public ElementModQ nonce;
    public ConstantChaumPedersenProofPojo proof;
  }

  public static class CiphertextBallotSelectionPojo {
    public String object_id;
    public ElementModQ description_hash;
    public ElGamalCiphertextPojo ciphertext;
    public ElementModQ crypto_hash;
    public boolean is_placeholder_selection;
    public ElementModQ nonce;
    public DisjunctiveChaumPedersenProofPojo proof;
    public ElGamalCiphertextPojo extended_data;
  }

  public static class ConstantChaumPedersenProofPojo {
    public ElementModP pad;
    public ElementModP data;
    public ElementModQ challenge;
    public ElementModQ response;
    public int constant;
  }

  public static class DisjunctiveChaumPedersenProofPojo {
    public ElementModP proof_zero_pad;
    public ElementModP proof_zero_data;
    public ElementModP proof_one_pad;
    public ElementModP proof_one_data;
    public ElementModQ proof_zero_challenge;
    public ElementModQ proof_one_challenge;
    public ElementModQ challenge;
    public ElementModQ proof_zero_response;
    public ElementModQ proof_one_response;
  }

  public static class ElGamalCiphertextPojo {
    public ElementModP pad;
    public ElementModP data;
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static SubmittedBallot deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    SubmittedBallotPojo pojo = gson.fromJson(jsonElem, SubmittedBallotPojo.class);
    return translateBallot(pojo);
  }

  // String object_id, String ballot_style, ElementModQ description_hash,
  //                            ElementModQ previous_tracking_hash, List<CiphertextBallotContest> contests,
  //                            Optional<ElementModQ> tracking_hash, long timestamp, ElementModQ crypto_hash,
  //                            Optional<ElementModQ> nonce
  private static SubmittedBallot translateBallot(SubmittedBallotPojo pojo) {
    return new SubmittedBallot(
            pojo.object_id,
            pojo.ballot_style,
            pojo.description_hash,
            pojo.previous_tracking_hash,
            convertList(pojo.contests, SubmittedBallotPojo::translateContest),
            pojo.tracking_hash,
            pojo.timestamp,
            pojo.crypto_hash,
            Optional.empty(), // Optional.ofNullable(pojo.nonce),
            pojo.state);
  }

  private static CiphertextBallot.Contest translateContest(CiphertextBallotContestPojo contest) {
    return new CiphertextBallot.Contest(
            contest.object_id,
            contest.description_hash,
            convertList(contest.ballot_selections, SubmittedBallotPojo::translateSelection),
            contest.crypto_hash,
            translateCiphertext(contest.ciphertext_accumulation),
            Optional.ofNullable(contest.nonce),
            Optional.ofNullable(translateConstantProof(contest.proof)));
  }

  private static CiphertextBallot.Selection translateSelection(CiphertextBallotSelectionPojo selection) {
    return new CiphertextBallot.Selection(
            selection.object_id,
            selection.description_hash,
            translateCiphertext(selection.ciphertext),
            selection.crypto_hash,
            selection.is_placeholder_selection,
            Optional.ofNullable(selection.nonce),
            Optional.ofNullable(translateDisjunctiveProof(selection.proof)),
            Optional.ofNullable(translateCiphertext(selection.extended_data)));
  }

  @Nullable
  private static ElGamal.Ciphertext translateCiphertext(@Nullable ElGamalCiphertextPojo ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    return new ElGamal.Ciphertext(
            ciphertext.pad,
            ciphertext.data);
  }

  @Nullable
  private static ChaumPedersen.ConstantChaumPedersenProof translateConstantProof(@Nullable ConstantChaumPedersenProofPojo proof) {
    if (proof == null) {
      return null;
    }
    return new ChaumPedersen.ConstantChaumPedersenProof(
            proof.pad,
            proof.data,
            proof.challenge,
            proof.response,
            proof.constant);
  }

  @Nullable
  private static ChaumPedersen.DisjunctiveChaumPedersenProof translateDisjunctiveProof(@Nullable DisjunctiveChaumPedersenProofPojo proof) {
    if (proof == null) {
      return null;
    }
    return new ChaumPedersen.DisjunctiveChaumPedersenProof(
            proof.proof_zero_pad,
            proof.proof_zero_data,
            proof.proof_one_pad,
            proof.proof_one_data,
            proof.proof_zero_challenge,
            proof.proof_one_challenge,
            proof.challenge,
            proof.proof_zero_response,
            proof.proof_one_response);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(SubmittedBallot src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    SubmittedBallotPojo pojo = convertAcceptedBallot(src);
    Type typeOfSrc = new TypeToken<SubmittedBallotPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static SubmittedBallotPojo convertAcceptedBallot(SubmittedBallot org) {
    SubmittedBallotPojo pojo = new SubmittedBallotPojo();
    pojo.object_id = org.object_id;
    pojo.ballot_style = org.ballot_style;
    pojo.description_hash = org.description_hash;
    pojo.previous_tracking_hash = org.previous_tracking_hash;
    pojo.contests = convertList(org.contests, SubmittedBallotPojo::convertContest);
    pojo.tracking_hash = org.tracking_hash;
    pojo.timestamp = org.timestamp;
    pojo.crypto_hash = org.crypto_hash;
    // pojo.nonce = org.nonce.orElse(null);
    pojo.state = org.state;
    return pojo;
  }

  private static CiphertextBallotContestPojo convertContest(CiphertextBallot.Contest contest) {
    CiphertextBallotContestPojo pojo = new CiphertextBallotContestPojo();
    pojo.object_id = contest.object_id;
    pojo.description_hash = contest.contest_hash;
    pojo.ballot_selections = convertList(contest.ballot_selections, SubmittedBallotPojo::convertSelection);
    pojo.crypto_hash = contest.crypto_hash;
    pojo.ciphertext_accumulation = convertCiphertext(contest.encrypted_total);
    pojo.nonce = contest.nonce.orElse(null);
    contest.proof.ifPresent(proof -> pojo.proof = convertConstantProof(proof));
    return pojo;
  }

  private static CiphertextBallotSelectionPojo convertSelection(CiphertextBallot.Selection selection) {
    CiphertextBallotSelectionPojo pojo = new CiphertextBallotSelectionPojo();
    pojo.object_id = selection.object_id;
    pojo.description_hash = selection.description_hash;
    pojo.ciphertext = convertCiphertext(selection.ciphertext());
    pojo.crypto_hash = selection.crypto_hash;
    pojo.is_placeholder_selection = selection.is_placeholder_selection;
    pojo.nonce = selection.nonce.orElse(null);
    selection.proof.ifPresent(proof -> pojo.proof = convertDisjunctiveProof(proof));
    selection.extended_data.ifPresent(ciphertext -> pojo.extended_data = convertCiphertext(ciphertext));
    return pojo;
  }

  private static ElGamalCiphertextPojo convertCiphertext(ElGamal.Ciphertext ciphertext) {
    ElGamalCiphertextPojo pojo = new ElGamalCiphertextPojo();
    pojo.pad = ciphertext.pad;
    pojo.data = ciphertext.data;
    return pojo;
  }

  private static ConstantChaumPedersenProofPojo convertConstantProof(ChaumPedersen.ConstantChaumPedersenProof proof) {
    ConstantChaumPedersenProofPojo pojo = new ConstantChaumPedersenProofPojo();
    pojo.pad = proof.pad;
    pojo.data = proof.data;
    pojo.challenge = proof.challenge;
    pojo.response = proof.response;
    pojo.constant = proof.constant;
    return pojo;
  }

  private static DisjunctiveChaumPedersenProofPojo convertDisjunctiveProof(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
    DisjunctiveChaumPedersenProofPojo pojo = new DisjunctiveChaumPedersenProofPojo();
    pojo.proof_zero_pad = proof.proof_zero_pad;
    pojo.proof_zero_data = proof.proof_zero_data;
    pojo.proof_one_pad = proof.proof_one_pad;
    pojo.proof_one_data = proof.proof_one_data;
    pojo.proof_zero_challenge = proof.proof_zero_challenge;
    pojo.proof_one_challenge = proof.proof_one_challenge;
    pojo.challenge = proof.challenge;
    pojo.proof_zero_response = proof.proof_zero_response;
    pojo.proof_one_response = proof.proof_one_response;
    return pojo;
  }
}
