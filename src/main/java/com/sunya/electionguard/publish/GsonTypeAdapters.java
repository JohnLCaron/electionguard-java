package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;

import java.lang.reflect.Type;
import java.math.BigInteger;

/**
 * LOOK: why do we need custom serializers?
 *    1. target must have no-arg constructors. But it may be private. AutoValue requires custom serializer.
 *    2. no circular references
 *    3. missing objects are set to their default (null, zero, false)
 *    4. collections must not be \<?>
 *    5. can do custom naming with @SerializedName
 */
class GsonTypeAdapters {

  static Gson enhancedGson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls()
            .registerTypeAdapter(Group.ElementModQ.class, new ModQDeserializer())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQSerializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPDeserializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPSerializer())
            .registerTypeAdapter(Manifest.class, new ElectionDescriptionSerializer())
            .registerTypeAdapter(Manifest.class, new ElectionDescriptionDeserializer())
            .registerTypeAdapter(GuardianRecord.class, new GuardianRecordSerializer()) // @AutoValue
            .registerTypeAdapter(GuardianRecord.class, new GuardianRecordDeserializer())
            .registerTypeAdapter(GuardianRecordPrivate.class, new GuardianRecordPrivateSerializer()) // @AutoValue
            .registerTypeAdapter(GuardianRecordPrivate.class, new GuardianRecordPrivateDeserializer())
            .registerTypeAdapter(SubmittedBallot.class, new CiphertextBallotSerializer())
            .registerTypeAdapter(SubmittedBallot.class, new CiphertextBallotDeserializer())
            .registerTypeAdapter(PlaintextBallot.class, new PlaintextBallotSerializer())
            .registerTypeAdapter(PlaintextBallot.class, new PlaintextBallotDeserializer())
            .registerTypeAdapter(PlaintextTally.class, new PlaintextTallySerializer())
            .registerTypeAdapter(PlaintextTally.class, new PlaintextTallyDeserializer())
            .registerTypeAdapter(CiphertextTally.class, new CiphertextTallySerializer())
            .registerTypeAdapter(CiphertextTally.class, new CiphertextTallyDeserializer())
            .create();
  }

  private static class ModQDeserializer implements JsonDeserializer<Group.ElementModQ> {
    @Override
    public Group.ElementModQ deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return Group.int_to_q(new BigInteger(content)).orElseThrow(RuntimeException::new);
    }
  }

  private static class ModPDeserializer implements JsonDeserializer<Group.ElementModP> {
    @Override
    public Group.ElementModP deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return Group.int_to_p(new BigInteger(content)).orElseThrow(RuntimeException::new);
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

  private static class ElectionDescriptionSerializer implements JsonSerializer<Manifest> {
    @Override
    public JsonElement serialize(Manifest src, Type typeOfSrc, JsonSerializationContext context) {
      return ManifestPojo.serialize(src);
    }
  }

  private static class ElectionDescriptionDeserializer implements JsonDeserializer<Manifest> {
    @Override
    public Manifest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return ManifestPojo.deserialize(json);
    }
  }

  private static class GuardianRecordSerializer implements JsonSerializer<GuardianRecord> {
    @Override
    public JsonElement serialize(GuardianRecord src, Type typeOfSrc, JsonSerializationContext context) {
      return GuardianRecordPojo.serialize(src);
    }
  }

  private static class GuardianRecordDeserializer implements JsonDeserializer<GuardianRecord> {
    @Override
    public GuardianRecord deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return GuardianRecordPojo.deserialize(json);
    }
  }

  private static class GuardianRecordPrivateSerializer implements JsonSerializer<GuardianRecordPrivate> {
    @Override
    public JsonElement serialize(GuardianRecordPrivate src, Type typeOfSrc, JsonSerializationContext context) {
      return GuardianRecordPrivatePojo.serialize(src);
    }
  }

  private static class GuardianRecordPrivateDeserializer implements JsonDeserializer<GuardianRecordPrivate> {
    @Override
    public GuardianRecordPrivate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return GuardianRecordPrivatePojo.deserialize(json);
    }
  }

  private static class CiphertextBallotSerializer implements JsonSerializer<SubmittedBallot> {
    @Override
    public JsonElement serialize(SubmittedBallot src, Type typeOfSrc, JsonSerializationContext context) {
      return SubmittedBallotPojo.serialize(src);
    }
  }

  private static class CiphertextBallotDeserializer implements JsonDeserializer<SubmittedBallot> {
    @Override
    public SubmittedBallot deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return SubmittedBallotPojo.deserialize(json);
    }
  }

  private static class PlaintextBallotSerializer implements JsonSerializer<PlaintextBallot> {
    @Override
    public JsonElement serialize(PlaintextBallot src, Type typeOfSrc, JsonSerializationContext context) {
      return PlaintextBallotPojo.serialize(src);
    }
  }

  private static class PlaintextBallotDeserializer implements JsonDeserializer<PlaintextBallot> {
    @Override
    public PlaintextBallot deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return PlaintextBallotPojo.deserialize(json);
    }
  }

  private static class PlaintextTallySerializer implements JsonSerializer<PlaintextTally> {
    @Override
    public JsonElement serialize(PlaintextTally src, Type typeOfSrc, JsonSerializationContext context) {
      return PlaintextTallyPojo.serialize(src);
    }
  }

  private static class PlaintextTallyDeserializer implements JsonDeserializer<PlaintextTally> {
    @Override
    public PlaintextTally deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return PlaintextTallyPojo.deserialize(json);
    }
  }

  private static class CiphertextTallySerializer implements JsonSerializer<CiphertextTally> {
    @Override
    public JsonElement serialize(CiphertextTally src, Type typeOfSrc, JsonSerializationContext context) {
      return CiphertextTallyPojo.serialize(src);
    }
  }

  private static class CiphertextTallyDeserializer implements JsonDeserializer<CiphertextTally> {
    @Override
    public CiphertextTally deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return CiphertextTallyPojo.deserialize(json);
    }
  }

  /*
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
              .where(new TypeParameter<>() {}, (TypeToken<E>) TypeToken.of(arg));
    }
  } */
}
