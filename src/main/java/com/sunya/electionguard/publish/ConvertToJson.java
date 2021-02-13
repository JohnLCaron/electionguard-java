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
class ConvertToJson {
  private static final Gson enhancedGson = GsonTypeAdapters.enhancedGson();

  static void write(Election object, Path where) throws IOException {
    Type type = new TypeToken<Election>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(CiphertextElectionContext object, Path where) throws IOException {
    Type type = new TypeToken<CiphertextElectionContext>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(ElectionConstants object, Path where) throws IOException {
    Type type = new TypeToken<ElectionConstants>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(Encrypt.EncryptionDevice object, Path where) throws IOException {
    Type type = new TypeToken<Encrypt.EncryptionDevice>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(KeyCeremony.CoefficientValidationSet object, Path where) throws IOException {
    Type type = new TypeToken<KeyCeremony.CoefficientValidationSet>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(CiphertextAcceptedBallot object, Path where) throws IOException {
    Type type = new TypeToken<CiphertextAcceptedBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(CiphertextTally object, Path where) throws IOException {
    Type type = new TypeToken<CiphertextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(PlaintextTally object, Path where) throws IOException {
    Type type = new TypeToken<PlaintextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void write(Guardian guardian, Path where) throws IOException {
    Type type = new TypeToken<Guardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(guardian, type, writer);
    }
  }

  static void write(PlaintextBallot ballot, Path where) throws IOException {
    Type type = new TypeToken<PlaintextBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(ballot, type, writer);
    }
  }

  public static void write(CiphertextBallot ballot, Path where) throws IOException {
    Type type = new TypeToken<CiphertextBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(ballot, type, writer);
    }
  }

}
