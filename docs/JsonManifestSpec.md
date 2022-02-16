# 🗳 Election Manifest JSON serialization (proposed specification)
draft 2/15/2022

# Data Types

| Data Type		 | JSON	                                            | language type                                                               |
|-------------|--------------------------------------------------|-----------------------------------------------------------------------------|
| BigInteger  | string	                                          | decimal encoded integer, arbitrary number of digits. LOOK or hex or base64? |
| bool		      | boolean                                          | boolean                                                                     |
| isodate	    | string	                                          | ISO 8601 formatted date/time                                                |
| timestamp	  | number	                                          | 64 bit signed integer = secs since unix epoch  LOOK or millis?              |
| Dict[K,V]	  | array of objects V with name K	 | Dict[K,V], Map<K, V>                                                        |
| ElementModQ | string	                                          | BigInteger converted to ElementModQ                                         |
| ElementModP | string	                                          | BigInteger converted to ElementModP                                         |
| enum<T>	    | string                                           | matches the name of an element of enum T                                    |
| int		       | number	                                          | 32 bit signed integer                                                       |
| List[T]	    | array                                            | 0 or more objects of type T                                                 |
| ulong		     | number	                                          | 64 bit unsigned integer                                                     |
| str		       | string	                                          | UTF-8 encoded string                                                        |
| T		         | object	                                          | T                                                                           |

Notes

  1. Any field may be missing or null. Could try to document when fields must be present 
  3. Could document the filenames and directory layout
  4. BigInteger currently inconsistent
  5. bool currently inconsistent
  6. ulong only used for EncryptionDevice.device_id, could switch that to str.
    
## Manifest

Its possible this should be simplified to be just the fields needed by electionguard. 
Assume that there is an existing system that captures all the metadata that election officials need, which is a 
superset of this.

### class Manifest
| Name		 | Type	 | Notes                                                |
|--------|-------|------------------------------------------------------|
|    election_scope_id| str  ||
|    spec_version| str		| add library/serialization version, manifest version? |
|    type| enum<ElectionType>  ||
|    start_date| isodate |                                                      |
|    end_date| isodate  |                                                      |
|    geopolitical_units| List[GeopoliticalUnit]  ||
|    parties| List[Party]  ||
|    candidates| List[Candidate]  ||
|    contests| List[ContestDescription]  ||
|    ballot_styles| List[BallotStyle]  ||
|    name| InternationalizedText  ||
|    contact_information| ContactInformation  ||

### class AnnotatedString
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    annotation| str  ||
|    value| str  ||

### class BallotStyle
| Name		 | Type	 | Notes                                   |
|--------|-------|-----------------------------------------|
|    object_id| str  ||
|    geopolitical_unit_ids| List[str]  | matches GeoPoliticalUnit.object_id      |
|    party_ids| List[str]  | matches Party.object_id? LOOK How used? |
|    image_uri| str  ||

### class Candidate
| Name		 | Type	 | Notes                                                                       |
|--------|-------|-----------------------------------------------------------------------------|
|    object_id| str  ||
|    name| InternationalizedText  ||
|    party_id| str  | matches Party.object_id                                                     |
|    image_uri| str  ||
|    is_write_in| bool  | LOOK this means you dont know the manifest until youve read all the ballots |

### class ContactInformation
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    name| str  ||
|    address_line| List[str]  ||
|    email| List[str]  ||
|    phone| List[str]  ||

### class GeopoliticalUnit
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str  ||
|    name| str  ||
|    type| enum<ReportingUnitType>  ||
|    contact_information| ContactInformation  ||

### class InternationalizedText
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    text| List<Language>  ||

### class Language
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    value| str  ||
|    language| str  ||

### class Party
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str  ||
|    name| InternationalizedText  ||
|    abbreviation| str  ||
|    color| str  ||
|    logo_uri| str  ||

### class ContestDescription
| Name		 | Type	 | Notes                           |
|--------|-------|---------------------------------|
|    object_id| str  ||
|    sequence_order| int  | deterministic sorting           |
|    electoral_district_id| str  | matches GeoPoliticalUnit.object_id |
|    vote_variation| enum<VoteVariationType>  ||
|    number_elected| int  ||
|    votes_allowed| int  ||
|    name| str  ||
|    ballot_selections| List[SelectionDescription]  ||
|    ballot_title| InternationalizedText  ||
|    ballot_subtitle| InternationalizedText  ||
|    primary_party_ids| List[str]  | Added from  CandidateContestDescription |

### class SelectionDescription
| Name		 | Type	 | Notes                        |
|--------|-------|------------------------------|
|    object_id| str  ||
|    sequence_order| int  | deterministic sorting        |
|    candidate_id| str  | matches Candidate.object_id  |
