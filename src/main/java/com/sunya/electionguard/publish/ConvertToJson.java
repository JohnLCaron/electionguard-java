package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.*;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;

// TODO, match python json, remove private fields
public class ConvertToJson {

  public static void write(Election.ElectionDescription object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Election.ElectionDescription>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Election.CiphertextElectionContext object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Election.CiphertextElectionContext>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Election.ElectionConstants object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Election.ElectionConstants>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Encrypt.EncryptionDevice object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Encrypt.EncryptionDevice>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(KeyCeremony.CoefficientValidationSet object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<KeyCeremony.CoefficientValidationSet>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Ballot.CiphertextAcceptedBallot object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Ballot.CiphertextAcceptedBallot>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Tally.PublishedCiphertextTally  object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Tally.PublishedCiphertextTally >(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  public static void write(Tally.PlaintextTally object, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Tally.PlaintextTally>(){}.getType();
    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(object, type, writer);
    }
  }

  // private
  public static void write(Guardian guardian, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Guardian>(){}.getType();

    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(guardian, type, writer);
    }
  }

  public static void write(Ballot.PlaintextBallot ballot, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Ballot.PlaintextBallot>(){}.getType();

    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(ballot, type, writer);
    }
  }

  public static void write(Ballot.CiphertextBallot ballot, Path where) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Type type = new TypeToken<Ballot.CiphertextBallot>(){}.getType();

    try (FileWriter writer = new FileWriter(where.toFile())) {
      gson.toJson(ballot, type, writer);
    }
  }

}
