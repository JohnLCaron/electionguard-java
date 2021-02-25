package com.sunya.electionguard.input;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Election;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ElectionInputBuilder {
  public static final String districtDef = "district";
  public static final String styleDef = "styling";

  private final String election_scope_id;
  private final ArrayList<ContestBuilder> contests = new ArrayList<>();
  private String style = styleDef;
  private Election.BallotStyle ballotStyle;
  private String district = districtDef;

  ElectionInputBuilder(String election_scope_id) {
    this.election_scope_id = election_scope_id;
  }

  ElectionInputBuilder setGpunit(String gpunit) {
    this.district = gpunit;
    return this;
  }

  ElectionInputBuilder setStyle(String style) {
    this.style = style;
    return this;
  }

  ElectionInputBuilder setBallotStyle(Election.BallotStyle ballotStyle) {
    this.ballotStyle = ballotStyle;
    return this;
  }

  ContestBuilder addContest(String contest_id) {
    ContestBuilder c = new ContestBuilder(contest_id);
    contests.add(c);
    return c;
  }

  Election build() {
    Election.GeopoliticalUnit gpUnit = new Election.GeopoliticalUnit(district, "name", Election.ReportingUnitType.congressional, null);
    Election.BallotStyle ballotStyle = this.ballotStyle != null ? this.ballotStyle :
            new Election.BallotStyle(style, ImmutableList.of(district), null, null);

    List<Election.Party> parties = ImmutableList.of(new Election.Party("dog"), new Election.Party("cat"));
    List<Election.Candidate> candidates = ImmutableList.of(new Election.Candidate("candidate_1"), new Election.Candidate("candidate_2"));

    // String election_scope_id,
    //                  ElectionType type,
    //                  OffsetDateTime start_date,
    //                  OffsetDateTime end_date,
    //                  List<GeopoliticalUnit> geopolitical_units,
    //                  List<Party> parties,
    //                  List<Candidate> candidates,
    //                  List<ContestDescription> contests,
    //                  List<BallotStyle> ballot_styles,
    //                  @Nullable InternationalizedText name,
    //                  @Nullable ContactInformation contact_information
    return new Election(election_scope_id, Election.ElectionType.general, OffsetDateTime.now(), OffsetDateTime.now(),
            ImmutableList.of(gpUnit), parties, candidates,
            contests.stream().map(ContestBuilder::build).collect(Collectors.toList()),
            ImmutableList.of(ballotStyle), null, null);
  }

  private static int contest_seq = 0;
  private static int selection_seq = 0;
  public class ContestBuilder {
    private final String id;
    private final int seq = contest_seq++;
    private final ArrayList<SelectionBuilder> selections = new ArrayList<>();
    private int allowed = 1;
    private String district = districtDef;

    ContestBuilder(String id) {
      this.id = id;
    }

    ContestBuilder setAllowedVotes(int allowed) {
      this.allowed = allowed;
      return this;
    }

    ContestBuilder setGpunit(String gpunit) {
      this.district = gpunit;
      return this;
    }

    ContestBuilder addSelection(String id, String candidate_id) {
      SelectionBuilder s = new SelectionBuilder(id, candidate_id);
      selections.add(s);
      return this;
    }

    ElectionInputBuilder done() {
      return ElectionInputBuilder.this;
    }

    Election.ContestDescription build() {
      // String object_id,
      //                              String electoral_district_id,
      //                              int sequence_order,
      //                              VoteVariationType vote_variation,
      //                              int number_elected,
      //                              int votes_allowed,
      //                              String name,
      //                              List<SelectionDescription> ballot_selections,
      //                              @Nullable InternationalizedText ballot_title,
      //                              @Nullable InternationalizedText ballot_subtitle
      return new Election.ContestDescription(id, district, seq, Election.VoteVariationType.one_of_m,
              allowed, allowed, "name",
              selections.stream().map(SelectionBuilder::build).collect(Collectors.toList()),
              null, null);
    }

    public class SelectionBuilder {
      private final String id;
      private final String candidate_id;
      private final int seq = selection_seq++;

      SelectionBuilder(String id, String candidate_id) {
        this.id = id;
        this.candidate_id = candidate_id;
      }

      Election.SelectionDescription build() {
        return new Election.SelectionDescription(id, candidate_id, seq);
      }
    }
  }

}
