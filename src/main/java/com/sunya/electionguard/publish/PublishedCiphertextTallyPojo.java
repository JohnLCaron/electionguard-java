package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PublishedCiphertextTally;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/** Conversion between Tally.PublishedCiphertextTally and Json, using python's object model. */
public class PublishedCiphertextTallyPojo {
  public String object_id;
  public Map<String, CiphertextTallyContestPojo> cast;

  public static class CiphertextTallyContestPojo {
    public String object_id;
    public Group.ElementModQ description_hash;
    public Map<String, CiphertextTallySelectionPojo> tally_selections;
  }

  public static class CiphertextTallySelectionPojo {
    public String object_id;
    public Group.ElementModQ description_hash;
    public ElGamal.Ciphertext ciphertext;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static PublishedCiphertextTally deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PublishedCiphertextTallyPojo pojo = gson.fromJson(jsonElem, PublishedCiphertextTallyPojo.class);
    return translateTally(pojo);
  }

  private static PublishedCiphertextTally translateTally(PublishedCiphertextTallyPojo pojo) {
    Map<String, PublishedCiphertextTally.CiphertextTallyContest> contests = new HashMap<>();
    for (Map.Entry<String, CiphertextTallyContestPojo> entry : pojo.cast.entrySet()) {
      contests.put(entry.getKey(), translateContest(entry.getValue()));
    }

    return new PublishedCiphertextTally(
            pojo.object_id,
            contests);
  }

  private static PublishedCiphertextTally.CiphertextTallyContest translateContest(CiphertextTallyContestPojo pojo) {
    Map<String, PublishedCiphertextTally.CiphertextTallySelection> selections = new HashMap<>();
    for (Map.Entry<String, CiphertextTallySelectionPojo> entry : pojo.tally_selections.entrySet()) {
      selections.put(entry.getKey(), translateSelection(entry.getValue()));
    }
    return new PublishedCiphertextTally.CiphertextTallyContest(
            pojo.object_id,
            pojo.description_hash,
            selections);
  }

  private static PublishedCiphertextTally.CiphertextTallySelection translateSelection(CiphertextTallySelectionPojo pojo) {
    //     public CiphertextTallySelection(String selectionDescriptionId, ElementModQ description_hash,
    //     @Nullable ElGamal.Ciphertext ciphertext) {
    return new PublishedCiphertextTally.CiphertextTallySelection(
            pojo.object_id,
            pojo.description_hash,
            pojo.ciphertext);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(PublishedCiphertextTally src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PublishedCiphertextTallyPojo pojo = convertTally(src);
    Type typeOfSrc = new TypeToken<PublishedCiphertextTallyPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static PublishedCiphertextTallyPojo convertTally(PublishedCiphertextTally org) {
    Map<String, CiphertextTallyContestPojo> cast = new HashMap<>();
    for (Map.Entry<String, PublishedCiphertextTally.CiphertextTallyContest> entry : org.contests.entrySet()) {
      cast.put(entry.getKey(), convertContest(entry.getValue()));
    }

    PublishedCiphertextTallyPojo pojo = new PublishedCiphertextTallyPojo();
    pojo.object_id = org.object_id;
    pojo.cast = cast;
    return pojo;
  }

  private static CiphertextTallyContestPojo convertContest(PublishedCiphertextTally.CiphertextTallyContest org) {
    Map<String, CiphertextTallySelectionPojo> selections = new HashMap<>();
    for (Map.Entry<String, PublishedCiphertextTally.CiphertextTallySelection> entry : org.tally_selections.entrySet()) {
      selections.put(entry.getKey(), convertSelection(entry.getValue()));
    }
    CiphertextTallyContestPojo pojo = new CiphertextTallyContestPojo();
    pojo.object_id = org.object_id;
    pojo.description_hash = org.description_hash;
    pojo.tally_selections = selections;
    return pojo;
  }

  private static CiphertextTallySelectionPojo convertSelection(PublishedCiphertextTally.CiphertextTallySelection org) {
    CiphertextTallySelectionPojo pojo = new CiphertextTallySelectionPojo();
    pojo.object_id = org.object_id;
    pojo.description_hash = org.description_hash;
    pojo.ciphertext = org.ciphertext();
    return pojo;
  }

}
