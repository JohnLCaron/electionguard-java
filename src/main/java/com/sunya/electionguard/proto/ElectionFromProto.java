package com.sunya.electionguard.proto;

import com.sunya.electionguard.Election;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.ElectionProto.AnnotatedString;
import static com.sunya.electionguard.proto.ElectionProto.BallotStyle;
import static com.sunya.electionguard.proto.ElectionProto.Candidate;
import static com.sunya.electionguard.proto.ElectionProto.ContactInformation;
import static com.sunya.electionguard.proto.ElectionProto.ContestDescription;
import static com.sunya.electionguard.proto.ElectionProto.ElectionDescription;
import static com.sunya.electionguard.proto.ElectionProto.GeopoliticalUnit;
import static com.sunya.electionguard.proto.ElectionProto.InternationalizedText;
import static com.sunya.electionguard.proto.ElectionProto.Language;
import static com.sunya.electionguard.proto.ElectionProto.Party;
import static com.sunya.electionguard.proto.ElectionProto.SelectionDescription;

public class ElectionFromProto {

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  public static Election.ElectionDescription translate(ElectionProto.ElectionDescription election) {
    OffsetDateTime start_date = OffsetDateTime.parse(election.getStartDate());
    OffsetDateTime end_date = OffsetDateTime.parse(election.getEndDate());
    Election.ContactInformation contact = election.hasContactInformation() ?
            translateContactInformation(election.getContactInformation()) : null;

    // String election_scope_id,
    //            Election.ElectionType type,
    //            OffsetDateTime start_date,
    //            OffsetDateTime end_date,
    //            List< Election.GeopoliticalUnit > geopolitical_units,
    //            List< Election.Party > parties,
    //            List< Election.Candidate > candidates,
    //            List< Election.ContestDescription > contests,
    //            List< Election.BallotStyle > ballot_styles,
    //            @Nullable InternationalizedText name,
    //            @Nullable ContactInformation contact_information
    return new Election.ElectionDescription(
            election.getElectionScopeId(),
            translate(election.getElectionType()),
            start_date,
            end_date,
            convertList(election.getGeopoliticalUnitsList(), ElectionFromProto::translateGeopoliticalUnit),
            convertList(election.getPartiesList(), ElectionFromProto::translateParty),
            convertList(election.getCandidatesList(), ElectionFromProto::translateCandidate),
            convertList(election.getContestsList(), ElectionFromProto::translateContestDescription),
            convertList(election.getBallotStylesList(), ElectionFromProto::translateBallotStyle),
            translateInternationalizedText(election.getName()),
            contact);
  }

  static Election.AnnotatedString translateAnnotatedString(AnnotatedString annotated) {
    return new Election.AnnotatedString(annotated.getAnnotation(), annotated.getValue());
  }

  static Election.BallotStyle translateBallotStyle(BallotStyle style) {
    return new Election.BallotStyle(
            style.getObjectId(),
            style.getGeopoliticalUnitIdsList(),
            style.getPartyIdsList(),
            style.getImageUrl());
  }

  static Election.Candidate translateCandidate(Candidate candidate) {
    return new Election.Candidate(
            candidate.getObjectId(),
            translateInternationalizedText(candidate.getName()),
            candidate.getPartyId(),
            candidate.getImageUrl(),
            candidate.getIsWriteIn());
  }

  static Election.ContactInformation translateContactInformation(@Nullable ContactInformation contact) {
    return new Election.ContactInformation(
            contact.getAddressLineList(),
            convertList(contact.getEmailList(), ElectionFromProto::translateAnnotatedString),
            convertList(contact.getPhoneList(), ElectionFromProto::translateAnnotatedString),
            contact.getName());
  }

  static Election.ContestDescription translateContestDescription(ContestDescription contest) {
    return new Election.ContestDescription(
            contest.getObjectId(),
            contest.getElectoralDistrictId(),
            contest.getSequenceOrder(),
            translateVoteVariationType(contest.getVoteVariation()),
            contest.getNumberElected(),
            contest.getVotesAllowed(),
            contest.getName(),
            convertList(contest.getBallotSelectionsList(), ElectionFromProto::translateSelectionDescription),
            translateInternationalizedText(contest.getBallotTitle()),
            translateInternationalizedText(contest.getBallotSubtitle()));
  }

  static Election.VoteVariationType translateVoteVariationType(ContestDescription.VoteVariationType type) {
    return Election.VoteVariationType.valueOf(type.name());
  }

  static Election.ElectionType translate(ElectionDescription.ElectionType type) {
    return Election.ElectionType.valueOf(type.name());
  }

  static Election.ReportingUnitType translateReportingUnitType(GeopoliticalUnit.ReportingUnitType type) {
    return Election.ReportingUnitType.valueOf(type.name());
  }

  static Election.GeopoliticalUnit translateGeopoliticalUnit(GeopoliticalUnit geoUnit) {
    Election.ContactInformation contact = geoUnit.hasContactInformation() ?
            translateContactInformation(geoUnit.getContactInformation()) : null;

    return new Election.GeopoliticalUnit(
            geoUnit.getObjectId(),
            geoUnit.getName(),
            translateReportingUnitType(geoUnit.getType()),
            contact);
  }

  static Election.InternationalizedText translateInternationalizedText(InternationalizedText text) {
    return new Election.InternationalizedText(convertList(text.getTextList(), ElectionFromProto::translateLanguage));
  }

  static Election.Language translateLanguage(Language language) {
    return new Election.Language(language.getValue(), language.getLanguage());
  }

  static Election.Party translateParty(Party party) {
    return new Election.Party(
            party.getObjectId(),
            translateInternationalizedText(party.getName()),
            party.getAbbreviation(),
            party.getColor(),
            party.getLogoUri());
  }

  static Election.SelectionDescription translateSelectionDescription(SelectionDescription selection) {
    return new Election.SelectionDescription(
            selection.getObjectId(),
            selection.getCandidateId(),
            selection.getSequenceOrder());
  }
}
