package com.sunya.electionguard.publish;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Election;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Helper class for conversion of Election description to/from Json, using python's object model. */
public class ElectionDescriptionPojo {
  public InternationalizedText name;
  public String election_scope_id;
  public String type;
  public String start_date; // ISO-8601 Local or UTC? Assume local has zone offset
  public String end_date; // ISO-8601 Local or UTC? Assume local has zone offset
  public ContactInformation contact_information;

  public List<GeopoliticalUnit> geopolitical_units;
  public List<Party> parties;
  public List<Candidate> candidates;
  public List<ContestDescription> contests;
  public List<BallotStyle> ballot_styles;

  public static class AnnotatedString {
    public String annotation;
    public String value;
  }

  public static class BallotStyle extends ElectionObjectBase {
    public List<String> geopolitical_unit_ids;
    public List<String> party_ids;
    public String image_uri;
  }

  public static class Candidate extends ElectionObjectBase {
    public InternationalizedText name;
    public String party_id;
    public String image_uri;
    public Boolean is_write_in;
  }

  public static class ContestDescription extends ElectionObjectBase {
    public String electoral_district_id;
    public int sequence_order;
    public String vote_variation;
    public int number_elected;
    public int votes_allowed;
    public String name;
    public List<SelectionDescription> ballot_selections;
    public InternationalizedText ballot_title;
    public InternationalizedText ballot_subtitle;
  }

  public static class ContactInformation {
    public List<String> address_line;
    public List<AnnotatedString> email;
    public List<AnnotatedString> phone;
    public String name;
  }

  public static class ElectionObjectBase {
    public String object_id;
  }

  public static class GeopoliticalUnit extends ElectionObjectBase {
    public String name;
    public String type;
    public ContactInformation contact_information;
  }

  public static class InternationalizedText {
    public List<Language> text;
  }

  public static class Language {
    public String value;
    public String language;
  }

  public static class Party extends ElectionObjectBase {
    public InternationalizedText ballot_name;
    public String abbreviation;
    public String color;
    public String logo_uri;
  }

  public static class SelectionDescription extends ElectionObjectBase {
    public String candidate_id;
    public int sequence_order;
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null || from.isEmpty() ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Election deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElectionDescriptionPojo pojo = gson.fromJson(jsonElem, ElectionDescriptionPojo.class);
    return convert(pojo);
  }

  private static Election convert(ElectionDescriptionPojo pojo) {
    // LOOK going to throw an Exception if these arent present or invalid format
    OffsetDateTime startLocalDate = OffsetDateTime.parse(pojo.start_date);
    OffsetDateTime endLocalDate = OffsetDateTime.parse(pojo.end_date);

    return new Election(
            pojo.election_scope_id,
            Election.ElectionType.valueOf(pojo.type),
            startLocalDate,
            endLocalDate,
            convertList(pojo.geopolitical_units, ElectionDescriptionPojo::convertGeopoliticalUnit),
            convertList(pojo.parties, ElectionDescriptionPojo::convertParty),
            convertList(pojo.candidates, ElectionDescriptionPojo::convertCandidate),
            convertList(pojo.contests, ElectionDescriptionPojo::convertContestDescription),
            convertList(pojo.ballot_styles, ElectionDescriptionPojo::convertBallotStyle),
            convertInternationalizedText(pojo.name),
            convertContactInformation(pojo.contact_information)
    );
  }

  @Nullable
  private static Election.AnnotatedString convertAnnotatedString(@Nullable ElectionDescriptionPojo.AnnotatedString pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.AnnotatedString(
            Strings.nullToEmpty(pojo.annotation),
            Strings.nullToEmpty(pojo.value));
  }


  @Nullable
  private static Election.BallotStyle convertBallotStyle(@Nullable ElectionDescriptionPojo.BallotStyle pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.BallotStyle(pojo.object_id,
            convertList(pojo.geopolitical_unit_ids, Strings::nullToEmpty),
            convertList(pojo.party_ids, Strings::nullToEmpty),
            Strings.emptyToNull(pojo.image_uri));
  }

  @Nullable
  private static Election.Candidate convertCandidate(@Nullable ElectionDescriptionPojo.Candidate pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.Candidate(pojo.object_id, convertInternationalizedText(pojo.name),
            pojo.party_id, pojo.image_uri, pojo.is_write_in);
  }

  @Nullable
  private static Election.ContactInformation convertContactInformation(@Nullable ElectionDescriptionPojo.ContactInformation pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.ContactInformation(
            convertList(pojo.address_line, Strings::nullToEmpty),
            convertList(pojo.email, ElectionDescriptionPojo::convertAnnotatedString),
            convertList(pojo.phone, ElectionDescriptionPojo::convertAnnotatedString),
            pojo.name);
  }

  @Nullable
  private static Election.ContestDescription convertContestDescription(@Nullable ElectionDescriptionPojo.ContestDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.ContestDescription(
            pojo.object_id,
            pojo.electoral_district_id,
            pojo.sequence_order,
            Election.VoteVariationType.valueOf(pojo.vote_variation),
            pojo.number_elected,
            pojo.votes_allowed,
            pojo.name,
            convertList(pojo.ballot_selections, ElectionDescriptionPojo::convertSelectionDescription),
            convertInternationalizedText(pojo.ballot_title),
            convertInternationalizedText(pojo.ballot_subtitle));
  }

  @Nullable
  private static Election.GeopoliticalUnit convertGeopoliticalUnit(@Nullable ElectionDescriptionPojo.GeopoliticalUnit pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.GeopoliticalUnit(
            pojo.object_id,
            pojo.name,
            Election.ReportingUnitType.valueOf(pojo.type),
            convertContactInformation(pojo.contact_information));
  }

  @Nullable
  private static Election.InternationalizedText convertInternationalizedText(@Nullable ElectionDescriptionPojo.InternationalizedText pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.InternationalizedText(convertList(pojo.text, ElectionDescriptionPojo::convertLanguage));
  }

  @Nullable
  private static Election.Language convertLanguage(@Nullable ElectionDescriptionPojo.Language pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.Language(
            Strings.nullToEmpty(pojo.value),
            Strings.nullToEmpty(pojo.language));
  }

  @Nullable
  private static Election.Party convertParty(@Nullable ElectionDescriptionPojo.Party pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.Party(
            pojo.object_id,
            convertInternationalizedText(pojo.ballot_name),
            pojo.abbreviation,
            pojo.color,
            pojo.logo_uri);
  }

  @Nullable
  private static Election.SelectionDescription convertSelectionDescription(@Nullable ElectionDescriptionPojo.SelectionDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new Election.SelectionDescription(
            pojo.object_id,
            pojo.candidate_id,
            pojo.sequence_order);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(Election src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ElectionDescriptionPojo pojo = convert(src);
    Type typeOfSrc = new TypeToken<ElectionDescriptionPojo>() {}.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static ElectionDescriptionPojo convert(Election org) {
    ElectionDescriptionPojo pojo = new ElectionDescriptionPojo();
    pojo.election_scope_id = org.election_scope_id;
    pojo.type = org.type.name();
    pojo.start_date = org.start_date.toString();
    pojo.end_date = org.end_date.toString();
    pojo.geopolitical_units = convertList(org.geopolitical_units, ElectionDescriptionPojo::convertGeopoliticalUnit);
    pojo.parties = convertList(org.parties, ElectionDescriptionPojo::convertParty);
    pojo.candidates = convertList(org.candidates, ElectionDescriptionPojo::convertCandidate);
    pojo.contests = convertList(org.contests, ElectionDescriptionPojo::convertContestDescription);
    pojo.ballot_styles = convertList(org.ballot_styles, ElectionDescriptionPojo::convertBallotStyle);
    pojo.name = convertInternationalizedText(org.name.orElse(null));
    pojo.contact_information = convertContactInformation(org.contact_information.orElse(null));

    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.AnnotatedString convertAnnotatedString(@Nullable Election.AnnotatedString org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.AnnotatedString pojo = new ElectionDescriptionPojo.AnnotatedString();
    pojo.annotation = Strings.nullToEmpty(org.annotation);
    pojo.value = Strings.nullToEmpty(org.value);
    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.BallotStyle convertBallotStyle(@Nullable Election.BallotStyle org) {
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
  private static ElectionDescriptionPojo.Candidate convertCandidate(@Nullable Election.Candidate org) {
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
  private static ElectionDescriptionPojo.ContactInformation convertContactInformation(@Nullable Election.ContactInformation org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.ContactInformation pojo = new ElectionDescriptionPojo.ContactInformation();
    pojo.address_line = convertList(org.address_line, Strings::nullToEmpty);
    pojo.email = convertList(org.email, ElectionDescriptionPojo::convertAnnotatedString);
    pojo.phone = convertList(org.phone, ElectionDescriptionPojo::convertAnnotatedString);
    pojo.name = org.name.orElse(null);
    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.ContestDescription convertContestDescription(@Nullable Election.ContestDescription org) {
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
    pojo.ballot_selections = convertList(org.ballot_selections, ElectionDescriptionPojo::convertSelectionDescription);
    pojo.ballot_title = convertInternationalizedText(org.ballot_title.orElse(null));
    pojo.ballot_subtitle = convertInternationalizedText(org.ballot_subtitle.orElse(null));
    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.GeopoliticalUnit convertGeopoliticalUnit(@Nullable Election.GeopoliticalUnit org) {
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
  private static ElectionDescriptionPojo.InternationalizedText convertInternationalizedText(@Nullable Election.InternationalizedText org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.InternationalizedText pojo = new ElectionDescriptionPojo.InternationalizedText();
    pojo.text = convertList(org.text, ElectionDescriptionPojo::convertLanguage);
    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.Language convertLanguage(@Nullable Election.Language org) {
    if (org == null) {
      return null;
    }
    ElectionDescriptionPojo.Language pojo = new ElectionDescriptionPojo.Language();
    pojo.value = Strings.nullToEmpty(org.value);
    pojo.language = Strings.nullToEmpty(org.language);
    return pojo;
  }

  @Nullable
  private static ElectionDescriptionPojo.Party convertParty(@Nullable Election.Party org) {
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
  private static ElectionDescriptionPojo.SelectionDescription convertSelectionDescription(@Nullable Election.SelectionDescription org) {
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