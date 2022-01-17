package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.PlaintextBallot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Conversion between PlaintextBallot and Json, using python's object model. */
public class PlaintextBallotPojo {
  public String object_id;
  public String style_id;
  public List<PlaintextBallotContest> contests;

  public static class PlaintextBallotContest {
    public String object_id;
    public Integer sequence_order;
    public List<PlaintextBallotSelection> ballot_selections;
  }

  public static class PlaintextBallotSelection {
    public String object_id;
    public Integer sequence_order;
    public Integer vote;
    public Boolean is_placeholder_selection = Boolean.FALSE;
    public ExtendedData extended_data;
  }

  public static class ExtendedData {
    public String value;
    public Integer length;
  }

  /////////////////////////////////////

  public static List<PlaintextBallot> get_ballots_from_file(String filename) throws IOException {
    try (InputStream is = new FileInputStream(filename)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = GsonTypeAdapters.enhancedGson();
      Type listType = new TypeToken<ArrayList<PlaintextBallotPojo>>(){}.getType();

      List<PlaintextBallotPojo> pojo = gson.fromJson(reader, listType);
      return ConvertPojos.convertList(pojo, PlaintextBallotPojo::convertPlaintextBallot);
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

  ///////////////////////////////////////////////////////////////////////////////////////

  public static PlaintextBallot deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    PlaintextBallotPojo pojo = gson.fromJson(jsonElem, PlaintextBallotPojo.class);
    return convertPlaintextBallot(pojo);
  }

  private static PlaintextBallot convertPlaintextBallot(PlaintextBallotPojo pojo) {
    return new PlaintextBallot(
            pojo.object_id,
            pojo.style_id,
            ConvertPojos.convertList(pojo.contests, PlaintextBallotPojo::convertPlaintextBallotContest));
  }

  private static PlaintextBallot.Contest convertPlaintextBallotContest(PlaintextBallotPojo.PlaintextBallotContest pojo) {
    return new PlaintextBallot.Contest(
            pojo.object_id,
            pojo.sequence_order,
            ConvertPojos.convertList(pojo.ballot_selections, PlaintextBallotPojo::convertPlaintextBallotSelection));
  }

  private static PlaintextBallot.Selection convertPlaintextBallotSelection(PlaintextBallotPojo.PlaintextBallotSelection pojo) {
    return new PlaintextBallot.Selection(
            pojo.object_id,
            pojo.sequence_order,
            pojo.vote,
            pojo.is_placeholder_selection,
            convertExtendedData(pojo.extended_data));
  }

  private static PlaintextBallot.ExtendedData convertExtendedData(PlaintextBallotPojo.ExtendedData pojo) {
    if (pojo == null) {
      return null;
    }
    return new PlaintextBallot.ExtendedData(
            pojo.value,
            pojo.length);
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
    pojo.style_id = src.style_id;
    pojo.contests = ConvertPojos.convertList(src.contests, PlaintextBallotPojo::convertPlaintextBallotContest);
    return pojo;
  }

  private static PlaintextBallotPojo.PlaintextBallotContest convertPlaintextBallotContest(PlaintextBallot.Contest src) {
    PlaintextBallotPojo.PlaintextBallotContest pojo = new PlaintextBallotPojo.PlaintextBallotContest ();
    pojo.object_id = src.contest_id;
    pojo.sequence_order = src.sequence_order;
    pojo.ballot_selections = ConvertPojos.convertList(src.ballot_selections, PlaintextBallotPojo::convertPlaintextBallotSelection);
    return pojo;
  }

  private static PlaintextBallotPojo.PlaintextBallotSelection convertPlaintextBallotSelection(PlaintextBallot.Selection src) {
    PlaintextBallotPojo.PlaintextBallotSelection pojo = new PlaintextBallotPojo.PlaintextBallotSelection ();
    pojo.object_id = src.selection_id;
    pojo.sequence_order = src.sequence_order;
    pojo.vote = src.vote;
    pojo.is_placeholder_selection = src.is_placeholder_selection;
    src.extended_data.ifPresent(ed -> pojo.extended_data = convertExtendedData(ed));
    return pojo;
  }

  private static PlaintextBallotPojo.ExtendedData convertExtendedData(PlaintextBallot.ExtendedData src) {
    PlaintextBallotPojo.ExtendedData pojo = new PlaintextBallotPojo.ExtendedData();
    pojo.value = src.value;
    pojo.length = src.length;
    return pojo;
  }

}
