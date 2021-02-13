package com.sunya.electionguard.publish;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;
import com.sunya.electionguard.*;

import java.io.*;

import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.KeyCeremony.CoefficientValidationSet;

/** Static helper methods for reading serialized classes from json files. */
public class ConvertFromJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Gson enhancedGson = GsonTypeAdapters.enhancedGson();

  public static CiphertextElectionContext readContext(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CiphertextElectionContext.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      ioe.printStackTrace();
      throw ioe;
    }
  }

  public static ElectionConstants readConstants(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, ElectionConstants.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Encrypt.EncryptionDevice readDevice(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Encrypt.EncryptionDevice.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static ElectionDescription readElection(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, ElectionDescription.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static CiphertextAcceptedBallot readCiphertextBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CiphertextAcceptedBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static PlaintextBallot readPlaintextBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, PlaintextBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static CiphertextTally readCiphertextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CiphertextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static PlaintextTally readPlaintextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, PlaintextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static CoefficientValidationSet readCoefficient(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CoefficientValidationSet.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

}
