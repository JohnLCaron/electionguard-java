package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Group;

import java.lang.reflect.Type;
import java.math.BigInteger;

/** Conversion between ElementMod and Json. */
public class ElementModPojo {
  public String data;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Group.ElementModQ deserializeQ(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElementModPojo pojo = gson.fromJson(jsonElem, ElementModPojo.class);

    return Group.int_to_q(new BigInteger(pojo.data, 16)).orElseThrow(RuntimeException::new);
  }

  public static Group.ElementModP deserializeP(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElementModPojo pojo = gson.fromJson(jsonElem, ElementModPojo.class);
    return Group.int_to_p(new BigInteger(pojo.data, 16)).orElseThrow(() ->
            new RuntimeException("deserializeP: "+ pojo.data + "\n with prime " + Group.getPrimes().largePrime));
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serializeQ(Group.ElementModQ src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElementModPojo pojo = new ElementModPojo();
    pojo.data = src.to_hex();
    Type typeOfSrc = new TypeToken<ElementModPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  public static JsonElement serializeP(Group.ElementModP src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElementModPojo pojo = new ElementModPojo();
    pojo.data = src.to_hex();
    Type typeOfSrc = new TypeToken<ElementModPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

}
