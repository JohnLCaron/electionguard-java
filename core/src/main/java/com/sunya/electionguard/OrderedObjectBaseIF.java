package com.sunya.electionguard;

/** Election objects that are identifiable by object_id and have a sequence ordering to sort on */
public interface OrderedObjectBaseIF extends ElectionObjectBaseIF {
  int sequence_order();
}
