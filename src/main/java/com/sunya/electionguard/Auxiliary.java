package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Wrapper around java.security.PrivateKey and java.security.PublicKey. */
public class Auxiliary {
  public static final Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  public static final Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));

  // To share secret values amongst each other, it is assumed that each guardian T i has previously
  // shared an auxiliary public encryption function E i with the group.(3) Each guardian T i then
  // publishes the encryption E l (R i,l , P i (l)) for every other guardian T l – where R i,l is a random
  // nonce.
  // (3) A “traditional” ElGamal public key is fine for this purpose. But the baseline ElectionGuard parameters p and q
  // are tuned for homomorphic purposes and are not well-suited for encrypting large values. The ElectionGuard
  // guardian keys can be used by breaking a message into small pieces (e.g. individual bytes) and encrypting a large
  // value as a sequence of small values. However, traditional public-key encryption methods are more efficient. Since
  // this key is only used internally, its form is not specified herein.

  /**
   * Pair of keys (public and secret) used to encrypt/decrypt information sent between guardians.
   * @see <a href="https://www.electionguard.vote/spec/0.95.0/4_Key_generation/#details-of-key-generation">Key Generation</a>
   * footnote 3.
   */
  @Immutable
  public static class KeyPair {
    /** The unique identifier of the guardian owning the key. */
    public final String owner_id;
    /** The sequence order of the auxiliary public key (usually the guardian's sequence order). */
    public final int sequence_order;
    public final java.security.PrivateKey secret_key;
    public final java.security.PublicKey public_key;

    public KeyPair(String owner_id, int sequence_order, java.security.PrivateKey secret_key, java.security.PublicKey public_key) {
      this.owner_id = Preconditions.checkNotNull(owner_id);
      this.sequence_order = sequence_order;
      this.secret_key = Preconditions.checkNotNull(secret_key);
      this.public_key = Preconditions.checkNotNull(public_key);
    }

    /** Share the auxiliary public key and associated data */
    public PublicKey share() {
      return new PublicKey(this.owner_id, this.sequence_order, this.public_key);
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

  /** A tuple of the auxiliary public key and owner information. */
  @Immutable
  public static class PublicKey {
    /** The unique identifier of the guardian owning the key. */
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

    /** Constructor makes a copy of the byte array. */
    public ByteString(byte[] bytes) {
      Preconditions.checkNotNull(bytes);
      this.bytes = bytes.clone();
    }

    /** Constructor makes a copy of the byte array. */
    public ByteString(String from) {
      Preconditions.checkNotNull(from);
      this.bytes = from.getBytes(UTF_8);
    }

    /** Get a copy of the byte array. */
    public byte[] getBytes() {
      return bytes.clone();
    }

    @Override
    public String toString() {
      return new String(bytes, UTF_8);
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
