package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.ElectionConstants;

import java.lang.reflect.Type;
import java.math.BigInteger;

/** Conversion between ElectionConstants and Json, using python's object model. */
public class ElectionConstantsPojo {
  public String name;
  public BigInteger large_prime;
  public BigInteger small_prime;
  public BigInteger cofactor;
  public BigInteger generator;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static ElectionConstants deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElectionConstantsPojo pojo = gson.fromJson(jsonElem, ElectionConstantsPojo.class);
    return new ElectionConstants(
            pojo.large_prime,
            pojo.small_prime,
            pojo.cofactor,
            pojo.generator);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(ElectionConstants org) {
    ElectionConstantsPojo pojo = new ElectionConstantsPojo();
    pojo.large_prime = org.largePrime;
    pojo.small_prime = org.smallPrime;
    pojo.cofactor = org.cofactor;
    pojo.generator = org.generator;

    Gson gson = GsonTypeAdapters.enhancedGson();
    Type typeOfSrc = new TypeToken<ElectionConstantsPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

}
