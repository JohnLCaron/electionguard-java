package com.sunya.electionguard;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;

/**
 * Election Manifest.
 * The election metadata in json format that is parsed into an Election Description.
 */
public class Election {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * enumerations for the `ElectionReport` entity
   * see: https://developers.google.com/elections-data/reference/election-type
   */
  public enum ElectionType {
    unknown,
    general,  // For an election held typically on the national day for elections.
    partisan_primary_closed, //	For a primary election that is for a specific party where voter eligibility is based on registration.
    partisan_primary_open, //	For a primary election that is for a specific party where voter declares desired party or chooses in private.
    primary,  //	For a primary election without a specified type, such as a nonpartisan primary.
    runoff,   //	For an election to decide a prior contest that ended with no candidate receiving a majority of the votes.
    special, //	For an election held out of sequence for special circumstances, for example, to fill a vacated office.
    other;  //	Used when the election type is not listed in this enumeration. If used, include a specific value of the OtherType element.
  }

  /**
   * Enumeration for the type of geopolitical unit
   * see: https://developers.google.com/elections-data/reference/reporting-unit-type
   */
  public enum ReportingUnitType {
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
  public enum VoteVariationType {
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
  public static class AnnotatedString implements Hash.CryptoHashable {
    public final String annotation;
    public final String value;

    public AnnotatedString(String annotation, String value) {
      this.annotation = annotation;
      this.value = value;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.annotation, this.value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AnnotatedString that = (AnnotatedString) o;
      return Objects.equals(annotation, that.annotation) &&
              Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(annotation, value);
    }
  }

  /**
   * The ISO-639 language
   * see: https://en.wikipedia.org/wiki/ISO_639
   */
  @Immutable
  public static class Language implements Hash.CryptoHashable {
    public final String value;
    public final String language;

    public Language(String value, String language) {
      this.value = value;
      this.language = language;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.value, this.language);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Language language1 = (Language) o;
      return Objects.equals(value, language1.value) &&
              Objects.equals(language, language1.language);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, language);
    }
  }

  /**
   * Data entity used to represent multi-national text. Use when text on a ballot contains multi-national text.
   * See: https://developers.google.com/elections-data/reference/internationalized-text
   */
  @Immutable
  public static class InternationalizedText implements Hash.CryptoHashable {
    public final ImmutableList<Language> text;

    public InternationalizedText(@Nullable List<Language> text) {
      this.text = toImmutableList(text);
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.text);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      InternationalizedText that = (InternationalizedText) o;
      return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
      return Objects.hash(text);
    }
  }

  /**
   * For defining contact information about objects such as persons, boards of authorities, and organizations.
   * See: https://developers.google.com/elections-data/reference/contact-information
   */
  @Immutable
  public static class ContactInformation implements Hash.CryptoHashable {
    public final Optional<ImmutableList<String>> address_line;
    public final Optional<ImmutableList<AnnotatedString>> email;
    public final Optional<ImmutableList<AnnotatedString>> phone;
    public final Optional<String> name;

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
      return Hash.hash_elems(this.name, this.address_line, this.email, this.phone);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ContactInformation that = (ContactInformation) o;
      return Objects.equals(address_line, that.address_line) &&
              Objects.equals(email, that.email) &&
              Objects.equals(phone, that.phone) &&
              Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(address_line, email, phone, name);
    }
  }

  /**
   * Use this entity for defining geopolitical units such as cities, districts, jurisdictions, or precincts,
   * for the purpose of associating contests, offices, vote counts, or other information with the geographies.
   * See: https://developers.google.com/elections-data/reference/gp-unit
   */
  @Immutable
  public static class GeopoliticalUnit extends ElectionObjectBase implements Hash.CryptoHashable {
    public final String name;
    public final ReportingUnitType type;
    public final Optional<ContactInformation> contact_information;

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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      GeopoliticalUnit that = (GeopoliticalUnit) o;
      return Objects.equals(name, that.name) &&
              type == that.type &&
              Objects.equals(contact_information, that.contact_information);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name, type, contact_information);
    }
  }

  /** A BallotStyle works as a key to uniquely specify a set of contests. */
  @Immutable
  public static class BallotStyle extends ElectionObjectBase implements Hash.CryptoHashable {
    public final Optional<ImmutableList<String>> geopolitical_unit_ids;
    public final Optional<ImmutableList<String>> party_ids;
    public final Optional<String> image_uri;

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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      BallotStyle that = (BallotStyle) o;
      return Objects.equals(geopolitical_unit_ids, that.geopolitical_unit_ids) &&
              Objects.equals(party_ids, that.party_ids) &&
              Objects.equals(image_uri, that.image_uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), geopolitical_unit_ids, party_ids, image_uri);
    }
  }

  /**
   * Use this entity to describe a political party that can then be referenced from other entities.
   * See: https://developers.google.com/elections-data/reference/party
   */
  @Immutable
  public static class Party extends ElectionObjectBase implements Hash.CryptoHashable {
    public final InternationalizedText ballot_name;
    public final Optional<String> abbreviation;
    public final Optional<String> color;
    public final Optional<String> logo_uri;

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

    String get_party_id() {
      return this.object_id;
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Party party = (Party) o;
      return Objects.equals(ballot_name, party.ballot_name) &&
              Objects.equals(abbreviation, party.abbreviation) &&
              Objects.equals(color, party.color) &&
              Objects.equals(logo_uri, party.logo_uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), ballot_name, abbreviation, color, logo_uri);
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
  public static class Candidate extends ElectionObjectBase implements Hash.CryptoHashable {
    public final InternationalizedText ballot_name;
    public final Optional<String> party_id;
    public final Optional<String> image_uri;
    public final Optional<Boolean> is_write_in;

    public Candidate(String object_id) {
      super(object_id);
      this.ballot_name = new InternationalizedText(ImmutableList.of());
      this.party_id = Optional.empty();
      this.image_uri = Optional.empty();
      this.is_write_in = Optional.empty();
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

    /** Get the "candidate ID" for this Candidate. */
    String get_candidate_id() {
      return this.object_id;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id, this.ballot_name, this.party_id, this.image_uri);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Candidate candidate = (Candidate) o;
      return Objects.equals(ballot_name, candidate.ballot_name) &&
              Objects.equals(party_id, candidate.party_id) &&
              Objects.equals(image_uri, candidate.image_uri) &&
              Objects.equals(is_write_in, candidate.is_write_in);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), ballot_name, party_id, image_uri, is_write_in);
    }
  }

  /**
   * Data entity for the ballot selections in a contest,
   * for example linking candidates and parties to their vote counts.
   * See: https://developers.google.com/elections-data/reference/ballot-selection
   * <p>
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * there is no difference for different types of selections.
   * The ElectionGuard Data Spec deviates from the NIST model in that
   * `sequence_order` is a required field since it is used for ordering selections
   * in a contest to ensure various encryption primitives are deterministic.
   * For a given election, the sequence of selections displayed to a user may be different
   * however that information is not captured by default when encrypting a specific ballot.
   */
  @Immutable
  public static class SelectionDescription extends ElectionObjectBase implements Hash.CryptoHashable {
    public final String candidate_id;
    /**
     * Used for ordering selections in a contest to ensure various encryption primitives are deterministic.
     * The sequence order must be unique and should be representative of how the contests are represented
     * on a "master" ballot in an external system.  The sequence order is not required to be in the order
     * in which they are displayed to a voter.  Any acceptable range of integer values may be provided.
     */
    public final int sequence_order;

    public SelectionDescription(String object_id, String candidate_id, int sequence_order) {
      super(object_id);
      this.candidate_id = candidate_id;
      this.sequence_order = sequence_order;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.object_id, this.sequence_order, this.candidate_id);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      SelectionDescription that = (SelectionDescription) o;
      return sequence_order == that.sequence_order &&
              Objects.equals(candidate_id, that.candidate_id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), candidate_id, sequence_order);
    }

    @Override
    public String toString() {
      return "SelectionDescription{" +
              "candidate_id='" + candidate_id + '\'' +
              ", sequence_order=" + sequence_order +
              ", object_id='" + object_id + '\'' +
              '}';
    }
  }

  /**
   * Use to describe a contest and link the contest to the associated candidates and parties.
   * See: https://developers.google.com/elections-data/reference/contest
   * <p>
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * `sequence_order` is a required field since it is used for ordering selections
   * in a contest to ensure various encryption primitives are deterministic.
   * For a given election, the sequence of contests displayed to a user may be different,
   * however that information is not captured by default when encrypting a specific ballot.
   */
  @Immutable
  public static class ContestDescription extends ElectionObjectBase implements Hash.CryptoHashable {

    public final String electoral_district_id;
    /**
     * Used for ordering contests in a ballot to ensure various encryption primitives are deterministic.
     * The sequence order must be unique and should be representative of how the contests are represented
     * on a "master" ballot in an external system.  The sequence order is not required to be in the order
     * in which they are displayed to a voter.  Any acceptable range of integer values may be provided.
     */
    public final int sequence_order;

    public final VoteVariationType vote_variation;

    /** Number of candidates that are elected in the contest ("n" of n-of-m). */
    // Note: a referendum is considered a specific case of 1-of-m in ElectionGuard
    public final int number_elected;

    /** Maximum number of votes/write-ins per voter in this contest. Used in cumulative voting
        to indicate how many total votes a voter can spread around. In n-of-m elections, this will be None. */
    public final Optional<Integer> votes_allowed;

    /** Name of the contest, not necessarily as it appears on the ballot. */
    public final String name;

    /** For associating a ballot selection for the contest, i.e., a candidate, a ballot measure. */
    public final ImmutableList<SelectionDescription> ballot_selections;

    /** Title of the contest as it appears on the ballot. */
    public final Optional<InternationalizedText> ballot_title;

    /** Subtitle of the contest as it appears on the ballot. */
    public final Optional<InternationalizedText> ballot_subtitle;

    public ContestDescription(String object_id,
                              String electoral_district_id,
                              int sequence_order,
                              VoteVariationType vote_variation,
                              int number_elected,
                              @Nullable Integer votes_allowed, // LOOK why optional ?
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
      this.ballot_selections = toImmutableListEmpty(ballot_selections);
      this.ballot_title = Optional.ofNullable(ballot_title);
      this.ballot_subtitle = Optional.ofNullable(ballot_subtitle);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ContestDescription that = (ContestDescription) o;
      return sequence_order == that.sequence_order &&
              number_elected == that.number_elected &&
              Objects.equals(electoral_district_id, that.electoral_district_id) &&
              vote_variation == that.vote_variation &&
              Objects.equals(votes_allowed, that.votes_allowed) &&
              Objects.equals(name, that.name) &&
              Objects.equals(ballot_selections, that.ballot_selections) &&
              Objects.equals(ballot_title, that.ballot_title) &&
              Objects.equals(ballot_subtitle, that.ballot_subtitle);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), electoral_district_id, sequence_order, vote_variation, number_elected,
              votes_allowed, name, ballot_selections, ballot_title, ballot_subtitle);
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

    /** Check the validity of the contest object by verifying its data. */
    boolean is_valid() {
      boolean contest_has_valid_number_elected = this.number_elected <= this.ballot_selections.size();
      boolean contest_has_valid_votes_allowed = this.votes_allowed.isEmpty() || this.number_elected <= this.votes_allowed.get();

      //  verify the candidate_ids, selection object_ids, and sequence_ids are unique
      HashSet<String> candidate_ids = new HashSet<>();
      HashSet<String> selection_ids = new HashSet<>();
      HashSet<Integer> sequence_ids = new HashSet<>();

      int expected_selection_count = this.ballot_selections.size();

      // count unique ids
      for (SelectionDescription selection : this.ballot_selections) {
        //  validate the object_id
        selection_ids.add(selection.object_id);
        //  validate the sequence_order
        sequence_ids.add(selection.sequence_order);
        //  validate the candidate id
        candidate_ids.add(selection.candidate_id);
      }

      boolean selections_have_valid_candidate_ids = candidate_ids.size() == expected_selection_count;
      boolean selections_have_valid_selection_ids = selection_ids.size() == expected_selection_count;
      boolean selections_have_valid_sequence_ids = sequence_ids.size() == expected_selection_count;

      boolean success = contest_has_valid_number_elected && contest_has_valid_votes_allowed &&
              selections_have_valid_candidate_ids && selections_have_valid_selection_ids && selections_have_valid_sequence_ids;

      if (!success) {
        logger.atWarning().log(
                "Contest %s failed validation check: %s", this.object_id,
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
   * Use this entity to describe a contest that involves selecting one or more candidates.
   * See: https://developers.google.com/elections-data/reference/contest
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this subclass is used purely for convenience
   */
  @Immutable
  public static class CandidateContestDescription extends ContestDescription {
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
                                       @Nullable List<String> primary_party_ids) {
      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed,
              name, ballot_selections, ballot_title, ballot_subtitle);
      this.primary_party_ids = toImmutableListEmpty(primary_party_ids);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      CandidateContestDescription that = (CandidateContestDescription) o;
      return Objects.equals(primary_party_ids, that.primary_party_ids);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), primary_party_ids);
    }
  }

  /**
   * Use this entity to describe a contest that involves selecting exactly one 'candidate'.
   * See: https://developers.google.com/elections-data/reference/contest
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this subclass is used purely for convenience.
   */
  @Immutable
  public static class ReferendumContestDescription extends ContestDescription {

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
   * (The ElectionGuard spec requires for n-of-m elections that there be *exactly* n counters that are one,
   * with the rest zero, so if a voter deliberately undervotes, one or more of the placeholder counters will
   * become one. This allows the `ConstantChaumPedersenProof` to verify correctly for undervoted contests.)
   */
  @Immutable
  public static class ContestDescriptionWithPlaceholders extends ContestDescription {
    final ImmutableList<SelectionDescription> placeholder_selections;

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
      this.placeholder_selections = toImmutableListEmpty(placeholder_selections);
    }

    boolean is_valid() {
      boolean contest_description_validates = super.is_valid();
      return contest_description_validates && this.placeholder_selections.size() == this.number_elected;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ContestDescriptionWithPlaceholders that = (ContestDescriptionWithPlaceholders) o;
      return Objects.equals(placeholder_selections, that.placeholder_selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), placeholder_selections);
    }

    /**
     * Gets the description for a particular id
     * @param selection_id: Id of Selection
     * @return description
     */
    Optional<SelectionDescription> selection_for(String selection_id) {

      Optional<SelectionDescription> first_match = this.ballot_selections.stream()
              .filter(s -> s.object_id.equals(selection_id)).findFirst();
      if (first_match.isPresent()) {
        return first_match;
      }

      return this.placeholder_selections.stream()
              .filter(s -> s.object_id.equals(selection_id)).findFirst();
    }
  }

  /**
   * The election metadata that describes the structure and type of the election, including geopolitical units,
   * contests, candidates, and ballot styles, etc. This class is
   * based on the NIST Election Common Standard Data Specification.  Some deviations
   * from the standard exist.
   * <p>
   * See: https://developers.google.com/elections-data/reference/election
   */
  @Immutable
  public static class ElectionDescription implements Hash.CryptoHashable {
    public final String election_scope_id;
    public final ElectionType type;
    public final OffsetDateTime start_date;
    public final OffsetDateTime end_date;
    public final ImmutableList<GeopoliticalUnit> geopolitical_units;
    public final ImmutableList<Party> parties;
    public final ImmutableList<Candidate> candidates;
    public final ImmutableList<ContestDescription> contests;
    public final ImmutableList<BallotStyle> ballot_styles;
    public final Optional<InternationalizedText> name;
    public final Optional<ContactInformation> contact_information;

    public ElectionDescription(String election_scope_id,
                               ElectionType type,
                               OffsetDateTime start_date,
                               OffsetDateTime end_date,
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
      this.geopolitical_units = toImmutableListEmpty(geopolitical_units);
      this.parties = toImmutableListEmpty(parties);
      this.candidates = toImmutableListEmpty(candidates);
      this.contests = toImmutableListEmpty(contests);
      this.ballot_styles = toImmutableListEmpty(ballot_styles);
      this.name = Optional.ofNullable(name);
      this.contact_information = Optional.ofNullable(contact_information);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ElectionDescription that = (ElectionDescription) o;
      return Objects.equals(election_scope_id, that.election_scope_id) &&
              type == that.type &&
              Objects.equals(start_date, that.start_date) &&
              Objects.equals(end_date, that.end_date) &&
              Objects.equals(geopolitical_units, that.geopolitical_units) &&
              Objects.equals(parties, that.parties) &&
              Objects.equals(candidates, that.candidates) &&
              Objects.equals(contests, that.contests) &&
              Objects.equals(ballot_styles, that.ballot_styles) &&
              Objects.equals(name, that.name) &&
              Objects.equals(contact_information, that.contact_information);
    }

    @Override
    public int hashCode() {
      return Objects.hash(election_scope_id, type, start_date, end_date, geopolitical_units, parties, candidates, contests, ballot_styles, name, contact_information);
    }

    @Override
    public String toString() {
      return "ElectionDescription{" +
              "election_scope_id='" + election_scope_id + '\'' +
              ", type=" + type +
              ", start_date=" + start_date +
              ", end_date=" + end_date +
              ", geopolitical_units=" + geopolitical_units +
              ", parties=" + parties +
              ", candidates=" + candidates +
              ", contests=" + contests +
              ", ballot_styles=" + ballot_styles +
              ", name=" + name +
              ", contact_information=" + contact_information +
              '}';
    }

    // LOOK this isnt going to generate the same hash as the python code. So when reading in a published Election,
    //  take Election.crypto_hash as given.
    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.election_scope_id,
              this.type.name(),
              this.start_date.toEpochSecond(), // to_ticks(self.start_date), number of seconds since the unix epoch
              this.end_date.toEpochSecond(),
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
                  logger.atWarning().log(
                          "Election failed validation check: is_valid: ",
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
  }

  /**
   * The subset of the election description required by ElectionGuard to validate ballots are
   * correctly associated with an election. LOOK This component mutates the state of the Election Description.
   */
  public static class InternalElectionDescription {
    final ElectionDescription description;

    final ImmutableList<GeopoliticalUnit> geopolitical_units;
    final ImmutableList<ContestDescriptionWithPlaceholders> contests;
    final ImmutableList<BallotStyle> ballot_styles;
    final Group.ElementModQ description_hash;

    public InternalElectionDescription(ElectionDescription description) {
      this.description = description;
      this.geopolitical_units = description.geopolitical_units;
      this.contests = generate_contests_with_placeholders(description);
      this.ballot_styles = description.ballot_styles;
      this.description_hash = description.crypto_hash();
    }

    Optional<ContestDescriptionWithPlaceholders> contest_for(String contest_id) {
      return contests.stream().filter(c -> c.object_id.equals(contest_id)).findFirst();
    }

    /** Find the ballot style for a specified ballot_style_id */
    Optional<BallotStyle> get_ballot_style(String ballot_style_id) {
      return ballot_styles.stream().filter(bs -> bs.object_id.equals(ballot_style_id)).findFirst();
    }

    /** Get contests that have the given ballot style. */
    List<ContestDescriptionWithPlaceholders> get_contests_for(String ballot_style_id) {
      Optional<BallotStyle> style = this.get_ballot_style(ballot_style_id);
      if (style.isEmpty() || style.get().geopolitical_unit_ids.isEmpty()) {
        return new ArrayList<>();
      }
      // gp_unit_ids = [gp_unit_id for gp_unit_id in style.geopolitical_unit_ids]
      List<String> gp_unit_ids = new ArrayList<>(style.get().geopolitical_unit_ids.get());
      // contests = list(filter(lambda i: i.electoral_district_id in gp_unit_ids, this.contests)
      return this.contests.stream().filter(c -> gp_unit_ids.contains(c.electoral_district_id)).collect(Collectors.toList());
    }

    /**
     * For each contest, append the `number_elected` number
     * of placeholder selections to the end of the contest collection.
     */
    private ImmutableList<ContestDescriptionWithPlaceholders> generate_contests_with_placeholders(
            ElectionDescription description) {

      List<ContestDescriptionWithPlaceholders> contests = new ArrayList<>();
      for (ContestDescription contest : description.contests) {
        List<SelectionDescription> placeholder_selections = generate_placeholder_selections_from(contest, contest.number_elected);
        contests.add(contest_description_with_placeholders_from(contest, placeholder_selections));
      }
      return ImmutableList.copyOf(contests);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      InternalElectionDescription that = (InternalElectionDescription) o;
      return Objects.equals(geopolitical_units, that.geopolitical_units) &&
              Objects.equals(contests, that.contests) &&
              Objects.equals(ballot_styles, that.ballot_styles) &&
              Objects.equals(description_hash, that.description_hash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(geopolitical_units, contests, ballot_styles, description_hash);
    }
  }

  /** The constants for mathematical functions used for this election. */
  @Immutable
  public static class ElectionConstants {
    public final BigInteger large_prime; // large prime or p
    public final BigInteger small_prime; // small prime or q
    public final BigInteger cofactor;    // cofactor or r
    public final BigInteger generator;   // generator or g

    public ElectionConstants() {
      this(Group.P, Group.Q, Group.R, Group.G);
    }

    public ElectionConstants(BigInteger large_prime, BigInteger small_prime, BigInteger cofactor, BigInteger generator) {
      this.large_prime = large_prime;
      this.small_prime = small_prime;
      this.cofactor = cofactor;
      this.generator = generator;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ElectionConstants that = (ElectionConstants) o;
      return large_prime.equals(that.large_prime) &&
              small_prime.equals(that.small_prime) &&
              cofactor.equals(that.cofactor) &&
              generator.equals(that.generator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(large_prime, small_prime, cofactor, generator);
    }

    @Override
    public String toString() {
      return "ElectionConstants{" +
              "\n large_prime= " + large_prime +
              "\n small_prime= " + small_prime +
              "\n cofactor= " + cofactor +
              "\n generator= " + generator +
              "}";
    }
  }

  /**
   * The cryptographic context of an election that is configured during the Key Ceremony.
   * <p>
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that
   * this object includes fields that are populated in the course of encrypting an election
   * Specifically, `crypto_base_hash`, `crypto_extended_base_hash` and `elgamal_public_key`
   * are populated with election-specific information necessary for encrypting the election.
   * Refer to the [Electionguard Specification](https://github.com/microsoft/electionguard) for more information.
   * <p>
   * To make an instance of this class, don't construct it directly. Use `make_ciphertext_election_context` instead.
   * LOOK: put in its own class
   */
  @Immutable
  public static class CiphertextElectionContext {
    public final int number_of_guardians; // The number of guardians necessary to generate the public key
    public final int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`

    // the `joint public key (K)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    public final Group.ElementModP elgamal_public_key;

    // The hash of the election metadata
    public final Group.ElementModQ description_hash;

    // the `base hash code (𝑄)` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    public final Group.ElementModQ crypto_base_hash;

    // the `extended base hash code (𝑄')` in the [ElectionGuard Spec](https://github.com/microsoft/electionguard/wiki)
    public final Group.ElementModQ crypto_extended_base_hash;

    CiphertextElectionContext(int number_of_guardians, int quorum, ElementModP elgamal_public_key,
                                     ElementModQ description_hash, ElementModQ crypto_base_hash, ElementModQ crypto_extended_base_hash) {
      this.number_of_guardians = number_of_guardians;
      this.quorum = quorum;
      this.elgamal_public_key = elgamal_public_key;
      this.description_hash = description_hash;
      this.crypto_base_hash = crypto_base_hash;
      this.crypto_extended_base_hash = crypto_extended_base_hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CiphertextElectionContext that = (CiphertextElectionContext) o;
      return number_of_guardians == that.number_of_guardians &&
              quorum == that.quorum &&
              Objects.equals(elgamal_public_key, that.elgamal_public_key) &&
              Objects.equals(description_hash, that.description_hash) &&
              Objects.equals(crypto_base_hash, that.crypto_base_hash) &&
              Objects.equals(crypto_extended_base_hash, that.crypto_extended_base_hash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(number_of_guardians, quorum, elgamal_public_key, description_hash, crypto_base_hash, crypto_extended_base_hash);
    }
  }

  /**
   * Makes a CiphertextElectionContext object.
   * <p>
   * @param number_of_guardians: The number of guardians necessary to generate the public key
   * @param quorum: The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
   * @param elgamal_public_key: the public key of the election
   * @param description_hash: the hash of the election metadata
   */
  public static CiphertextElectionContext make_ciphertext_election_context(
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

    return new CiphertextElectionContext(
            number_of_guardians,
            quorum,
            elgamal_public_key,
            description_hash,
            crypto_base_hash,
            crypto_extended_base_hash);
  }

  /**
   * Generates a placeholder selection description.
   * @param description: contest description
   * @param placeholders: list of placeholder descriptions of selections
   * @return a SelectionDescription or None
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

  /**
   * Generates a placeholder selection description that is unique so it can be hashed.
   *
   * @param use_sequence_idO: an optional integer unique to the contest identifying this selection's place in the contest
   * @return a SelectionDescription or None
   */
  static Optional<SelectionDescription> generate_placeholder_selection_from(
          ContestDescription contest, Optional<Integer> use_sequence_idO) {

    // sequence_ids = [selection.sequence_order for selection in contest.ballot_selections]
    List<Integer> sequence_ids = contest.ballot_selections.stream().map(s -> s.sequence_order).collect(Collectors.toList());

    int use_sequence_id;
    if (use_sequence_idO.isEmpty()) {
      // if no sequence order is specified, take the max
      use_sequence_id = sequence_ids.stream().max(Integer::compare).orElse(0) + 1;
    } else {
      use_sequence_id = use_sequence_idO.get();
      if (sequence_ids.contains(use_sequence_id)) {
        logger.atWarning().log("mismatched placeholder selection %s already exists", use_sequence_id);
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
   * Generates the specified number of placeholder selections in ascending sequence order from the max selection sequence order
   * <p>
   * @param contest: ContestDescription for input
   * @param count: optionally specify a number of placeholders to generate
   * @return a collection of `SelectionDescription` objects, which may be empty
   */
  static List<SelectionDescription> generate_placeholder_selections_from(ContestDescription contest, int count) {
    //  max_sequence_order = max([selection.sequence_order for selection in contest.ballot_selections]);
    int max_sequence_order = contest.ballot_selections.stream().map(s -> s.sequence_order).max(Integer::compare).orElse(0);
    List<SelectionDescription> selections = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int sequence_order = max_sequence_order + 1 + i;
      Optional<SelectionDescription> sd = generate_placeholder_selection_from(contest, Optional.of(sequence_order));
      selections.add(sd.orElseThrow(IllegalStateException::new));
    }
    return selections;
  }

  private static <T> ImmutableList<T> toImmutableList(List<T> from) {
    if (from == null || from.isEmpty()) {
      return null;
    }
    return ImmutableList.copyOf(from);
  }

  private static <T> ImmutableList<T> toImmutableListEmpty(List<T> from) {
    if (from == null || from.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(from);
  }
}