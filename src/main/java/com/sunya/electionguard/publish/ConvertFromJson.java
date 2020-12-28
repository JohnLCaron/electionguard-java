package com.sunya.electionguard.publish;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;
import com.sunya.electionguard.*;

import java.io.*;

import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.KeyCeremony.CoefficientValidationSet;

// TODO make compatible with python, eg ElementModQ
public class ConvertFromJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static CiphertextElectionContext readContext(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson();
      return gson.fromJson(reader, CiphertextElectionContext.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static ElectionConstants readConstants(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson();
      return gson.fromJson(reader, ElectionConstants.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Encrypt.EncryptionDevice readDevice(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson();
      return gson.fromJson(reader, Encrypt.EncryptionDevice.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Ballot.CiphertextAcceptedBallot readBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson();
      return gson.fromJson(reader, Ballot.CiphertextAcceptedBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Tally.PublishedCiphertextTally readCiphertextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new GsonBuilder().registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create()).create();
      return gson.fromJson(reader, Tally.PublishedCiphertextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Tally.PlaintextTally readPlaintextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new GsonBuilder().registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create()).create();
      return gson.fromJson(reader, Tally.PlaintextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static CoefficientValidationSet readCoefficient(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new GsonBuilder().registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create()).create();
      return gson.fromJson(reader, CoefficientValidationSet.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

}
