package com.sunya.electionguard.publish;

import com.google.gson.TypeAdapterFactory;
import com.ryanharter.auto.value.gson.GsonTypeAdapterFactory;

/** Gson TypeAdapterFactory for @AutoValue classes. */
@GsonTypeAdapterFactory
public abstract class AutoValueGsonTypeAdapterFactory implements TypeAdapterFactory {
  public static TypeAdapterFactory create() {
    return new AutoValueGson_AutoValueGsonTypeAdapterFactory();
  }
}
