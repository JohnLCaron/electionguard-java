package com.sunya.electionguard;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/** Superclass of Proofs. */
@Immutable
public abstract class Proof {
  enum Usage {
    Unknown("Unknown"),
    SecretValue("Prove knowledge of secret value"),
    SelectionLimit("Prove value within selection's limit"),
    SelectionValue("Prove selection's value (0 or 1)");

    private final String desc;
    Usage(String desc) {
      this.desc = desc;
    }

    public String getDesc() {
      return desc;
    }
  }

  public final String name;
  public final Usage usage;

  protected Proof(String name, Usage usage) {
    this.name = name;
    this.usage = usage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Proof proof = (Proof) o;
    return name.equals(proof.name) &&
            usage == proof.usage;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, usage);
  }
}
