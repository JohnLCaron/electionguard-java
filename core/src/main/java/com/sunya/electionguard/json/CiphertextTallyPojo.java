package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.ballot.EncryptedTally;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/** Conversion between CiphertextTally and Json, using python's object model. */
public class CiphertextTallyPojo {
  public String object_id;
  public Map<String, CiphertextTallyContestPojo> contests;

  public static class CiphertextTallyContestPojo {
    public String object_id;
    public int sequence_order; // JSON leaves it out when 0 ? or old versions dont have this
    public Group.ElementModQ description_hash;
    public Map<String, CiphertextTallySelectionPojo> selections;
  }

  public static class CiphertextTallySelectionPojo {
    public String object_id;
    public int sequence_order;
    public Group.ElementModQ description_hash;
    public CiphertextPojo ciphertext;
  }

  public static class CiphertextPojo {
    public Group.ElementModP pad;
    public Group.ElementModP data;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static EncryptedTally deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CiphertextTallyPojo pojo = gson.fromJson(jsonElem, CiphertextTallyPojo.class);
    return translateTally(pojo);
  }

  private static EncryptedTally translateTally(CiphertextTallyPojo pojo) {
    Map<String, EncryptedTally.Contest> contests = new HashMap<>();
    for (Map.Entry<String, CiphertextTallyContestPojo> entry : pojo.contests.entrySet()) {
      contests.put(entry.getKey(), translateContest(entry.getValue()));
    }

    return new EncryptedTally(
            pojo.object_id,
            contests);
  }

  private static EncryptedTally.Contest translateContest(CiphertextTallyContestPojo pojo) {
    Map<String, EncryptedTally.Selection> selections = new HashMap<>();
    for (Map.Entry<String, CiphertextTallySelectionPojo> entry : pojo.selections.entrySet()) {
      selections.put(entry.getKey(), translateSelection(entry.getValue()));
    }
    return new EncryptedTally.Contest(
            pojo.object_id,
            pojo.sequence_order,
            pojo.description_hash,
            selections);
  }

  private static EncryptedTally.Selection translateSelection(CiphertextTallySelectionPojo pojo) {
    return new EncryptedTally.Selection(
            pojo.object_id,
            pojo.sequence_order,
            pojo.description_hash,
            translateCiphertext(pojo.ciphertext));
  }

  public static ElGamal.Ciphertext translateCiphertext(CiphertextPojo pojo) {
    return new ElGamal.Ciphertext(
            pojo.pad,
            pojo.data);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(EncryptedTally src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    CiphertextTallyPojo pojo = convertTally(src);
    Type typeOfSrc = new TypeToken<CiphertextTallyPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static CiphertextTallyPojo convertTally(EncryptedTally org) {
    Map<String, CiphertextTallyContestPojo> contests = new HashMap<>();
    for (Map.Entry<String, EncryptedTally.Contest> entry : org.contests.entrySet()) {
      contests.put(entry.getKey(), convertContest(entry.getValue()));
    }

    CiphertextTallyPojo pojo = new CiphertextTallyPojo();
    pojo.object_id = org.object_id();
    pojo.contests = contests;
    return pojo;
  }

  private static CiphertextTallyContestPojo convertContest(EncryptedTally.Contest org) {
    Map<String, CiphertextTallySelectionPojo> selections = new HashMap<>();
    for (Map.Entry<String, EncryptedTally.Selection> entry : org.selections.entrySet()) {
      selections.put(entry.getKey(), convertSelection(entry.getValue()));
    }
    CiphertextTallyContestPojo pojo = new CiphertextTallyContestPojo();
    pojo.object_id = org.object_id();
    pojo.sequence_order = org.sequence_order();
    pojo.description_hash = org.contestDescriptionHash;
    pojo.selections = selections;
    return pojo;
  }

  private static CiphertextTallySelectionPojo convertSelection(EncryptedTally.Selection org) {
    CiphertextTallySelectionPojo pojo = new CiphertextTallySelectionPojo();
    pojo.object_id = org.object_id();
    pojo.sequence_order = org.sequence_order();
    pojo.description_hash = org.description_hash();
    pojo.ciphertext = convertCiphertext(org.ciphertext());
    return pojo;
  }

  public static CiphertextPojo convertCiphertext(ElGamal.Ciphertext org) {
    CiphertextPojo pojo = new CiphertextPojo();
    pojo.pad = org.pad();
    pojo.data = org.data();
    return pojo;
  }

}
