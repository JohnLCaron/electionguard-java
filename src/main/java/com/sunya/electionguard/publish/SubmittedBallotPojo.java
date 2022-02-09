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

/** Conversion between SubmittedBallot and Json, using python's object model. */
public class SubmittedBallotPojo {
  public String object_id;
  public String style_id;
  public ElementModQ manifest_hash;
  public ElementModQ code;
  public ElementModQ code_seed;
  public List<CiphertextBallotContestPojo> contests;
  public Long timestamp;
  public ElementModQ crypto_hash;
  public ElementModQ nonce; // LOOK nonce always must be null, so can omit?
  public BallotBox.State state;

  public static class CiphertextBallotContestPojo {
    public String object_id;
    public Integer sequence_order;
    public ElementModQ description_hash;
    public List<CiphertextBallotSelectionPojo> ballot_selections;
    public ElementModQ crypto_hash;
    public ElGamalCiphertextPojo ciphertext_accumulation;
    public ElementModQ nonce; // LOOK nonce always must be null, so can omit?
    public ConstantChaumPedersenProofPojo proof;
  }

  public static class CiphertextBallotSelectionPojo {
    public String object_id;
    public Integer sequence_order;
    public ElementModQ description_hash;
    public ElGamalCiphertextPojo ciphertext;
    public ElementModQ crypto_hash;
    public Boolean is_placeholder_selection = Boolean.FALSE;
    public ElementModQ nonce; // LOOK nonce always must be null, so can omit?
    public DisjunctiveChaumPedersenProofPojo proof;
    public ElGamalCiphertextPojo extended_data;
  }

  public static class ConstantChaumPedersenProofPojo {
    public ElementModP pad;
    public ElementModP data;
    public ElementModQ challenge;
    public ElementModQ response;
    public Integer constant;
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

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static SubmittedBallot deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    SubmittedBallotPojo pojo = gson.fromJson(jsonElem, SubmittedBallotPojo.class);
    return translateBallot(pojo);
  }

  private static SubmittedBallot translateBallot(SubmittedBallotPojo pojo) {
    return new SubmittedBallot(
            pojo.object_id,
            pojo.style_id,
            pojo.manifest_hash,
            pojo.code_seed,
            ConvertPojos.convertList(pojo.contests, SubmittedBallotPojo::translateContest),
            pojo.code,
            pojo.timestamp,
            pojo.crypto_hash,
            pojo.state);
  }

  private static CiphertextBallot.Contest translateContest(CiphertextBallotContestPojo contest) {
    return new CiphertextBallot.Contest(
            contest.object_id,
            contest.sequence_order,
            contest.description_hash,
            ConvertPojos.convertList(contest.ballot_selections, SubmittedBallotPojo::translateSelection),
            contest.crypto_hash,
            translateCiphertext(contest.ciphertext_accumulation),
            Optional.ofNullable(contest.nonce),
            Optional.ofNullable(translateConstantProof(contest.proof)));
  }

  private static CiphertextBallot.Selection translateSelection(CiphertextBallotSelectionPojo selection) {
    return new CiphertextBallot.Selection(
            selection.object_id,
            selection.sequence_order,
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
    SubmittedBallotPojo pojo = convertSubmittedBallot(src);
    Type typeOfSrc = new TypeToken<SubmittedBallotPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static SubmittedBallotPojo convertSubmittedBallot(SubmittedBallot org) {
    SubmittedBallotPojo pojo = new SubmittedBallotPojo();
    pojo.object_id = org.object_id();
    pojo.style_id = org.style_id;
    pojo.manifest_hash = org.manifest_hash;
    pojo.code_seed = org.code_seed;
    pojo.contests = ConvertPojos.convertList(org.contests, SubmittedBallotPojo::convertContest);
    pojo.code = org.code;
    pojo.timestamp = org.timestamp;
    pojo.crypto_hash = org.crypto_hash;
    pojo.nonce = org.nonce.orElse(null);
    pojo.state = org.state;
    return pojo;
  }

  private static CiphertextBallotContestPojo convertContest(CiphertextBallot.Contest contest) {
    CiphertextBallotContestPojo pojo = new CiphertextBallotContestPojo();
    pojo.object_id = contest.object_id();
    pojo.sequence_order = contest.sequence_order();
    pojo.description_hash = contest.contest_hash;
    pojo.ballot_selections = ConvertPojos.convertList(contest.ballot_selections, SubmittedBallotPojo::convertSelection);
    pojo.crypto_hash = contest.crypto_hash;
    pojo.ciphertext_accumulation = convertCiphertext(contest.encrypted_total);
    pojo.nonce = contest.nonce.orElse(null);
    contest.proof.ifPresent(proof -> pojo.proof = convertConstantProof(proof));
    return pojo;
  }

  private static CiphertextBallotSelectionPojo convertSelection(CiphertextBallot.Selection selection) {
    CiphertextBallotSelectionPojo pojo = new CiphertextBallotSelectionPojo();
    pojo.object_id = selection.object_id();
    pojo.sequence_order = selection.sequence_order();
    pojo.description_hash = selection.description_hash();
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
