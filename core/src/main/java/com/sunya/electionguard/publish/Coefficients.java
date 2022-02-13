package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.sunya.electionguard.Group;
import java.util.List;
import java.util.Objects;

/** Conversion between Coefficients and Json, using python's object model. */
public class Coefficients {
  public final List<Group.ElementModQ> coefficients;

  public Coefficients(List<Group.ElementModQ> coefficients) {
    this.coefficients = coefficients;
  }

  @Override
  public String toString() {
    return "Coefficients{" +
            "coefficients=" + coefficients +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Coefficients that = (Coefficients) o;
    return coefficients.equals(that.coefficients);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coefficients);
  }

  public static Coefficients deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    return new Coefficients(gson.fromJson(jsonElem, Pojo.class).coefficients);
  }

  public static JsonElement serialize(Coefficients src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    return gson.toJsonTree(new Pojo(src.coefficients), Pojo.class);
  }

  static class Pojo {
    public final List<Group.ElementModQ> coefficients;

    public Pojo(List<Group.ElementModQ> coefficients) {
      this.coefficients = coefficients;
    }
  }

}
