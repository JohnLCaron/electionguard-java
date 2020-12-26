package com.sunya.electionguard;

import java.util.Objects;

/**
 *     A base object to derive other election objects
 *     that is both serializable and identifiable by objectId
 */
public class ElectionObjectBase {
  // TODO TestTallyProperties needs to change this, make mutable version?
  public String object_id;

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
