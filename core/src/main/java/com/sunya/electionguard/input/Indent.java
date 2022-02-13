package com.sunya.electionguard.input;

public class Indent {
  private final int nspaces;
  private int level;
  private final StringBuilder blanks = new StringBuilder();
  private String indent = "";

  public Indent(int nspaces) {
    this.nspaces = nspaces;
    this.makeBlanks(100);
  }

  public Indent incr() {
    ++this.level;
    this.setIndentLevel(this.level);
    return this;
  }

  public Indent decr() {
    --this.level;
    this.setIndentLevel(this.level);
    return this;
  }

  public int level() {
    return this.level;
  }

  public String toString() {
    return this.indent;
  }

  public void setIndentLevel(int level) {
    this.level = level;
    if (level * this.nspaces >= this.blanks.length()) {
      this.makeBlanks(100);
    }

    int end = Math.min(level * this.nspaces, this.blanks.length());
    this.indent = this.blanks.substring(0, end);
  }

  private void makeBlanks(int len) {
    for(int i = 0; i < len * this.nspaces; ++i) {
      this.blanks.append(" ");
    }

  }
}
