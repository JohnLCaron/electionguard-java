package com.sunya.electionguard.core;

import at.favre.lib.bytes.Bytes;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static at.favre.lib.bytes.BytesTransformers.hmacSha256;

/** The ciphertext representation of an arbitrary byte-array, encrypted with an ElGamal public key. */
 public record HashedElGamalCiphertext(
        Group.ElementModP c0, // pad or alpha
        Bytes c1, // encrypted data or beta
        UInt256 c2, // message authentication code for hmac
        int numBytes // length of result
  ) {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int BLOCK_SIZE = 32;

  /**
   * Given an array of plaintext bytes, encrypts those bytes using the "hashed ElGamal" stream cipher,
   * described in the ElectionGuard specification, in section 3. The nonce may be specified to make
   * the encryption deterministic. Otherwise, it's selected at random.
   */
  public static HashedElGamalCiphertext create(
          Bytes message,
          Group.ElementModP publicKey,
          Group.ElementModQ nonce) {

    List<Bytes> blocks = chunk(message, BLOCK_SIZE);

    // ElectionGuard spec: (alpha, beta) = (g^R mod p, K^R mod p)
    Group.ElementModP alpha = Group.g_pow_p(nonce);
    Group.ElementModP beta = Group.pow_p(publicKey, nonce);
    Group.ElementModQ kdfKey = Hash.hash_elems(alpha, beta);

    // NIST spec: the length is the size of the message in *bits*, but that's annoying
    // to use anywhere else, so we're otherwise just tracking the size in bytes.
    KDF kdf = new KDF(UInt256.fromModQ(kdfKey), "", "", message.length() * 8);
    Bytes k0 = kdf.get(0);
    Bytes c0 = alpha.bytes();

    List<Bytes> encryptedBlocks = new ArrayList<>();
    int count = 0;
    for (Bytes block : blocks) {
      encryptedBlocks.add(block.xor(kdf.get(count + 1)));
      count++;
    }
    Bytes c1 = Bytes.from(encryptedBlocks.toArray(new Bytes[0]));
    Bytes c2b = Bytes.from(c0, c1).transform(hmacSha256(k0.array()));
    UInt256 c2 = new UInt256(c2b.array());

    return new HashedElGamalCiphertext(alpha, c1, c2, message.length());
  }

  /**
   * Attempts to decrypt this [HashedElGamalCiphertext] using the given secret key. Returns `null` if
   * the decryption fails, likely from an HMAC verification failure.
   */
  @Nullable
  public Bytes decrypt(Group.ElementModQ secretKey) {
    Group.ElementModP beta = Group.pow_p(this.c0, secretKey);
    Group.ElementModQ kdfKey = Hash.hash_elems(this.c0, beta);
    KDF kdf = new KDF(UInt256.fromModQ(kdfKey), "", "", this.numBytes * 8);
    Bytes k0 = kdf.get(0);

    Bytes input = Bytes.from(
            Bytes.from(this.c0.bytes()),
            this.c1);
    UInt256 expectedHmac = new UInt256(input.transform(hmacSha256(k0.array())).array());

    if (!expectedHmac.equals(this.c2)) {
      logger.atSevere().log("HashedElGamalCiphertext decryption failure: HMAC doesn't match");
      return null;
    }

    List<Bytes> blocks = chunk(this.c1, BLOCK_SIZE);
    List<Bytes> xorList = new ArrayList<>();
    int count = 0;
    for (Bytes block : blocks) {
      xorList.add(block.xor(kdf.get(count + 1)));
      count++;
    }
    Bytes plaintext = Bytes.from(xorList.toArray(new Bytes[0]));

    if (plaintext.length() > this.numBytes) {
      // Truncate trailing values, which should be zeros. No need to check, because
      // we've already validated the HMAC on the data.
      plaintext = plaintext.copy(0, this.numBytes);
    }

    return plaintext;
  }

  private static List<Bytes> chunk(Bytes org, int size) {
    List<Bytes> result = new ArrayList<>();
    int nchunks = org.length() / size;
    for (int i = 0; i < nchunks; i++) {
      result.add(org.copy(i * size, size));
    }
    int remainder = org.length() % size;
    if (remainder > 0) {
      Bytes rest = org.copy(org.length() - remainder, remainder);
      Bytes restPadded = rest.append(new byte[size - remainder]);
      result.add(restPadded);
    }
    return result;
  }

  /**
   * NIST 800-108-compliant key derivation function (KDF) state.
   * [See the spec](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-108.pdf),
   * section 5.1.
   *
   *  - The [key] must be 32 bytes long, suitable for use in HMAC-SHA256.
   *  - The [label] is a string that identifies the purpose for the derived keying material.
   *  - The [context] is a string containing the information related to the derived keying material.
   *    It may include identities of parties who are deriving and/or using the derived keying material.
   *  - The [length] specifies the length of the encrypted message in *bits*, not bytes.
   */
  private static class KDF {
    final Bytes key;
    final Bytes labelBytes;
    final Bytes lengthBytes;
    final Bytes contextBytes;

    KDF(UInt256 key, String label, String context, int length) {
      this.key = key.toBytes();
      this.labelBytes = Bytes.from(label);
      this.lengthBytes = Bytes.from(length);
      this.contextBytes = Bytes.from(context);
    }

    //Get the requested key bits from the sequence.
    Bytes get(int index) {
      // NIST spec: K(i) := PRF (KI, [i] || Label || 0x00 || Context || [L])
      Bytes input = Bytes.from(
              Bytes.from(index),
              labelBytes,
              Bytes.from((byte) 0),
              contextBytes,
              lengthBytes
      );
      return input.transform(hmacSha256(key.array()));
    }
  }
}
