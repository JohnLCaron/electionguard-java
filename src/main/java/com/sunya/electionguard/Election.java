package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.*;

import static com.sunya.electionguard.Group.*;

/**
 * Election Manifest.
 * see: https://developers.google.com/elections-data/reference
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
    special,  //	For an election held out of sequence for special circumstances, for example, to fill a vacated office.
    other     //	Used when the election type is not listed in this enumeration. If used, include a specific value of the OtherType element.
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
      this.annotation = Preconditions.checkNotNull(annotation);
      this.value = Preconditions.checkNotNull(value);
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
      return annotation.equals(that.annotation) &&
              value.equals(that.value);
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
      this.value = Preconditions.checkNotNull(value);
      this.language = Preconditions.checkNotNull(language);
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
      return value.equals(language1.value) &&
              language.equals(language1.language);
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
      this.text = toImmutableListEmpty(text);
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
      return text.equals(that.text);
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
    public final ImmutableList<String> address_line; // may be empty
    public final ImmutableList<AnnotatedString> email; // may be empty
    public final ImmutableList<AnnotatedString> phone; // may be empty
    public final Optional<String> name;

    public ContactInformation(@Nullable List<String> address_line,
                              @Nullable List<AnnotatedString> email,
                              @Nullable List<AnnotatedString> phone,
                              @Nullable String name) {
      this.address_line = toImmutableListEmpty(address_line);
      this.email = toImmutableListEmpty(email);
      this.phone = toImmutableListEmpty(phone);
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
      return address_line.equals(that.address_line) &&
              email.equals(that.email) &&
              phone.equals(that.phone) &&
              name.equals(that.name);
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
      this.name = Preconditions.checkNotNull(name);
      this.type = Preconditions.checkNotNull(type);
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
      return name.equals(that.name) &&
              type == that.type &&
              contact_information.equals(that.contact_information);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name, type, contact_information);
    }
  }

  /** A BallotStyle works as a key to uniquely specify a set of contests. */
  @Immutable
  public static class BallotStyle extends ElectionObjectBase implements Hash.CryptoHashable {
    public final ImmutableList<String> geopolitical_unit_ids; // may be empty
    public final ImmutableList<String> party_ids; // may be empty
    public final Optional<String> image_uri;

    public BallotStyle(String object_id,
                       @Nullable List<String> geopolitical_unit_ids,
                       @Nullable List<String> party_ids,
                       @Nullable String image_uri) {
      super(object_id);
      this.geopolitical_unit_ids = toImmutableListEmpty(geopolitical_unit_ids);
      this.party_ids = toImmutableListEmpty(party_ids);
      this.image_uri = Optional.ofNullable(Strings.emptyToNull(image_uri));
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
      return geopolitical_unit_ids.equals(that.geopolitical_unit_ids) &&
              party_ids.equals(that.party_ids) &&
              image_uri.equals(that.image_uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), geopolitical_unit_ids, party_ids, image_uri);
    }

    @Override
    public String toString() {
      return object_id;
    }
  }

  /**
   * Use this entity to describe a political party that can then be referenced from other entities.
   * See: https://developers.google.com/elections-data/reference/party
   */
  @Immutable
  public static class Party extends ElectionObjectBase implements Hash.CryptoHashable {
    public final InternationalizedText name;
    public final Optional<String> abbreviation;
    public final Optional<String> color;
    public final Optional<String> logo_uri;

    public Party(String object_id) {
      super(object_id);
      this.name = new InternationalizedText(ImmutableList.of());
      this.abbreviation = Optional.empty();
      this.color = Optional.empty();
      this.logo_uri = Optional.empty();
    }

    public Party(String object_id,
                 InternationalizedText name,
                 @Nullable String abbreviation,
                 @Nullable String color,
                 @Nullable String logo_uri) {
      super(object_id);
      this.name = name != null ? name : new InternationalizedText(ImmutableList.of());
      this.abbreviation = Optional.ofNullable(Strings.emptyToNull(abbreviation));
      this.color = Optional.ofNullable(Strings.emptyToNull(color));
      this.logo_uri = Optional.ofNullable(Strings.emptyToNull(logo_uri));
    }

    String get_party_id() {
      return this.object_id;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id,
              this.name,
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
      return name.equals(party.name) &&
              abbreviation.equals(party.abbreviation) &&
              color.equals(party.color) &&
              logo_uri.equals(party.logo_uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name, abbreviation, color, logo_uri);
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
    public final InternationalizedText name;
    public final Optional<String> party_id;
    public final Optional<String> image_uri;
    public final boolean is_write_in;

    public Candidate(String object_id) {
      super(object_id);
      this.name = new InternationalizedText(ImmutableList.of());
      this.party_id = Optional.empty();
      this.image_uri = Optional.empty();
      this.is_write_in = false;
    }

    public Candidate(String object_id,
                     InternationalizedText name,
                     @Nullable String party_id,
                     @Nullable String image_uri,
                     @Nullable Boolean is_write_in) {
      super(object_id);
      this.name = Preconditions.checkNotNull(name);
      this.party_id = Optional.ofNullable(Strings.emptyToNull(party_id));
      this.image_uri = Optional.ofNullable(Strings.emptyToNull(image_uri));
      this.is_write_in = is_write_in != null ? is_write_in :  false;
    }

    /** Get the "candidate ID" for this Candidate. */
    String get_candidate_id() {
      return this.object_id;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(
              this.object_id, this.name, this.party_id, this.image_uri);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Candidate candidate = (Candidate) o;
      return is_write_in == candidate.is_write_in &&
              name.equals(candidate.name) &&
              party_id.equals(candidate.party_id) &&
              image_uri.equals(candidate.image_uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), name, party_id, image_uri, is_write_in);
    }

    @Override
    public String toString() {
      return object_id;
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
      Preconditions.checkArgument(!Strings.isNullOrEmpty(candidate_id));
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
              candidate_id.equals(that.candidate_id);
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
    public final Optional<Integer> votes_allowed; // LOOK why optional ?

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
                              int votes_allowed,
                              String name,
                              List<SelectionDescription> ballot_selections,
                              @Nullable InternationalizedText ballot_title,
                              @Nullable InternationalizedText ballot_subtitle) {
      super(object_id);
      Preconditions.checkArgument(!Strings.isNullOrEmpty(electoral_district_id));

      this.electoral_district_id = electoral_district_id;
      this.sequence_order = sequence_order;
      this.vote_variation = Preconditions.checkNotNull(vote_variation);
      this.number_elected = number_elected;
      this.votes_allowed = votes_allowed == 0 ? Optional.empty() : Optional.of(votes_allowed);
      this.name = Preconditions.checkNotNull(name);
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
              electoral_district_id.equals(that.electoral_district_id) &&
              vote_variation == that.vote_variation &&
              votes_allowed.equals(that.votes_allowed) &&
              name.equals(that.name) &&
              ballot_selections.equals(that.ballot_selections) &&
              ballot_title.equals(that.ballot_title) &&
              ballot_subtitle.equals(that.ballot_subtitle);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed, name, ballot_selections, ballot_title, ballot_subtitle);
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
                                       int votes_allowed,
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
      return primary_party_ids.equals(that.primary_party_ids);
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
                                        int votes_allowed,
                                        String name, List<SelectionDescription> ballot_selections,
                                        @Nullable InternationalizedText ballot_title,
                                        @Nullable InternationalizedText ballot_subtitle) {
      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed,
              name, ballot_selections, ballot_title, ballot_subtitle);
    }

  }

  /**
   * The election metadata that describes the structure and type of the election, including geopolitical units,
   * contests, candidates, and ballot styles, etc.
   * This class is based on the NIST Election Common Standard Data Specification.
   * Some deviations from the standard exist.
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
    public final ElementModQ crypto_hash;

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

      Preconditions.checkArgument(!Strings.isNullOrEmpty(election_scope_id));
      this.election_scope_id = election_scope_id;
      this.type = Preconditions.checkNotNull(type);
      this.start_date = Preconditions.checkNotNull(start_date);
      this.end_date = Preconditions.checkNotNull(end_date);
      this.geopolitical_units = toImmutableListEmpty(geopolitical_units);
      this.parties = toImmutableListEmpty(parties);
      this.candidates = toImmutableListEmpty(candidates);
      this.contests = toImmutableListEmpty(contests);
      this.ballot_styles = toImmutableListEmpty(ballot_styles);
      this.name = Optional.ofNullable(name);
      this.contact_information = Optional.ofNullable(contact_information);

      this.crypto_hash = Hash.hash_elems(
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ElectionDescription that = (ElectionDescription) o;
      return election_scope_id.equals(that.election_scope_id) &&
              type == that.type &&
              start_date.equals(that.start_date) &&
              end_date.equals(that.end_date) &&
              geopolitical_units.equals(that.geopolitical_units) &&
              parties.equals(that.parties) &&
              candidates.equals(that.candidates) &&
              contests.equals(that.contests) &&
              ballot_styles.equals(that.ballot_styles) &&
              name.equals(that.name) &&
              contact_information.equals(that.contact_information);
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

    @Override
    public Group.ElementModQ crypto_hash() {
      return this.crypto_hash;
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
        for (String gp_unit_id : style.geopolitical_unit_ids) {
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

  /** The constants for mathematical functions used for this election. LOOK maybe another class? */
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
      this.large_prime = Preconditions.checkNotNull(large_prime);
      this.small_prime = Preconditions.checkNotNull(small_prime);
      this.cofactor = Preconditions.checkNotNull(cofactor);
      this.generator = Preconditions.checkNotNull(generator);
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
   * LOOK doesnt belong in this class?
   * LOOK: add serialization version?
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

    public CiphertextElectionContext(int number_of_guardians, int quorum, ElementModP jointPublicKey,
           ElementModQ description_hash, ElementModQ crypto_base_hash, ElementModQ crypto_extended_base_hash) {
      this.number_of_guardians = number_of_guardians;
      this.quorum = quorum;
      this.elgamal_public_key = Preconditions.checkNotNull(jointPublicKey);
      this.description_hash = Preconditions.checkNotNull(description_hash);
      this.crypto_base_hash = Preconditions.checkNotNull(crypto_base_hash);
      this.crypto_extended_base_hash = Preconditions.checkNotNull(crypto_extended_base_hash);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CiphertextElectionContext that = (CiphertextElectionContext) o;
      return number_of_guardians == that.number_of_guardians &&
              quorum == that.quorum &&
              elgamal_public_key.equals(that.elgamal_public_key) &&
              description_hash.equals(that.description_hash) &&
              crypto_base_hash.equals(that.crypto_base_hash) &&
              crypto_extended_base_hash.equals(that.crypto_extended_base_hash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(number_of_guardians, quorum, elgamal_public_key, description_hash, crypto_base_hash, crypto_extended_base_hash);
    }
  }

  public static ElementModQ make_crypto_base_hash(int number_of_guardians, int quorum, ElectionDescription election) {
    return Hash.hash_elems(P, Q, G, number_of_guardians, quorum, election.crypto_hash());
  }

  /**
   * Makes a CiphertextElectionContext object.
   * @param number_of_guardians The number of guardians necessary to generate the public key.
   * @param quorum The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians.
   * @param elgamal_public_key the public key of the election.
   * @param description the election description.
   * @param commitment_hash all the public commitments for all the guardians = H(K 1,0 , K 1,1 , K 1,2 , ... ,
   *    K 1,k−1 , K 2,0 , K 2,1 , K 2,2 , ... , K 2,k−1 , ... , K n,0 , K n,1 , K n,2 , ... , K n,k−1 )
   */
  public static CiphertextElectionContext make_ciphertext_election_context(
          int number_of_guardians,
          int quorum,
          ElementModP elgamal_public_key,
          ElectionDescription description,
          ElementModQ commitment_hash) {

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

    ElementModQ crypto_base_hash = make_crypto_base_hash(number_of_guardians, quorum, description);
    ElementModQ crypto_extended_base_hash = Hash.hash_elems(crypto_base_hash, commitment_hash);

    return new CiphertextElectionContext(
            number_of_guardians,
            quorum,
            elgamal_public_key,
            description.crypto_hash(),
            crypto_base_hash,
            crypto_extended_base_hash);
  }

  private static <T> ImmutableList<T> toImmutableListEmpty(List<T> from) {
    if (from == null || from.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(from);
  }
}