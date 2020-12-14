package sunya.electionguard;

import javax.annotation.concurrent.Immutable;

public class Auxillary {

  /** A tuple of a secret key and public key. */
  @Immutable
  public static class KeyPair {
    public final String secretKey;
    public final String publicKey;

    public KeyPair(String secretKey, String publicKey) {
      this.secretKey = secretKey;
      this.publicKey = publicKey;
    }
  }

  /** A tuple of auxiliary public key and owner information. */
  @Immutable
  public static class PublicKey {
    /** The unique identifier of the guardian. */
    public final String ownerId;

    /** The sequence order of the auxiliary public key (usually the guardian's sequence order). */
    public final String sequenceOrder;

    /**
     * A string representation of the Auxiliary public key.
     *     It is up to the external `AuxiliaryEncrypt` function to know how to parse this value
     */
    public final String key;

    public PublicKey(String ownerId, String sequenceOrder, String key) {
      this.ownerId = ownerId;
      this.sequenceOrder = sequenceOrder;
      this.key = key;
    }
  }


}
