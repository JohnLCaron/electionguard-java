package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommonConvert {

  @Nullable
  static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  /////////////////////////////////////////////////////////////////////////////////////////
  // from proto
  
  @Nullable
  public static Group.ElementModQ convertElementModQ(@Nullable CommonProto.ElementModQ modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(modQ.getValue().toByteArray());
    return Group.int_to_q_unchecked(elem);
  }

  @Nullable
  public static Group.ElementModP convertElementModP(@Nullable CommonProto.ElementModP modP) {
    if (modP == null || modP.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(modP.getValue().toByteArray());
    return Group.int_to_p_unchecked(elem);
  }

  @Nullable
  static ElGamal.Ciphertext convertCiphertext(@Nullable CommonProto.ElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasPad()) {
      return null;
    }
    return new ElGamal.Ciphertext(
            convertElementModP(ciphertext.getPad()),
            convertElementModP(ciphertext.getData())
    );
  }

  public static SchnorrProof convertSchnorrProof(CommonProto.SchnorrProof proof) {
    return new SchnorrProof(
            convertElementModP(proof.getPublicKey()),
            convertElementModP(proof.getCommitment()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  public static java.security.PublicKey convertJavaPublicKey(CommonProto.RSAPublicKey proto) {
    BigInteger publicExponent = new BigInteger(proto.getPublicExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    return Rsa.convertJavaPublicKey(modulus, publicExponent);
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  static java.security.PrivateKey convertJavaPrivateKey(CommonProto.RSAPrivateKey proto) {
    BigInteger privateExponent = new BigInteger(proto.getPrivateExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    return Rsa.convertJavaPrivateKey(modulus, privateExponent);
  }

  /////////////////////////////////////////////////////////////////////////////////////////
  // to proto

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

  static CommonProto.ElGamalCiphertext convertCiphertext(ElGamal.Ciphertext ciphertext) {
    CommonProto.ElGamalCiphertext.Builder builder = CommonProto.ElGamalCiphertext.newBuilder();
    builder.setPad(convertElementModP(ciphertext.pad));
    builder.setData(convertElementModP(ciphertext.data));
    return builder.build();
  }

  public static CommonProto.SchnorrProof convertSchnorrProof(SchnorrProof proof) {
    CommonProto.SchnorrProof.Builder builder = CommonProto.SchnorrProof.newBuilder();
    builder.setPublicKey(convertElementModP(proof.public_key));
    builder.setCommitment(convertElementModP(proof.commitment));
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    return builder.build();
  }


  // LOOK there may be something better to do when serializing. Find out before use in production.
  public static CommonProto.RSAPublicKey convertJavaPublicKey(java.security.PublicKey key) {
    Rsa.KeyPieces pieces = Rsa.convertJavaPublicKey(key);
    CommonProto.RSAPublicKey.Builder builder = CommonProto.RSAPublicKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(pieces.modulus.toByteArray()));
    builder.setPublicExponent(ByteString.copyFrom(pieces.exponent.toByteArray()));
    return builder.build();
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  public static CommonProto.RSAPrivateKey convertJavaPrivateKey(java.security.PrivateKey key) {
    Rsa.KeyPieces pieces = Rsa.convertJavaPrivateKey(key);
    CommonProto.RSAPrivateKey.Builder builder = CommonProto.RSAPrivateKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(pieces.modulus.toByteArray()));
    builder.setPrivateExponent(ByteString.copyFrom(pieces.exponent.toByteArray()));
    return builder.build();
  }
  
}
