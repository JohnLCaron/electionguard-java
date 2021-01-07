package com.sunya.electionguard.publish;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.sunya.electionguard.*;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;

// LOOK, match python json, remove private fields
/** Static helper methods for writing serialized classes to json files. */
@SuppressWarnings("UnstableApiUsage")
public class ConvertToJson {
  private static final Gson enhancedGson = ConvertFromJson.enhancedGson();

  public static void write(Election.ElectionDescription object, Path where) throws IOException {
    // Here we are using our own conversion
    ElectionDescriptionToJson writer = new ElectionDescriptionToJson(where.toString());
    writer.write(object);

    /* TODO investigate using standard Gson
    Type type = new TypeToken<Election.ElectionDescription>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    } */
  }

  public static void write(Election.CiphertextElectionContext object, Path where) throws IOException {
    Type type = new TypeToken<Election.CiphertextElectionContext>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Election.ElectionConstants object, Path where) throws IOException {
    Type type = new TypeToken<Election.ElectionConstants>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Encrypt.EncryptionDevice object, Path where) throws IOException {
    Type type = new TypeToken<Encrypt.EncryptionDevice>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(KeyCeremony.CoefficientValidationSet object, Path where) throws IOException {
    Type type = new TypeToken<KeyCeremony.CoefficientValidationSet>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Ballot.CiphertextAcceptedBallot object, Path where) throws IOException {
    Type type = new TypeToken<Ballot.CiphertextAcceptedBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Tally.PublishedCiphertextTally  object, Path where) throws IOException {
    Type type = new TypeToken<Tally.PublishedCiphertextTally >(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Tally.PlaintextTally object, Path where) throws IOException {
    Type type = new TypeToken<Tally.PlaintextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void write(Guardian guardian, Path where) throws IOException {
    Type type = new TypeToken<Guardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(guardian, type, writer);
    }
  }

  public static void write(Ballot.PlaintextBallot ballot, Path where) throws IOException {
    Type type = new TypeToken<Ballot.PlaintextBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(ballot, type, writer);
    }
  }

  public static void write(Ballot.CiphertextBallot ballot, Path where) throws IOException {
    Type type = new TypeToken<Ballot.CiphertextBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(ballot, type, writer);
    }
  }

  /*
  private static Gson enhancedGson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls()
            .registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create())
            .registerTypeAdapterFactory(new GsonJava8TypeAdapterFactory())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQSerializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPSerializer())
            .create();
  }

  private static class ModQSerializer implements JsonSerializer<Group.ElementModQ> {
    @Override
    public JsonElement serialize(Group.ElementModQ src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.getBigInt().toString());
    }
  }

  private static class ModPSerializer implements JsonSerializer<Group.ElementModP> {
    @Override
    public JsonElement serialize(Group.ElementModP src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.getBigInt().toString());
    }
  } */

}
