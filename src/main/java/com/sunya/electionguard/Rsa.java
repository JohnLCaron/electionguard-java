package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.util.Optional;

/** Wrapper for Java RSA encryption. */
class Rsa {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int KEY_SIZE = 4096;

  static Optional<Auxiliary.ByteString> encrypt(String message, PublicKey public_key) {
    try {
      return Optional.of(new Auxiliary.ByteString(rsa_encrypt(message, public_key)));
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("rsa_encrypt failed");
      throw new RuntimeException(e);
    }
  }

  static Optional<String> decrypt(Auxiliary.ByteString encrypted_message, PrivateKey secret_key) {
    try {
      return Optional.of(rsa_decrypt(encrypted_message.getBytes(), secret_key));
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("rsa_decrypt failed");
      throw new RuntimeException(e);
    }
  }

  /**  Create RSA keypair */
  static KeyPair rsa_keypair() {
    KeyPairGenerator keyGen;
    try {
      keyGen = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    keyGen.initialize(KEY_SIZE);
    return keyGen.generateKeyPair();
  }

  static byte[] rsa_encrypt(String data, PublicKey publicKey) throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, publicKey);
    return cipher.doFinal(data.getBytes());
  }

  static String rsa_decrypt(byte[] data, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.DECRYPT_MODE, privateKey);
    return new String(cipher.doFinal(data));
  }
}
