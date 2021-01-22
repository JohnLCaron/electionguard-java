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

  public static Ballot.CiphertextAcceptedBallot readBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Ballot.CiphertextAcceptedBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Tally.PublishedCiphertextTally readCiphertextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Tally.PublishedCiphertextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
      throw ioe;
    }
  }

  public static Tally.PlaintextTally readPlaintextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Tally.PlaintextTally.class);
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
