package com.sunya.electionguard.publish;

import java.util.List;

/** Helper class for conversion of Election description to/from Json. */
public class ElectionDescriptionPojo {
  public InternationalizedText name;
  public String election_scope_id;
  public String type;
  public String start_date; // ISO-8601 Local or UTC? Assume local has zone offset
  public String end_date; // ISO-8601 Local or UTC? Assume local has zone offset
  public ContactInformation contact_information;

  public List<GeopoliticalUnit> geopolitical_units;
  public List<Party> parties;
  public List<Candidate> candidates;
  public List<ContestDescription> contests;
  public List<BallotStyle> ballot_styles;

  public static class AnnotatedString {
    public String annotation;
    public String value;
  }

  public static class BallotStyle extends ElectionObjectBase {
    public List<String> geopolitical_unit_ids;
    public List<String> party_ids;
    public String image_uri;
  }

  public static class Candidate extends ElectionObjectBase {
    public InternationalizedText name;
    public String party_id;
    public String image_uri;
    public Boolean is_write_in;
  }

  public static class ContestDescription extends ElectionObjectBase {
    public String electoral_district_id;
    public int sequence_order;
    public String vote_variation;
    public int number_elected;
    public int votes_allowed;
    public String name;
    public List<SelectionDescription> ballot_selections;
    public InternationalizedText ballot_title;
    public InternationalizedText ballot_subtitle;
  }

  public static class ContactInformation {
    public List<String> address_line;
    public List<AnnotatedString> email;
    public List<AnnotatedString> phone;
    public String name;
  }

  public static class ElectionObjectBase {
    public String object_id;
  }

  public static class GeopoliticalUnit extends ElectionObjectBase {
    public String name;
    public String type;
    public ContactInformation contact_information;
  }

  public static class InternationalizedText {
    public List<Language> text;
  }

  public static class Language {
    public String value;
    public String language;
  }

  public static class Party extends ElectionObjectBase {
    public InternationalizedText ballot_name;
    public String abbreviation;
    public String color;
    public String logo_uri;
  }

  public static class SelectionDescription extends ElectionObjectBase {
    public String candidate_id;
    public int sequence_order;
  }

}