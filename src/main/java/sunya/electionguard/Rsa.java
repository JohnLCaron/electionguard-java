package sunya.electionguard;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

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
    KeyPairGenerator keyGen = null;
    try {
      keyGen = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    keyGen.initialize(KEY_SIZE);
    return keyGen.generateKeyPair();
  }

  static PublicKey getPublicKey(String base64PublicKey){
    PublicKey publicKey = null;
    try{
      X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.getBytes()));
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      publicKey = keyFactory.generatePublic(keySpec);
      return publicKey;
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (InvalidKeySpecException e) {
      e.printStackTrace();
    }
    return publicKey;
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


  /*
  static RSAKeyPair rsa_keypair_python() {
    RSAPrivateKeyWithSerialization private_key = generate_private_key(
            public_exponent=PUBLIC_EXPONENT, key_size=KEY_SIZE, backend=default_backend()
    );
    byte[] private_key_bytes = private_key.private_bytes(
            encoding=Encoding.PEM,
            format=PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=NoEncryption(),
            );

    String private_key_string = str(private_key_bytes, ISO_ENCODING)

    RSAPublicKey public_key  = private_key.public_key()
    byte[]  public_key_bytes = public_key.public_bytes(
            encoding=Encoding.PEM, format=PublicFormat.SubjectPublicKeyInfo
    );
    String public_key_string = str(public_key_bytes, ISO_ENCODING);

    return new RSAKeyPair(private_key_string, public_key_string);
  }

  static Optional<String> rsa_encrypt_python(String message, String public_key) {
    byte[] data = bytes(public_key, ISO_ENCODING);
    RSAPublicKey rsa_public_key = load_pem_public_key(data, backend = default_backend())
    String plaintext = bytes.fromhex(message)[];
    if (plaintext.length() > MAX_BITS) {
      return Optional.empty();
    }
    String ciphertext = rsa_public_key.encrypt(plaintext, PKCS1v15());
    return str(ciphertext, ISO_ENCODING);
  }

  static Optional<String>  rsa_decrypt_python(String encrypted_message, String private_key) {
    byte[] data = bytes(private_key, ISO_ENCODING);
    RSAPrivateKey rsa_private_key = load_pem_private_key(data, password = None, backend = default_backend());
    String ciphertext = bytes(encrypted_message, ISO_ENCODING);
    try {
      plaintext = rsa_private_key.decrypt(ciphertext, PKCS1v15());
    } catch (Exception e) {
      return Optional.empty();
    }
    return plaintext.hex();
  } */

  /** Count set bits for a particular integer. Not used ? */
  static int count_set_bits(int n) {
    int count = 0;
    while (n != 0) { // ??
      count += n & 1;
      n >>= 1;
    }
    return count;
  }
}
