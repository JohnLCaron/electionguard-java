package com.sunya.electionguard;

import java.util.Objects;

public class Proof {
  enum Usage {
    Unknown("Unknown"),
    SecretValue("Prove knowledge of secret value"),
    SelectionLimit("Prove value within selection's limit"),
    SelectionValue("Prove selection's value (0 or 1)");

    private String desc;
    Usage(String desc) {
      this.desc = desc;
    }

    public String getDesc() {
      return desc;
    }
  }

  final String name;
  final Usage usage;

  public Proof(String name, Usage usage) {
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
