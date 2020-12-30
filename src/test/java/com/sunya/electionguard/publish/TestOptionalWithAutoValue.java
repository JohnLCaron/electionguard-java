package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dongliu.gson.GsonJava8TypeAdapterFactory;
import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestOptionalWithAutoValue {
  private static final Gson gson = new GsonBuilder().serializeNulls()
          // doesnt matter which order these are registered in
          .registerTypeAdapterFactory(new GsonJava8TypeAdapterFactory())
          .registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create())
          .create();

  @Example
  public void testAutoValueOptionalEmpty() {
    AvoTestClass subject = AvoTestClass.create(Optional.empty());

    String json = gson.toJson(subject, AvoTestClass.class);
    System.out.printf("Json produced = %s%n", json);
    AvoTestClass back = gson.fromJson(json, new TypeToken<AvoTestClass>() {}.getType());
    assertThat(back).isEqualTo(subject);
  }

  @Example
  public void testAutoValueOptionalFull() {
    AvoTestClass subject = AvoTestClass.create(Optional.of("ok"));

    String json = gson.toJson(subject, AvoTestClass.class);
    System.out.printf("Json produced = '%s'%n", json);
    AvoTestClass back = gson.fromJson(json, new TypeToken<AvoTestClass>() {}.getType());
    assertThat(back).isEqualTo(subject);
  }
}
