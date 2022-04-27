package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Encrypt;

import java.lang.reflect.Type;

/** Conversion between Encrypt.EncryptionDevice and Json, using python's object model. */
public class EncryptionDevicePojo {
  /** Unique identifier for device. */
  public Long device_id;
  /** Used to identify session and protect the timestamp. */
  public Long session_id;
  /** Election initialization value. */
  public Long launch_code;
  /** Arbitrary string to designate the location of the device. */
  public String location;

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Encrypt.EncryptionDevice deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    EncryptionDevicePojo pojo = gson.fromJson(jsonElem, EncryptionDevicePojo.class);
    return new Encrypt.EncryptionDevice(
            pojo.device_id,
            pojo.session_id,
            pojo.launch_code,
            pojo.location);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(Encrypt.EncryptionDevice org) {
    EncryptionDevicePojo pojo = new EncryptionDevicePojo();
    pojo.device_id = org.deviceId();
    pojo.session_id = org.sessionId();
    pojo.launch_code = org.launchCode();
    pojo.location = org.location();

    Gson gson = GsonTypeAdapters.enhancedGson();
    Type typeOfSrc = new TypeToken<EncryptionDevicePojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

}
