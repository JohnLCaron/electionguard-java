package com.sunya.electionguard.publish;

import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;

import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;

/** Conversion of CiphertextDecryptionSelection to/from Json. */
public class CiphertextDecryptionSelectionPojo {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public String object_id;
  public String guardian_id;
  public BigInteger description_hash;
  public BigInteger share;
  public ChaumPedersen.ChaumPedersenProof proof; // Optional
  public Map<String, CiphertextCompensatedDecryptionSelection> recovered_parts; // Optional

  public static JsonElement serialize(CiphertextDecryptionSelection src) {
    Gson gson = ConvertFromJson.enhancedGson();
    CiphertextDecryptionSelectionPojo pojo = convert(src);
    Type typeOfSrc = new TypeToken<CiphertextDecryptionSelectionPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  public static void write(String pathname, CiphertextDecryptionSelection src) throws IOException {
    Gson gson = ConvertFromJson.enhancedGson();
    CiphertextDecryptionSelectionPojo pojo = convert(src);
    Type type = new TypeToken<CiphertextDecryptionSelectionPojo>() {}.getType();
    try (FileWriter writer = new FileWriter(pathname)) {
      gson.toJson(pojo, type, writer);
    }
  }

  private static CiphertextDecryptionSelectionPojo convert(CiphertextDecryptionSelection org) {
    CiphertextDecryptionSelectionPojo pojo = new CiphertextDecryptionSelectionPojo();
    pojo.object_id = org.object_id();
    pojo.guardian_id = org.guardian_id();
    pojo.description_hash = org.description_hash().getBigInt();
    pojo.share = org.share().getBigInt();
    pojo.proof = org.proof().orElse(null);
    pojo.recovered_parts = org.recovered_parts().orElse(null);
    return pojo;
  }

  //////////////////////////////////////

  public static CiphertextDecryptionSelection deserialize(JsonElement jsonElem) {
    Gson gson = ConvertFromJson.enhancedGson();
    CiphertextDecryptionSelectionPojo pojo = gson.fromJson(jsonElem, CiphertextDecryptionSelectionPojo.class);
    return convert(pojo);
  }

  public static CiphertextDecryptionSelection read(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = ConvertFromJson.enhancedGson();
      CiphertextDecryptionSelectionPojo pojo = gson.fromJson(reader, CiphertextDecryptionSelectionPojo.class);
      return convert(pojo);
    }
  }

  private static CiphertextDecryptionSelection convert(CiphertextDecryptionSelectionPojo pojo) {
    return CiphertextDecryptionSelection.create(
            pojo.object_id,
            pojo.guardian_id,
            Group.int_to_q(pojo.description_hash).orElseThrow(RuntimeException::new),
            Group.int_to_p(pojo.share).orElseThrow(RuntimeException::new),
            Optional.ofNullable(pojo.proof),
            Optional.ofNullable(pojo.recovered_parts));
  }
}
