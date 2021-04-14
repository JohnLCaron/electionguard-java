package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Optional;

/** Wrapper for Java RSA encryption. */
public class Rsa {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int KEY_SIZE = 4096;

  private Rsa() {}

  // python: rsa_encrypt
  public static Optional<Auxiliary.ByteString> encrypt(String message, java.security.PublicKey public_key) {
    try {
      return Optional.of(new Auxiliary.ByteString(rsa_encrypt(message, public_key)));
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("rsa_encrypt failed");
      return Optional.empty();
    }
  }

  // rsa_decrypt
  public static Optional<String> decrypt(Auxiliary.ByteString encrypted_message, java.security.PrivateKey secret_key) {
    try {
      return Optional.of(rsa_decrypt(encrypted_message.getBytes(), secret_key));
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("rsa_decrypt failed");
      return Optional.empty();
    }
  }

  /**  Create RSA keypair */
  public static java.security.KeyPair rsa_keypair() {
    KeyPairGenerator keyGen;
    try {
      keyGen = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    keyGen.initialize(KEY_SIZE);
    return keyGen.generateKeyPair();
  }

  static byte[] rsa_encrypt(String data, java.security.PublicKey publicKey) throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
    return cipher.doFinal(data.getBytes());
  }

  static String rsa_decrypt(byte[] data, java.security.PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.DECRYPT_MODE, privateKey);
    return new String(cipher.doFinal(data));
  }

  static boolean comparePrivateKeys(java.security.PrivateKey key1, java.security.PrivateKey key2) {
    RSAPrivateKey rsa1 = (RSAPrivateKey) key1;
    RSAPrivateKey rsa2 = (RSAPrivateKey) key2;
    boolean modOk = rsa1.getModulus().equals(rsa2.getModulus());
    boolean expOk = rsa1.getPrivateExponent().equals(rsa2.getPrivateExponent());
    return modOk && expOk;
  }

  public static java.security.PrivateKey convertJavaPrivateKey(BigInteger modulus, BigInteger privateExponent) {
    RSAPrivateKeySpec spec = new RSAPrivateKeySpec(modulus, privateExponent); // or RSAPrivateCrtKeySpec ?
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      throw new RuntimeException(e);
    }
  }

  public static java.security.PublicKey convertJavaPublicKey(BigInteger modulus, BigInteger publicExponent) {
    RSAPublicKeySpec Spec = new RSAPublicKeySpec(modulus, publicExponent);
    try {
      return KeyFactory.getInstance("RSA").generatePublic(Spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      throw new RuntimeException(e);
    }
  }

  public static class KeyPieces {
    public final BigInteger modulus;
    public final BigInteger exponent;

    public KeyPieces(BigInteger modulus, BigInteger exponent) {
      this.modulus = modulus;
      this.exponent = exponent;
    }
  }

  public static KeyPieces convertJavaPublicKey(java.security.PublicKey key) {
    RSAPublicKey publicKey = (RSAPublicKey) key;
    return new KeyPieces(publicKey.getModulus(), publicKey.getPublicExponent());
  }

  public static KeyPieces convertJavaPrivateKey(java.security.PrivateKey key) {
    RSAPrivateKey privateKey = (RSAPrivateKey) key;
    return new KeyPieces(privateKey.getModulus(), privateKey.getPrivateExponent());
  }
}
