package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Superclass for encrypted selections.
 */
@Immutable
public class CiphertextSelection implements OrderedObjectBaseIF {
  private final String object_id;
  private final int sequence_order;
  /** Manifest.SelectionDescription.crypto_hash(). */
  private final Group.ElementModQ description_hash;
  private final ElGamal.Ciphertext ciphertext; // only accessed through ciphertext(), so subclass can override
  public final boolean is_placeholder;

  CiphertextSelection(String object_id, int sequence_order, Group.ElementModQ description_hash,
                      ElGamal.Ciphertext ciphertext, boolean is_placeholder) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
    this.object_id = object_id;
    this.sequence_order = sequence_order;
    this.description_hash = Preconditions.checkNotNull(description_hash);
    this.ciphertext = Preconditions.checkNotNull(ciphertext);
    this.is_placeholder = is_placeholder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextSelection that = (CiphertextSelection) o;
    return sequence_order == that.sequence_order &&
            is_placeholder == that.is_placeholder &&
            object_id.equals(that.object_id) &&
            description_hash.equals(that.description_hash) &&
            ciphertext.equals(that.ciphertext);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, sequence_order, description_hash, ciphertext, is_placeholder);
  }

  @Override
  public String toString() {
    return "CiphertextSelection{" +
            "object_id='" + object_id + '\'' +
            ", sequence_order=" + sequence_order +
            ", description_hash=" + description_hash +
            ", ciphertext=" + ciphertext +
            ", is_placeholder=" + is_placeholder +
            '}';
  }

  /** The encrypted vote count. */
  public ElGamal.Ciphertext ciphertext() {
    return ciphertext;
  }

  @Override
  public String object_id() {
    return object_id;
  }

  @Override
  public int sequence_order() {
    return sequence_order;
  }

  public Group.ElementModQ description_hash() {
    return description_hash;
  }
}
