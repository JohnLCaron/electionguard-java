package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.json.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import electionguard.ballot.ElectionConfig;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterContainer;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.publish.Publisher.Mode.createIfMissing;

public class TestHashAgainstPython {
  public static final String topdirJsonPythonData = "src/test/data/python_data/";
  public static final String output = "/home/snake/tmp/electionguard/TestHashAgainstPython/";
  private static final boolean writeOut = false;

  @AfterContainer
  public static void cleanup() {
    Group.setPrimes(ElectionConstants.STANDARD_CONSTANTS);
  }

  // failing probably because order is different?
  // our order is matching kotlin i think
  @Example
  public void testManifestHash() throws IOException {
    // Group.setPrimes(ElectionConstants.LARGE_TEST_CONSTANTS);
    Manifest subject = ConvertFromJson.readManifest(
            topdirJsonPythonData + "hamilton-county/election_manifest.json");
    assertThat(subject).isNotNull();
    assertThat(subject.is_valid()).isTrue();

    System.out.printf("Manifest crypto_hash: %s%n", subject.cryptoHash().base16());
    System.out.printf("     start_date: %s%n", subject.startDate()); // .format(Utils.dtf));
    System.out.printf("     end_date: %s%n", subject.endDate()); // .format(Utils.dtf));

    for (Manifest.ContestDescription contest : subject.contests()) {
      System.out.printf("  Contest %s crypto_hash: %s%n",
              contest.contestId(), contest.cryptoHash().base16());
      for (Manifest.SelectionDescription selection : contest.selections()) {
        System.out.printf("    Selection %s crypto_hash: %s%n",
                selection.selectionId(), selection.cryptoHash().base16());
      }
    }
    assertThat(subject.cryptoHash().base16()).isEqualTo("C02919B0A6BC022C5E56D6C51985078AF9A94359C12DEF441574189457B9D52B");
  }

  @Example
  public void testWriteManifestHash() throws IOException {
    ElectionConstants constants = ElectionConstants.STANDARD_CONSTANTS;
    Group.setPrimes(constants);
    Manifest subject = ConvertFromJson.readManifest(
            topdirJsonPythonData + "hamilton-county/election_manifest.json");
    assertThat(subject).isNotNull();
    assertThat(subject.is_valid()).isTrue();

    System.out.printf("Manifest crypto_hash: %s%n", subject.cryptoHash().base16());
    System.out.printf("     start_date: %s%n", subject.startDate()); // .format(Utils.dtf));
    System.out.printf("     end_date: %s%n", subject.endDate()); // .format(Utils.dtf));

    for (Manifest.ContestDescription contest : subject.contests()) {
      System.out.printf("  Contest %s crypto_hash: %s%n",
              contest.contestId(), contest.cryptoHash().base16());
      for (Manifest.SelectionDescription selection : contest.selections()) {
        System.out.printf("    Selection %s crypto_hash: %s%n",
                selection.selectionId(), selection.cryptoHash().base16());
      }
    }

    if (writeOut) {
      Publisher publisher = new Publisher(output, createIfMissing);
      ElectionConfig config = new ElectionConfig(subject, 3, 3);
      publisher.writeElectionConfig(config);
    }
  }

  // python standard
  //  hashAll: |hamilton-county|congress-district-7|
  //  hashAll: |congress-district-7-hamilton-county|557C9E46B221BE0A366DBBE3D1C8027ACC581AED71A9A60966BAEC1DE059E9D6|null|null|
  // testBallotStyleHash = 5DF2F070E007D049992ED4A7ABA224606D43EA8244D74509E250C4747E8F84A1
  //
  // java standard
  //  hashAll: |hamilton-county|congress-district-7|
  //  hashAll: |congress-district-7-hamilton-county|557C9E46B221BE0A366DBBE3D1C8027ACC581AED71A9A60966BAEC1DE059E9D6|null|null|
  //  BallotStyle crypto_hash: 5DF2F070E007D049992ED4A7ABA224606D43EA8244D74509E250C4747E8F84A1
  //
  // python largeTest
  //  hashAll: |hamilton-county|congress-district-7|
  //  hashAll: |congress-district-7-hamilton-county|60B2|null|null|
  // testBallotStyleHash = 586E
  //
  // java largeTest
  //  hashAll: |hamilton-county|congress-district-7|
  //  hashAll: |congress-district-7-hamilton-county|00000000000000000000000000000000000000000000000000000000000060B2|null|null|
  // BallotStyle crypto_hash: 0000000000000000000000000000000000000000000000000000000000004EC7
  @Example
  public void testBallotStyleHash() {
    // Group.setPrimes(ElectionConstants.LARGE_TEST_CONSTANTS);
    Manifest.BallotStyle bs = new Manifest.BallotStyle(
            "congress-district-7-hamilton-county",
            ImmutableList.of("hamilton-county", "congress-district-7"),
            ImmutableList.of(),
            null
            );

    System.out.printf("BallotStyle crypto_hash: %s%n", bs.cryptoHash().base16());
    assertThat(bs.cryptoHash().base16()).isEqualTo("5DF2F070E007D049992ED4A7ABA224606D43EA8244D74509E250C4747E8F84A1");
  }

  @Example
  public void testAgainstKotlin() {
    Group.ElementModQ h1 = Hash.hash_elems("barchi-hallaren-selection", 0, "barchi-hallaren");
    System.out.printf("h1: %s%n", h1);
    Group.ElementModQ expected = Group.hex_to_q_unchecked("c49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206");
    assertThat(h1).isEqualTo(expected);
  }

  @Example
  public void testNonces() {
    Group.ElementModQ contestDescriptionHashQ = Group.hex_to_q_unchecked("00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
    Group.ElementModQ ballotNonce = Group.hex_to_q_unchecked("13E7A2F4253E6CCE42ED5576CF7B01A06BE07835227E7AFE5F538FB94E9A9B73");
    Group.ElementModQ plain = Hash.hash_elems(contestDescriptionHashQ, ballotNonce);
    Group.ElementModQ plainExpected = Group.hex_to_q_unchecked("dc16f99c4cce602de6caaa4bb91ac531f7f108b251752b71ed8f9affb82d7fe7");
    assertThat(plain).isEqualTo(plainExpected);

    Nonces nonces = new Nonces(contestDescriptionHashQ, ballotNonce);
    Group.ElementModQ expectedSeed = Group.hex_to_q_unchecked("DC16F99C4CCE602DE6CAAA4BB91AC531F7F108B251752B71ED8F9AFFB82D7FE7");
    assertThat(nonces.internalSeed).isEqualTo(expectedSeed);

    Group.ElementModQ contest_nonce = nonces.get(0);

    Group.ElementModQ expected = Group.hex_to_q_unchecked("9AD1E8A7127EFFB627C4A8E65818C846BD3FB854B384910098E85E1F6BAF4D2B");
    assertThat(contest_nonce).isEqualTo(expected);
  }

  @Example
  public void testHexString() {
      show("A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("9A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("0C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("000C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
      show("0000C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
  }

  public void show(String s1) {
    Group.ElementModQ s1q = Group.hex_to_q_unchecked(s1);
    UInt256 s1u = UInt256.fromModQ(s1q);
    System.out.printf(" len = %d s1u = %s s1q = %s%n", s1.length(), s1u, s1q.base16());
  }

  @Example
  public void testOptional() {
    assertThat(Hash.hash_elems(Optional.of("hay"))).isEqualTo(Hash.hash_elems("hay"));
    String snull = null;
    assertThat(Hash.hash_elems(Optional.empty())).isEqualTo(Hash.hash_elems(snull));
  }

  @Example
  public void testElementModQ() {
    Group.ElementModQ s1q = Group.hex_to_q_unchecked("C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");
    Group.ElementModQ s2q = Group.hex_to_q_unchecked("000C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206");

    assertThat(s1q).isEqualTo(s2q);
    assertThat(s1q.base16()).isEqualTo(s2q.base16());
    assertThat(Hash.hash_elems(s1q)).isEqualTo(Hash.hash_elems(s2q));

    System.out.printf(" len = %d hex = %s%n", s1q.base16().length(), s1q.base16());
  }

  @Example
  public void testElementModQToHex() {
    Group.ElementModQ subject = Group.TWO_MOD_Q;
    System.out.printf(" len = %d hex = '%s'%n", subject.base16().length(), subject.base16());

    assertThat(subject.base16().length()).isEqualTo(64);
  }

  @Example
  public void testElementModP() {
    Group.ElementModQ q = Group.rand_q();
    Group.ElementModP p = Group.g_pow_p(q);
    String ps = p.base16();
    String ps0 = "000" + ps;
    assertThat(ps0.length()).isEqualTo(ps.length() + 3);

    Group.ElementModP s1p = Group.hex_to_p_unchecked(ps);
    Group.ElementModP s2p = Group.hex_to_p_unchecked(ps0);

    assertThat(s1p).isEqualTo(p);
    assertThat(s1p).isEqualTo(s2p);
    assertThat(Hash.hash_elems(s1p)).isEqualTo(Hash.hash_elems(s2p));

    assertThat(s1p.base16()).isEqualTo(s2p.base16());
    System.out.printf("ps0.len = %d, s2p.to_hex() len = %d%n", ps0.length(), s2p.base16().length());
  }

  @Example
  public void testIterable() {
    Group.ElementModQ h1 = Hash.hash_elems("hay1", List.of("hey2", "hey3"));
    Group.ElementModQ h2 = Hash.hash_elems("hay1", "hey2", "hey3");
    System.out.printf(" h1 = %s%n", h1);
    System.out.printf(" h2 = %s%n", h2);
    assertThat(h1).isNotEqualTo(h2);

    Group.ElementModQ expect1 = Group.hex_to_q_unchecked("FA059A112CDC05E6554073659B6B3D67E7C4678BDFAE7E69D66ECB7AE3344F53");
    Group.ElementModQ expect2 = Group.hex_to_q_unchecked("E90FF27925C3CCF7A024BAD4B7406A6F2F0A86EB11273CDA35DECE815B5B382F");
    assertThat(h1).isEqualTo(expect1);
    assertThat(h2).isEqualTo(expect2);
  }

}
