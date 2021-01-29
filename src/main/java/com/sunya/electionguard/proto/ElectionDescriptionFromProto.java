package com.sunya.electionguard.proto;

import com.sunya.electionguard.Election;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

import static com.sunya.electionguard.proto.CommonConvert.convertList;
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

public class ElectionDescriptionFromProto {

  public static Election.ElectionDescription translateFromProto(ElectionProto.ElectionDescription election) {
    OffsetDateTime start_date = OffsetDateTime.parse(election.getStartDate());
    OffsetDateTime end_date = OffsetDateTime.parse(election.getEndDate());
    Election.InternationalizedText name = election.hasName() ?
            convertInternationalizedText(election.getName()) : null;
    Election.ContactInformation contact = election.hasContactInformation() ?
            convertContactInformation(election.getContactInformation()) : null;

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
            convert(election.getElectionType()),
            start_date,
            end_date,
            convertList(election.getGeopoliticalUnitsList(), ElectionDescriptionFromProto::convertGeopoliticalUnit),
            convertList(election.getPartiesList(), ElectionDescriptionFromProto::convertParty),
            convertList(election.getCandidatesList(), ElectionDescriptionFromProto::convertCandidate),
            convertList(election.getContestsList(), ElectionDescriptionFromProto::convertContestDescription),
            convertList(election.getBallotStylesList(), ElectionDescriptionFromProto::convertBallotStyle),
            name,
            contact);
  }

  static Election.AnnotatedString convertAnnotatedString(AnnotatedString annotated) {
    return new Election.AnnotatedString(annotated.getAnnotation(), annotated.getValue());
  }

  static Election.BallotStyle convertBallotStyle(BallotStyle style) {
    return new Election.BallotStyle(
            style.getObjectId(),
            style.getGeopoliticalUnitIdsList(),
            style.getPartyIdsList(),
            style.getImageUrl());
  }

  static Election.Candidate convertCandidate(Candidate candidate) {
    return new Election.Candidate(
            candidate.getObjectId(),
            convertInternationalizedText(candidate.getName()),
            candidate.getPartyId(),
            candidate.getImageUrl(),
            candidate.getIsWriteIn());
  }

  @Nullable
  static Election.ContactInformation convertContactInformation(@Nullable ContactInformation contact) {
    if (contact == null) {
      return null;
    }
    return new Election.ContactInformation(
            contact.getAddressLineList(),
            convertList(contact.getEmailList(), ElectionDescriptionFromProto::convertAnnotatedString),
            convertList(contact.getPhoneList(), ElectionDescriptionFromProto::convertAnnotatedString),
            contact.getName());
  }

  static Election.ContestDescription convertContestDescription(ContestDescription contest) {
    return new Election.ContestDescription(
            contest.getObjectId(),
            contest.getElectoralDistrictId(),
            contest.getSequenceOrder(),
            convertVoteVariationType(contest.getVoteVariation()),
            contest.getNumberElected(),
            contest.getVotesAllowed(),
            contest.getName(),
            convertList(contest.getBallotSelectionsList(), ElectionDescriptionFromProto::convertSelectionDescription),
            contest.hasBallotTitle() ? convertInternationalizedText(contest.getBallotTitle()) : null,
            contest.hasBallotSubtitle() ? convertInternationalizedText(contest.getBallotSubtitle()) : null);
  }

  static Election.VoteVariationType convertVoteVariationType(ContestDescription.VoteVariationType type) {
    return Election.VoteVariationType.valueOf(type.name());
  }

  static Election.ElectionType convert(ElectionDescription.ElectionType type) {
    return Election.ElectionType.valueOf(type.name());
  }

  static Election.ReportingUnitType convertReportingUnitType(GeopoliticalUnit.ReportingUnitType type) {
    return Election.ReportingUnitType.valueOf(type.name());
  }

  static Election.GeopoliticalUnit convertGeopoliticalUnit(GeopoliticalUnit geoUnit) {
    return new Election.GeopoliticalUnit(
            geoUnit.getObjectId(),
            geoUnit.getName(),
            convertReportingUnitType(geoUnit.getType()),
            geoUnit.hasContactInformation() ? convertContactInformation(geoUnit.getContactInformation()) : null);
  }

  static Election.InternationalizedText convertInternationalizedText(InternationalizedText text) {
    return new Election.InternationalizedText(convertList(text.getTextList(), ElectionDescriptionFromProto::convertLanguage));
  }

  static Election.Language convertLanguage(Language language) {
    return new Election.Language(language.getValue(), language.getLanguage());
  }

  static Election.Party convertParty(Party party) {
    return new Election.Party(
            party.getObjectId(),
            convertInternationalizedText(party.getName()),
            party.getAbbreviation(),
            party.getColor(),
            party.getLogoUri());
  }

  static Election.SelectionDescription convertSelectionDescription(SelectionDescription selection) {
    return new Election.SelectionDescription(
            selection.getObjectId(),
            selection.getCandidateId(),
            selection.getSequenceOrder());
  }
}
