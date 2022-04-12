package com.sunya.electionguard.input;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.Manifest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManifestInputBuilder {
  public static final String districtDefault = "district";
  public static final String styleDefault = "styling";

  private final String manifest_name;
  private final ArrayList<ContestBuilder> contests = new ArrayList<>();
  private final Map<String, Manifest.Candidate> candidates = new HashMap<>();
  private String style = styleDefault;
  List<Manifest.GeopoliticalUnit> districts = new ArrayList<>();
  List<Manifest.BallotStyle> ballotStyles = new ArrayList<>();

  public ManifestInputBuilder(String manifest_name) {
    this.manifest_name = manifest_name;
  }

  ManifestInputBuilder addGpunit(String gpunitName) {
    districts.add(new Manifest.GeopoliticalUnit(gpunitName, "name", Manifest.ReportingUnitType.congressional, null));
    return this;
  }

  ManifestInputBuilder setDefaultStyle(String style) {
    this.style = style;
    return this;
  }

  ManifestInputBuilder addStyle(String style, String... gpunits) {
    for (String gpunit : gpunits) {
      districts.add(new Manifest.GeopoliticalUnit(gpunit, "name", Manifest.ReportingUnitType.congressional, null));
    }
    ballotStyles.add(new Manifest.BallotStyle(style, Arrays.asList(gpunits), null, null));

    return this;
  }

  public ManifestInputBuilder addCandidateAndParty(String candidate_id, String party) {
    Manifest.Candidate c = new Manifest.Candidate(candidate_id, new Manifest.InternationalizedText(ImmutableList.of()), party, null, null);
    candidates.put(candidate_id, c);
    return this;
  }

  ManifestInputBuilder addBallotStyle(Manifest.BallotStyle ballotStyle) {
    ballotStyles.add(ballotStyle);
    return this;
  }

  public ContestBuilder addContest(String contest_id) {
    ContestBuilder c = new ContestBuilder(contest_id);
    contests.add(c);
    return c;
  }
  public ContestBuilder addContest(String contest_id, int seq) {
    ContestBuilder c = new ContestBuilder(contest_id).setSequence(seq);
    contests.add(c);
    return c;
  }


  public void addCandidate(String candidate_id) {
    Manifest.Candidate c = new Manifest.Candidate(candidate_id);
    candidates.put(candidate_id, c);
  }

  public ManifestInputBuilder removeCandidate(String candidate_id) {
    candidates.remove(candidate_id);
    return this;
  }

  public Manifest build() {
    if (districts.isEmpty()) {
      districts.add(new Manifest.GeopoliticalUnit(districtDefault, "name", Manifest.ReportingUnitType.congressional, null));
    }
    if (ballotStyles.isEmpty()) {
      ballotStyles.add(new Manifest.BallotStyle(style, ImmutableList.of(districtDefault), null, null));
    }

    List<Manifest.Party> parties = ImmutableList.of(new Manifest.Party("dog"), new Manifest.Party("cat"));

    return new Manifest(manifest_name, ElectionContext.SPEC_VERSION, Manifest.ElectionType.general,
            OffsetDateTime.now().toString(), OffsetDateTime.now().toString(),
            districts, parties, candidates.values().stream().toList(),
            contests.stream().map(ContestBuilder::build).toList(),
            ballotStyles, null, null, null);
  }

  private static int contest_seq = 0;
  private static int selection_seq = 0;
  public class ContestBuilder {
    private final String id;
    private int seq = contest_seq++;
    private final ArrayList<SelectionBuilder> selections = new ArrayList<>();
    private Manifest.VoteVariationType type = Manifest.VoteVariationType.one_of_m;
    private int allowed = 1;
    private String district = districtDefault;
    private String name = "name";

    ContestBuilder(String id) {
      this.id = id;
    }

    ContestBuilder setVoteVariationType(Manifest.VoteVariationType type, int allowed) {
      Preconditions.checkArgument(allowed > 0);
      this.type = type;
      this.allowed = type == Manifest.VoteVariationType.one_of_m ? 1 : allowed;
      return this;
    }

    ContestBuilder setGpunit(String gpunit) {
      this.district = gpunit;
      return this;
    }

    ContestBuilder setSequence(int seq) {
      this.seq = seq;
      return this;
    }

    public ContestBuilder addSelection(String id, String candidate_id) {
      SelectionBuilder s = new SelectionBuilder(id, candidate_id);
      selections.add(s);
      addCandidate(candidate_id);
      return this;
    }

    public ContestBuilder addSelection(String id, String candidate_id, int seq) {
      SelectionBuilder s = new SelectionBuilder(id, candidate_id).setSequence(seq);
      selections.add(s);
      addCandidate(candidate_id);
      return this;
    }

    public ManifestInputBuilder done() {
      return ManifestInputBuilder.this;
    }

    public Manifest.ContestDescription build() {

      Preconditions.checkArgument(selections.size() > 0);
      if (type == Manifest.VoteVariationType.approval) {
        allowed = selections.size();
      }
      if (type == Manifest.VoteVariationType.n_of_m) {
        Preconditions.checkArgument(allowed <= selections.size());
      }

      // String contestId,
      //                              String electoral_district_id,
      //                              int sequence_order,
      //                              VoteVariationType vote_variation,
      //                              int number_elected,
      //                              int votes_allowed,
      //                              String name,
      //                              List<SelectionDescription> ballot_selections,
      //                              @Nullable InternationalizedText ballot_title,
      //                              @Nullable InternationalizedText ballot_subtitle
      return new Manifest.ContestDescription(id, seq, district,
              type, allowed, allowed, name,
              selections.stream().map(SelectionBuilder::build).toList(),
              null, null, ImmutableList.of(), null);
    }

    public class SelectionBuilder {
      private final String id;
      private final String candidate_id;
      private int seq = selection_seq++;

      SelectionBuilder(String id, String candidate_id) {
        this.id = id;
        this.candidate_id = candidate_id;
      }

      SelectionBuilder setSequence(int seq) {
        this.seq = seq;
        return this;
      }

      Manifest.SelectionDescription build() {
        return new Manifest.SelectionDescription(id, seq, candidate_id, null);
      }
    }
  }

}
