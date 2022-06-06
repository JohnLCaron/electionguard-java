package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.sunya.electionguard.Group;

import java.math.BigInteger;

/** Conversion between ElementMod and Json. */
public class ElementModPojo {
  public String data;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Group.ElementModQ deserializeQ(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    BigInteger biggy = gson.fromJson(jsonElem, BigInteger.class);
    return Group.int_to_q(biggy).orElseThrow();
  }

  public static Group.ElementModP deserializeP(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    BigInteger biggy = gson.fromJson(jsonElem, BigInteger.class);
    return Group.int_to_p(biggy).orElseThrow();
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serializeQ(Group.ElementModQ src) {
    return new JsonPrimitive(src.base16());
  }

  public static JsonElement serializeP(Group.ElementModP src) {
    return new JsonPrimitive(src.base16());
  }

}
