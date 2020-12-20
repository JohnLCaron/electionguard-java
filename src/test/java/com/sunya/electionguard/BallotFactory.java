package com.sunya.electionguard;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;

public class BallotFactory {
  private static final String simple_ballot_filename = "ballot_in_simple.json";
  private static final String simple_ballots_filename = "plaintext_ballots_simple.json";
  private static final Random random = new Random(System.currentTimeMillis());

  static PlaintextBallotSelection get_random_selection_from(SelectionDescription description) {
    return Encrypt.selection_from(description, false, random.nextBoolean());
  }

  /**
   *         Get a randomly filled contest for the given description that
   *         may be undervoted and may include explicitly false votes.
   *         Since this is only used for testing, the random number generator
   *         (`random`) must be provided to make this function deterministic.
   */
  PlaintextBallotContest get_random_contest_from(
          ContestDescription description,
          boolean suppress_validity_check, // false, false
          boolean with_trues) {

    if (!suppress_validity_check) {
      assertWithMessage("the contest description must be valid").that(description.is_valid()).isTrue();
    }

    List<PlaintextBallotSelection> selections = new ArrayList<>();

    int voted = 0;

    for (SelectionDescription selection_description : description.ballot_selections) {
      PlaintextBallotSelection selection = this.get_random_selection_from(selection_description);
          // the caller may force a true value
      voted += selection.to_int();
      if (with_trues && voted <= 1 && selection.to_int() == 1) {
        selections.add(selection);
        continue;
      }

          // Possibly append the true selection, indicating an undervote
      if (voted <= description.number_elected && random.nextBoolean()) {
        selections.add(selection);
        // Possibly append the false selections as well, indicating some choices may be explicitly false
      } else if (random.nextBoolean()) {
        selections.add( Encrypt.selection_from(selection_description, false, false));
      }
    }

    return new PlaintextBallotContest(description.object_id, selections);
  }

  /**
   * Get a single Fake Ballot object that is manually constructed with default vaules .
   */
  PlaintextBallot get_fake_ballot(
          InternalElectionDescription election,
          @Nullable String ballot_id,
          boolean with_trues) { // default true

    if (ballot_id == null) {
      ballot_id = "some-unique-ballot-id-123";
    }

    List<PlaintextBallotContest> contests = new ArrayList<>();
    for (ContestDescriptionWithPlaceholders contest : election.get_contests_for(election.ballot_styles.get(0).object_id)) {
      contests.add(this.get_random_contest_from(contest, false, with_trues));
    }

    return new PlaintextBallot(ballot_id, election.ballot_styles.get(0).object_id, contests);
  }

  ///////////////////////////////////////////////////

  static PlaintextBallotSelection get_selection_well_formed() {
    ExtendedData extra_data = new ExtendedData("random", 33);
    return new PlaintextBallotSelection("selection-{draw(uuids)}",
                random.nextBoolean() ? "true" : "false",
                random.nextBoolean(),
                random.nextBoolean() ? Optional.of(extra_data) : Optional.empty());
  }

  static PlaintextBallotSelection get_selection_poorly_formed() {
    ExtendedData extra_data = new ExtendedData("random", 33);
    return new PlaintextBallotSelection("selection-{draw(uuids)}",
            random.nextBoolean() ? "yeah" : "nope",
            random.nextBoolean(),
            random.nextBoolean() ? Optional.of(extra_data) : Optional.empty());
  }

  //////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  PlaintextBallot get_simple_ballot_from_file() {
    return this._get_ballot_from_file(this.simple_ballot_filename);
  }

  @Nullable
  private PlaintextBallot _get_ballot_from_file(String filename) {
    try {
      String current = new java.io.File("./src/test/resources/").getCanonicalPath();
      InputStream is = new FileInputStream((current + "/" + filename));
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson(); // default exclude nulls
      PlaintextBallotPojo pojo = gson.fromJson(reader, PlaintextBallotPojo.class);
      return convertPlaintextBallot(pojo);

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  @Nullable
  List<PlaintextBallot> get_simple_ballots_from_file() {
    return this._get_ballots_from_file(this.simple_ballots_filename);
  }

  @Nullable
  private List<PlaintextBallot> _get_ballots_from_file(String filename) {
    try {
      String current = new java.io.File("./src/test/resources/").getCanonicalPath();
      InputStream is = new FileInputStream((current + "/" + filename));
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson(); // default exclude nulls
      Type listType = new TypeToken<ArrayList<PlaintextBallotPojo>>(){}.getType();

      List<PlaintextBallotPojo> pojo = gson.fromJson(reader, listType);
      return convertList(pojo, this::convertPlaintextBallot);

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  @Nullable
  <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter::apply).collect(Collectors.toList());
  }

  @Nullable
  private PlaintextBallot convertPlaintextBallot(@Nullable PlaintextBallotPojo pojo) {
    if (pojo == null) {
      return null;
    }
    return new PlaintextBallot(
            Strings.nullToEmpty(pojo.object_id),
            Strings.nullToEmpty(pojo.ballot_style),
            convertList(pojo.contests, this::convertPlaintextBallotContest));
  }

  @Nullable
  private PlaintextBallotContest convertPlaintextBallotContest(@Nullable PlaintextBallotPojo.PlaintextBallotContest pojo) {
    if (pojo == null) {
      return null;
    }
    return new PlaintextBallotContest(
            Strings.nullToEmpty(pojo.object_id),
            convertList(pojo.ballot_selections, this::convertPlaintextBallotSelection));
  }

  @Nullable
  private PlaintextBallotSelection convertPlaintextBallotSelection(@Nullable PlaintextBallotPojo.PlaintextBallotSelection pojo) {
    if (pojo == null) {
      return null;
    }

    return new PlaintextBallotSelection(
            Strings.nullToEmpty(pojo.object_id),
            Strings.nullToEmpty(pojo.vote),
            false,
            (pojo.extra_data == null) ?
                    Optional.empty() :
                    Optional.of(new ExtendedData(pojo.extra_data, pojo.extra_data.length())));
  }

}
