package com.sunya.electionguard.publish;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;

import javax.annotation.Nullable;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Election.*;

/** Conversion of ElectionDescription from Json. */
public class ElectionDescriptionFromJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String pathname;

  /**
   * Open a json file containing ElectionDescription.
   *
   * @param pathname absolute pathname.
   */
  public ElectionDescriptionFromJson(String pathname) {
    this.pathname = pathname;
  }

  public ElectionDescription build() throws IOException {
    try (InputStream is = new FileInputStream(pathname)) {
      Reader reader = new InputStreamReader(is);
      Gson gson = new Gson(); // default exclude nulls
      ElectionDescriptionPojo pojo = gson.fromJson(reader, ElectionDescriptionPojo.class);
      return convert(pojo);
    } catch (Exception ioe) {
      logger.atSevere().log("Failed on file '%s'", pathname);
      throw ioe;
    }
  }

  @Nullable
  <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  ElectionDescription convert(ElectionDescriptionPojo pojo) {
    // LOOK going to throw an Exception if these arent present or invalid format
    OffsetDateTime startLocalDate = OffsetDateTime.parse(pojo.start_date);
    OffsetDateTime endLocalDate = OffsetDateTime.parse(pojo.end_date);

    return new ElectionDescription(
            pojo.election_scope_id,
            ElectionType.valueOf(pojo.type),
            startLocalDate,
            endLocalDate,
            convertList(pojo.geopolitical_units, this::convertGeopoliticalUnit),
            convertList(pojo.parties, this::convertParty),
            convertList(pojo.candidates, this::convertCandidate),
            convertList(pojo.contests, this::convertContestDescription),
            convertList(pojo.ballot_styles, this::convertBallotStyle),
            convertInternationalizedText(pojo.name),
            convertContactInformation(pojo.contact_information)
    );
  }

  @Nullable
  AnnotatedString convertAnnotatedString(@Nullable ElectionDescriptionPojo.AnnotatedString pojo) {
    if (pojo == null) {
      return null;
    }
    return new AnnotatedString(
            Strings.nullToEmpty(pojo.annotation),
            Strings.nullToEmpty(pojo.value));
  }


  @Nullable
  BallotStyle convertBallotStyle(@Nullable ElectionDescriptionPojo.BallotStyle pojo) {
    if (pojo == null) {
      return null;
    }
    return new BallotStyle(pojo.object_id,
            convertList(pojo.geopolitical_unit_ids, Strings::nullToEmpty),
            convertList(pojo.party_ids, Strings::nullToEmpty),
            Strings.emptyToNull(pojo.image_uri));
  }

  @Nullable
  Candidate convertCandidate(@Nullable ElectionDescriptionPojo.Candidate pojo) {
    if (pojo == null) {
      return null;
    }
    return new Candidate(pojo.object_id, convertInternationalizedText(pojo.ballot_name),
            pojo.party_id, pojo.image_uri, pojo.is_write_in);
  }

  @Nullable
  ContactInformation convertContactInformation(@Nullable ElectionDescriptionPojo.ContactInformation pojo) {
    if (pojo == null) {
      return null;
    }
    return new ContactInformation(
            convertList(pojo.address_line, Strings::nullToEmpty),
            convertList(pojo.email, this::convertAnnotatedString),
            convertList(pojo.phone, this::convertAnnotatedString),
            pojo.name);
  }

  @Nullable
  ContestDescription convertContestDescription(@Nullable ElectionDescriptionPojo.ContestDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new ContestDescription(
            pojo.object_id,
            pojo.electoral_district_id,
            pojo.sequence_order,
            VoteVariationType.valueOf(pojo.vote_variation),
            pojo.number_elected,
            pojo.votes_allowed,
            pojo.name,
            convertList(pojo.ballot_selections, this::convertSelectionDescription),
            convertInternationalizedText(pojo.ballot_title),
            convertInternationalizedText(pojo.ballot_subtitle));
  }

  @Nullable
  GeopoliticalUnit convertGeopoliticalUnit(@Nullable ElectionDescriptionPojo.GeopoliticalUnit pojo) {
    if (pojo == null) {
      return null;
    }
    return new GeopoliticalUnit(
            pojo.object_id,
            pojo.name,
            ReportingUnitType.valueOf(pojo.type),
            convertContactInformation(pojo.contact_information));
  }

  @Nullable
  InternationalizedText convertInternationalizedText(@Nullable ElectionDescriptionPojo.InternationalizedText pojo) {
    if (pojo == null) {
      return null;
    }
    return new InternationalizedText(convertList(pojo.text, this::convertLanguage));
  }

  @Nullable
  Language convertLanguage(@Nullable ElectionDescriptionPojo.Language pojo) {
    if (pojo == null) {
      return null;
    }
    return new Language(
            Strings.nullToEmpty(pojo.value),
            Strings.nullToEmpty(pojo.language));
  }

  @Nullable
  Party convertParty(@Nullable ElectionDescriptionPojo.Party pojo) {
    if (pojo == null) {
      return null;
    }
    return new Party(
            pojo.object_id,
            convertInternationalizedText(pojo.ballot_name),
            pojo.abbreviation,
            pojo.color,
            pojo.logo_uri);
  }

  @Nullable
  SelectionDescription convertSelectionDescription(@Nullable ElectionDescriptionPojo.SelectionDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new SelectionDescription(
            pojo.object_id,
            pojo.candidate_id,
            pojo.sequence_order);
  }

}
