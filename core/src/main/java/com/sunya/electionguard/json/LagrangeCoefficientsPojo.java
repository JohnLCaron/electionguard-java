package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.sunya.electionguard.Group;
import java.util.Map;
import java.util.Objects;

/** Conversion between AvailableGuardian and Json, using python's object model. */
public class LagrangeCoefficientsPojo {
  public final Map<String, Group.ElementModQ> coefficients; // guardian id to lagrange coefficient

  public LagrangeCoefficientsPojo(Map<String, Group.ElementModQ> coefficients) {
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
    LagrangeCoefficientsPojo that = (LagrangeCoefficientsPojo) o;
    return coefficients.equals(that.coefficients);
  }

  @Override
  public int hashCode() {
    return Objects.hash(coefficients);
  }

  public static LagrangeCoefficientsPojo deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    return new LagrangeCoefficientsPojo(gson.fromJson(jsonElem, Pojo.class).coefficients);
  }

  public static JsonElement serialize(LagrangeCoefficientsPojo src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    return gson.toJsonTree(new Pojo(src.coefficients), Pojo.class);
  }

  static class Pojo {
    public final Map<String, Group.ElementModQ> coefficients;

    public Pojo(Map<String, Group.ElementModQ> coefficients) {
      this.coefficients = coefficients;
    }
  }

}
