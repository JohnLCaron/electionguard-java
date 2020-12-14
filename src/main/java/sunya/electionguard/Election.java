package sunya.electionguard;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static sunya.electionguard.Group.*;

public class Election {

  /**
   * enumerations for the `ElectionReport` entity
   * see: https://developers.google.com/elections-data/reference/election-type
   */
  enum ElectionType {
    unknown,
    general,
    partisan_primary_closed,
    partisan_primary_open,
    primary,
    runoff,
    special,
    other
  }

  /**
   * Enumeration for the type of geopolitical unit
   * see: https://developers.google.com/elections-data/reference/reporting-unit-type
   */
  enum ReportingUnitType {
    unknown,
    ballot_batch,
    ballot_style_area,
    borough,
    city,
    city_council,
    combined_precinct,
    congressional,
    country,
    county,
    county_council,
    drop_box,
    judicial,
    municipality,
    polling_place,
    precinct,
    school,
    special,
    split_precinct,
    state,
    state_house,
    state_senate,
    township,
    utility,
    village,
    vote_center,
    ward,
    water,
    other
  }

  /**
   * Enumeration for contest algorithm or rules in the `Contest` entity
   * see: https://developers.google.com/elections-data/reference/vote-variation
   */
  enum VoteVariationType {
    unknown,
    one_of_m,
    approval,
    borda,
    cumulative,
    majority,
    n_of_m,
    plurality,
    proportional,
    range,
    rcv,
    super_majority,
    other
  }

  /**
   * Use this as a type for character strings.
   * See: https://developers.google.com/elections-data/reference/annotated-string
   */
  @Immutable
  static class AnnotatedString implements Hash.CryptoHashable {
    final String annotation;
    final String value;

    public AnnotatedString(String annotation, String value) {
      this.annotation = annotation;
      this.value = value;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.annotation, this.value);
    }
  }

  /**
   * The ISO-639 language
   * see: https://en.wikipedia.org/wiki/ISO_639
   */
  @Immutable
  static class Language implements Hash.CryptoHashable {
    final String value;
    final String language;

    public Language(String value, String language) {
      this.value = value;
      this.language = language;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.value, this.language);
    }
  }

  /**
   * Data entity used to represent multi-national text. Use when text on a ballot contains multi-national text.
   * See: https://developers.google.com/elections-data/reference/internationalized-text
   */
  @Immutable
  static class InternationalizedText implements Hash.CryptoHashable {
    final ImmutableList<Language> text;

    public InternationalizedText(@Nullable List<Language> text) {
      this.text = text == null ? ImmutableList.of() : ImmutableList.copyOf(text);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.text);
    }
  }

  /**
   * For defining contact information about objects such as persons, boards of authorities, and organizations.
   * See: https://developers.google.com/elections-data/reference/contact-information
   */
  @Immutable
  static class ContactInformation implements Hash.CryptoHashable {
    final Optional<ImmutableList<String>> address_line;
    final Optional<ImmutableList<AnnotatedString>> email;
    final Optional<ImmutableList<AnnotatedString>> phone;
    final Optional<String> name;

    public ContactInformation(@Nullable List<String> address_line,
                              @Nullable List<AnnotatedString> email,
                              @Nullable List<AnnotatedString> phone,
                              @Nullable String name) {
      this.address_line = Optional.ofNullable(toImmutableList(address_line));
      this.email = Optional.ofNullable(toImmutableList(email));
      this.phone = Optional.ofNullable(toImmutableList(phone));
      this.name = Optional.ofNullable(Strings.emptyToNull(name));
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      // TODO hash Optional
      return Hash.hash_elems(this.name, this.address_line, this.email, this.phone);
    }
  }

  static <T> ImmutableList<T> toImmutableList(List<T> from) {
    if (from == null || from.isEmpty()) {
      return null;
    }
    return ImmutableList.copyOf(from);
  }

  /**
   * Use this entity for defining geopolitical units such as cities, districts, jurisdictions, or precincts,
   * for the purpose of associating contests, offices, vote counts, or other information with the geographies.
   * See: https://developers.google.com/elections-data/reference/gp-unit
   */
  @Immutable
  static class GeopoliticalUnit extends ElectionObjectBase implements Hash.CryptoHashable {
    final String name;
    final ReportingUnitType type;
    final Optional<ContactInformation> contact_information;

    public GeopoliticalUnit(String object_id,
                            String name,
                            ReportingUnitType type,
                            @Nullable ContactInformation contact_information) {
      super(object_id);
      this.name = name;
      this.type = type;
      this.contact_information = Optional.ofNullable(contact_information);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.object_id, this.name, this.type.name(), this.contact_information);
    }
  }

  /**
   * A BallotStyle works as a key to uniquely specify a set of contests. See also `ContestDescription`.
   */
  @Immutable
  static class BallotStyle extends ElectionObjectBase implements Hash.CryptoHashable {
    final Optional<ImmutableList<String>> geopolitical_unit_ids;
    final Optional<ImmutableList<String>> party_ids;
    final Optional<String> image_uri;

    public BallotStyle(String object_id,
                       @Nullable List<String> geopolitical_unit_ids,
                       @Nullable List<String> party_ids,
                       @Nullable String image_uri) {
      super(object_id);
      this.geopolitical_unit_ids = Optional.ofNullable(toImmutableList(geopolitical_unit_ids));
      this.party_ids = Optional.ofNullable(toImmutableList(party_ids));
      this.image_uri = Optional.ofNullable(image_uri);
    }


    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id, this.geopolitical_unit_ids, this.party_ids, this.image_uri);
    }
  }

  /**
   * Use this entity to describe a political party that can then be referenced from other entities.
   * See: https://developers.google.com/elections-data/reference/party
   */
  @Immutable
  static class Party extends ElectionObjectBase implements Hash.CryptoHashable {
    final InternationalizedText ballot_name;
    final Optional<String> abbreviation;
    final Optional<String> color;
    final Optional<String> logo_uri;

    public Party(String object_id) {
      super(object_id);
      this.ballot_name = new InternationalizedText(ImmutableList.of());
      this.abbreviation = Optional.empty();
      this.color = Optional.empty();
      this.logo_uri = Optional.empty();
    }

    public Party(String object_id,
                 InternationalizedText ballot_name,
                 @Nullable String abbreviation,
                 @Nullable String color,
                 @Nullable String logo_uri) {
      super(object_id);
      this.ballot_name = ballot_name;
      this.abbreviation = Optional.ofNullable(abbreviation);
      this.color = Optional.ofNullable(color);
      this.logo_uri = Optional.ofNullable(logo_uri);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id,
              this.ballot_name,
              this.abbreviation,
              this.color,
              this.logo_uri);
    }
  }

  /**
   * Entity describing information about a candidate in a contest.
   * See: https://developers.google.com/elections-data/reference/candidate
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * selections for any contest type are considered a "candidate".
   * for instance, on a yes-no referendum contest, two `candidate` objects
   * would be included in the model to represent the `affirmative` and `negative`
   * selections for the contest.  See the wiki, readme's, and tests in this repo for more info
   */
  @Immutable
  static class Candidate extends ElectionObjectBase implements Hash.CryptoHashable {
    final InternationalizedText ballot_name;
    final Optional<String> party_id;
    final Optional<String> image_uri;
    final Optional<Boolean> is_write_in;

    public Candidate(String object_id) {
      super(object_id);
      this.ballot_name = new InternationalizedText(ImmutableList.of());
      this.party_id = Optional.empty();
      this.image_uri = Optional.empty();
      this.is_write_in = Optional.empty();
      ;
    }

    public Candidate(String object_id,
                     InternationalizedText ballot_name,
                     @Nullable String party_id,
                     @Nullable String image_uri,
                     @Nullable Boolean is_write_in) {
      super(object_id);
      this.ballot_name = ballot_name;
      this.party_id = Optional.ofNullable(party_id);
      this.image_uri = Optional.ofNullable(image_uri);
      this.is_write_in = Optional.ofNullable(is_write_in);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id, this.ballot_name, this.party_id, this.image_uri);
    }
  }

  /**
   * Data entity for the ballot selections in a contest,
   * for example linking candidates and parties to their vote counts.
   * See: https://developers.google.com/elections-data/reference/ballot-selection
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * there is no difference for different types of selections.
   * The ElectionGuard Data Spec deviates from the NIST model in that
   * `sequence_order` is a required field since it is used for ordering selections
   * in a contest to ensure various encryption primitives are deterministic.
   * For a given election, the sequence of selections displayed to a user may be different
   * however that information is not captured by default when encrypting a specific ballot.
   */
  @Immutable
  static class SelectionDescription extends ElectionObjectBase implements Hash.CryptoHashable {
    final String candidate_id;
    /**
     * Used for ordering selections in a contest to ensure various encryption primitives are deterministic.
     * The sequence order must be unique and should be representative of how the contests are represnted
     * on a "master" ballot in an external system.  The sequence order is not required to be in the order
     * in which they are displayed to a voter.  Any acceptable range of integer values may be provided.
     */
    final int sequence_order;

    public SelectionDescription(String object_id, String candidate_id, int sequence_order) {
      super(object_id);
      this.candidate_id = candidate_id;
      this.sequence_order = sequence_order;
    }


    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.object_id, this.sequence_order, this.candidate_id);
    }
  }

  /**
   * Use this data entity for describing a contest and linking the contest
   * to the associated candidates and parties.
   * See: https://developers.google.com/elections-data/reference/contest
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * `sequence_order` is a required field since it is used for ordering selections
   * in a contest to ensure various encryption primitives are deterministic.
   * For a given election, the sequence of contests displayed to a user may be different
   * however that information is not captured by default when encrypting a specific ballot.
   */
  static class ContestDescription extends ElectionObjectBase implements Hash.CryptoHashable {

    final String electoral_district_id;
    /**
     * Used for ordering contests in a ballot to ensure various encryption primitives are deterministic.
     * The sequence order must be unique and should be representative of how the contests are represnted
     * on a "master" ballot in an external system.  The sequence order is not required to be in the order
     * in which they are displayed to a voter.  Any acceptable range of integer values may be provided.
     */
    final int sequence_order;

    final VoteVariationType vote_variation;

    // Number of candidates that are elected in the contest ("n" of n-of-m).
    // Note: a referendum is considered a specific case of 1-of-m in ElectionGuard
    final int number_elected;

    // Maximum number of votes/write-ins per voter in this contest. Used in cumulative voting
    // to indicate how many total votes a voter can spread around. In n-of-m elections, this will be None.
    final Optional<Integer> votes_allowed;

    //Name of the contest, not necessarily as it appears on the ballot.
    final String name;

    // For associating a ballot selection for the contest, i.e., a candidate, a ballot measure.
    final ImmutableList<SelectionDescription> ballot_selections;

    // Title of the contest as it appears on the ballot.
    final Optional<InternationalizedText> ballot_title;

    // Subtitle of the contest as it appears on the ballot.
    final Optional<InternationalizedText> ballot_subtitle;

    public ContestDescription(String object_id,
                              String electoral_district_id,
                              int sequence_order,
                              VoteVariationType vote_variation,
                              int number_elected,
                              @Nullable Integer votes_allowed,
                              String name,
                              List<SelectionDescription> ballot_selections,
                              @Nullable InternationalizedText ballot_title,
                              @Nullable InternationalizedText ballot_subtitle) {
      super(object_id);
      this.electoral_district_id = electoral_district_id;
      this.sequence_order = sequence_order;
      this.vote_variation = vote_variation;
      this.number_elected = number_elected;
      this.votes_allowed = Optional.ofNullable(votes_allowed);
      this.name = name;
      this.ballot_selections = ImmutableList.copyOf(ballot_selections);
      this.ballot_title = Optional.ofNullable(ballot_title);
      this.ballot_subtitle = Optional.ofNullable(ballot_subtitle);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id,
              this.sequence_order,
              this.electoral_district_id,
              this.vote_variation.name(),
              this.ballot_title,
              this.ballot_subtitle,
              this.name,
              this.number_elected,
              this.votes_allowed,
              this.ballot_selections);
    }

    /**
     * Check the validity of the contest object by verifying its data.
     */
    boolean is_valid() {
      boolean contest_has_valid_number_elected = this.number_elected <= this.ballot_selections.size();
      boolean contest_has_valid_votes_allowed = this.votes_allowed.isEmpty() || this.number_elected <= this.votes_allowed.get();

      //  verify the candidate_ids, selection object_ids, and sequence_ids are unique
      HashSet<String> candidate_ids = new HashSet<>();
      HashSet<String> selection_ids = new HashSet<>();
      HashSet<Integer> sequence_ids = new HashSet<>();

      int selection_count = 0;
      int expected_selection_count = this.ballot_selections.size();

      // count unique ids
      for (SelectionDescription selection : this.ballot_selections) {
        selection_count += 1;
        //  validate the object_id
        if (!selection_ids.contains(selection.object_id)) {
          selection_ids.add(selection.object_id);
        }
        //  validate the sequence_order
        if (!sequence_ids.contains(selection.sequence_order)) {
          sequence_ids.add(selection.sequence_order);
        }
        //  validate the candidate id
        if (!candidate_ids.contains(selection.candidate_id)) {
          candidate_ids.add(selection.candidate_id);
        }
      }

      boolean selections_have_valid_candidate_ids = candidate_ids.size() == expected_selection_count;
      boolean selections_have_valid_selection_ids = selection_ids.size() == expected_selection_count;
      boolean selections_have_valid_sequence_ids = sequence_ids.size() == expected_selection_count;

      boolean success = contest_has_valid_number_elected && contest_has_valid_votes_allowed &&
              selections_have_valid_candidate_ids && selections_have_valid_selection_ids && selections_have_valid_sequence_ids;

      if (!success) {
        /* log_warning(
                "Contest %s failed validation check: %s",
                this.object_id,
                str(
                        {
                                "contest_has_valid_number_elected":contest_has_valid_number_elected,
                "contest_has_valid_votes_allowed":contest_has_valid_votes_allowed,
                "selections_have_valid_candidate_ids":selections_have_valid_candidate_ids,
                "selections_have_valid_selection_ids":selections_have_valid_selection_ids,
                "selections_have_valid_sequence_ids":selections_have_valid_sequence_ids,
    }
                ),
                        ) */
      }

      return success;
    }
  }

  /**
   * Use this entity to describe a contest that involves selecting one or more candidates.
   * See: https://developers.google.com/elections-data/reference/contest
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this subclass is used purely for convenience
   */
  static class CandidateContestDescription extends ContestDescription {
    final ImmutableList<String> primary_party_ids;

    public CandidateContestDescription(String object_id,
                                       String electoral_district_id,
                                       int sequence_order,
                                       VoteVariationType vote_variation,
                                       int number_elected,
                                       @Nullable Integer votes_allowed,
                                       String name,
                                       List<SelectionDescription> ballot_selections,
                                       @Nullable InternationalizedText ballot_title,
                                       @Nullable InternationalizedText ballot_subtitle,
                                       List<String> primary_party_ids) {
      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed,
              name, ballot_selections, ballot_title, ballot_subtitle);
      this.primary_party_ids = ImmutableList.copyOf(primary_party_ids);
    }
  }

  /**
   * Use this entity to describe a contest that involves selecting exactly one 'candidate'.
   * See: https://developers.google.com/elections-data/reference/contest
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this subclass is used purely for convenience.
   */
  static class ReferendumContestDescription extends ContestDescription {

    public ReferendumContestDescription(String object_id,
                                        String electoral_district_id,
                                        int sequence_order,
                                        VoteVariationType vote_variation,
                                        int number_elected,
                                        @Nullable Integer votes_allowed,
                                        String name, List<SelectionDescription> ballot_selections,
                                        @Nullable InternationalizedText ballot_title,
                                        @Nullable InternationalizedText ballot_subtitle) {
      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed,
              name, ballot_selections, ballot_title, ballot_subtitle);
    }
  }

  /**
   * ContestDescriptionWithPlaceholders is a `ContestDescription` with ElectionGuard `placeholder_selections`.
   * (The ElectionGuard spec requires for n-of-m elections that there be *exactly* n counters that are one
   * with the rest zero, so if a voter deliberately undervotes, one or more of the placeholder counters will
   * become one. This allows the `ConstantChaumPedersenProof` to verify correctly for undervoted contests.)
   */
  static class ContestDescriptionWithPlaceholders extends ContestDescription {
    ImmutableList<SelectionDescription> placeholder_selections;

    public ContestDescriptionWithPlaceholders(String object_id,
                                              String electoral_district_id,
                                              int sequence_order,
                                              VoteVariationType vote_variation,
                                              int number_elected,
                                              @Nullable Integer votes_allowed,
                                              String name,
                                              List<SelectionDescription> ballot_selections,
                                              @Nullable InternationalizedText ballot_title,
                                              @Nullable InternationalizedText ballot_subtitle,
                                              List<SelectionDescription> placeholder_selections) {

      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed, name,
              ballot_selections, ballot_title, ballot_subtitle);
      this.placeholder_selections = ImmutableList.copyOf(placeholder_selections);
    }

    boolean is_valid() {
      boolean contest_description_validates = super.is_valid();
      return contest_description_validates && this.placeholder_selections.size() == this.number_elected;
    }
  }

  /**
   * Use this entity for defining the structure of the election and associated
   * information such as candidates, contests, and vote counts.  This class is
   * based on the NIST Election Common Standard Data Specification.  Some deviations
   * from the standard exist.
   * <p>
   * This structure is considered an immutable input object and should not be changed
   * through the course of an election, as it's hash representation is the basis for all
   * other hash representations within an ElectionGuard election context.
   * <p>
   * See: https://developers.google.com/elections-data/reference/election
   */
  public static class ElectionDescription implements Hash.CryptoHashable {
    final String election_scope_id;
    final ElectionType type;
    final LocalDate start_date;
    final LocalDate end_date;
    final ImmutableList<GeopoliticalUnit> geopolitical_units;
    final ImmutableList<Party> parties;
    final ImmutableList<Candidate> candidates;
    final ImmutableList<ContestDescription> contests;
    final ImmutableList<BallotStyle> ballot_styles;
    final Optional<InternationalizedText> name;
    final Optional<ContactInformation> contact_information;

    // TODO maybe use a Builder?
    public ElectionDescription(String election_scope_id,
                               ElectionType type,
                               LocalDate start_date,
                               LocalDate end_date,
                               List<GeopoliticalUnit> geopolitical_units,
                               List<Party> parties,
                               List<Candidate> candidates,
                               List<ContestDescription> contests,
                               List<BallotStyle> ballot_styles,
                               @Nullable InternationalizedText name,
                               @Nullable ContactInformation contact_information) {
      this.election_scope_id = election_scope_id;
      this.type = type;
      this.start_date = start_date;
      this.end_date = end_date;
      this.geopolitical_units = ImmutableList.copyOf(geopolitical_units);
      this.parties = ImmutableList.copyOf(parties);
      this.candidates = ImmutableList.copyOf(candidates);
      this.contests = ImmutableList.copyOf(contests);
      this.ballot_styles = ImmutableList.copyOf(ballot_styles);
      this.name = Optional.ofNullable(name);
      this.contact_information = Optional.ofNullable(contact_information);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.election_scope_id,
              this.type.name(),
              this.start_date,
              this.end_date,
              this.name,
              this.contact_information,
              this.geopolitical_units,
              this.parties,
              this.contests,
              this.ballot_styles);
    }

    /**
     * Verifies the dataset to ensure it is well-formed.
     */
    boolean is_valid() {

      HashSet<String> gp_unit_ids = new HashSet<>();
      HashSet<String> ballot_style_ids = new HashSet<>();
      HashSet<String> party_ids = new HashSet<>();
      HashSet<String> candidate_ids = new HashSet<>();
      HashSet<String> contest_ids = new HashSet<>();

      // Validate GP Units
      for (GeopoliticalUnit gp_unit : this.geopolitical_units) {
        gp_unit_ids.add(gp_unit.object_id);
      }
      // fail if there are duplicates
      boolean geopolitical_units_valid = gp_unit_ids.size() == this.geopolitical_units.size();

      // Validate Ballot Styles
      boolean ballot_styles_have_valid_gp_unit_ids = true;
      for (BallotStyle style : this.ballot_styles) {
        ballot_style_ids.add(style.object_id);

        if (style.geopolitical_unit_ids.isEmpty()) {
          ballot_styles_have_valid_gp_unit_ids = false;
          break;
        }
        // validate associated gp unit ids
        for (String gp_unit_id : style.geopolitical_unit_ids.get()) {
          ballot_styles_have_valid_gp_unit_ids &= gp_unit_ids.contains(gp_unit_id);
        }
      }

      boolean ballot_styles_valid = (ballot_style_ids.size() == this.ballot_styles.size() &&
              ballot_styles_have_valid_gp_unit_ids);

      // Validate Parties
      for (Party party : this.parties) {
        party_ids.add(party.object_id);
      }
      boolean parties_valid = party_ids.size() == this.parties.size();

      // Validate Candidates
      boolean candidates_have_valid_party_ids = true;
      for (Candidate candidate : this.candidates) {
        candidate_ids.add(candidate.object_id);
        // validate the associated party id
        candidates_have_valid_party_ids &=
                candidate.party_id.isEmpty() || party_ids.contains(candidate.party_id.get());
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

        contest_ids.add(contest.object_id);
        contest_sequence_ids.add(contest.sequence_order);

        // validate the associated gp unit id
        contests_have_valid_electoral_district_id &= gp_unit_ids.contains(contest.electoral_district_id);

        if (contest instanceof CandidateContestDescription) {
          CandidateContestDescription candidate_contest = (CandidateContestDescription) contest;
          if (candidate_contest.primary_party_ids != null) {
            for (String primary_party_id : candidate_contest.primary_party_ids) {
              // validate the party ids
              candidate_contests_have_valid_party_ids &= party_ids.contains(primary_party_id);
            }
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
                  /* log_warning(
                          "Election failed validation check: is_valid: %s",
                          str(
                                  {
                                          "geopolitical_units_valid":geopolitical_units_valid,
                          "ballot_styles_valid":ballot_styles_valid,
                          "ballot_styles_have_valid_gp_unit_ids":ballot_styles_have_valid_gp_unit_ids,
                          "parties_valid":parties_valid,
                          "candidates_valid":candidates_valid,
                          "candidates_have_valid_length":candidates_have_valid_length,
                          "candidates_have_valid_party_ids":candidates_have_valid_party_ids,
                          "contests_valid":contests_valid,
                          "contests_have_valid_object_ids":contests_have_valid_object_ids,
                          "contests_have_valid_sequence_ids":contests_have_valid_sequence_ids,
                          "contests_validate_their_properties":contests_validate_their_properties,
                          "contests_have_valid_electoral_district_id":contests_have_valid_electoral_district_id,
                          "candidate_contests_have_valid_party_ids":candidate_contests_have_valid_party_ids,
    */
      }
      return success;
    }
  }

  /**
   * `InternalElectionDescription` is a subset of the `ElectionDescription` structure that specifies
   * the components that ElectionGuard uses for conducting an election.  The key component is the
   * `contests` collection, which applies placeholder selections to the `ElectionDescription` contests
   */
  static class InternalElectionDescription {
    final ElectionDescription description;

    ImmutableList<GeopoliticalUnit> geopolitical_units;
    ImmutableList<ContestDescriptionWithPlaceholders> contests;
    ImmutableList<BallotStyle> ballot_styles;
    Group.ElementModQ description_hash;

    public InternalElectionDescription(ElectionDescription description) {
      this.description = description;
      this.geopolitical_units = description.geopolitical_units;
      this.contests = _generate_contests_with_placeholders(description);
      this.ballot_styles = description.ballot_styles;
      this.description_hash = description.crypto_hash();
    }

    Optional<ContestDescriptionWithPlaceholders> contest_for(String contest_id) {
      return contests.stream().filter(c -> c.object_id.equals(contest_id)).findFirst();
    }


    /**
     * Find the ballot style for a specified ballot_style_id
     */
    Optional<BallotStyle> get_ballot_style(String ballot_style_id) {
      return ballot_styles.stream().filter(bs -> bs.object_id.equals(ballot_style_id)).findFirst();
    }

    /**
     * Get contests for a ballot style
     *
     * @param ballot_style_id: ballot style id
     * @return: contest descriptions
     */
    List<ContestDescriptionWithPlaceholders> get_contests_for(String ballot_style_id) {
      Optional<BallotStyle> style = this.get_ballot_style(ballot_style_id);
      if (style.isEmpty() || style.get().geopolitical_unit_ids.isEmpty()) {
        return new ArrayList<>();
      }
      // gp_unit_ids = [gp_unit_id for gp_unit_id in style.geopolitical_unit_ids]
      List<String> gp_unit_ids = new ArrayList<>(style.get().geopolitical_unit_ids.get());
      // contests = list(filter(lambda i: i.electoral_district_id in gp_unit_ids, this.contests)
      List<ContestDescriptionWithPlaceholders> result =
              this.contests.stream().filter(c -> gp_unit_ids.contains(c.electoral_district_id)).collect(Collectors.toList());
      return result;
    }

    /**
     * For each contest, append the `number_elected` number
     * of placeholder selections to the end of the contest collection.
     */
    private ImmutableList<ContestDescriptionWithPlaceholders> _generate_contests_with_placeholders(
            ElectionDescription description) {

      List<ContestDescriptionWithPlaceholders> contests = new ArrayList<>();
      for (ContestDescription contest : description.contests) {
        List<SelectionDescription> placeholder_selections = generate_placeholder_selections_from(contest, contest.number_elected);
        contests.add(contest_description_with_placeholders_from(contest, placeholder_selections));
      }
      return ImmutableList.copyOf(contests);
    }

  }

  /**
   * The constants for mathematical functions during the election.
   */
  @Immutable
  static class ElectionConstants {
    public static final BigInteger large_prime = Group.P; // large prime or p"""
    public static final BigInteger small_prime = Group.Q; // small prime or q"""
    public static final BigInteger cofactor = Group.R; // cofactor or r"""
    public static final BigInteger generator = Group.G; // generator or g"""
  }

  /**
   * `CiphertextElectionContext` is the ElectionGuard representation of a specific election
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this object includes fields that are populated in the course of encrypting an election
   * Specifically, `crypto_base_hash`, `crypto_extended_base_hash` and `elgamal_public_key`
   * are populated with election-specific information necessary for encrypting the election.
   * Refer to the [Electionguard Specification](https://github.com/microsoft/electionguard) for more information.
   * <p>
   * To make an instance of this class, don't construct it directly. Use
   * `make_ciphertext_election_context` instead.
   */
  static class CiphertextElectionContext {
    final int number_of_guardians; // The number of guardians necessary to generate the public key
    final int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`

    // the `joint public key (K)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    final Group.ElementModP elgamal_public_key;

    // The hash of the election metadata
    final Group.ElementModQ description_hash;

    // the `base hash code (𝑄)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    final Group.ElementModQ crypto_base_hash;

    // the `extended base hash code (𝑄')` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    final Group.ElementModQ crypto_extended_base_hash;

    public CiphertextElectionContext(int number_of_guardians, int quorum, ElementModP elgamal_public_key,
                                     ElementModQ description_hash, ElementModQ crypto_base_hash, ElementModQ crypto_extended_base_hash) {
      this.number_of_guardians = number_of_guardians;
      this.quorum = quorum;
      this.elgamal_public_key = elgamal_public_key;
      this.description_hash = description_hash;
      this.crypto_base_hash = crypto_base_hash;
      this.crypto_extended_base_hash = crypto_extended_base_hash;
    }
  }

  /**
   * Makes a CiphertextElectionContext object.
   * <p>
   * :param number_of_guardians: The number of guardians necessary to generate the public key
   * :param quorum: The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
   * :param elgamal_public_key: the public key of the election
   * :param description_hash: the hash of the election metadata
   */
  static CiphertextElectionContext make_ciphertext_election_context(
          int number_of_guardians,
          int quorum,
          ElementModP elgamal_public_key,
          ElementModQ description_hash) {

    // What's a crypto_base_hash?
    // The metadata of this object are hashed together with the
    //  - prime modulus (𝑝),
    //  - subgroup order (𝑞),
    //  - generator (𝑔),
    //  - number of guardians (𝑛),
    //  - decryption threshold value (𝑘),
    //  to form a base hash code (𝑄) which will be incorporated
    //  into every subsequent hash computation in the election.

    //  What's a crypto_extended_base_hash?
    //  Once the baseline parameters have been produced and confirmed,
    //  all of the public guardian commitments 𝐾𝑖,𝑗 are hashed together
    //  with the base hash 𝑄 to form an extended base hash 𝑄' that will
    //  form the basis of subsequent hash computations.

    ElementModQ crypto_base_hash = Hash.hash_elems(P, Q, G, number_of_guardians, quorum, description_hash);
    ElementModQ crypto_extended_base_hash = Hash.hash_elems(crypto_base_hash, elgamal_public_key);

    // int number_of_guardians, int quorum, ElementModP elgamal_public_key,
    //          ElementModQ description_hash, ElementModQ crypto_base_hash, ElementModQ crypto_extended_base_hash
    return new CiphertextElectionContext(
            number_of_guardians,
            quorum,
            elgamal_public_key,
            description_hash,
            crypto_base_hash,
            crypto_extended_base_hash);
  }

  /**
   * Generates a placeholder selection description
   * :param description: contest description
   * :param placeholders: list of placeholder descriptions of selections
   * :return: a SelectionDescription or None
   */
  static ContestDescriptionWithPlaceholders contest_description_with_placeholders_from(
          ContestDescription description, List<SelectionDescription> placeholders) {

    return new ContestDescriptionWithPlaceholders(
            description.object_id,
            description.electoral_district_id,
            description.sequence_order,
            description.vote_variation,
            description.number_elected,
            description.votes_allowed.orElse(null),
            description.name,
            description.ballot_selections,
            description.ballot_title.orElse(null),
            description.ballot_subtitle.orElse(null),
            placeholders);
  }

  /*
      Generates a placeholder selection description that is unique so it can be hashed

    :param use_sequence_id: an optional integer unique to the contest identifying this selection's place in the contest
    :return: a SelectionDescription or None
   */
  static Optional<SelectionDescription> generate_placeholder_selection_from(
          ContestDescription contest, Optional<Integer> use_sequence_idO) {

    //     sequence_ids = [selection.sequence_order for selection in contest.ballot_selections]
    List<Integer> sequence_ids = contest.ballot_selections.stream().map(s -> s.sequence_order).collect(Collectors.toList());

    int use_sequence_id;
    if (use_sequence_idO.isEmpty()) {
      // if no sequence order is specified, take the max
      use_sequence_id = sequence_ids.stream().max(Integer::compare).orElse(0) + 1;
    } else {
      use_sequence_id = use_sequence_idO.get();
      if (sequence_ids.contains(use_sequence_id)) {
        // log_warning(f"mismatched placeholder selection {use_sequence_id} already exists")
        return Optional.empty();
      }
    }

    String placeholder_object_id = String.format("%s-%s", contest.object_id, use_sequence_id);
    return Optional.of(new SelectionDescription(
            String.format("%s-placeholder", placeholder_object_id),
            String.format("%s-candidate", placeholder_object_id),
            use_sequence_id));
  }

  /**
   * Generates the specified number of placeholder selections in ascending sequence order from the max selection sequence orderf
   * <p>
   * :param contest: ContestDescription for input
   * :param count: optionally specify a number of placeholders to generate
   * :return: a collection of `SelectionDescription` objects, which may be empty
   */
  static List<SelectionDescription> generate_placeholder_selections_from(ContestDescription contest, int count) {
    //  max_sequence_order = max([selection.sequence_order for selection in contest.ballot_selections]);
    int max_sequence_order = contest.ballot_selections.stream().map(s -> s.sequence_order).max(Integer::compare).orElse(0);
    List<SelectionDescription> selections = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int sequence_order = max_sequence_order + 1 + i;
      selections.add(generate_placeholder_selection_from(contest, Optional.of(sequence_order)).get());
    }
    return selections;
  }
}