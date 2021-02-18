# 🗳 ElectionGuard Java

## Differences with Python

### Json serialization

1. Optional fields

    * Python: Optional fields that are empty have _"None"_ in the serialization.
    * Java: Optional fields that are empty have a _null_ or are left out.

2. Spoiled plaintext tallies

    * Python: Each spoiled ballot tally is stored inside the election plaintext tally. 
      Java library does not currently have access to those, anticipate PR #305 will fix.
    * Java: Each spoiled ballot tally stored in a separate file in *spoiled_tallies/ballot_<ballot_id>*
      Placeholder selections are removed.

3. Spoiled plaintext ballots

    * Python: These are not currently serialized to the election record. 
      Spoiled ciphertext ballots are mistakenly being written instead, anticipate PR #305 will fix.
    * Java: Each spoiled ballot stored in a separate file in *spoiled_ballots/ballot_<ballot_id>*
      Placeholder selections are removed.
