package com.sunya.electionguard.protoconvert;

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
  public static UInt256 importUInt256(@Nullable CommonProto.UInt256 modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    return new UInt256(modQ.getValue().toByteArray());
  }

  @Nullable
  public static UInt256 importUInt256fromQ(@Nullable CommonProto.UInt256 modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    return UInt256.fromModQ(importUInt256toQ(modQ));
  }

  @Nullable
  public static Group.ElementModQ importUInt256toQ(@Nullable CommonProto.UInt256 modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modQ.getValue().toByteArray());
    return Group.int_to_q_normalized(elem);
  }
  
  @Nullable
  public static Group.ElementModQ importElementModQ(@Nullable CommonProto.ElementModQ modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modQ.getValue().toByteArray());
    return Group.int_to_q_normalized(elem);
  }

  @Nullable
  public static Group.ElementModP importElementModP(@Nullable CommonProto.ElementModP modP) {
    if (modP == null || modP.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(1, modP.getValue().toByteArray());
    return Group.int_to_p_normalized(elem);
  }

  @Nullable
  public static ElGamal.Ciphertext importCiphertext(@Nullable CommonProto.ElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasPad()) {
      return null;
    }
    return new ElGamal.Ciphertext(
            importElementModP(ciphertext.getPad()),
            importElementModP(ciphertext.getData())
    );
  }

  @Nullable
  public static HashedElGamalCiphertext importHashedCiphertext(CommonProto.HashedElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasC0()) {
      return null;
    }

    return new HashedElGamalCiphertext(
            importElementModP(ciphertext.getC0()),
            ciphertext.getC1().toByteArray(),
            importUInt256fromQ(ciphertext.getC2()),
            ciphertext.getNumBytes()
    );
  }

  public static ChaumPedersen.ChaumPedersenProof importChaumPedersenProof(CommonProto.GenericChaumPedersenProof proof) {
    if (proof.hasPad() && proof.hasData()) {
      // ver1
      return new ChaumPedersen.ChaumPedersenProof(
              importElementModP(proof.getPad()),
              importElementModP(proof.getData()),
              importElementModQ(proof.getChallenge()),
              importElementModQ(proof.getResponse()));
    }
    // ver2
    return new ChaumPedersen.ChaumPedersenProof(
            importElementModQ(proof.getChallenge()),
            importElementModQ(proof.getResponse()));
  }

  public static SchnorrProof importSchnorrProof(CommonProto.SchnorrProof proof) {
    return new SchnorrProof(
            importElementModP(proof.getPublicKey()),
            importElementModP(proof.getCommitment()),
            importElementModQ(proof.getChallenge()),
            importElementModQ(proof.getResponse()));
  }

  /////////////////////////////////////////////////////////////////////////////////////////
  // to proto

  public static CommonProto.UInt256 publishUInt256(UInt256 modQ) {
    CommonProto.UInt256.Builder builder = CommonProto.UInt256.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.toBytes().array()));
    return builder.build();
  }

  public static CommonProto.UInt256 publishUInt256fromQ(Group.ElementModQ modQ) {
    CommonProto.UInt256.Builder builder = CommonProto.UInt256.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElementModQ publishElementModQ(Group.ElementModQ modQ) {
    CommonProto.ElementModQ.Builder builder = CommonProto.ElementModQ.newBuilder();
    builder.setValue(ByteString.copyFrom(modQ.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElementModP publishElementModP(Group.ElementModP modP) {
    CommonProto.ElementModP.Builder builder = CommonProto.ElementModP.newBuilder();
    builder.setValue(ByteString.copyFrom(modP.getBigInt().toByteArray()));
    return builder.build();
  }

  public static CommonProto.ElGamalCiphertext publishCiphertext(ElGamal.Ciphertext ciphertext) {
    CommonProto.ElGamalCiphertext.Builder builder = CommonProto.ElGamalCiphertext.newBuilder();
    builder.setPad(publishElementModP(ciphertext.pad()));
    builder.setData(publishElementModP(ciphertext.data()));
    return builder.build();
  }

  public static CommonProto.HashedElGamalCiphertext publishHashedCiphertext(HashedElGamalCiphertext ciphertext) {
    CommonProto.HashedElGamalCiphertext.Builder builder = CommonProto.HashedElGamalCiphertext.newBuilder();
    builder.setC0(publishElementModP(ciphertext.c0()));
    builder.setC1(ByteString.copyFrom(ciphertext.c1()));
    builder.setC2(publishUInt256(ciphertext.c2()));
    builder.setNumBytes(ciphertext.numBytes());
    return builder.build();
  }

  public static CommonProto.GenericChaumPedersenProof publishChaumPedersenProof(ChaumPedersen.ChaumPedersenProof proof) {
    CommonProto.GenericChaumPedersenProof.Builder builder = CommonProto.GenericChaumPedersenProof.newBuilder();
    if (proof.pad != null) {
      builder.setPad(publishElementModP(proof.pad));
    }
    if (proof.data != null) {
      builder.setData(publishElementModP(proof.data));
    }
    builder.setChallenge(publishElementModQ(proof.challenge));
    builder.setResponse(publishElementModQ(proof.response));
    return builder.build();
  }

  public static CommonProto.SchnorrProof publishSchnorrProof(SchnorrProof proof) {
    CommonProto.SchnorrProof.Builder builder = CommonProto.SchnorrProof.newBuilder();
    if (proof.publicKey != null) {
      builder.setPublicKey(publishElementModP(proof.publicKey));
    }
    if (proof.commitment != null) {
      builder.setCommitment(publishElementModP(proof.commitment));
    }
    builder.setChallenge(publishElementModQ(proof.challenge));
    builder.setResponse(publishElementModQ(proof.response));
    return builder.build();
  }
  
}
