package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion between Tally.PlaintextTally and Json, using python's object model. */
public class PlaintextTallyPojo {
  public String object_id;
  public Map<String, PlaintextTallyContestPojo> contests;

  public static class PlaintextTallyContestPojo {
    public String object_id;
    public int sequence_order;
    public Map<String, PlaintextTallySelectionPojo> selections;
  }

  public static class PlaintextTallySelectionPojo {
    public String object_id;
    public int sequence_order;
    public Integer tally;
    public Group.ElementModP value;
    public ElGamal.Ciphertext message;
    public List<CiphertextDecryptionSelectionPojo> shares;
  }

  public static class CiphertextDecryptionSelectionPojo {
    public String object_id;
    public String guardian_id;
    public Group.ElementModP share;
    public ChaumPedersenProofPojo proof; // Optional
    public Map<String, CiphertextCompensatedDecryptionSelectionPojo> recovered_parts; // Optional
  }

  public static class CiphertextCompensatedDecryptionSelectionPojo {
    public String object_id;
    public String guardian_id;
    public String missing_guardian_id;
    public Group.ElementModP share;
    public Group.ElementModP recovery_key;
    public ChaumPedersenProofPojo proof;
  }

  public static class ChaumPedersenProofPojo {
    public Group.ElementModP pad;
    public Group.ElementModP data;
    public Group.ElementModQ challenge;
    public Group.ElementModQ response;
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static PlaintextTally deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextTallyPojo pojo = gson.fromJson(jsonElem, PlaintextTallyPojo.class);
    return translateTally(pojo);
  }

  private static PlaintextTally translateTally(PlaintextTallyPojo pojo) {
    Map<String, PlaintextTally.Contest> contests = pojo.contests.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e2 -> translateContest(e2.getValue())));

    return new PlaintextTally(
            pojo.object_id,
            contests);
  }

  private static PlaintextTally.Contest translateContest(PlaintextTallyContestPojo pojo) {
    Map<String, PlaintextTally.Selection> selections = pojo.selections.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e2 -> translateSelection(e2.getValue())));

    return PlaintextTally.Contest.create(
            pojo.object_id,
            selections);
  }

  private static PlaintextTally.Selection translateSelection(PlaintextTallySelectionPojo pojo) {
    return PlaintextTally.Selection.create(
            pojo.object_id,
            pojo.sequence_order,
            pojo.tally,
            pojo.value,
            pojo.message,
            convertList(pojo.shares, PlaintextTallyPojo::translateShare));
  }

  private static DecryptionShare.CiphertextDecryptionSelection translateShare(CiphertextDecryptionSelectionPojo pojo) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered = null;
    if (pojo.recovered_parts != null) {
      recovered = new HashMap<>();
      for (Map.Entry<String, CiphertextCompensatedDecryptionSelectionPojo> entry : pojo.recovered_parts.entrySet()) {
        recovered.put(entry.getKey(), translateCompensatedShare(entry.getValue()));
      }
    }

    return DecryptionShare.CiphertextDecryptionSelection.create(
            pojo.object_id,
            pojo.guardian_id,
            pojo.share,
            Optional.ofNullable(translateProof(pojo.proof)),
            Optional.ofNullable(recovered));
  }

  private static DecryptionShare.CiphertextCompensatedDecryptionSelection translateCompensatedShare(
          CiphertextCompensatedDecryptionSelectionPojo pojo) {

    return DecryptionShare.CiphertextCompensatedDecryptionSelection.create(
            pojo.object_id,
            pojo.guardian_id,
            pojo.missing_guardian_id,
            pojo.share,
            pojo.recovery_key,
            translateProof(pojo.proof));
  }

  @Nullable
  private static ChaumPedersen.ChaumPedersenProof translateProof(@Nullable ChaumPedersenProofPojo proof) {
    if (proof == null) {
      return null;
    }
    return new ChaumPedersen.ChaumPedersenProof(
      proof.pad,
      proof.data,
      proof.challenge,
      proof.response);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(PlaintextTally src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextTallyPojo pojo = convertTally(src);
    Type typeOfSrc = new TypeToken<PlaintextTallyPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static PlaintextTallyPojo convertTally(PlaintextTally org) {
    PlaintextTallyPojo pojo = new PlaintextTallyPojo();
    pojo.object_id = org.object_id;

    pojo.contests = org.contests.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e2 -> convertContest(e2.getValue())));

    return pojo;
  }

  private static PlaintextTallyContestPojo convertContest(PlaintextTally.Contest org) {
    PlaintextTallyContestPojo pojo = new PlaintextTallyContestPojo();
    pojo.object_id = org.object_id();
    pojo.selections = org.selections().entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e2 -> convertSelection(e2.getValue())));
    return pojo;
  }

  private static PlaintextTallySelectionPojo convertSelection(PlaintextTally.Selection org) {
    PlaintextTallySelectionPojo pojo = new PlaintextTallySelectionPojo();
    pojo.object_id = org.object_id();
    pojo.sequence_order = org.sequence_order();
    pojo.tally = org.tally();
    pojo.value = org.value();
    pojo.message = org.message();
    pojo.shares = convertList(org.shares(), PlaintextTallyPojo::convertShare);
    return pojo;
  }

  private static CiphertextDecryptionSelectionPojo convertShare(DecryptionShare.CiphertextDecryptionSelection org) {
    final Map<String, CiphertextCompensatedDecryptionSelectionPojo> recovered = new HashMap<>();
    org.recovered_parts().ifPresent(org_recoverd ->  {
      for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : org_recoverd.entrySet()) {
        recovered.put(entry.getKey(), convertCompensatedShare(entry.getValue()));
      }
    });

    CiphertextDecryptionSelectionPojo pojo = new CiphertextDecryptionSelectionPojo();
    pojo.object_id = org.object_id();
    pojo.guardian_id = org.guardian_id();
    pojo.share = org.share();
    org.proof().ifPresent( proof -> pojo.proof = convertProof(proof));
    pojo.recovered_parts = org.recovered_parts().isPresent() ? recovered : null;
    return pojo;
  }

  private static CiphertextCompensatedDecryptionSelectionPojo convertCompensatedShare(
          DecryptionShare.CiphertextCompensatedDecryptionSelection org) {

    CiphertextCompensatedDecryptionSelectionPojo pojo = new CiphertextCompensatedDecryptionSelectionPojo();
    pojo.object_id = org.object_id();
    pojo.guardian_id = org.guardian_id();
    pojo.missing_guardian_id = org.missing_guardian_id();
    pojo.share = org.share();
    pojo.recovery_key = org.recovery_key();
    pojo.proof = convertProof(org.proof());
    return pojo;
  }

  private static ChaumPedersenProofPojo convertProof(ChaumPedersen.ChaumPedersenProof proof) {
    ChaumPedersenProofPojo pojo = new ChaumPedersenProofPojo();
    pojo.pad = proof.pad;
    pojo.data = proof.data;
    pojo.challenge = proof.challenge;
    pojo.response = proof.response;
    return pojo;
  }

}
