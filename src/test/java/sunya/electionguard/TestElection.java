package sunya.electionguard;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestElection {
  static ElectionFactory election_factory;

  @BeforeClass
  public static void setup() {
    election_factory = new ElectionFactory();
  }

  @Test
  public void test_simple_election_is_valid() throws IOException {
    Election.ElectionDescription subject = election_factory.get_simple_election_from_file();
    assertThat(subject.election_scope_id).isEmpty();
    assertThat(subject.election_scope_id).isEqualTo("jefferson-county-primary");
    assertThat(subject.is_valid()).isTrue();
  }
}
