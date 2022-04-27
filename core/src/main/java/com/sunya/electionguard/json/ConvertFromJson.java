package com.sunya.electionguard.json;

import com.google.common.flogger.FluentLogger;
import com.google.gson.*;
import com.sunya.electionguard.*;
import com.sunya.electionguard.standard.GuardianPrivateRecord;

import java.io.*;

/** Static helper methods for reading serialized classes from json files. */
public class ConvertFromJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Gson enhancedGson = GsonTypeAdapters.enhancedGson();

  public static AvailableGuardian readAvailableGuardian(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, AvailableGuardian.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readAvailableGuardian file '%s'", pathname);
      throw ioe;
    }
  }

  public static LagrangeCoefficientsPojo readCoefficients(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, LagrangeCoefficientsPojo.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readCoefficients file '%s'", pathname);
      throw ioe;
    }
  }

  public static CiphertextTally readCiphertextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CiphertextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readCiphertextTally file '%s'", pathname);
      throw ioe;
    }
  }

  public static GuardianRecord readGuardianRecord(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, GuardianRecord.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readGuardianRecord file '%s'", pathname);
      throw ioe;
    }
  }

  public static GuardianPrivateRecord readGuardianRecordPrivate(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      GuardianRecordPrivatePojo pojo = enhancedGson.fromJson(reader, GuardianRecordPrivatePojo.class);
      return GuardianRecordPrivatePojo.deserializeGuardianPrivateRecord(pojo);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readGuardianRecordPrivate file '%s'", pathname);
      throw ioe;
    }
  }

  public static ElectionConstants readConstants(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, ElectionConstants.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readConstants file '%s'", pathname);
      throw ioe;
    }
  }

  public static ElectionContext readContext(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, ElectionContext.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readContext file '%s'", pathname);
      ioe.printStackTrace();
      throw ioe;
    }
  }

  public static Manifest readManifest(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Manifest.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readElection file '%s'", pathname);
      throw ioe;
    }
  }

  public static Encrypt.EncryptionDevice readDevice(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, Encrypt.EncryptionDevice.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readDevice file '%s'", pathname);
      throw ioe;
    }
  }

  public static PlaintextBallot readPlaintextBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, PlaintextBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readPlaintextBallot file '%s'", pathname);
      throw new IOException(String.format("Failed readPlaintextBallot %s", pathname), ioe);
    }
  }

  public static PlaintextTally readPlaintextTally(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, PlaintextTally.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readPlaintextTally file '%s'", pathname);
      throw ioe;
    }
  }

  public static SubmittedBallot readSubmittedBallot(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, SubmittedBallot.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed readSubmittedBallot file '%s'", pathname);
      throw ioe;
    }
  }

}
