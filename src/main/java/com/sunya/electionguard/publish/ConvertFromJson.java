package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.sunya.electionguard.*;
import net.dongliu.gson.GsonJava8TypeAdapterFactory;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.KeyCeremony.CoefficientValidationSet;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;

// LOOK make compatible with python, eg ElementModQ
/** Static helper methods for reading serialized classes from json files. */
public class ConvertFromJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Gson enhancedGson = enhancedGson();

  public static CiphertextElectionContext readContext(String pathname) throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      return enhancedGson.fromJson(reader, CiphertextElectionContext.class);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed reading file '%s'", pathname);
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
    // Here we are using our own conversion, LOOK investigate using standard Gson
    ElectionDescriptionFromJson reader = new ElectionDescriptionFromJson(pathname);
    return reader.build();
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

  public static Gson enhancedGson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls()
            .registerTypeAdapterFactory(AutoValueGsonTypeAdapterFactory.create())
            .registerTypeAdapterFactory(new GsonJava8TypeAdapterFactory())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQDeserializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPDeserializer())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQSerializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPSerializer())
            .registerTypeAdapter(CiphertextDecryptionSelection.class, new CiphertextDecryptionSelectionSerializer())
            .registerTypeAdapter(CiphertextDecryptionSelection.class, new CiphertextDecryptionSelectionDeserializer())
            .registerTypeAdapter(ImmutableList.class, new ImmutableListDeserializer())
            .create();
  }

  private static class ModQDeserializer implements JsonDeserializer<Group.ElementModQ> {
    public Group.ElementModQ deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return new Group.ElementModQ(new BigInteger(content));
    }
  }

  private static class ModPDeserializer implements JsonDeserializer<Group.ElementModP> {
    public Group.ElementModP deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return new Group.ElementModP(new BigInteger(content));
    }
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
  }

  private static class CiphertextDecryptionSelectionSerializer implements JsonSerializer<CiphertextDecryptionSelection> {
    @Override
    public JsonElement serialize(CiphertextDecryptionSelection src, Type typeOfSrc, JsonSerializationContext context) {
      return CiphertextDecryptionSelectionPojo.serialize(src);
    }
  }

  private static class CiphertextDecryptionSelectionDeserializer implements JsonDeserializer<CiphertextDecryptionSelection> {
    public CiphertextDecryptionSelection deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return CiphertextDecryptionSelectionPojo.deserialize(json);
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final class ImmutableListDeserializer implements JsonDeserializer<ImmutableList<?>> {
    @Override
    public ImmutableList<?> deserialize(final JsonElement json, final Type type, final JsonDeserializationContext context) throws JsonParseException {
      final Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
      final Type parameterizedType = listOf(typeArguments[0]).getType();
      final List<?> list = context.deserialize(json, parameterizedType);
      return ImmutableList.copyOf(list);
    }

    private <E> TypeToken<List<E>> listOf(final Type arg) {
      return new TypeToken<List<E>>() {}
              .where(new TypeParameter<E>() {}, (TypeToken<E>) TypeToken.of(arg));
    }
  }

}
