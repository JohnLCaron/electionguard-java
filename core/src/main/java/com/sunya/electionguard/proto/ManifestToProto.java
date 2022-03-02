package com.sunya.electionguard.proto;

import com.sunya.electionguard.Manifest;

import electionguard.protogen.ManifestProto;

public class ManifestToProto {

  public static ManifestProto.Manifest translateToProto(Manifest election) {
    ManifestProto.Manifest.Builder builder = ManifestProto.Manifest.newBuilder();
    builder.setElectionScopeId(election.election_scope_id());
    if (election.spec_version() != null) {
      builder.setSpecVersion(election.spec_version());
    }
    builder.setElectionType(convertElectionType(election.electionType()));
    builder.setStartDate(election.start_date().toString());
    builder.setEndDate(election.end_date().toString());

    election.geopolitical_units().forEach(value -> builder.addGeopoliticalUnits(convertGeopoliticalUnit(value)));
    election.parties().forEach(value -> builder.addParties(convertParty(value)));
    election.candidates().forEach(value -> builder.addCandidates(convertCandidate(value)));
    election.contests().forEach(value -> builder.addContests(convertContestDescription(value)));
    election.ballot_styles().forEach(value -> builder.addBallotStyles(convertBallotStyle(value)));

    if (election.name() != null) {
      builder.setName(convertInternationalizedText(election.name()));
    }
    if (election.contact_information() != null) {
      builder.setContactInformation(convertContactInformation(election.contact_information()));
    }

    return builder.build();
  }

  static ManifestProto.AnnotatedString convertAnnotatedString(Manifest.AnnotatedString annotated) {
    if (annotated == null) {
      return null;
    }
    ManifestProto.AnnotatedString.Builder builder = ManifestProto.AnnotatedString.newBuilder();
    builder.setAnnotation(annotated.annotation());
    builder.setValue(annotated.value());
    return builder.build();
  }

  static ManifestProto.BallotStyle convertBallotStyle(Manifest.BallotStyle style) {
    ManifestProto.BallotStyle.Builder builder = ManifestProto.BallotStyle.newBuilder();
    builder.setBallotStyleId(style.object_id());
    builder.addAllGeopoliticalUnitIds(style.geopolitical_unit_ids());
    builder.addAllPartyIds(style.party_ids());
    if (style.image_uri() != null) {
      builder.setImageUrl(style.image_uri());
    }
    return builder.build();
  }

  static ManifestProto.Candidate convertCandidate(Manifest.Candidate candidate) {
    ManifestProto.Candidate.Builder builder = ManifestProto.Candidate.newBuilder();
    builder.setCandidateId(candidate.object_id());
    builder.setName(convertInternationalizedText(candidate.name()));
    if (candidate.party_id() != null) {
      builder.setPartyId(candidate.party_id());
    }
    if (candidate.image_uri() != null) {
      builder.setImageUrl(candidate.image_uri());
    }
    builder.setIsWriteIn(candidate.is_write_in());
    return builder.build();
  }

  static ManifestProto.ContactInformation convertContactInformation(Manifest.ContactInformation contact) {
    ManifestProto.ContactInformation.Builder builder = ManifestProto.ContactInformation.newBuilder();
    if (contact.name() != null) {
      builder.setName(contact.name());
    }
    builder.addAllAddressLine(contact.address_line());
    contact.email().forEach(value -> builder.addEmail(convertAnnotatedString(value)));
    contact.phone().forEach(value -> builder.addPhone(convertAnnotatedString(value)));
    return builder.build();
  }

  static ManifestProto.ContestDescription convertContestDescription(Manifest.ContestDescription contest) {
    ManifestProto.ContestDescription.Builder builder = ManifestProto.ContestDescription.newBuilder();
    builder.setContestId(contest.object_id());
    builder.setGeopoliticalUnitId(contest.electoral_district_id());
    builder.setSequenceOrder(contest.sequence_order());
    builder.setVoteVariation(convertVoteVariationType(contest.vote_variation()));
    builder.setNumberElected(contest.number_elected());
    builder.setVotesAllowed(contest.votes_allowed());
    builder.setName(contest.name());
    contest.ballot_selections().forEach(value -> builder.addSelections(convertSelectionDescription(value)));
    if (contest.ballot_title() != null) {
      builder.setBallotTitle(convertInternationalizedText(contest.ballot_title()));
    }
    if (contest.ballot_subtitle() != null) {
      builder.setBallotSubtitle(convertInternationalizedText(contest.ballot_subtitle()));
    }
    builder.addAllPrimaryPartyIds(contest.primary_party_ids());
    return builder.build();
  }

  static ManifestProto.ContestDescription.VoteVariationType convertVoteVariationType(Manifest.VoteVariationType type) {
    return ManifestProto.ContestDescription.VoteVariationType.valueOf(type.name());
  }

  static ManifestProto.Manifest.ElectionType convertElectionType(Manifest.ElectionType type) {
    return ManifestProto.Manifest.ElectionType.valueOf(type.name());
  }

  static ManifestProto.GeopoliticalUnit.ReportingUnitType convertReportingUnitType(Manifest.ReportingUnitType type) {
    return ManifestProto.GeopoliticalUnit.ReportingUnitType.valueOf(type.name());
  }

  static ManifestProto.GeopoliticalUnit convertGeopoliticalUnit(Manifest.GeopoliticalUnit geoUnit) {
    ManifestProto.GeopoliticalUnit.Builder builder = ManifestProto.GeopoliticalUnit.newBuilder();
    builder.setGeopoliticalUnitId(geoUnit.object_id());
    builder.setName(geoUnit.name());
    builder.setType(convertReportingUnitType(geoUnit.type()));
    if (geoUnit.contact_information() != null) {
      builder.setContactInformation(convertContactInformation(geoUnit.contact_information()));
    }
    return builder.build();
  }

  static ManifestProto.InternationalizedText convertInternationalizedText(Manifest.InternationalizedText text) {
    ManifestProto.InternationalizedText.Builder builder = ManifestProto.InternationalizedText.newBuilder();
    if (text.text() != null) {
      text.text().forEach(value -> builder.addText(convertLanguage(value)));
    }
    return builder.build();
  }

  static ManifestProto.Language convertLanguage(Manifest.Language text) {
    ManifestProto.Language.Builder builder = ManifestProto.Language.newBuilder();
    builder.setValue(text.value());
    builder.setLanguage(text.language());
    return builder.build();
  }

  static ManifestProto.Party convertParty(Manifest.Party party) {
    ManifestProto.Party.Builder builder = ManifestProto.Party.newBuilder();
    builder.setPartyId(party.object_id());
    if (party.name() != null) {
      builder.setName(convertInternationalizedText(party.name()));
    }
    if (party.abbreviation() != null) {
      builder.setAbbreviation(party.abbreviation());
    }
    if (party.color() != null) {
      builder.setColor(party.color());
    }
    if (party.logo_uri() != null) {
      builder.setLogoUri(party.logo_uri());
    }
    return builder.build();
  }

  static ManifestProto.SelectionDescription convertSelectionDescription(Manifest.SelectionDescription selection) {
    ManifestProto.SelectionDescription.Builder builder = ManifestProto.SelectionDescription.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setCandidateId(selection.candidate_id());
    builder.setSequenceOrder(selection.sequence_order());
    return builder.build();
  }
}
