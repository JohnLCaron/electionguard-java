package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * The Manifest: defines the candidates, contests, and associated information for a specific election.
 *
 * @see <a href="https://developers.google.com/elections-data/reference/election">Civics Common Standard Data Specification</a>
 */
public record Manifest(
        String electionScopeId,
        String specVersion,
        ElectionType electionType,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        List<GeopoliticalUnit> geopoliticalUnits,
        List<Party> parties,
        List<Candidate> candidates,
        List<ContestDescription> contests,
        List<BallotStyle> ballotStyles,
        @Nullable InternationalizedText name,
        @Nullable ContactInformation contactInformation,
        Group.ElementModQ cryptoHash
) implements Hash.CryptoHashable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public Manifest {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(electionScopeId));
    Preconditions.checkNotNull(electionType);
    Preconditions.checkNotNull(startDate);
    Preconditions.checkNotNull(endDate);
    if (Strings.isNullOrEmpty(specVersion)) {
      specVersion = ElectionContext.SPEC_VERSION;
    }
    geopoliticalUnits = toImmutableListEmpty(geopoliticalUnits);
    parties = toImmutableListEmpty(parties);
    candidates = toImmutableListEmpty(candidates);
    contests = toImmutableListEmpty(contests);
    ballotStyles = toImmutableListEmpty(ballotStyles);
    if (cryptoHash == null) {
      cryptoHash = recalcCryptoHash(
              electionScopeId,
              electionType,
              startDate,
              endDate,
              geopoliticalUnits,
              parties,
              candidates,
              contests,
              ballotStyles,
              name,
              contactInformation);
    }
  }

  public static Group.ElementModQ recalcCryptoHash(String electionScopeId,
                                                   ElectionType electionType,
                                                   OffsetDateTime startDate,
                                                   OffsetDateTime endDate,
                                                   List<GeopoliticalUnit> geopoliticalUnits,
                                                   List<Party> parties,
                                                   List<Candidate> candidates,
                                                   List<ContestDescription> contests,
                                                   List<BallotStyle> ballotStyles,
                                                   @Nullable InternationalizedText name,
                                                   @Nullable ContactInformation contactInformation) {
    return Hash.hash_elems(
            electionScopeId,
            electionType.name(),
            startDate.format(Utils.dtf), // python: to_iso_date_string, eg 2020-03-01T13:00:00Z
            endDate.format(Utils.dtf),
            name,
            contactInformation,
            geopoliticalUnits,
            parties,
            // candidates,
            contests,
            ballotStyles);
  }

  /**
   * Verifies the dataset to ensure it is well-formed.
   */
  public boolean is_valid() {
    HashSet<String> gp_unit_ids = new HashSet<>();
    HashSet<String> ballot_style_ids = new HashSet<>();
    HashSet<String> party_ids = new HashSet<>();
    HashSet<String> candidate_ids = new HashSet<>();
    HashSet<String> contest_ids = new HashSet<>();

    // Validate GP Units
    for (GeopoliticalUnit gp_unit : this.geopoliticalUnits) {
      gp_unit_ids.add(gp_unit.geopoliticalUnitId);
    }
    // fail if there are duplicates
    boolean geopolitical_units_valid = gp_unit_ids.size() == this.geopoliticalUnits.size();

    // Validate Ballot Styles
    boolean ballot_styles_have_valid_gp_unit_ids = true;
    for (BallotStyle style : this.ballotStyles) {
      ballot_style_ids.add(style.ballotStyleId);

      if (style.geopoliticalUnitIds.isEmpty()) {
        ballot_styles_have_valid_gp_unit_ids = false;
        break;
      }
      // validate associated gp unit ids
      for (String gp_unit_id : style.geopoliticalUnitIds) {
        ballot_styles_have_valid_gp_unit_ids &= gp_unit_ids.contains(gp_unit_id);
      }
    }

    boolean ballot_styles_valid = (ballot_style_ids.size() == this.ballotStyles.size() &&
            ballot_styles_have_valid_gp_unit_ids);

    // Validate Parties
    for (Party party : this.parties) {
      party_ids.add(party.partyId);
    }
    boolean parties_valid = party_ids.size() == this.parties.size();

    // Validate Candidates
    boolean candidates_have_valid_party_ids = true;
    for (Candidate candidate : this.candidates) {
      candidate_ids.add(candidate.candidateId);
      // validate the associated party id
      candidates_have_valid_party_ids &= (candidate.partyId == null) || party_ids.contains(candidate.partyId);
    }

    boolean candidates_have_valid_length = candidate_ids.size() == this.candidates.size();
    boolean candidates_valid = (candidates_have_valid_length && candidates_have_valid_party_ids);

    // Validate Contests
    boolean contests_validate_their_properties = true;
    boolean contests_have_valid_electoral_district_id = true;
    boolean candidate_contests_have_valid_party_ids = true;

    HashSet<Integer> contest_sequence_ids = new HashSet<>();

    for (ContestDescription contest : this.contests) {
      contests_validate_their_properties &= contest.is_valid();

      contest_ids.add(contest.contestId);
      contest_sequence_ids.add(contest.sequenceOrder);

      // validate the associated gp unit id
      contests_have_valid_electoral_district_id &= gp_unit_ids.contains(contest.geopoliticalUnitId);

      if (contest.primaryPartyIds != null) {
        for (String primary_party_id : contest.primaryPartyIds) {
          // validate the party ids
          candidate_contests_have_valid_party_ids &= party_ids.contains(primary_party_id);
        }
      }
    }

    // TODO: ISSUE //55: verify that the contest sequence order set is in the proper order
    boolean contests_have_valid_object_ids = contest_ids.size() == this.contests.size();
    boolean contests_have_valid_sequence_ids = contest_sequence_ids.size() == this.contests.size();
    boolean contests_valid = (
            contests_have_valid_object_ids
                    && contests_have_valid_sequence_ids
                    && contests_validate_their_properties
                    && contests_have_valid_electoral_district_id
                    && candidate_contests_have_valid_party_ids
    );

    boolean success = (
            geopolitical_units_valid
                    && ballot_styles_valid
                    && parties_valid
                    && candidates_valid
                    && contests_valid
    );

    if (!success) {
      logger.atWarning().log(
              "Manifest failed validation check: is_valid: ",
              "geopolitical_units_valid", geopolitical_units_valid,
              "ballot_styles_valid", ballot_styles_valid,
              "ballot_styles_have_valid_gp_unit_ids", ballot_styles_have_valid_gp_unit_ids,
              "parties_valid", parties_valid,
              "candidates_valid", candidates_valid,
              "candidates_have_valid_length", candidates_have_valid_length,
              "candidates_have_valid_party_ids", candidates_have_valid_party_ids,
              "contests_valid", contests_valid,
              "contests_have_valid_object_ids", contests_have_valid_object_ids,
              "contests_have_valid_sequence_ids", contests_have_valid_sequence_ids,
              "contests_validate_their_properties", contests_validate_their_properties,
              "contests_have_valid_electoral_district_id", contests_have_valid_electoral_district_id,
              "candidate_contests_have_valid_party_ids", candidate_contests_have_valid_party_ids);
    }
    return success;
  }

  /**
   * The type of election.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/election-type">Civics Common Standard Data Specification</a>
   */
  public enum ElectionType {
    unknown,
    /**
     * For an election held typically on the national day for elections.
     */
    general,
    /**
     * For a primary election that is for a specific party where voter eligibility is based on registration.
     */
    partisan_primary_closed,
    /**
     * For a primary election that is for a specific party where voter declares desired party or chooses in private.
     */
    partisan_primary_open,
    /**
     * For a primary election without a specified type, such as a nonpartisan primary.
     */
    primary,
    /**
     * For an election to decide a prior contest that ended with no candidate receiving a majority of the votes.
     */
    runoff,
    /**
     * For an election held out of sequence for special circumstances, for example, to fill a vacated office.
     */
    special,
    /**
     * Used when the election type is not listed in this enumeration. If used, include a specific value of the OtherType element.
     */
    other
  }

  /**
   * The type of geopolitical unit.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/reporting-unit-type">Civics Common Standard Data Specification</a>
   */
  public enum ReportingUnitType {
    unknown,
    /**
     * Used to report batches of ballots that might cross precinct boundaries.
     */
    ballot_batch,
    /**
     * Used for a ballot-style area that's generally composed of precincts.
     */
    ballot_style_area,
    /**
     * Used as a synonym for a county.
     */
    borough,
    /**
     * Used for a city that reports results or for the district that encompasses it.
     */
    city,
    /**
     * Used for city council districts.
     */
    city_council,
    /**
     * Used for one or more precincts that have been combined for the purposes of reporting. If the term ward is
     * used interchangeably with combined precinct, use combined-precinct for the ReportingUnitType.
     */
    combined_precinct,
    /**
     * Used for national legislative body districts.
     */
    congressional,
    /**
     * Used for a country.
     */
    country,
    /**
     * Used for a county or for the district that encompasses it. Synonymous with borough and parish in some localities.
     */
    county,
    /**
     * Used for county council districts.
     */
    county_council,
    /**
     * Used for a dropbox for absentee ballots.
     */
    drop_box,
    /**
     * Used for judicial districts.
     */
    judicial,
    /**
     * Used as applicable for various units such as towns, townships, villages that report votes, or for the
     * district that encompasses them.
     */
    municipality,
    /**
     * Used for a polling place.
     */
    polling_place,
    /**
     * Used if the terms for ward or district are used interchangeably with precinct.
     */
    precinct,
    /**
     * Used for a school district.
     */
    school,
    /**
     * Used for a special district.
     */
    special,
    /**
     * Used for splits of precincts.
     */
    split_precinct,
    /**
     * Used for a state or for the district that encompasses it.
     */
    state,
    /**
     * Used for a state house or assembly district.
     */
    state_house,
    /**
     * Used for a state senate district.
     */
    state_senate,
    /**
     * Used for type of municipality that reports votes or for the district that encompasses it.
     */
    town,
    /**
     * Used for type of municipality that reports votes or for the district that encompasses it.
     */
    township,
    /**
     * Used for a utility district.
     */
    utility,
    /**
     * Used for a type of municipality that reports votes or for the district that encompasses it.
     */
    village,
    /**
     * Used for a vote center.
     */
    vote_center,
    /**
     * Used for combinations or groupings of precincts or other units.
     */
    ward,
    /**
     * Used for a water district.
     */
    water,
    /**
     * Used for other types of reporting units that aren't included in this enumeration.
     * If used, provide the item's custom type in an OtherType element.
     */
    other
  }

  /**
   * Enumeration for contest algorithm or rules in the contest.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/vote-variation">Civics Common Standard Data Specification</a>
   */
  public enum VoteVariationType {
    /**
     * Each voter can select up to one option.
     */
    one_of_m,
    /**
     * Approval voting, where each voter can select as many options as desired.
     */
    approval,
    /**
     * Borda count, where each voter can rank the options, and the rankings are assigned point values.
     */
    borda,
    /**
     * Cumulative voting, where each voter can distribute their vote to up to N options.
     */
    cumulative,
    /**
     * A 1-of-m method where the winner needs more than 50% of the vote to be elected.
     */
    majority,
    /**
     * A method where each voter can select up to N options.
     */
    n_of_m,
    /**
     * A 1-of-m method where the option with the most votes is elected, regardless of whether the option has
     * more than 50% of the vote.
     */
    plurality,
    /**
     * A proportional representation method, which is any system that elects winners in proportion to the total vote.
     * For the single transferable vote (STV) method, use rcv instead.
     */
    proportional,
    /**
     * Range voting, where each voter can select a score for each option.
     */
    range,
    /**
     * Ranked choice voting (RCV), where each voter can rank the options, and the ballots are counted in rounds.
     * Also known as instant-runoff voting (IRV) and the single transferable vote (STV).
     */
    rcv,
    /**
     * A 1-of-m method where the winner needs more than some predetermined fraction of the vote to be elected,
     * and where the fraction is more than 50%. For example, the winner might need three-fifths or two-thirds of the vote.
     */
    super_majority,
    /**
     * The vote variation is a type that isn't included in this enumeration. If used, provide the item's custom type
     * in an OtherType element.
     */
    other
  }

  /**
   * An annotated character string.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/annotated-string">Civics Common Standard Data Specification</a>
   */
  public record AnnotatedString(
          String annotation,
          String value
  ) implements Hash.CryptoHashable {

    public AnnotatedString {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(annotation));
      Preconditions.checkArgument(!Strings.isNullOrEmpty(value));
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.annotation, this.value);
    }
  }


  /**
   * Classifies a set of contests by their set of parties and geopolitical units
   *
   * @param ballotStyleId       A unique name
   * @param geopoliticalUnitIds matches GeoPoliticalUnit.object_id; may be empty
   * @param partyIds            matches Party.object_id; may be empty
   * @param imageUri            an optional image_uri for this BallotStyle
   */
  public record BallotStyle(
          String ballotStyleId,
          List<String> geopoliticalUnitIds,
          List<String> partyIds,
          @Nullable String imageUri
  ) implements Hash.CryptoHashable {

    public BallotStyle {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(ballotStyleId));
      geopoliticalUnitIds = toImmutableListEmpty(geopoliticalUnitIds);
      partyIds = toImmutableListEmpty(partyIds);
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.ballotStyleId, this.geopoliticalUnitIds, this.partyIds, this.imageUri);
    }
  }

  /**
   * A candidate in a contest.
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that selections for any contest type
   * are considered a "candidate". for instance, on a yes-no referendum contest, two `candidate` objects
   * would be included in the model to represent the `affirmative` and `negative` selections for the contest.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/candidate">Civics Common Standard Data Specification</a>
   */
  public record Candidate(
          String candidateId,
          InternationalizedText name,
          @Nullable String partyId,
          @Nullable String imageUri,
          Boolean isWriteIn
  ) implements Hash.CryptoHashable {

    /**
     * A Candidate with only an object_id.
     */
    public Candidate(String object_id) {
      this(object_id, new InternationalizedText(ImmutableList.of()), null, null, false);
    }

    /**
     * A Candidate with an object_id and a name, and optional other fields.
     */
    public Candidate {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(candidateId));
      Preconditions.checkNotNull(name);
      if (isWriteIn == null) {
        isWriteIn = false;
      }
    }

    String get_candidate_id() {
      return this.candidateId;
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(
              this.candidateId, this.name, this.partyId, this.imageUri);
    }
  }

  /**
   * Contact information about persons, boards of authorities, organizations, etc.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/contact-information">Civics Common Standard Data Specification</a>
   */
  public record ContactInformation(
          List<String> addressLine,
          List<AnnotatedString> email,
          List<AnnotatedString> phone,
          @Nullable String name
  ) implements Hash.CryptoHashable {

    public ContactInformation {
      addressLine = toImmutableListEmpty(addressLine);
      email = toImmutableListEmpty(email);
      phone = toImmutableListEmpty(phone);
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.name, this.addressLine, this.email, this.phone);
    }
  }

  /**
   * A physical or virtual unit of representation or vote/seat aggregation.
   * Use this entity to define geopolitical units such as cities, districts, jurisdictions, or precincts
   * to associate contests, offices, vote counts, or other information with those geographies.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/gp-unit">Civics Common Standard Data Specification</a>
   */
  public record GeopoliticalUnit(
          String geopoliticalUnitId,
          String name,
          ReportingUnitType type,
          @Nullable ContactInformation contactInformation
  ) implements Hash.CryptoHashable {

    public GeopoliticalUnit {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(geopoliticalUnitId));
      Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
      Preconditions.checkNotNull(type);
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.geopoliticalUnitId, this.name, this.type.name(), this.contactInformation);
    }
  }

  /**
   * Text that may have translations in multiple languages.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/internationalized-text">Civics Common Standard Data Specification</a>
   */
  public record InternationalizedText(
          List<Language> text
  ) implements Hash.CryptoHashable {

    public InternationalizedText {
      text = toImmutableListEmpty(text);
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.text);
    }
  }

  /**
   * The ISO-639 language code.
   *
   * @see <a href="https://en.wikipedia.org/wiki/ISO_639">ISO 639</a>
   */
  public record Language(
          String value,
          String language
  ) implements Hash.CryptoHashable {

    public Language {
      Preconditions.checkNotNull(value);
      Preconditions.checkNotNull(language);
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(this.value, this.language);
    }
  }

  /**
   * A political party.
   *
   * @see <a href="https://developers.google.com/elections-data/reference/party">Civics Common Standard Data Specification</a>
   */
  public record Party(
          String partyId,
          InternationalizedText name,
          @Nullable String abbreviation,
          @Nullable String color,
          @Nullable String logoUri
  ) implements Hash.CryptoHashable {

    /**
     * A Party with only an object_id.
     */
    public Party(String object_id) {
      this(object_id, new InternationalizedText(ImmutableList.of()), null, null, null);
    }

    public Party {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(partyId));
      Preconditions.checkNotNull(name);
    }

    String get_party_id() {
      return this.partyId;
    }

    @Override
    public Group.ElementModQ cryptoHash() {
      return Hash.hash_elems(
              this.partyId,
              this.name,
              this.abbreviation,
              this.color,
              this.logoUri);
    }
  }

  /**
   * The metadata that describes the structure and type of one contest in the election.
   *
   * @param contestId      Contest id, must be unique.
   * @param sequenceOrder  Used for ordering contests in a ballot to ensure various encryption primitives are deterministic.
   * @param numberElected  Number of candidates that are elected in the contest ("n" of n-of-m).
   * @param votesAllowed   Maximum number of selections per voter in this contest.
   * @param name           Name of the contest, not necessarily as it appears on the ballot
   * @param selections     The possible ballot selections for the contest.
   * @param ballotTitle    Title of the contest as it appears on the ballot.
   * @param ballotSubtitle Subtitle of the contest as it appears on the ballot.
   * @see <a href="https://developers.google.com/elections-data/reference/contest">Civics Common Standard Data Specification</a>
   */
  public record ContestDescription(
          String contestId,
          int sequenceOrder,
          String geopoliticalUnitId,
          VoteVariationType voteVariation,
          int numberElected,
          Integer votesAllowed,
          String name,
          List<SelectionDescription> selections,
          @Nullable InternationalizedText ballotTitle,
          @Nullable InternationalizedText ballotSubtitle,
          @Nullable List<String> primaryPartyIds, // match Party.party_id, only in CandidateContestDescription
          Group.ElementModQ cryptoHash
  ) implements Hash.CryptoHashable {

    public ContestDescription {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(contestId));
      Preconditions.checkArgument(!Strings.isNullOrEmpty(geopoliticalUnitId));
      Preconditions.checkNotNull(voteVariation);
      Preconditions.checkNotNull(votesAllowed);
      Preconditions.checkNotNull(name);
      selections = toImmutableListEmpty(selections);
      primaryPartyIds = toImmutableListEmpty(primaryPartyIds);
      if (cryptoHash == null) {
        cryptoHash = recalcCryptoHash(
                contestId,
                sequenceOrder,
                geopoliticalUnitId,
                voteVariation,
                numberElected,
                votesAllowed,
                name,
                selections,
                ballotTitle,
                ballotSubtitle,
                primaryPartyIds
        );
      }
    }

    public static Group.ElementModQ recalcCryptoHash(
            String contestId,
            int sequenceOrder,
            String geopoliticalUnitId,
            VoteVariationType voteVariation,
            int numberElected,
            Integer votesAllowed,
            String name,
            List<SelectionDescription> selections,
            @Nullable InternationalizedText ballotTitle,
            @Nullable InternationalizedText ballotSubtitle,
            @Nullable List<String> primaryPartyIds) {
      return Hash.hash_elems(
              contestId,
              sequenceOrder,
              geopoliticalUnitId,
              voteVariation.name(),
              ballotTitle,
              ballotSubtitle,
              name,
              numberElected,
              votesAllowed,
              selections);
      // this.primaryPartyIds);
    }

    @Override
    public String toString() {
      return "ContestDescription{" +
              "\n contestId='" + contestId + '\'' +
              "\n sequenceOrder=" + sequenceOrder +
              "\n contestHash=" + cryptoHash() +
              "\n geopoliticalUnitId='" + geopoliticalUnitId + '\'' +
              "\n voteVariation=" + voteVariation +
              "\n numberElected=" + numberElected +
              "\n votesAllowed=" + votesAllowed +
              "\n name='" + name + '\'' +
              "\n selections=" + selections.size() +
              "\n ballotTitle=" + ballotTitle +
              "\n ballotSubtitle=" + ballotSubtitle +
              "\n primaryPartyIds=" + primaryPartyIds +
              '}';
    }

    /**
     * Check the validity of the contest object by verifying its data.
     */
    boolean is_valid() {
      boolean contest_has_valid_number_elected = this.numberElected <= this.selections.size();
      boolean contest_has_valid_votes_allowed = this.numberElected <= this.votesAllowed;

      //  verify the candidate_ids, selection object_ids, and sequence_ids are unique
      HashSet<String> candidate_ids = new HashSet<>();
      HashSet<String> selection_ids = new HashSet<>();
      HashSet<Integer> sequence_ids = new HashSet<>();

      int expected_selection_count = this.selections.size();

      // count unique ids
      for (SelectionDescription selection : this.selections) {
        //  validate the object_id
        selection_ids.add(selection.selectionId);
        //  validate the sequence_order
        sequence_ids.add(selection.sequenceOrder);
        //  validate the candidate id
        candidate_ids.add(selection.candidateId);
      }

      boolean selections_have_valid_candidate_ids = candidate_ids.size() == expected_selection_count;
      boolean selections_have_valid_selection_ids = selection_ids.size() == expected_selection_count;
      boolean selections_have_valid_sequence_ids = sequence_ids.size() == expected_selection_count;

      boolean success = contest_has_valid_number_elected && contest_has_valid_votes_allowed &&
              selections_have_valid_candidate_ids && selections_have_valid_selection_ids && selections_have_valid_sequence_ids;

      if (!success) {
        logger.atWarning().log(
                "Contest %s failed validation check: %s", this.contestId,
                String.format("contest_has_valid_number_elected %s%n" +
                                "contest_has_valid_votes_allowed %s%n" +
                                "selections_have_valid_candidate_ids %s%n" +
                                "selections_have_valid_selection_ids %s%n" +
                                "selections_have_valid_sequence_ids %s%n",
                        contest_has_valid_number_elected, contest_has_valid_votes_allowed, selections_have_valid_candidate_ids,
                        selections_have_valid_selection_ids, selections_have_valid_sequence_ids));
      }

      return success;
    }
  }

  /**
   * A ballot selection for a specific candidate in a contest.
   *
   * @param selectionId   Contest id, must be unique across all contests.
   * @param sequenceOrder Used for ordering selections in a contest to ensure various encryption primitives are deterministic.
   *                      Unique within the contest, consider making unique across the ballot.
   * @param candidateId   Candidate id, must match a Candidate.candidateId.
   * @see <a href="https://developers.google.com/elections-data/reference/ballot-selection">Civics Common Standard Data Specification</a>
   */
  public record SelectionDescription(
          String selectionId,
          int sequenceOrder,
          String candidateId,
          Group.ElementModQ cryptoHash
  ) implements Hash.CryptoHashable {

    public SelectionDescription {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(selectionId));
      Preconditions.checkArgument(!Strings.isNullOrEmpty(candidateId));
      if (cryptoHash == null) {
        cryptoHash = recalcCryptoHash(selectionId, sequenceOrder, candidateId);
      }
    }

    public static Group.ElementModQ recalcCryptoHash(String selectionId,
                                                     int sequenceOrder,
                                                     String candidateId) {
      return Hash.hash_elems(selectionId, sequenceOrder, candidateId);
    }

    @Override
    public String toString() {
      return "SelectionDescription{" +
              "\n selectionId='" + selectionId + '\'' +
              "\n sequenceOrder=" + sequenceOrder +
              "\n candidateId='" + candidateId + '\'' +
              "\n selectionHash='" + cryptoHash() + '\'' +
              '}';
    }
  }

  private static <T> ImmutableList<T> toImmutableListEmpty(List<T> from) {
    if (from == null || from.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(from);
  }
}