package com.sunya.electionguard.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.standard.GuardianPrivateRecord;

import java.lang.reflect.Type;
import java.math.BigInteger;

/**
 * When do we need custom serializers?
 *    1. target must have no-arg constructors. But it may be private. AutoValue requires custom serializer.
 *    2. no circular references
 *    3. missing objects are set to their default (null, zero, false)
 *    4. collections must not be \<?>
 *    5. can do custom naming with @SerializedName
 */
class GsonTypeAdapters {

  static Gson enhancedGson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls()
            .registerTypeAdapter(AvailableGuardian.class, new AvailableGuardianSerializer())
            .registerTypeAdapter(AvailableGuardian.class, new AvailableGuardianDeserializer())
            .registerTypeAdapter(BallotBox.State.class, new BallotBoxStateSerializer())
            .registerTypeAdapter(BallotBox.State.class, new BallotBoxStateDeserializer())
            .registerTypeAdapter(BigInteger.class, new BigIntegerDeserializer())
            .registerTypeAdapter(BigInteger.class, new BigIntegerSerializer())
            .registerTypeAdapter(Boolean.class, new BooleanSerializer())
            .registerTypeAdapter(Boolean.class, new BooleanDeserializer())
            .registerTypeAdapter(LagrangeCoefficientsPojo.class, new LagrangeCoefficientsSerializer())
            .registerTypeAdapter(LagrangeCoefficientsPojo.class, new LagrangeCoefficientsDeserializer())
            .registerTypeAdapter(CiphertextTally.class, new CiphertextTallySerializer())
            .registerTypeAdapter(CiphertextTally.class, new CiphertextTallyDeserializer())
            .registerTypeAdapter(ElectionConstants.class, new ElectionConstantsSerializer())
            .registerTypeAdapter(ElectionConstants.class, new ElectionConstantsDeserializer())
            .registerTypeAdapter(ElectionCryptoContext.class, new ElectionContextSerializer())
            .registerTypeAdapter(ElectionCryptoContext.class, new ElectionContextDeserializer())
            .registerTypeAdapter(Encrypt.EncryptionDevice.class, new EncryptionDeviceSerializer())
            .registerTypeAdapter(Encrypt.EncryptionDevice.class, new EncryptionDeviceDeserializer())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQDeserializer())
            .registerTypeAdapter(Group.ElementModQ.class, new ModQSerializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPDeserializer())
            .registerTypeAdapter(Group.ElementModP.class, new ModPSerializer())
            .registerTypeAdapter(GuardianRecord.class, new GuardianRecordSerializer())
            .registerTypeAdapter(GuardianRecord.class, new GuardianRecordDeserializer())
            .registerTypeAdapter(GuardianPrivateRecord.class, new GuardianRecordPrivateSerializer())
            .registerTypeAdapter(GuardianPrivateRecord.class, new GuardianRecordPrivateDeserializer())
            // .registerTypeAdapter(Integer.class, new IntegerSerializer())
            // .registerTypeAdapter(Integer.class, new IntegerDeserializer())
            // .registerTypeAdapter(Long.class, new LongSerializer())
            // .registerTypeAdapter(Long.class, new LongDeserializer())
            .registerTypeAdapter(Manifest.class, new ManifestSerializer())
            .registerTypeAdapter(Manifest.class, new ManifestDeserializer())
            .registerTypeAdapter(PlaintextBallot.class, new PlaintextBallotSerializer())
            .registerTypeAdapter(PlaintextBallot.class, new PlaintextBallotDeserializer())
            .registerTypeAdapter(PlaintextTally.class, new PlaintextTallySerializer())
            .registerTypeAdapter(PlaintextTally.class, new PlaintextTallyDeserializer())
            .registerTypeAdapter(SubmittedBallot.class, new CiphertextBallotSerializer())
            .registerTypeAdapter(SubmittedBallot.class, new CiphertextBallotDeserializer())
            .create();
  }

  private static class AvailableGuardianDeserializer implements JsonDeserializer<AvailableGuardian> {
    @Override
    public AvailableGuardian deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return AvailableGuardianPojo.deserialize(json);
    }
  }

  private static class AvailableGuardianSerializer implements JsonSerializer<AvailableGuardian> {
    @Override
    public JsonElement serialize(AvailableGuardian src, Type typeOfSrc, JsonSerializationContext context) {
      return AvailableGuardianPojo.serialize(src);
    }
  }

  // ?? JsonElement.getAsBigInteger()
  private static class BigIntegerDeserializer implements JsonDeserializer<BigInteger> {
    @Override
    public BigInteger deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return new BigInteger(content, 10);
    }
  }

  private static class BigIntegerSerializer implements JsonSerializer<BigInteger> {
    @Override
    public JsonElement serialize(BigInteger src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.toString(10));
    }
  }

  private static class ModQDeserializer implements JsonDeserializer<Group.ElementModQ> {
    @Override
    public Group.ElementModQ deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return ElementModPojo.deserializeQ(json);
    }
  }

  private static class ModQSerializer implements JsonSerializer<Group.ElementModQ> {
    @Override
    public JsonElement serialize(Group.ElementModQ src, Type typeOfSrc, JsonSerializationContext context) {
      return ElementModPojo.serializeQ(src);
    }
  }

  private static class ModPDeserializer implements JsonDeserializer<Group.ElementModP> {
    @Override
    public Group.ElementModP deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return ElementModPojo.deserializeP(json);
    }
  }

  private static class ModPSerializer implements JsonSerializer<Group.ElementModP> {
    @Override
    public JsonElement serialize(Group.ElementModP src, Type typeOfSrc, JsonSerializationContext context) {
      return ElementModPojo.serializeP(src);
    }
  }

  private static class ManifestSerializer implements JsonSerializer<Manifest> {
    @Override
    public JsonElement serialize(Manifest src, Type typeOfSrc, JsonSerializationContext context) {
      return ManifestPojo.serialize(src);
    }
  }

  private static class ManifestDeserializer implements JsonDeserializer<Manifest> {
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

  private static class GuardianRecordPrivateSerializer implements JsonSerializer<GuardianPrivateRecord> {
    @Override
    public JsonElement serialize(GuardianPrivateRecord src, Type typeOfSrc, JsonSerializationContext context) {
      return GuardianRecordPrivatePojo.serialize(src);
    }
  }

  private static class GuardianRecordPrivateDeserializer implements JsonDeserializer<GuardianPrivateRecord> {
    @Override
    public GuardianPrivateRecord deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
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

  private static class ElectionConstantsSerializer implements JsonSerializer<ElectionConstants> {
    @Override
    public JsonElement serialize(ElectionConstants src, Type typeOfSrc, JsonSerializationContext context) {
      return ElectionConstantsPojo.serialize(src);
    }
  }

  private static class ElectionConstantsDeserializer implements JsonDeserializer<ElectionConstants> {
    @Override
    public ElectionConstants deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return ElectionConstantsPojo.deserialize(json);
    }
  }


  private static class ElectionContextSerializer implements JsonSerializer<ElectionCryptoContext> {
    @Override
    public JsonElement serialize(ElectionCryptoContext src, Type typeOfSrc, JsonSerializationContext context) {
      return ElectionContextPojo.serialize(src);
    }
  }

  private static class ElectionContextDeserializer implements JsonDeserializer<ElectionCryptoContext> {
    @Override
    public ElectionCryptoContext deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return ElectionContextPojo.deserialize(json);
    }
  }

  private static class EncryptionDeviceSerializer implements JsonSerializer<Encrypt.EncryptionDevice> {
    @Override
    public JsonElement serialize(Encrypt.EncryptionDevice src, Type typeOfSrc, JsonSerializationContext context) {
      return EncryptionDevicePojo.serialize(src);
    }
  }

  private static class EncryptionDeviceDeserializer implements JsonDeserializer<Encrypt.EncryptionDevice> {
    @Override
    public Encrypt.EncryptionDevice deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return EncryptionDevicePojo.deserialize(json);
    }
  }

  private static class LagrangeCoefficientsSerializer implements JsonSerializer<LagrangeCoefficientsPojo> {
    @Override
    public JsonElement serialize(LagrangeCoefficientsPojo src, Type typeOfSrc, JsonSerializationContext context) {
      return LagrangeCoefficientsPojo.serialize(src);
    }
  }

  private static class LagrangeCoefficientsDeserializer implements JsonDeserializer<LagrangeCoefficientsPojo> {
    @Override
    public LagrangeCoefficientsPojo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      return LagrangeCoefficientsPojo.deserialize(json);
    }
  }

  private static class IntegerSerializer implements JsonSerializer<Integer> {
    @Override
    public JsonElement serialize(Integer src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(Integer.toHexString(src));
    }
  }

  private static class IntegerDeserializer implements JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return Integer.parseInt(content, 16); // LOOK should it be unsigned?
    }
  }

  private static class LongSerializer implements JsonSerializer<Long> {
    @Override
    public JsonElement serialize(Long src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(Long.toHexString(src));
    }
  }

  private static class LongDeserializer implements JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      return Long.parseUnsignedLong(content, 16);
    }
  }

  private static class BooleanSerializer implements JsonSerializer<Boolean> {
    @Override
    public JsonElement serialize(Boolean src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src ? "01" : "00");
    }
  }

  private static class BooleanDeserializer implements JsonDeserializer<Boolean> {
    @Override
    public Boolean deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      String content = json.getAsJsonPrimitive().getAsString();
      switch (content) {
        case "00": return false;
        case "false": return false;
        case "01": return true;
        case "true": return true;
      }
      throw new IllegalStateException("Unknown boolean encoding " + content);
    }
  }

  private static class BallotBoxStateSerializer implements JsonSerializer<BallotBox.State> {
    @Override
    public JsonElement serialize(BallotBox.State state, Type typeOfSrc, JsonSerializationContext context) {
      int content = 3;
      switch (state) {
        case CAST: content =  1; break;
        case SPOILED: content =  2; break;
      }
      return new JsonPrimitive(content);
    }
  }

  private static class BallotBoxStateDeserializer implements JsonDeserializer<BallotBox.State> {
    @Override
    public BallotBox.State deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
      int content = json.getAsJsonPrimitive().getAsNumber().intValue();
      switch (content) {
        case 1: return BallotBox.State.CAST;
        case 2: return BallotBox.State.SPOILED;
        case 3: return BallotBox.State.UNKNOWN;
      }
      throw new IllegalStateException("Unknown BallotBox.State encoding " + content);
    }
  }
}
