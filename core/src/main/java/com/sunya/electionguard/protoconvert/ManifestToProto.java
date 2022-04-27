package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.Manifest;

import electionguard.protogen.ManifestProto;

public class ManifestToProto {

  public static ManifestProto.Manifest publishManifest(Manifest election) {
    ManifestProto.Manifest.Builder builder = ManifestProto.Manifest.newBuilder();
    builder.setElectionScopeId(election.electionScopeId());
    if (election.specVersion() != null) {
      builder.setSpecVersion(election.specVersion());
    }
    builder.setElectionType(convertElectionType(election.electionType()));
    builder.setStartDate(election.startDate());
    builder.setEndDate(election.endDate());

    election.geopoliticalUnits().forEach(value -> builder.addGeopoliticalUnits(convertGeopoliticalUnit(value)));
    election.parties().forEach(value -> builder.addParties(convertParty(value)));
    election.candidates().forEach(value -> builder.addCandidates(convertCandidate(value)));
    election.contests().forEach(value -> builder.addContests(convertContestDescription(value)));
    election.ballotStyles().forEach(value -> builder.addBallotStyles(convertBallotStyle(value)));

    if (election.name() != null) {
      builder.setName(convertInternationalizedText(election.name()));
    }
    if (election.contactInformation() != null) {
      builder.setContactInformation(convertContactInformation(election.contactInformation()));
    }
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(election.cryptoHash()));

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
    builder.setBallotStyleId(style.ballotStyleId());
    builder.addAllGeopoliticalUnitIds(style.geopoliticalUnitIds());
    builder.addAllPartyIds(style.partyIds());
    if (style.imageUri() != null) {
      builder.setImageUrl(style.imageUri());
    }
    return builder.build();
  }

  static ManifestProto.Candidate convertCandidate(Manifest.Candidate candidate) {
    ManifestProto.Candidate.Builder builder = ManifestProto.Candidate.newBuilder();
    builder.setCandidateId(candidate.candidateId());
    builder.setName(convertInternationalizedText(candidate.name()));
    if (candidate.partyId() != null) {
      builder.setPartyId(candidate.partyId());
    }
    if (candidate.imageUri() != null) {
      builder.setImageUrl(candidate.imageUri());
    }
    builder.setIsWriteIn(candidate.isWriteIn());
    return builder.build();
  }

  static ManifestProto.ContactInformation convertContactInformation(Manifest.ContactInformation contact) {
    ManifestProto.ContactInformation.Builder builder = ManifestProto.ContactInformation.newBuilder();
    if (contact.name() != null) {
      builder.setName(contact.name());
    }
    builder.addAllAddressLine(contact.addressLine());
    contact.email().forEach(value -> builder.addEmail(convertAnnotatedString(value)));
    contact.phone().forEach(value -> builder.addPhone(convertAnnotatedString(value)));
    return builder.build();
  }

  static ManifestProto.ContestDescription convertContestDescription(Manifest.ContestDescription contest) {
    ManifestProto.ContestDescription.Builder builder = ManifestProto.ContestDescription.newBuilder();
    builder.setContestId(contest.contestId());
    builder.setGeopoliticalUnitId(contest.geopoliticalUnitId());
    builder.setSequenceOrder(contest.sequenceOrder());
    builder.setVoteVariation(convertVoteVariationType(contest.voteVariation()));
    builder.setNumberElected(contest.numberElected());
    builder.setVotesAllowed(contest.votesAllowed());
    builder.setName(contest.name());
    contest.selections().forEach(value -> builder.addSelections(convertSelectionDescription(value)));
    if (contest.ballotTitle() != null) {
      builder.setBallotTitle(convertInternationalizedText(contest.ballotTitle()));
    }
    if (contest.ballotSubtitle() != null) {
      builder.setBallotSubtitle(convertInternationalizedText(contest.ballotSubtitle()));
    }
    builder.addAllPrimaryPartyIds(contest.primaryPartyIds());
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(contest.cryptoHash()));
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
    builder.setGeopoliticalUnitId(geoUnit.geopoliticalUnitId());
    builder.setName(geoUnit.name());
    builder.setType(convertReportingUnitType(geoUnit.type()));
    if (geoUnit.contactInformation() != null) {
      builder.setContactInformation(convertContactInformation(geoUnit.contactInformation()));
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
    builder.setPartyId(party.partyId());
    if (party.name() != null) {
      builder.setName(convertInternationalizedText(party.name()));
    }
    if (party.abbreviation() != null) {
      builder.setAbbreviation(party.abbreviation());
    }
    if (party.color() != null) {
      builder.setColor(party.color());
    }
    if (party.logoUri() != null) {
      builder.setLogoUri(party.logoUri());
    }
    return builder.build();
  }

  static ManifestProto.SelectionDescription convertSelectionDescription(Manifest.SelectionDescription selection) {
    ManifestProto.SelectionDescription.Builder builder = ManifestProto.SelectionDescription.newBuilder();
    builder.setSelectionId(selection.selectionId());
    builder.setCandidateId(selection.candidateId());
    builder.setSequenceOrder(selection.sequenceOrder());
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(selection.cryptoHash()));
    return builder.build();
  }
}
