package com.sunya.electionguard.input;

import java.util.ArrayList;
import java.util.Formatter;

class ValidationMessenger {
  private final String name;
  private final String id;
  private final ArrayList<String> messages = new ArrayList<>();
  private final ArrayList<ValidationMessenger> nested = new ArrayList<>();
  private final Indent indent = new Indent(2);

  public ValidationMessenger(String name, String id) {
    this.name = name;
    this.id = id;
  }

  void add(String mess) {
    messages.add(mess);
  }

  ValidationMessenger nested(String name, String id) {
    ValidationMessenger mess = new ValidationMessenger(name, id);
    mess.indent.setIndentLevel(this.indent.level() + 1);
    nested.add(mess);
    return mess;
  }

  boolean makeMesses(Formatter problems) {
    if (hasProblem()) {
      problems.format("%s %s '%s' has problems%n", indent, name, id);
      for (String mess : messages) {
        problems.format("%s   %s%n", indent, mess);
      }
      for (ValidationMessenger nest : nested) {
        nest.makeMesses(problems);
      }
      return false;
    }
    return true;
  }

  boolean hasProblem() {
    return !messages.isEmpty() || nested.stream().map(ValidationMessenger::hasProblem).findAny().orElse(false);
  }
}
