package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.DecryptWithSecrets.*;


public class TestDecryptWithSecretsProperties extends TestProperties {

  //// TestDisjunctiveChaumPedersen

  @Property
  public void test_decrypt_selection_valid_input_succeeds(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ nonce_seed) {

    Election.SelectionDescription description = ElectionFactory.get_selection_description_well_formed().selection_description;

    Ballot.PlaintextBallotSelection data = BallotFactory.get_random_selection_from(description);

    Optional<Ballot.CiphertextBallotSelection> subjectO = Encrypt.encrypt_selection(data, description, keypair.public_key, ONE_MOD_Q, nonce_seed, false, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotSelection subject = subjectO.get();

    Optional<Ballot.PlaintextBallotSelection> result_from_key =
            decrypt_selection_with_secret(subject, description, keypair.public_key, keypair.secret_key, ONE_MOD_Q, false);
    Optional<Ballot.PlaintextBallotSelection> result_from_nonce =
            decrypt_selection_with_nonce(subject, description, keypair.public_key, ONE_MOD_Q, Optional.empty(),false);
    Optional<Ballot.PlaintextBallotSelection> result_from_nonce_seed =
            decrypt_selection_with_nonce(subject, description, keypair.public_key, ONE_MOD_Q, Optional.of(nonce_seed), false);

    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();
    assertThat(data.vote).isEqualTo(result_from_key.get().vote);
    assertThat(data.vote).isEqualTo(result_from_nonce.get().vote);
    assertThat(data.vote).isEqualTo(result_from_nonce_seed.get().vote);
  }
}
