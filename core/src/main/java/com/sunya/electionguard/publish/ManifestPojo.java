package com.sunya.electionguard.publish;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.Manifest;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Helper class for conversion of Manifest description to/from Json, using python's object model.
 */
public class ManifestPojo {
  public String election_scope_id;
  public String spec_version;
  public String type;
  public String start_date; // LOOK specify ISO-8601 format
  public String end_date; // ISO-8601 Local or UTC? Assume local has zone offset
  public InternationalizedText name;
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
    public Boolean is_write_in = Boolean.FALSE;
  }

  public static class ContactInformation {
    public List<String> address_line;
    public List<AnnotatedString> email;
    public List<AnnotatedString> phone;
    public String name;
  }

  public static class ContestDescription extends ElectionObjectBase {
    public String electoral_district_id;
    public Integer sequence_order;
    public String vote_variation;
    public Integer number_elected;
    public Integer votes_allowed;
    public String name;
    public List<SelectionDescription> ballot_selections;
    public InternationalizedText ballot_title;
    public InternationalizedText ballot_subtitle;
    public List<String> primary_party_ids;
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
    public InternationalizedText name;
    public String abbreviation;
    public String color;
    public String logo_uri;
  }

  public static class SelectionDescription extends ElectionObjectBase {
    public String candidate_id;
    public Integer sequence_order;
  }

  ////////////////////////////////////////////////////////////////////////////
  // deserialize

  public static Manifest deserialize(JsonElement jsonElem) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ManifestPojo pojo = gson.fromJson(jsonElem, ManifestPojo.class);
    return convert(pojo);
  }

  private static Manifest convert(ManifestPojo pojo) {
    // LOOK going to throw an Exception if these arent present or invalid format
    OffsetDateTime startLocalDate = OffsetDateTime.parse(pojo.start_date);
    OffsetDateTime endLocalDate = OffsetDateTime.parse(pojo.end_date);

    return new Manifest(
            pojo.election_scope_id,
            pojo.spec_version,
            Manifest.ElectionType.valueOf(pojo.type),
            startLocalDate,
            endLocalDate,
            ConvertPojos.convertList(pojo.geopolitical_units, ManifestPojo::convertGeopoliticalUnit),
            ConvertPojos.convertList(pojo.parties, ManifestPojo::convertParty),
            ConvertPojos.convertList(pojo.candidates, ManifestPojo::convertCandidate),
            ConvertPojos.convertList(pojo.contests, ManifestPojo::convertContestDescription),
            ConvertPojos.convertList(pojo.ballot_styles, ManifestPojo::convertBallotStyle),
            convertInternationalizedText(pojo.name),
            convertContactInformation(pojo.contact_information),
            null);
  }

  @Nullable
  private static Manifest.AnnotatedString convertAnnotatedString(@Nullable ManifestPojo.AnnotatedString pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.AnnotatedString(
            Strings.nullToEmpty(pojo.annotation),
            Strings.nullToEmpty(pojo.value));
  }


  @Nullable
  private static Manifest.BallotStyle convertBallotStyle(@Nullable ManifestPojo.BallotStyle pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.BallotStyle(pojo.object_id,
            ConvertPojos.convertList(pojo.geopolitical_unit_ids, Strings::nullToEmpty),
            ConvertPojos.convertList(pojo.party_ids, Strings::nullToEmpty),
            Strings.emptyToNull(pojo.image_uri));
  }

  @Nullable
  private static Manifest.Candidate convertCandidate(@Nullable ManifestPojo.Candidate pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.Candidate(pojo.object_id, convertInternationalizedText(pojo.name),
            pojo.party_id, pojo.image_uri, pojo.is_write_in);
  }

  @Nullable
  private static Manifest.ContactInformation convertContactInformation(@Nullable ManifestPojo.ContactInformation pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.ContactInformation(
            ConvertPojos.convertList(pojo.address_line, Strings::nullToEmpty),
            ConvertPojos.convertList(pojo.email, ManifestPojo::convertAnnotatedString),
            ConvertPojos.convertList(pojo.phone, ManifestPojo::convertAnnotatedString),
            pojo.name);
  }

  @Nullable
  private static Manifest.ContestDescription convertContestDescription(@Nullable ManifestPojo.ContestDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.ContestDescription(
            pojo.object_id,
            pojo.electoral_district_id,
            pojo.sequence_order,
            Manifest.VoteVariationType.valueOf(pojo.vote_variation),
            pojo.number_elected,
            pojo.votes_allowed,
            pojo.name,
            ConvertPojos.convertList(pojo.ballot_selections, ManifestPojo::convertSelectionDescription),
            convertInternationalizedText(pojo.ballot_title),
            convertInternationalizedText(pojo.ballot_subtitle),
            pojo.primary_party_ids);
  }

  @Nullable
  private static Manifest.GeopoliticalUnit convertGeopoliticalUnit(@Nullable ManifestPojo.GeopoliticalUnit pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.GeopoliticalUnit(
            pojo.object_id,
            pojo.name,
            Manifest.ReportingUnitType.valueOf(pojo.type),
            convertContactInformation(pojo.contact_information));
  }

  private static Manifest.InternationalizedText convertInternationalizedText(@Nullable ManifestPojo.InternationalizedText pojo) {
    if (pojo == null) {
      return new Manifest.InternationalizedText(ImmutableList.of());
    }
    return new Manifest.InternationalizedText(ConvertPojos.convertList(pojo.text, ManifestPojo::convertLanguage));
  }

  @Nullable
  private static Manifest.Language convertLanguage(@Nullable ManifestPojo.Language pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.Language(
            Strings.nullToEmpty(pojo.value),
            Strings.nullToEmpty(pojo.language));
  }

  @Nullable
  private static Manifest.Party convertParty(@Nullable ManifestPojo.Party pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.Party(
            pojo.object_id,
            convertInternationalizedText(pojo.name),
            pojo.abbreviation,
            pojo.color,
            pojo.logo_uri);
  }

  @Nullable
  private static Manifest.SelectionDescription convertSelectionDescription(@Nullable ManifestPojo.SelectionDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new Manifest.SelectionDescription(
            pojo.object_id,
            pojo.candidate_id,
            pojo.sequence_order);
  }

  ////////////////////////////////////////////////////////////////////////////
  // serialize

  public static JsonElement serialize(Manifest src) {
    Gson gson = GsonTypeAdapters.enhancedGson();
    ManifestPojo pojo = convert(src);
    Type typeOfSrc = new TypeToken<ManifestPojo>() {
    }.getType();
    return gson.toJsonTree(pojo, typeOfSrc);
  }

  private static ManifestPojo convert(Manifest org) {
    ManifestPojo pojo = new ManifestPojo();
    pojo.election_scope_id = org.election_scope_id();
    pojo.type = org.type().name();
    pojo.start_date = org.start_date().toString();
    pojo.end_date = org.end_date().toString();
    pojo.geopolitical_units = ConvertPojos.convertList(org.geopolitical_units(), ManifestPojo::convertGeopoliticalUnit);
    pojo.parties = ConvertPojos.convertList(org.parties(), ManifestPojo::convertParty);
    pojo.candidates = ConvertPojos.convertList(org.candidates(), ManifestPojo::convertCandidate);
    pojo.contests = ConvertPojos.convertList(org.contests(), ManifestPojo::convertContestDescription);
    pojo.ballot_styles = ConvertPojos.convertList(org.ballot_styles(), ManifestPojo::convertBallotStyle);
    if (org.name() != null) {
      pojo.name = convertInternationalizedText(org.name());
    }
    if (org.contact_information() != null) {
      pojo.contact_information = convertContactInformation(org.contact_information());
    }

    return pojo;
  }

  @Nullable
  private static ManifestPojo.AnnotatedString convertAnnotatedString(@Nullable Manifest.AnnotatedString org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.AnnotatedString pojo = new ManifestPojo.AnnotatedString();
    pojo.annotation = Strings.nullToEmpty(org.annotation());
    pojo.value = Strings.nullToEmpty(org.value());
    return pojo;
  }

  @Nullable
  private static ManifestPojo.BallotStyle convertBallotStyle(@Nullable Manifest.BallotStyle org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.BallotStyle pojo = new ManifestPojo.BallotStyle();

    pojo.object_id = org.object_id();
    pojo.geopolitical_unit_ids = ConvertPojos.convertList(org.geopolitical_unit_ids(), Strings::nullToEmpty);
    pojo.party_ids = ConvertPojos.convertList(org.party_ids(), Strings::nullToEmpty);
    if (org.image_uri() != null) {
      pojo.image_uri = org.image_uri();
    }
    return pojo;
  }

  @Nullable
  private static ManifestPojo.Candidate convertCandidate(@Nullable Manifest.Candidate org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.Candidate pojo = new ManifestPojo.Candidate();
    pojo.object_id = org.object_id();
    pojo.name = convertInternationalizedText(org.name());
    pojo.party_id = org.party_id();
    pojo.image_uri = org.image_uri();
    pojo.is_write_in = org.is_write_in();
    return pojo;
  }

  @Nullable
  private static ManifestPojo.ContactInformation convertContactInformation(@Nullable Manifest.ContactInformation org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.ContactInformation pojo = new ManifestPojo.ContactInformation();
    pojo.address_line = ConvertPojos.convertList(org.address_line(), Strings::nullToEmpty);
    pojo.email = ConvertPojos.convertList(org.email(), ManifestPojo::convertAnnotatedString);
    pojo.phone = ConvertPojos.convertList(org.phone(), ManifestPojo::convertAnnotatedString);
    if (org.name() != null) {
      pojo.name = org.name();
    }
    return pojo;
  }

  @Nullable
  private static ManifestPojo.ContestDescription convertContestDescription(@Nullable Manifest.ContestDescription org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.ContestDescription pojo = new ManifestPojo.ContestDescription();
    pojo.object_id = org.object_id();
    pojo.electoral_district_id = org.electoral_district_id();
    pojo.sequence_order = org.sequence_order();
    pojo.vote_variation = org.vote_variation().name();
    pojo.number_elected = org.number_elected();
    pojo.votes_allowed = org.votes_allowed();
    pojo.name = org.name();
    pojo.ballot_selections = ConvertPojos.convertList(org.ballot_selections(), ManifestPojo::convertSelectionDescription);
    if (org.ballot_title() != null) {
      pojo.ballot_title = convertInternationalizedText(org.ballot_title());
    }
    if (org.ballot_subtitle() != null) {
      pojo.ballot_subtitle = convertInternationalizedText(org.ballot_subtitle());
    }
    pojo.primary_party_ids = org.primary_party_ids();
    return pojo;
  }

  @Nullable
  private static ManifestPojo.GeopoliticalUnit convertGeopoliticalUnit(@Nullable Manifest.GeopoliticalUnit org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.GeopoliticalUnit pojo = new ManifestPojo.GeopoliticalUnit();
    pojo.object_id = org.object_id();
    pojo.name = org.name();
    pojo.type = org.type().name();
    if (org.contact_information() != null) {
      pojo.contact_information = convertContactInformation(org.contact_information());
    }
    return pojo;
  }

  @Nullable
  private static ManifestPojo.InternationalizedText convertInternationalizedText(@Nullable Manifest.InternationalizedText org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.InternationalizedText pojo = new ManifestPojo.InternationalizedText();
    pojo.text = ConvertPojos.convertList(org.text(), ManifestPojo::convertLanguage);
    return pojo;
  }

  @Nullable
  private static ManifestPojo.Language convertLanguage(@Nullable Manifest.Language org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.Language pojo = new ManifestPojo.Language();
    pojo.value = Strings.nullToEmpty(org.value());
    pojo.language = Strings.nullToEmpty(org.language());
    return pojo;
  }

  @Nullable
  private static ManifestPojo.Party convertParty(@Nullable Manifest.Party org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.Party pojo = new ManifestPojo.Party();
    pojo.object_id = org.object_id();
    pojo.name = convertInternationalizedText(org.name());
    if (org.abbreviation() != null) {
      pojo.abbreviation = org.abbreviation();
    }
    if (org.color() != null) {
      pojo.color = org.color();
    }
    if (org.logo_uri() != null) {
      pojo.logo_uri = org.logo_uri();
    }
    return pojo;
  }

  @Nullable
  private static ManifestPojo.SelectionDescription convertSelectionDescription(@Nullable Manifest.SelectionDescription org) {
    if (org == null) {
      return null;
    }
    ManifestPojo.SelectionDescription pojo = new ManifestPojo.SelectionDescription();
    pojo.object_id = org.object_id();
    pojo.candidate_id = org.candidate_id();
    pojo.sequence_order = org.sequence_order();
    return pojo;
  }

}