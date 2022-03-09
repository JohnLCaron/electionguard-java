package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Map;

/** Conversion between ElectionContext and Json, using python's object model. */
public class ElectionContextPojo {
  public int number_of_guardians;
  public int quorum;
  public Group.ElementModP elgamal_public_key;
  public Group.ElementModQ manifest_hash;
  public Group.ElementModQ crypto_base_hash;
  public Group.ElementModQ crypto_extended_base_hash;
  public Group.ElementModQ commitment_hash;
  @Nullable
  public Map<String, String> extended_data;
  
  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static ElectionContext deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElectionContextPojo pojo = gson.fromJson(jsonElem, ElectionContextPojo.class);
    return new ElectionContext(
            pojo.number_of_guardians,
            pojo.quorum,
            pojo.elgamal_public_key,
            pojo.manifest_hash,
            pojo.crypto_base_hash,
            pojo.crypto_extended_base_hash,
            pojo.commitment_hash,
            pojo.extended_data
            );
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(ElectionContext org) {
    ElectionContextPojo pojo = new ElectionContextPojo();
    pojo.number_of_guardians = org.numberOfGuardians;
    pojo.quorum = org.quorum;
    pojo.elgamal_public_key = org.jointPublicKey;
    pojo.manifest_hash = org.manifestHash;
    pojo.crypto_base_hash = org.cryptoBaseHash;
    pojo.crypto_extended_base_hash = org.cryptoExtendedBaseHash;
    pojo.commitment_hash = org.commitmentHash;
    pojo.extended_data = org.extended_data;

    Gson gson = GsonTypeAdapters.enhancedGson();
    Type typeOfSrc = new TypeToken<ElectionContextPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

}
