package com.sunya.electionguard;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.protogen.CommonProto;
import net.jqwik.api.Example;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Rsa.*;

public class TestRsa {

  @Example
  public void test_rsa_encrypt() throws Exception {
    String message = "9893e1c926521dc595d501056d03c4387b87986089539349bed6eb1018229b2e0029dd38647bfc80746726b3710c8ac3f69187da2234b438370a4348a784791813b9857446eb14afc676eece5b789a207bcf633ba1676d3410913ae46dd247166c6a682cb0ccc5ecde53";

    KeyPair key_pair = rsa_keypair();
    byte[] encrypted_message = rsa_encrypt(message, key_pair.getPublic());
    String decrypted_message = rsa_decrypt(encrypted_message, key_pair.getPrivate());

    assertThat(message).isEqualTo(decrypted_message);
  }

  @Example
  public void test_serialize_public_keys() {
    KeyPair key_pair = rsa_keypair();
    CommonProto.RSAPublicKey proto = convertJavaPublicKey(key_pair.getPublic());
    java.security.PublicKey roundtrip = convertJavaPublicKey(proto);
    assertThat(roundtrip).isEqualTo(key_pair.getPublic());
  }

  @Example
  public void test_serialize_private_keys() {
    KeyPair key_pair = rsa_keypair();
    java.security.PrivateKey privateKey = key_pair.getPrivate();
    CommonProto.RSAPrivateKey proto = convertJavaPrivateKey(privateKey);
    java.security.PrivateKey orgKey = convertJavaPrivateKey(proto);
    // This fails with expected:
    //    SunRsaSign RSA private CRT key, 4096 bits
    //  but was:
    //    Sun RSA private key, 4096 bits
    // assertThat(roundtrip).isEqualTo(privateKey);
    System.out.printf("PrivateKey original=  %s%n", privateKey.getClass().getName());
    System.out.printf("PrivateKey roundtrip= %s%n", orgKey.getClass().getName());
    assertThat(comparePrivateKeys(orgKey, privateKey)).isTrue();
  }

  private static boolean comparePrivateKeys(java.security.PrivateKey key1, java.security.PrivateKey key2) {
    RSAPrivateKey rsa1 = (RSAPrivateKey) key1;
    RSAPrivateKey rsa2 = (RSAPrivateKey) key2;
    boolean modOk = rsa1.getModulus().equals(rsa2.getModulus());
    boolean expOk = rsa1.getPrivateExponent().equals(rsa2.getPrivateExponent());
    return modOk && expOk;
  }

  private static java.security.PublicKey convertJavaPublicKey(CommonProto.RSAPublicKey proto) {
    BigInteger publicExponent = new BigInteger(proto.getPublicExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    RSAPublicKeySpec Spec = new RSAPublicKeySpec(modulus, publicExponent);
    try {
      return KeyFactory.getInstance("RSA").generatePublic(Spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      throw new RuntimeException(e);
    }
  }

  private static CommonProto.RSAPublicKey convertJavaPublicKey(java.security.PublicKey key) {
    RSAPublicKey publicKey = (RSAPublicKey) key;
    CommonProto.RSAPublicKey.Builder builder = CommonProto.RSAPublicKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(publicKey.getModulus().toByteArray()));
    builder.setPublicExponent(ByteString.copyFrom(publicKey.getPublicExponent().toByteArray()));
    return builder.build();
  }

  private static java.security.PrivateKey convertJavaPrivateKey(CommonProto.RSAPrivateKey proto) {
    BigInteger privateExponent = new BigInteger(proto.getPrivateExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    RSAPrivateKeySpec spec = new RSAPrivateKeySpec(modulus, privateExponent); // or RSAPrivateCrtKeySpec ?
    try {
      KeyFactory fac = KeyFactory.getInstance("RSA");
      return fac.generatePrivate(spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      // java.security.spec.InvalidKeySpecException: Only RSAPrivate(Crt)KeySpec and PKCS8EncodedKeySpec supported for RSA private keys
      throw new RuntimeException(e);
    }
  }

  private static CommonProto.RSAPrivateKey convertJavaPrivateKey(java.security.PrivateKey key) {
    RSAPrivateKey privateKey = (RSAPrivateKey) key;
    CommonProto.RSAPrivateKey.Builder builder = CommonProto.RSAPrivateKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(privateKey.getModulus().toByteArray()));
    builder.setPrivateExponent(ByteString.copyFrom(privateKey.getPrivateExponent().toByteArray()));
    return builder.build();
  }
}
