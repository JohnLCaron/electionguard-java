package com.sunya.electionguard.publish;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import java.util.Optional;

@AutoValue
public abstract class AvoTestClass {
  abstract Optional<String> sval();

  public static AvoTestClass create(Optional<String> sval) {
    return new AutoValue_AvoTestClass(sval);
  }

  public static TypeAdapter<AvoTestClass> typeAdapter(Gson gson) {
    return new AutoValue_AvoTestClass.GsonTypeAdapter(gson);
  }
}