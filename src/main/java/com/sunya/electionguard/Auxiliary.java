package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Wrapper around java.security.PrivateKey. */
public class Auxiliary {

  /** Pair of keys (public and secret) used to encrypt/decrypt information sent between guardians. */
  @Immutable
  public static class KeyPair {
    public final java.security.PrivateKey secret_key;
    public final java.security.PublicKey public_key;

    public KeyPair(java.security.PrivateKey secret_key, java.security.PublicKey public_key) {
      this.secret_key = Preconditions.checkNotNull(secret_key);
      this.public_key = Preconditions.checkNotNull(public_key);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      KeyPair keyPair = (KeyPair) o;
      return secret_key.equals(keyPair.secret_key) &&
              public_key.equals(keyPair.public_key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(secret_key, public_key);
    }
  }

  /** A tuple of auxiliary public key and owner information. */
  @Immutable
  public static class PublicKey {
    /** The unique identifier of the guardian. */
    public final String owner_id;

    /** The sequence order of the auxiliary public key (usually the guardian's sequence order). */
    public final int sequence_order;

    /** The public key. */
    public final java.security.PublicKey key;

    public PublicKey(String owner_id, int sequence_order, java.security.PublicKey key) {
      this.owner_id = Preconditions.checkNotNull(owner_id);
      this.sequence_order = sequence_order;
      this.key = Preconditions.checkNotNull(key);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PublicKey publicKey = (PublicKey) o;
      return sequence_order == publicKey.sequence_order &&
              owner_id.equals(publicKey.owner_id) &&
              key.equals(publicKey.key);
    }

    @Override
    public int hashCode() {
      return Objects.hash(owner_id, sequence_order, key);
    }
  }

  /** An immutable byte array. */
  @Immutable
  public static final class ByteString {
    private final byte[] bytes;

    public ByteString(byte[] bytes) {
      Preconditions.checkNotNull(bytes);
      this.bytes = bytes.clone();
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ByteString that = (ByteString) o;
      return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(bytes);
    }
  }

  /** A callable type that represents the auxiliary encryption scheme. */
   public interface Encryptor {
      Optional<ByteString> encrypt(String message, java.security.PublicKey public_key);
  }

  /** A callable type that represents the auxiliary decryption scheme. */
  public interface Decryptor {
    Optional<String> decrypt(ByteString encrypted_message, java.security.PrivateKey secret_key);
  }
}
