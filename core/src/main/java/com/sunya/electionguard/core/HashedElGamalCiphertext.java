package com.sunya.electionguard.core;

import com.sunya.electionguard.Group;

public record HashedElGamalCiphertext(
        Group.ElementModP c0,
        byte[] c1,
        UInt256 c2,
        Integer numBytes
) {
}
