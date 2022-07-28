package com.sunya.electionguard.json;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.sunya.electionguard.*;
import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.standard.GuardianPrivateRecord;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Static helper methods for writing serialized classes to json files. */
@SuppressWarnings("UnstableApiUsage")
public class ConvertToJson {
  private static final Gson enhancedGson = GsonTypeAdapters.enhancedGson();

  // LOOK maybe replace AvailableGuardian with AvailableGuardian ??
  static void writeAvailableGuardian(DecryptingGuardian object, Path where) throws IOException {
    Type type = new TypeToken<DecryptingGuardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeCoefficients(Iterable<DecryptingGuardian> object, Path where) throws IOException {
    LagrangeCoefficientsPojo coeffs = new LagrangeCoefficientsPojo(
            StreamSupport.stream(object.spliterator(), false)
            .collect(Collectors.toMap(DecryptingGuardian::guardianId, DecryptingGuardian::lagrangeCoefficient)));
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(coeffs, LagrangeCoefficientsPojo.class, writer);
    }
  }

  static void writeSubmittedBallot(EncryptedBallot object, Path where) throws IOException {
    Type type = new TypeToken<EncryptedBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeContext(ElectionCryptoContext object, Path where) throws IOException {
    Type type = new TypeToken<ElectionCryptoContext>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  static void writeCiphertextTally(EncryptedTally object, Path where) throws IOException {
    Type type = new TypeToken<EncryptedTally>(){}.getType();
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


  /////////////////////////////////////////////////////////////////////////

  /* static void writeGuardian(Guardian guardian, Path where) throws IOException {
    Type type = new TypeToken<Guardian>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(guardian, type, writer);
    }
  } */


  public static void writeGuardianRecord(GuardianRecord object, Path where) throws IOException {
    Type type = new TypeToken<GuardianRecord>() {}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

  public static void writeGuardianRecordPrivate(GuardianPrivateRecord object, Path where) throws IOException {
    Type type = new TypeToken<GuardianPrivateRecord>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      enhancedGson.toJson(object, type, writer);
    }
  }

}
