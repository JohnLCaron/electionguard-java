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

  static void writeAvailableGuardian(AvailableGuardian object, Path where) throws IOException {
    Type type = new TypeToken<AvailableGuardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeSubmittedBallot(SubmittedBallot object, Path where) throws IOException {
    Type type = new TypeToken<SubmittedBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeContext(CiphertextElectionContext object, Path where) throws IOException {
    Type type = new TypeToken<CiphertextElectionContext>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeCiphertextTally(CiphertextTally object, Path where) throws IOException {
    Type type = new TypeToken<CiphertextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeGuardianRecord(GuardianRecord object, Path where) throws IOException {
    Type type = new TypeToken<GuardianRecord>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeGuardianRecordPrivate(GuardianRecordPrivate object, Path where) throws IOException {
    Type type = new TypeToken<GuardianRecordPrivate>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeElection(Manifest object, Path where) throws IOException {
    Type type = new TypeToken<Manifest>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeConstants(ElectionConstants object, Path where) throws IOException {
    Type type = new TypeToken<ElectionConstants>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeDevice(Encrypt.EncryptionDevice object, Path where) throws IOException {
    Type type = new TypeToken<Encrypt.EncryptionDevice>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeGuardian(Guardian guardian, Path where) throws IOException {
    Type type = new TypeToken<Guardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(guardian, type, writer);
    }
  }

  static void writePlaintextBallot(PlaintextBallot ballot, Path where) throws IOException {
    Type type = new TypeToken<PlaintextBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(ballot, type, writer);
    }
  }

  static void writePlaintextTally(PlaintextTally object, Path where) throws IOException {
    Type type = new TypeToken<PlaintextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

}
