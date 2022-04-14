package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import com.sunya.electionguard.core.HashedElGamalCiphertext;
import com.sunya.electionguard.core.UInt256;
import electionguard.protogen.*;

public class CommonConvert {

  @Nullable
  static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).toList();
  }

  /////////////////////////////////////////////////////////////////////////////////////////
  // from proto

  @Nullable
  public static UInt256 convertUInt256fromQ(@Nullable CommonProto.UInt256 modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    return UInt256.fromModQ(convertUInt256toQ(modQ));
  }

  @Nullable
  public static Group.ElementModQ convertUInt256toQ(@Nullable CommonProto.UInt256 modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modQ.getValue().toByteArray());
    return Group.int_to_q_unchecked(elem);
  }
  
  @Nullable
  public static Group.ElementModQ convertElementModQ(@Nullable CommonProto.ElementModQ modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modQ.getValue().toByteArray());
    return Group.int_to_q_unchecked(elem);
  }

  @Nullable
  public static Group.ElementModP convertElementModP(@Nullable CommonProto.ElementModP modP) {
    if (modP == null || modP.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modP.getValue().toByteArray());
    return Group.int_to_p_unchecked(elem);
  }

  @Nullable
  public static ElGamal.Ciphertext convertCiphertext(@Nullable CommonProto.ElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasPad()) {
      return null;
    }
    return new ElGamal.Ciphertext(
            convertElementModP(ciphertext.getPad()),
            convertElementModP(ciphertext.getData())
    );
  }

  @Nullable
  public static HashedElGamalCiphertext convertHashedCiphertext(CommonProto.HashedElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasC0()) {
      return null;
    }

    return new HashedElGamalCiphertext(
            convertElementModP(ciphertext.getC0()),
            ciphertext.getC1().toByteArray(),
            convertUInt256fromQ(ciphertext.getC2()),
            ciphertext.getNumBytes()
    );
  }

  public static ChaumPedersen.ChaumPedersenProof convertChaumPedersenProof(CommonProto.GenericChaumPedersenProof proof) {
    if (proof.hasPad() && proof.hasData()) {
      // ver1
      return new ChaumPedersen.ChaumPedersenProof(
              convertElementModP(proof.getPad()),
              convertElementModP(proof.getData()),
              convertElementModQ(proof.getChallenge()),
              convertElementModQ(proof.getResponse()));
    }
    // ver2
    return new ChaumPedersen.ChaumPedersenProof(
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }

  public static SchnorrProof convertSchnorrProof(CommonProto.SchnorrProof proof) {
    return new SchnorrProof(
            convertElementModP(proof.getPublicKey()),
            convertElementModP(proof.getCommitment()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }

  /////////////////////////////////////////////////////////////////////////////////////////
  // to proto

  public static CommonProto.UInt256 convertUInt256(UInt256 modQ) {
    CommonProto.UInt256.Builder builder = CommonProto.UInt256.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.toBytes().array()));
    return builder.build();
  }

  public static CommonProto.UInt256 convertUInt256fromQ(Group.ElementModQ modQ) {
    CommonProto.UInt256.Builder builder = CommonProto.UInt256.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElementModQ convertElementModQ(Group.ElementModQ modQ) {
    CommonProto.ElementModQ.Builder builder = CommonProto.ElementModQ.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElementModP convertElementModP(Group.ElementModP modP) {
    CommonProto.ElementModP.Builder builder = CommonProto.ElementModP.newBuilder();
    builder.setValue(ByteString.copyFrom(modP.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElGamalCiphertext convertCiphertext(ElGamal.Ciphertext ciphertext) {
    CommonProto.ElGamalCiphertext.Builder builder = CommonProto.ElGamalCiphertext.newBuilder();
    builder.setPad(convertElementModP(ciphertext.pad()));
    builder.setData(convertElementModP(ciphertext.data()));
    return builder.build();
  }

  public static CommonProto.HashedElGamalCiphertext convertHashedCiphertext(HashedElGamalCiphertext ciphertext) {
    CommonProto.HashedElGamalCiphertext.Builder builder = CommonProto.HashedElGamalCiphertext.newBuilder();
    builder.setC0(convertElementModP(ciphertext.c0()));
    builder.setC1(ByteString.copyFrom(ciphertext.c1()));
    builder.setC2(convertUInt256(ciphertext.c2()));
    builder.setNumBytes(ciphertext.numBytes());
    return builder.build();
  }

  public static CommonProto.GenericChaumPedersenProof convertChaumPedersenProof(ChaumPedersen.ChaumPedersenProof proof) {
    CommonProto.GenericChaumPedersenProof.Builder builder = CommonProto.GenericChaumPedersenProof.newBuilder();
    if (proof.pad != null) {
      builder.setPad(convertElementModP(proof.pad));
    }
    if (proof.data != null) {
      builder.setData(convertElementModP(proof.data));
    }
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    return builder.build();
  }

  public static CommonProto.SchnorrProof convertSchnorrProof(SchnorrProof proof) {
    CommonProto.SchnorrProof.Builder builder = CommonProto.SchnorrProof.newBuilder();
    if (proof.publicKey != null) {
      builder.setPublicKey(convertElementModP(proof.publicKey));
    }
    if (proof.commitment != null) {
      builder.setCommitment(convertElementModP(proof.commitment));
    }
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    return builder.build();
  }
  
}
