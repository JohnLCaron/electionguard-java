package com.sunya.electionguard;

import java.util.Objects;

/** Superclass for election objects that are identifiable by object_id. */
public class ElectionObjectBase {
  public final String object_id;

  public ElectionObjectBase(String object_id) {
    this.object_id = object_id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionObjectBase that = (ElectionObjectBase) o;
    return object_id.equals(that.object_id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id);
  }
}
