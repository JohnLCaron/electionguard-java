package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sunya.electionguard.protogen.*;

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
  public static ElGamal.Ciphertext convertCiphertext(@Nullable CommonProto.ElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasPad()) {
      return null;
    }
    return new ElGamal.Ciphertext(
            convertElementModP(ciphertext.getPad()),
            convertElementModP(ciphertext.getData())
    );
  }

  public static ChaumPedersen.ChaumPedersenProof convertChaumPedersenProof(CommonProto.ChaumPedersenProof proof) {
    return new ChaumPedersen.ChaumPedersenProof(
            convertElementModP(proof.getPad()),
            convertElementModP(proof.getData()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }

  public static ElGamal.KeyPair convertElgamalKeypair(CommonProto.ElGamalKeyPair keypair) {
    return new ElGamal.KeyPair(
            CommonConvert.convertElementModQ(keypair.getSecretKey()),
            CommonConvert.convertElementModP(keypair.getPublicKey()));
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
  public static java.security.PrivateKey convertJavaPrivateKey(CommonProto.RSAPrivateKey proto) {
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

  public static CommonProto.ElGamalCiphertext convertCiphertext(ElGamal.Ciphertext ciphertext) {
    CommonProto.ElGamalCiphertext.Builder builder = CommonProto.ElGamalCiphertext.newBuilder();
    builder.setPad(convertElementModP(ciphertext.pad));
    builder.setData(convertElementModP(ciphertext.data));
    return builder.build();
  }

  public static CommonProto.ChaumPedersenProof convertChaumPedersenProof(ChaumPedersen.ChaumPedersenProof proof) {
    CommonProto.ChaumPedersenProof.Builder builder = CommonProto.ChaumPedersenProof.newBuilder();
    builder.setPad(convertElementModP(proof.pad));
    builder.setData(convertElementModP(proof.data));
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    return builder.build();
  }

  public static CommonProto.ElGamalKeyPair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    CommonProto.ElGamalKeyPair.Builder builder = CommonProto.ElGamalKeyPair.newBuilder();
    builder.setSecretKey(CommonConvert.convertElementModQ(keypair.secret_key));
    builder.setPublicKey(CommonConvert.convertElementModP(keypair.public_key));
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
