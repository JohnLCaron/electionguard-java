package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dongliu.gson.GsonJava8TypeAdapterFactory;
import net.jqwik.api.Example;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestOptional {
  private static final Gson gson = new GsonBuilder().serializeNulls()
          // doesnt matter which order these are registered in
          .registerTypeAdapterFactory(new GsonJava8TypeAdapterFactory())
          .registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create())
          .create();

  static class TestClassWithOptional {
    Optional<String> sval;

    public TestClassWithOptional(Optional<String> sval) {
      this.sval = sval;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TestClassWithOptional that = (TestClassWithOptional) o;
      return sval.equals(that.sval);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sval);
    }
  }

  @Example
  public void testOptionalEmpty() {
    TestClassWithOptional subject = new TestClassWithOptional(Optional.empty());

    String json = gson.toJson(subject, TestClassWithOptional.class);
    System.out.printf("Json produced = %s%n", json);
    TestClassWithOptional back = gson.fromJson(json, new TypeToken<TestClassWithOptional>() {}.getType());
    assertThat(back).isEqualTo(subject);
  }

  @Example
  public void testOptionalFull() {
    TestClassWithOptional subject = new TestClassWithOptional(Optional.of("ok"));

    String json = gson.toJson(subject, TestClassWithOptional.class);
    System.out.printf("Json produced = '%s'%n", json);
    TestClassWithOptional back = gson.fromJson(json, new TypeToken<TestClassWithOptional>() {}.getType());
    assertThat(back).isEqualTo(subject);
  }
}
