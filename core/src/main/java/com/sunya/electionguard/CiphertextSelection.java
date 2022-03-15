package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Superclass for encrypted selections.
 */
@Immutable
public class CiphertextSelection {
  public final String selectionId;
  public final int sequenceOrder;
  /** Manifest.SelectionDescription.crypto_hash(). */
  public final Group.ElementModQ selectionHash;
  private final ElGamal.Ciphertext ciphertext; // only accessed through ciphertext(), so subclass can override
  public final boolean isPlaceholderSelection;

  CiphertextSelection(String selectionId, int sequenceOrder, Group.ElementModQ selectionHash,
                      ElGamal.Ciphertext ciphertext, boolean isPlaceholderSelection) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(selectionId));
    this.selectionId = selectionId;
    this.sequenceOrder = sequenceOrder;
    this.selectionHash = Preconditions.checkNotNull(selectionHash);
    this.ciphertext = Preconditions.checkNotNull(ciphertext);
    this.isPlaceholderSelection = isPlaceholderSelection;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextSelection that = (CiphertextSelection) o;
    return sequenceOrder == that.sequenceOrder &&
            isPlaceholderSelection == that.isPlaceholderSelection &&
            selectionId.equals(that.selectionId) &&
            selectionHash.equals(that.selectionHash) &&
            ciphertext.equals(that.ciphertext);
  }

  @Override
  public int hashCode() {
    return Objects.hash(selectionId, sequenceOrder, selectionHash, ciphertext, isPlaceholderSelection);
  }

  @Override
  public String toString() {
    return "CiphertextSelection{" +
            "\n selectionId='" + selectionId + '\'' +
            "\n sequenceOrder=" + sequenceOrder +
            "\n selectionHash=" + selectionHash +
            "\n ciphertext=" + ciphertext +
            "\n isPlaceholderSelection=" + isPlaceholderSelection +
            '}';
  }

  /** The encrypted vote count. */
  public ElGamal.Ciphertext ciphertext() {
    return ciphertext;
  }

  public String object_id() {
    return selectionId;
  }

  public int sequence_order() {
    return sequenceOrder;
  }

  public Group.ElementModQ description_hash() {
    return selectionHash;
  }
}
