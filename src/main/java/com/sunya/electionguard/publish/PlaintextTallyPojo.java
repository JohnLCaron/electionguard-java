package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Tally;

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
  public Map<String, Map<String, PlaintextTallyContestPojo>> spoiled_ballots;

  public static class PlaintextTallyContestPojo {
    public String object_id;
    public Map<String, PlaintextTallySelectionPojo> selections;
  }

  public static class PlaintextTallySelectionPojo {
    public String object_id;
    public Integer tally;
    public Group.ElementModP value;
    public ElGamal.Ciphertext message;
    public List<CiphertextDecryptionSelectionPojo> shares;
  }

  public static class CiphertextDecryptionSelectionPojo {
    public String object_id;
    public String guardian_id;
    public Group.ElementModQ description_hash;
    public Group.ElementModP share;
    public ChaumPedersenProofPojo proof; // Optional
    public Map<String, CiphertextCompensatedDecryptionSelectionPojo> recovered_parts; // Optional
  }

  public static class CiphertextCompensatedDecryptionSelectionPojo {
    public String object_id;
    public String guardian_id;
    public String missing_guardian_id;
    public Group.ElementModQ description_hash;
    public Group.ElementModP share;
    public Group.ElementModP recovery_key;
    public ChaumPedersenProofPojo proof;
  }

  public static class ChaumPedersenProofPojo {
    public Group.ElementModP pad;
    public Group.ElementModP data;
    public Group.ElementModQ challenge;
    public Group.ElementModQ response;
    public int constant;
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Tally.PlaintextTally deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextTallyPojo pojo = gson.fromJson(jsonElem, PlaintextTallyPojo.class);
    return translateTally(pojo);
  }

  private static Tally.PlaintextTally translateTally(PlaintextTallyPojo pojo) {
    Map<String, Tally.PlaintextTallyContest> contests = new HashMap<>();
    for (Map.Entry<String, PlaintextTallyContestPojo> entry : pojo.contests.entrySet()) {
      contests.put(entry.getKey(), translateContest(entry.getValue()));
    }

    Map<String, Map<String, Tally.PlaintextTallyContest>> spoiled_ballots = new HashMap<>();
    for (Map.Entry<String, Map<String, PlaintextTallyContestPojo>> entry1 : pojo.spoiled_ballots.entrySet()) {
      Map<String, Tally.PlaintextTallyContest> byContest = new HashMap<>();
      for (Map.Entry<String, PlaintextTallyContestPojo> entry2 : entry1.getValue().entrySet()) {
        byContest.put(entry2.getKey(), translateContest(entry2.getValue()));
      }
      spoiled_ballots.put(entry1.getKey(), byContest);
    }

    return Tally.PlaintextTally.create(
            pojo.object_id,
            contests,
            spoiled_ballots);
  }

  private static Tally.PlaintextTallyContest translateContest(PlaintextTallyContestPojo pojo) {
    Map<String, Tally.PlaintextTallySelection> selections = new HashMap<>();
    for (Map.Entry<String, PlaintextTallySelectionPojo> entry : pojo.selections.entrySet()) {
      selections.put(entry.getKey(), translateSelection(entry.getValue()));
    }
    return Tally.PlaintextTallyContest.create(
            pojo.object_id,
            selections);
  }

  private static Tally.PlaintextTallySelection translateSelection(PlaintextTallySelectionPojo pojo) {
    return Tally.PlaintextTallySelection.create(
            pojo.object_id,
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
            pojo.description_hash,
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
            pojo.description_hash,
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

  public static JsonElement serialize(Tally.PlaintextTally src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextTallyPojo pojo = convertTally(src);
    Type typeOfSrc = new TypeToken<PlaintextTallyPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static PlaintextTallyPojo convertTally(Tally.PlaintextTally org) {
    Map<String, PlaintextTallyContestPojo> contests = new HashMap<>();
    for (Map.Entry<String, Tally.PlaintextTallyContest> entry : org.contests().entrySet()) {
      contests.put(entry.getKey(), convertContest(entry.getValue()));
    }

    Map<String, Map<String, PlaintextTallyContestPojo>> spoiled_ballots = new HashMap<>();
    for (Map.Entry<String, Map<String, Tally.PlaintextTallyContest>> entry1 : org.spoiled_ballots().entrySet()) {
      Map<String, PlaintextTallyContestPojo> byContest = new HashMap<>();
      for (Map.Entry<String, Tally.PlaintextTallyContest> entry2 : entry1.getValue().entrySet()) {
        byContest.put(entry2.getKey(), convertContest(entry2.getValue()));
      }
      spoiled_ballots.put(entry1.getKey(), byContest);
    }

    PlaintextTallyPojo pojo = new PlaintextTallyPojo();
    pojo.object_id = org.object_id();
    pojo.contests = contests;
    pojo.spoiled_ballots = spoiled_ballots;
    return pojo;
  }

  private static PlaintextTallyContestPojo convertContest(Tally.PlaintextTallyContest org) {
    Map<String, PlaintextTallySelectionPojo> selections = new HashMap<>();
    for (Map.Entry<String, Tally.PlaintextTallySelection> entry : org.selections().entrySet()) {
      selections.put(entry.getKey(), convertSelection(entry.getValue()));
    }
    PlaintextTallyContestPojo pojo = new PlaintextTallyContestPojo();
    pojo.object_id = org.object_id();
    pojo.selections = selections;
    return pojo;
  }

  private static PlaintextTallySelectionPojo convertSelection(Tally.PlaintextTallySelection org) {
    PlaintextTallySelectionPojo pojo = new PlaintextTallySelectionPojo();
    pojo.object_id = org.object_id();
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
    pojo.description_hash = org.description_hash();
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
    pojo.description_hash = org.description_hash();
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
