package com.sunya.electionguard.publish;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Ballot;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlaintextBallotFromJson {
  private final String filename;

  public PlaintextBallotFromJson(String filename) {
    this.filename = filename;
  }

  public List<Ballot.PlaintextBallot> get_ballots_from_file() {
    try {
      InputStream is = new FileInputStream(this.filename);
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson(); // default exclude nulls
      Type listType = new TypeToken<ArrayList<PlaintextBallotPojo>>(){}.getType();

      List<PlaintextBallotPojo> pojo = gson.fromJson(reader, listType);
      return convertList(pojo, this::convertPlaintextBallot);

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public Ballot.PlaintextBallot get_ballot_from_file() {
    try {
      InputStream is = new FileInputStream(this.filename);
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson(); // default exclude nulls
      PlaintextBallotPojo pojo = gson.fromJson(reader, PlaintextBallotPojo.class);
      return convertPlaintextBallot(pojo);

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  @Nullable
  <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter::apply).collect(Collectors.toList());
  }

  @Nullable
  private Ballot.PlaintextBallot convertPlaintextBallot(@Nullable PlaintextBallotPojo pojo) {
    if (pojo == null) {
      return null;
    }
    return new Ballot.PlaintextBallot(
            Strings.nullToEmpty(pojo.object_id),
            Strings.nullToEmpty(pojo.ballot_style),
            convertList(pojo.contests, this::convertPlaintextBallotContest));
  }

  @Nullable
  private Ballot.PlaintextBallotContest convertPlaintextBallotContest(@Nullable PlaintextBallotPojo.PlaintextBallotContest pojo) {
    if (pojo == null) {
      return null;
    }
    return new Ballot.PlaintextBallotContest(
            Strings.nullToEmpty(pojo.object_id),
            convertList(pojo.ballot_selections, this::convertPlaintextBallotSelection));
  }

  @Nullable
  private Ballot.PlaintextBallotSelection convertPlaintextBallotSelection(@Nullable PlaintextBallotPojo.PlaintextBallotSelection pojo) {
    if (pojo == null) {
      return null;
    }

    return new Ballot.PlaintextBallotSelection(
            Strings.nullToEmpty(pojo.object_id),
            Strings.nullToEmpty(pojo.vote),
            false,
            (pojo.extra_data == null) ?
                    Optional.empty() :
                    Optional.of(new Ballot.ExtendedData(pojo.extra_data, pojo.extra_data.length())));
  }
}
