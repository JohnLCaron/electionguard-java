package com.sunya.electionguard.publish;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Election;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Election.*;

/** Conversion of ElectionDescription to Json. */
class ElectionDescriptionToJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static JsonElement serialize(Election.ElectionDescription src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElectionDescriptionToJson converter = new ElectionDescriptionToJson(null);
    ElectionDescriptionPojo pojo = converter.convert(src);
    Type typeOfSrc = new TypeToken<ElectionDescriptionPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private final String pathname;
  ElectionDescriptionToJson(String pathname) {
    this.pathname = pathname;
  }

  // used for testing
  void write(ElectionDescription org) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      ElectionDescriptionPojo pojo = convert(org);
      Type type = new TypeToken<ElectionDescriptionPojo>() {}.getType();
      try (FileWriter writer = new FileWriter(pathname)) {
        gson.toJson(pojo, type, writer);
      }
    } catch (Exception ioe) {
      logger.atSevere().log("Failed on file '%s'", pathname);
      throw ioe;
    }
  }

  @Nullable
  <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ElectionDescriptionPojo convert(ElectionDescription org) {
    ElectionDescriptionPojo pojo = new ElectionDescriptionPojo();
    pojo.election_scope_id = org.election_scope_id;
    pojo.type = org.type.name();
    pojo.start_date = org.start_date.toString();
    pojo.end_date = org.end_date.toString();
    pojo.geopolitical_units = convertList(org.geopolitical_units, this::convertGeopoliticalUnit);
    pojo.parties = convertList(org.parties, this::convertParty);
    pojo.candidates = convertList(org.candidates, this::convertCandidate);
    pojo.contests = convertList(org.contests, this::convertContestDescription);
    pojo.ballot_styles = convertList(org.ballot_styles, this::convertBallotStyle);
    pojo.name = convertInternationalizedText(org.name.orElse(null));
    pojo.contact_information = convertContactInformation(org.contact_information.orElse(null));

    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.AnnotatedString convertAnnotatedString(@Nullable AnnotatedString org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.AnnotatedString pojo = new ElectionDescriptionPojo.AnnotatedString();
    pojo.annotation = Strings.nullToEmpty(org.annotation);
    pojo.value = Strings.nullToEmpty(org.value);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.BallotStyle convertBallotStyle(@Nullable BallotStyle org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.BallotStyle pojo = new ElectionDescriptionPojo.BallotStyle();

    pojo.object_id = org.object_id;
    pojo.geopolitical_unit_ids = convertList(org.geopolitical_unit_ids, Strings::nullToEmpty);
    pojo.party_ids = convertList(org.party_ids, Strings::nullToEmpty);
    pojo.image_uri = org.image_uri.orElse(null);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.Candidate convertCandidate(@Nullable Candidate org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.Candidate pojo = new ElectionDescriptionPojo.Candidate();
    pojo.object_id = org.object_id;
    pojo.name = convertInternationalizedText(org.name);
    pojo.party_id = org.party_id.orElse(null);
    pojo.image_uri = org.image_uri.orElse(null);
    pojo.is_write_in = org.is_write_in;
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.ContactInformation convertContactInformation(@Nullable ContactInformation org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.ContactInformation pojo = new ElectionDescriptionPojo.ContactInformation();
    pojo.address_line = convertList(org.address_line, Strings::nullToEmpty);
    pojo.email = convertList(org.email, this::convertAnnotatedString);
    pojo.phone = convertList(org.phone, this::convertAnnotatedString);
    pojo.name = org.name.orElse(null);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.ContestDescription convertContestDescription(@Nullable ContestDescription org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.ContestDescription pojo = new ElectionDescriptionPojo.ContestDescription();
    pojo.object_id = org.object_id;
    pojo.electoral_district_id = org.electoral_district_id;
    pojo.sequence_order = org.sequence_order;
    pojo.vote_variation = org.vote_variation.name();
    pojo.number_elected = org.number_elected;
    pojo.votes_allowed = org.votes_allowed.orElse(null);
    pojo.name = org.name;
    pojo.ballot_selections = convertList(org.ballot_selections, this::convertSelectionDescription);
    pojo.ballot_title = convertInternationalizedText(org.ballot_title.orElse(null));
    pojo.ballot_subtitle = convertInternationalizedText(org.ballot_subtitle.orElse(null));
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.GeopoliticalUnit convertGeopoliticalUnit(@Nullable GeopoliticalUnit org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.GeopoliticalUnit pojo = new ElectionDescriptionPojo.GeopoliticalUnit();
    pojo.object_id = org.object_id;
    pojo.name = org.name;
    pojo.type = org.type.name();
    pojo.contact_information = convertContactInformation(org.contact_information.orElse(null));
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.InternationalizedText convertInternationalizedText(@Nullable InternationalizedText org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.InternationalizedText pojo = new ElectionDescriptionPojo.InternationalizedText();
    pojo.text = convertList(org.text, this::convertLanguage);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.Language convertLanguage(@Nullable Language org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.Language pojo = new ElectionDescriptionPojo.Language();
    pojo.value = Strings.nullToEmpty(org.value);
    pojo.language = Strings.nullToEmpty(org.language);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.Party convertParty(@Nullable Party org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.Party pojo = new ElectionDescriptionPojo.Party();
    pojo.object_id = org.object_id;
    pojo.ballot_name = convertInternationalizedText(org.name);
    pojo.abbreviation = org.abbreviation.orElse(null);
    pojo.color = org.color.orElse(null);
    pojo.logo_uri = org.logo_uri.orElse(null);
    return pojo;
  }

  @Nullable
  ElectionDescriptionPojo.SelectionDescription convertSelectionDescription(@Nullable SelectionDescription org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.SelectionDescription pojo = new ElectionDescriptionPojo.SelectionDescription();
    pojo.object_id = org.object_id;
    pojo.candidate_id = org.candidate_id;
    pojo.sequence_order = org.sequence_order;
    return pojo;
  }

}
