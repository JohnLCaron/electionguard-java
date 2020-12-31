package com.sunya.electionguard;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.util.Optional;

/** Wrapper for java RSA encryption. */
public class Rsa {
  private static final int PUBLIC_EXPONENT = 65537;
  private static final int KEY_SIZE = 4096;
  private static final int PADDING = 11;
  private static final int MAX_BITS = KEY_SIZE / 8 - PADDING;
  private static final String ISO_ENCODING = "ISO-8859-1";
  private static final String BYTE_ORDER = "big";

  public static Optional<Auxiliary.ByteString> encrypt(String message, PublicKey public_key) {
    try {
      return Optional.of(new Auxiliary.ByteString(rsa_encrypt(message, public_key)));
    } catch (Exception e) {
      // log
      return Optional.empty();
    }
  }

  public static Optional<String> decrypt(Auxiliary.ByteString encrypted_message, PrivateKey secret_key) {
    try {
      return Optional.of(rsa_decrypt(encrypted_message.getBytes(), secret_key));
    } catch (Exception e) {
      // log
      return Optional.empty();
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
