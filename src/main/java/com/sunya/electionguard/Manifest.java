package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Manifest Manifest: defines the candidates, contests, and associated information for a specific election.
 * @see <a href="https://developers.google.com/elections-data/reference/election">Civics Common Standard Data Specification</a>
 */
@Immutable
public class Manifest implements Hash.CryptoHashable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  public final Group.ElementModQ crypto_hash;

  public Manifest(String election_scope_id,
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
            this.start_date.toString(), // python: to_iso_date_string, LOOK isnt all that well defined.
            this.end_date.toString(),
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
    Manifest that = (Manifest) o;
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
    return "Manifest{" +
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

  /** The Manifest (aka ElectionDescription) hash. */
  @Override
  public Group.ElementModQ crypto_hash() {
    return this.crypto_hash;
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
   * @see <a href="https://developers.google.com/elections-data/reference/annotated-string">Civics Common Standard Data Specification</a>
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

    @Override
    public String toString() {
      return "{" +
              "annotation='" + annotation + '\'' +
              ", value='" + value + '\'' +
              '}';
    }
  }

  /**
   * The ISO-639 language code.
   * @see <a href="https://en.wikipedia.org/wiki/ISO_639">ISO 639</a>
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

    @Override
    public String toString() {
      return value + " (" + language + ")";
    }
  }

  /**
   * Text that may have translations in multiple languages.
   * @see <a href="https://developers.google.com/elections-data/reference/internationalized-text">Civics Common Standard Data Specification</a>
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

    @Override
    public String toString() {
      return text.toString();
    }
  }

  /**
   * Contact information about persons, boards of authorities, organizations, etc.
   * @see <a href="https://developers.google.com/elections-data/reference/contact-information">Civics Common Standard Data Specification</a>
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

    @Override
    public String toString() {
      return "{" +
              "address_line=" + address_line +
              "\n email=" + email +
              "\n phone=" + phone +
              "\n name=" + name +
              '}';
    }
  }

  /**
   * A physical or virtual unit of representation or vote/seat aggregation.
   * Use this entity to define geopolitical units such as cities, districts, jurisdictions, or precincts
   * to associate contests, offices, vote counts, or other information with those geographies.
   * @see <a href="https://developers.google.com/elections-data/reference/gp-unit">Civics Common Standard Data Specification</a>
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

    @Override
    public String toString() {
      return "GeopoliticalUnit{" +
              "\n object_id='" + object_id + '\'' +
              "\n name='" + name + '\'' +
              "\n type=" + type +
              "\n contact_information=" + contact_information +
              '}';
    }
  }

  /** Classifies a set of contests by their set of parties and geopolitical units */
  @Immutable
  public static class BallotStyle extends ElectionObjectBase implements Hash.CryptoHashable {
    public final ImmutableList<String> geopolitical_unit_ids; // matches GeoPoliticalUnit.object_id; may be empty
    public final ImmutableList<String> party_ids; // matches Party.object_id; may be empty
    public final Optional<String> image_uri;

    /**
     * Constructor.
     * @param object_id A unique name
     * @param geopolitical_unit_ids matches GeoPoliticalUnit.object_id; may be empty
     * @param party_ids matches Party.object_id; may be empty
     * @param image_uri an optional image_uri for this BallotStyle
     */
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
      return "BallotStyle{" +
              "\n object_id='" + object_id + '\'' +
              "\n geopolitical_unit_ids=" + geopolitical_unit_ids +
              "\n party_ids=" + party_ids +
              "\n image_uri=" + image_uri +
              '}';
    }
  }

  /**
   * A political party.
   * @see <a href="https://developers.google.com/elections-data/reference/party">Civics Common Standard Data Specification</a>
   */
  @Immutable
  public static class Party extends ElectionObjectBase implements Hash.CryptoHashable {
    public final InternationalizedText name;
    public final Optional<String> abbreviation;
    public final Optional<String> color;
    public final Optional<String> logo_uri;

    /** A Party with only an object_id. */
    public Party(String object_id) {
      super(object_id);
      this.name = new InternationalizedText(ImmutableList.of());
      this.abbreviation = Optional.empty();
      this.color = Optional.empty();
      this.logo_uri = Optional.empty();
    }

    /** A Party with an object_id and a name, and optional other fields. */
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

    @Override
    public String toString() {
      return "Party{" +
              "\n object_id='" + object_id + '\'' +
              "\n name=" + name +
              "\n abbreviation=" + abbreviation +
              "\n color=" + color +
              "\n logo_uri=" + logo_uri +
              '}';
    }
  }

  /**
   * A candidate in a contest.
   * Note: The ElectionGuard Data Spec deviates from the NIST model in that selections for any contest type
   * are considered a "candidate". for instance, on a yes-no referendum contest, two `candidate` objects
   * would be included in the model to represent the `affirmative` and `negative` selections for the contest.
   * @see <a href="https://developers.google.com/elections-data/reference/candidate">Civics Common Standard Data Specification</a>
   */
  @Immutable
  public static class Candidate extends ElectionObjectBase implements Hash.CryptoHashable {
    public final InternationalizedText name;
    public final Optional<String> party_id;
    public final Optional<String> image_uri;
    public final boolean is_write_in;

    /** A Candidate with only an object_id. */
    public Candidate(String object_id) {
      super(object_id);
      this.name = new InternationalizedText(ImmutableList.of());
      this.party_id = Optional.empty();
      this.image_uri = Optional.empty();
      this.is_write_in = false;
    }

    /** A Candidate with an object_id and a name, and optional other fields. */
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
      return "Candidate{" +
              "\n object_id='" + object_id + '\'' +
              "\n name=" + name +
              "\n party_id=" + party_id +
              "\n image_uri=" + image_uri +
              "\n is_write_in=" + is_write_in +
              '}';
    }
  }

  /**
   * A ballot selection for a specific candidate in a contest.
   * @see <a href="https://developers.google.com/elections-data/reference/ballot-selection">Civics Common Standard Data Specification</a>
   */
  @Immutable
  public static class SelectionDescription implements OrderedObjectBaseIF, Hash.CryptoHashable {
    public final String object_id;
    public final String candidate_id;
    /**
     * Used for ordering selections in a contest to ensure various encryption primitives are deterministic.
     * The sequence order must be unique and should be representative of how the contests are represented
     * on a "master" ballot in an external system.  The sequence order is not required to be in the order
     * in which they are displayed to a voter.  Any acceptable range of integer values may be provided.
     */
    public final int sequence_order;

    public SelectionDescription(String object_id, String candidate_id, int sequence_order) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
      this.object_id = object_id;
      this.sequence_order = sequence_order;
      Preconditions.checkArgument(!Strings.isNullOrEmpty(candidate_id));
      this.candidate_id = candidate_id;
    }

    @Override
    public Group.ElementModQ crypto_hash() {
      return Hash.hash_elems(this.object_id, this.sequence_order, this.candidate_id);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SelectionDescription that = (SelectionDescription) o;
      return sequence_order == that.sequence_order && object_id.equals(that.object_id) && candidate_id.equals(that.candidate_id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(object_id, candidate_id, sequence_order);
    }

    @Override
    public String toString() {
      return "SelectionDescription{" +
              "object_id='" + object_id + '\'' +
              ", candidate_id='" + candidate_id + '\'' +
              ", sequence_order=" + sequence_order +
              '}';
    }

    @Override
    public String object_id() {
      return object_id;
    }

    @Override
    public int sequence_order() {
      return sequence_order;
    }
  }

  /**
   * The metadata that describes the structure and type of one contest in the election.
   * @see <a href="https://developers.google.com/elections-data/reference/contest">Civics Common Standard Data Specification</a>
   */
  @Immutable
  public static class ContestDescription implements OrderedObjectBaseIF, Hash.CryptoHashable {

    public final String object_id;

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

      Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
      this.object_id = object_id;

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
      ContestDescription that = (ContestDescription) o;
      return sequence_order == that.sequence_order && number_elected == that.number_elected && object_id.equals(that.object_id) && electoral_district_id.equals(that.electoral_district_id) && vote_variation == that.vote_variation && votes_allowed.equals(that.votes_allowed) && name.equals(that.name) && ballot_selections.equals(that.ballot_selections) && ballot_title.equals(that.ballot_title) && ballot_subtitle.equals(that.ballot_subtitle);
    }

    @Override
    public int hashCode() {
      return Objects.hash(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed, name, ballot_selections, ballot_title, ballot_subtitle);
    }

    @Override
    public String toString() {
      return "ContestDescription{" +
              "object_id='" + object_id + '\'' +
              ", electoral_district_id='" + electoral_district_id + '\'' +
              ", sequence_order=" + sequence_order +
              ", vote_variation=" + vote_variation +
              ", number_elected=" + number_elected +
              ", votes_allowed=" + votes_allowed +
              ", name='" + name + '\'' +
              ", ballot_selections=" + ballot_selections +
              ", ballot_title=" + ballot_title +
              ", ballot_subtitle=" + ballot_subtitle +
              '}';
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

    @Override
    public String object_id() {
      return object_id;
    }

    @Override
    public int sequence_order() {
      return sequence_order;
    }
  }

  /**
   * A type of contest that involves selecting one or more candidates.
   * @see <a href="https://developers.google.com/elections-data/reference/contest">Civics Common Standard Data Specification</a>
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
   * A type of contest that involves selecting exactly one 'candidate'. LOOK why needed?
   * @see <a href="https://developers.google.com/elections-data/reference/contest">Civics Common Standard Data Specification</a>
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

  private static <T> ImmutableList<T> toImmutableListEmpty(List<T> from) {
    if (from == null || from.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(from);
  }
}
