package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.PlaintextBallot;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion between PlaintextBallot and Json, using python's object model. */
public class PlaintextBallotPojo {
  public String object_id;
  public String ballot_style;
  public List<PlaintextBallotContest> contests;

  public static class PlaintextBallotContest {
    public String object_id;
    public List<PlaintextBallotSelection> ballot_selections;
  }

  public static class PlaintextBallotSelection {
    public String object_id;
    public int vote;
    public boolean is_placeholder_selection;
    public String extra_data; // optional
  }

  /////////////////////////////////////

  public static List<PlaintextBallot> get_ballots_from_file(String filename) throws IOException {
    try (InputStream is = new FileInputStream(filename)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = GsonTypeAdapters.enhancedGson();
      Type listType = new TypeToken<ArrayList<PlaintextBallotPojo>>(){}.getType();

      List<PlaintextBallotPojo> pojo = gson.fromJson(reader, listType);
      return convertList(pojo, PlaintextBallotPojo::convertPlaintextBallot);
    }
  }

  public static PlaintextBallot get_ballot_from_file(String filename) throws IOException {
    try (InputStream is = new FileInputStream(filename)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = GsonTypeAdapters.enhancedGson();
      PlaintextBallotPojo pojo = gson.fromJson(reader, PlaintextBallotPojo.class);
      return convertPlaintextBallot(pojo);
    }
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ///////////////////////////////////////////////////////////////////////////////////////

  public static PlaintextBallot deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextBallotPojo pojo = gson.fromJson(jsonElem, PlaintextBallotPojo.class);
    return convertPlaintextBallot(pojo);
  }

  private static PlaintextBallot convertPlaintextBallot(PlaintextBallotPojo pojo) {
    return new PlaintextBallot(
            pojo.object_id,
            pojo.ballot_style,
            convertList(pojo.contests, PlaintextBallotPojo::convertPlaintextBallotContest));
  }

  private static PlaintextBallot.Contest convertPlaintextBallotContest(PlaintextBallotPojo.PlaintextBallotContest pojo) {
    return new PlaintextBallot.Contest(
            pojo.object_id,
            convertList(pojo.ballot_selections, PlaintextBallotPojo::convertPlaintextBallotSelection));
  }

  private static PlaintextBallot.Selection convertPlaintextBallotSelection(PlaintextBallotPojo.PlaintextBallotSelection pojo) {
    CiphertextBallot.ExtendedData extra = (pojo.extra_data == null) ? null :
            new CiphertextBallot.ExtendedData(pojo.extra_data, pojo.extra_data.length());

    return new PlaintextBallot.Selection(
            pojo.object_id,
            pojo.vote,
            pojo.is_placeholder_selection,
            extra);
  }

  ////////////////////////////////////////////////////////////////////////////////////////

  public static JsonElement serialize(PlaintextBallot src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextBallotPojo pojo = convertPlaintextBallot(src);
    Type typeOfSrc = new TypeToken<PlaintextBallotPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static PlaintextBallotPojo convertPlaintextBallot(PlaintextBallot src) {
     PlaintextBallotPojo pojo = new PlaintextBallotPojo();
    pojo.object_id = src.object_id;
    pojo.ballot_style = src.ballot_style;
    pojo.contests = convertList(src.contests, PlaintextBallotPojo::convertPlaintextBallotContest);
    return pojo;
  }

  private static PlaintextBallotPojo.PlaintextBallotContest convertPlaintextBallotContest(PlaintextBallot.Contest src) {
    PlaintextBallotPojo.PlaintextBallotContest pojo = new PlaintextBallotPojo.PlaintextBallotContest ();
    pojo.object_id = src.contest_id;
    pojo.ballot_selections = convertList(src.ballot_selections, PlaintextBallotPojo::convertPlaintextBallotSelection);
    return pojo;
  }

  private static PlaintextBallotPojo.PlaintextBallotSelection convertPlaintextBallotSelection(PlaintextBallot.Selection src) {
    PlaintextBallotPojo.PlaintextBallotSelection pojo = new PlaintextBallotPojo.PlaintextBallotSelection ();
    pojo.object_id = src.selection_id;
    pojo.vote = src.vote;
    pojo.is_placeholder_selection = src.is_placeholder_selection;
    src.extended_data.ifPresent( data -> pojo.extra_data = data.value);
    return pojo;
  }

}
