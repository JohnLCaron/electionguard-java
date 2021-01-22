package com.sunya.electionguard.proto;

import com.sunya.electionguard.Election;

import static com.sunya.electionguard.proto.ElectionProto.*;

public class ElectionToProto {

  public static ElectionProto.ElectionDescription translate(Election.ElectionDescription election) {
    ElectionDescription.Builder builder = ElectionDescription.newBuilder();
    builder.setElectionScopeId(election.election_scope_id);
    builder.setElectionType(translateElectionType(election.type));
    builder.setStartDate(election.start_date.toString());
    builder.setEndDate(election.end_date.toString());

    election.geopolitical_units.forEach(value -> builder.addGeopoliticalUnits(translateGeopoliticalUnit(value)));
    election.parties.forEach(value -> builder.addParties(translateParty(value)));
    election.candidates.forEach(value -> builder.addCandidates(translateCandidate(value)));
    election.contests.forEach(value -> builder.addContests(translateContestDescription(value)));
    election.ballot_styles.forEach(value -> builder.addBallotStyles(translateBallotStyle(value)));

    election.name.ifPresent(value -> builder.setName(translateInternationalizedText(value)));
    election.contact_information.ifPresent(value -> builder.setContactInformation(translateContactInformation(value)));

    return builder.build();
  }

  static AnnotatedString translateAnnotatedString(Election.AnnotatedString annotated) {
    if (annotated == null) {
      return null;
    }
    AnnotatedString.Builder builder = AnnotatedString.newBuilder();
    builder.setAnnotation(annotated.annotation);
    builder.setValue(annotated.value);
    return builder.build();
  }

  static BallotStyle translateBallotStyle(Election.BallotStyle style) {
    BallotStyle.Builder builder = BallotStyle.newBuilder();
    builder.setObjectId(style.object_id);
    style.geopolitical_unit_ids.ifPresent(builder::addAllGeopoliticalUnitIds);
    style.party_ids.ifPresent(builder::addAllPartyIds);
    style.image_uri.ifPresent(builder::setImageUrl);
    return builder.build();
  }

  static Candidate translateCandidate(Election.Candidate candidate) {
    Candidate.Builder builder = Candidate.newBuilder();
    builder.setObjectId(candidate.object_id);
    builder.setName(translateInternationalizedText(candidate.name));
    candidate.party_id.ifPresent(builder::setPartyId);
    candidate.image_uri.ifPresent(builder::setImageUrl);
    builder.setIsWriteIn(candidate.is_write_in);
    return builder.build();
  }

  static ContactInformation translateContactInformation(Election.ContactInformation contact) {
    ContactInformation.Builder builder = ContactInformation.newBuilder();
    contact.name.ifPresent(builder::setName);
    contact.address_line.ifPresent(builder::addAllAddressLine);
    contact.email.ifPresent(email -> email.forEach(value -> builder.addEmail(translateAnnotatedString(value))));
    contact.phone.ifPresent(phone -> phone.forEach(value -> builder.addPhone(translateAnnotatedString(value))));
    return builder.build();
  }

  static ContestDescription translateContestDescription(Election.ContestDescription contest) {
    // LOOK check for subtypes of ContestDescription. Argues for just having optional fields
    ContestDescription.Builder builder = ContestDescription.newBuilder();
    builder.setObjectId(contest.object_id);
    builder.setElectoralDistrictId(contest.electoral_district_id);
    builder.setSequenceOrder(contest.sequence_order);
    builder.setVoteVariation(translateVoteVariationType(contest.vote_variation));
    builder.setNumberElected(contest.number_elected);
    contest.votes_allowed.ifPresent(builder::setVotesAllowed);
    builder.setName(contest.name);
    contest.ballot_selections.forEach(value -> builder.addBallotSelections(translateSelectionDescription(value)));
    contest.ballot_title.ifPresent(value -> builder.setBallotTitle(translateInternationalizedText(value)));
    contest.ballot_subtitle.ifPresent(value -> builder.setBallotSubtitle(translateInternationalizedText(value)));
    return builder.build();
  }

  static ContestDescription.VoteVariationType translateVoteVariationType(Election.VoteVariationType type) {
    return ContestDescription.VoteVariationType.valueOf(type.name());
  }

  static ElectionDescription.ElectionType translateElectionType(Election.ElectionType type) {
    return ElectionDescription.ElectionType.valueOf(type.name());
  }

  static GeopoliticalUnit.ReportingUnitType translateReportingUnitType(Election.ReportingUnitType type) {
    return GeopoliticalUnit.ReportingUnitType.valueOf(type.name());
  }

  static GeopoliticalUnit translateGeopoliticalUnit(Election.GeopoliticalUnit geoUnit) {
    GeopoliticalUnit.Builder builder = GeopoliticalUnit.newBuilder();
    builder.setObjectId(geoUnit.object_id);
    builder.setName(geoUnit.name);
    builder.setType(translateReportingUnitType(geoUnit.type));
    geoUnit.contact_information.ifPresent(value -> builder.setContactInformation(translateContactInformation(value)));
    return builder.build();
  }

  static InternationalizedText translateInternationalizedText(Election.InternationalizedText text) {
    InternationalizedText.Builder builder = InternationalizedText.newBuilder();
    if (text.text != null) {
      text.text.forEach(value -> builder.addText(translateLanguage(value)));
    }
    return builder.build();
  }

  static Language translateLanguage(Election.Language text) {
    Language.Builder builder = Language.newBuilder();
    builder.setValue(text.value);
    builder.setLanguage(text.language);
    return builder.build();
  }

  static Party translateParty(Election.Party party) {
    Party.Builder builder = Party.newBuilder();
    builder.setObjectId(party.object_id);
    if (party.name.text != null) {
      builder.setName(translateInternationalizedText(party.name));
    }
    party.abbreviation.ifPresent(builder::setAbbreviation);
    party.color.ifPresent(builder::setColor);
    party.logo_uri.ifPresent(builder::setLogoUri);
    return builder.build();
  }

  static SelectionDescription translateSelectionDescription(Election.SelectionDescription selection) {
    SelectionDescription.Builder builder = SelectionDescription.newBuilder();
    builder.setObjectId(selection.object_id);
    builder.setCandidateId(selection.candidate_id);
    builder.setSequenceOrder(selection.sequence_order);
    return builder.build();
  }
}
