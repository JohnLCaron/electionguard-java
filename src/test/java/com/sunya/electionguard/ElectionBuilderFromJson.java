package com.sunya.electionguard;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.sunya.integration.ElectionBuilderPojo;

import javax.annotation.Nullable;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Election.*;

public class ElectionBuilderFromJson {

  private final String filename;
  ElectionBuilderFromJson(String filename) {
    this.filename = filename;
  }

  ElectionDescription build() throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    System.out.printf("Current '%s'%n", current);

    InputStream is = new FileInputStream((current + "/" + filename));
    Reader reader = new InputStreamReader(is);
    Gson gson = new Gson(); // default exclude nulls
    ElectionBuilderPojo pojo = gson.fromJson(reader, ElectionBuilderPojo.class);
    return convert(pojo);
  }

  @Nullable
  <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter::apply).collect(Collectors.toList());
  }

  ElectionDescription convert(ElectionBuilderPojo pojo) {
    // TODO going to throw an Exception if these arent present or invalid format
    DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    LocalDate startLocalDate = formatter.parse(pojo.start_date, TemporalQueries.localDate());
    LocalDate endLocalDate = formatter.parse(pojo.end_date, TemporalQueries.localDate());

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
  AnnotatedString convertAnnotatedString(@Nullable ElectionBuilderPojo.AnnotatedString pojo) {
    if (pojo == null) {
      return null;
    }
    return new AnnotatedString(
            Strings.nullToEmpty(pojo.annotation),
            Strings.nullToEmpty(pojo.value));
  }


  @Nullable
  BallotStyle convertBallotStyle(@Nullable ElectionBuilderPojo.BallotStyle pojo) {
    if (pojo == null) {
      return null;
    }
    return new BallotStyle(pojo.object_id,
            convertList(pojo.geopolitical_unit_ids, Strings::nullToEmpty),
            convertList(pojo.party_ids, Strings::nullToEmpty),
            Strings.emptyToNull(pojo.image_uri));
  }

  @Nullable
  Candidate convertCandidate(@Nullable ElectionBuilderPojo.Candidate pojo) {
    if (pojo == null) {
      return null;
    }
    return new Candidate(pojo.object_id, convertInternationalizedText(pojo.ballot_name),
            pojo.party_id, pojo.image_uri, pojo.is_write_in);
  }

  @Nullable
  ContactInformation convertContactInformation(@Nullable ElectionBuilderPojo.ContactInformation pojo) {
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
  ContestDescription convertContestDescription(@Nullable ElectionBuilderPojo.ContestDescription pojo) {
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
  GeopoliticalUnit convertGeopoliticalUnit(@Nullable ElectionBuilderPojo.GeopoliticalUnit pojo) {
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
  InternationalizedText convertInternationalizedText(@Nullable ElectionBuilderPojo.InternationalizedText pojo) {
    if (pojo == null) {
      return null;
    }
    return new InternationalizedText(convertList(pojo.text, this::convertLanguage));
  }

  @Nullable
  Language convertLanguage(@Nullable ElectionBuilderPojo.Language pojo) {
    if (pojo == null) {
      return null;
    }
    return new Language(
            Strings.nullToEmpty(pojo.value),
            Strings.nullToEmpty(pojo.language));
  }

  @Nullable
  Party convertParty(@Nullable ElectionBuilderPojo.Party pojo) {
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
  SelectionDescription convertSelectionDescription(@Nullable ElectionBuilderPojo.SelectionDescription pojo) {
    if (pojo == null) {
      return null;
    }
    return new SelectionDescription(
            pojo.object_id,
            pojo.candidate_id,
            pojo.sequence_order);
  }




}