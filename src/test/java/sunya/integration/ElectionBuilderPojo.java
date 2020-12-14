package sunya.integration;

import java.util.List;

/** POJO'S for reading Election metadata in using Gson. */
public class ElectionBuilderPojo {
  InternationalizedText name;
  String election_scope_id;
  String type;
  String start_date;
  String end_date;
  ContactInformation contact_information;

  List<GeopoliticalUnit> geopolitical_units;
  List<Party> parties;
  List<Candidate> candidates;
  List<ContestDescription> contests;
  List<BallotStyle> ballot_styles;

  static class AnnotatedString {
    String annotation;
    String value;
  }

  static class BallotStyle extends ElectionObjectBase {
    List<String> geopolitical_unit_ids;
    List<String> party_ids;
    String image_uri;
  }

  static class Candidate extends ElectionObjectBase {
    InternationalizedText ballot_name;
    String party_id;
    String image_uri;
    boolean is_write_in;
  }

  static class ContestDescription extends ElectionObjectBase {
    String electoral_district_id;
    int sequence_order;
    String vote_variation;
    int number_elected;
    int votes_allowed;
    String name;
    List<SelectionDescription> ballot_selections;
    InternationalizedText ballot_title;
    InternationalizedText ballot_subtitle;
  }

  static class ContactInformation {
    List<String> address_line;
    List<AnnotatedString> email;
    List<AnnotatedString> phone;
    String name;
  }

  static class ElectionObjectBase {
    String object_id;
  }

  static class GeopoliticalUnit extends ElectionObjectBase {
    String name;
    String type;
    ContactInformation contact_information;
  }

  static class InternationalizedText {
    List<Language> text;
  }

  static class Language {
    String value;
    String language;
  }

  static class Party extends ElectionObjectBase {
    InternationalizedText ballot_name;
    String abbreviation;
    String color;
    String logo_uri;
  }

  static class SelectionDescription extends ElectionObjectBase {
    String candidate_id;
  }

}