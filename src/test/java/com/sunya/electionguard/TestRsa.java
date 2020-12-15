package com.sunya.electionguard;

import org.junit.Test;

import java.security.KeyPair;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Rsa.*;

public class TestRsa {

  @Test
  public void test_rsa_encrypt() throws Exception {
    String message = "9893e1c926521dc595d501056d03c4387b87986089539349bed6eb1018229b2e0029dd38647bfc80746726b3710c8ac3f69187da2234b438370a4348a784791813b9857446eb14afc676eece5b789a207bcf633ba1676d3410913ae46dd247166c6a682cb0ccc5ecde53";

    KeyPair key_pair = rsa_keypair();
    byte[] encrypted_message = rsa_encrypt(message, key_pair.getPublic());
    String decrypted_message = rsa_decrypt(encrypted_message, key_pair.getPrivate());

    assertThat(message).isEqualTo(decrypted_message);
  }
}
