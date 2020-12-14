package sunya.electionguard;

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
}
