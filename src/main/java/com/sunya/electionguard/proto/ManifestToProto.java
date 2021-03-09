package com.sunya.electionguard.proto;

import com.sunya.electionguard.Manifest;

import static com.sunya.electionguard.proto.ManifestProto.*;

public class ManifestToProto {

  public static ManifestProto.Manifest translateToProto(Manifest election) {
    ManifestProto.Manifest.Builder builder = ManifestProto.Manifest.newBuilder();
    builder.setElectionScopeId(election.election_scope_id);
    builder.setElectionType(convertElectionType(election.type));
    builder.setStartDate(election.start_date.toString());
    builder.setEndDate(election.end_date.toString());

    election.geopolitical_units.forEach(value -> builder.addGeopoliticalUnits(convertGeopoliticalUnit(value)));
    election.parties.forEach(value -> builder.addParties(convertParty(value)));
    election.candidates.forEach(value -> builder.addCandidates(convertCandidate(value)));
    election.contests.forEach(value -> builder.addContests(convertContestDescription(value)));
    election.ballot_styles.forEach(value -> builder.addBallotStyles(convertBallotStyle(value)));

    election.name.ifPresent(value -> builder.setName(convertInternationalizedText(value)));
    election.contact_information.ifPresent(value -> builder.setContactInformation(convertContactInformation(value)));

    return builder.build();
  }

  static AnnotatedString convertAnnotatedString(Manifest.AnnotatedString annotated) {
    if (annotated == null) {
      return null;
    }
    AnnotatedString.Builder builder = AnnotatedString.newBuilder();
    builder.setAnnotation(annotated.annotation);
    builder.setValue(annotated.value);
    return builder.build();
  }

  static BallotStyle convertBallotStyle(Manifest.BallotStyle style) {
    BallotStyle.Builder builder = BallotStyle.newBuilder();
    builder.setObjectId(style.object_id);
    builder.addAllGeopoliticalUnitIds(style.geopolitical_unit_ids);
    builder.addAllPartyIds(style.party_ids);
    style.image_uri.ifPresent(builder::setImageUrl);
    return builder.build();
  }

  static Candidate convertCandidate(Manifest.Candidate candidate) {
    Candidate.Builder builder = Candidate.newBuilder();
    builder.setObjectId(candidate.object_id);
    builder.setName(convertInternationalizedText(candidate.name));
    candidate.party_id.ifPresent(builder::setPartyId);
    candidate.image_uri.ifPresent(builder::setImageUrl);
    builder.setIsWriteIn(candidate.is_write_in);
    return builder.build();
  }

  static ContactInformation convertContactInformation(Manifest.ContactInformation contact) {
    ContactInformation.Builder builder = ContactInformation.newBuilder();
    contact.name.ifPresent(builder::setName);
    builder.addAllAddressLine(contact.address_line);
    contact.email.forEach(value -> builder.addEmail(convertAnnotatedString(value)));
    contact.phone.forEach(value -> builder.addPhone(convertAnnotatedString(value)));
    return builder.build();
  }

  static ContestDescription convertContestDescription(Manifest.ContestDescription contest) {
    // LOOK check for subtypes of ContestDescription. Argues for just adding subtype's fields
    ContestDescription.Builder builder = ContestDescription.newBuilder();
    builder.setObjectId(contest.object_id);
    builder.setElectoralDistrictId(contest.electoral_district_id);
    builder.setSequenceOrder(contest.sequence_order);
    builder.setVoteVariation(convertVoteVariationType(contest.vote_variation));
    builder.setNumberElected(contest.number_elected);
    contest.votes_allowed.ifPresent(builder::setVotesAllowed);
    builder.setName(contest.name);
    contest.ballot_selections.forEach(value -> builder.addBallotSelections(convertSelectionDescription(value)));
    contest.ballot_title.ifPresent(value -> builder.setBallotTitle(convertInternationalizedText(value)));
    contest.ballot_subtitle.ifPresent(value -> builder.setBallotSubtitle(convertInternationalizedText(value)));
    return builder.build();
  }

  static ContestDescription.VoteVariationType convertVoteVariationType(Manifest.VoteVariationType type) {
    return ContestDescription.VoteVariationType.valueOf(type.name());
  }

  static ManifestProto.Manifest.ElectionType convertElectionType(Manifest.ElectionType type) {
    return ManifestProto.Manifest.ElectionType.valueOf(type.name());
  }

  static GeopoliticalUnit.ReportingUnitType convertReportingUnitType(Manifest.ReportingUnitType type) {
    return GeopoliticalUnit.ReportingUnitType.valueOf(type.name());
  }

  static GeopoliticalUnit convertGeopoliticalUnit(Manifest.GeopoliticalUnit geoUnit) {
    GeopoliticalUnit.Builder builder = GeopoliticalUnit.newBuilder();
    builder.setObjectId(geoUnit.object_id);
    builder.setName(geoUnit.name);
    builder.setType(convertReportingUnitType(geoUnit.type));
    geoUnit.contact_information.ifPresent(value -> builder.setContactInformation(convertContactInformation(value)));
    return builder.build();
  }

  static InternationalizedText convertInternationalizedText(Manifest.InternationalizedText text) {
    InternationalizedText.Builder builder = InternationalizedText.newBuilder();
    if (text.text != null) {
      text.text.forEach(value -> builder.addText(convertLanguage(value)));
    }
    return builder.build();
  }

  static Language convertLanguage(Manifest.Language text) {
    Language.Builder builder = Language.newBuilder();
    builder.setValue(text.value);
    builder.setLanguage(text.language);
    return builder.build();
  }

  static Party convertParty(Manifest.Party party) {
    Party.Builder builder = Party.newBuilder();
    builder.setObjectId(party.object_id);
    if (party.name.text != null) {
      builder.setName(convertInternationalizedText(party.name));
    }
    party.abbreviation.ifPresent(builder::setAbbreviation);
    party.color.ifPresent(builder::setColor);
    party.logo_uri.ifPresent(builder::setLogoUri);
    return builder.build();
  }

  static SelectionDescription convertSelectionDescription(Manifest.SelectionDescription selection) {
    SelectionDescription.Builder builder = SelectionDescription.newBuilder();
    builder.setObjectId(selection.object_id);
    builder.setCandidateId(selection.candidate_id);
    builder.setSequenceOrder(selection.sequence_order);
    return builder.build();
  }
}
