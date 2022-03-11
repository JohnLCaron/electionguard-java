# 🗳 ElectionGuard Java

## Attack SubmittedBallot

Attack the SubmittedBallot by switching the selectionId (see TestAttackSubmittedBallot). Verifier does not currently 
catch this.

1. The attack fails when accumulating the tally, because CiphertextTally.batch_append() calls 
BallotValidations.ballot_is_valid_for_election(), which calls CiphertextBallot.is_valid_encryption() which
fails with message "CiphertextBallot.Selection mismatching crypto hash". 

That fails because is_valid_encryption() recalculates the selection.crypto_hash = Hash.hash_elems(selectionId, selectionHash, ciphertext.crypto_hash())
and compares to the original crypto_hash. Thus the selection_id and the ciphertext cannot be switched. 
The ciphertext is the encrypted vote count for that selection.

If the CiphertextBallot.is_valid_encryption check was removed, the CiphertextTally would be changed and the verifier
would not catch it. So the verifier should also check each ballot with CiphertextBallot.is_valid_encryption().

2. If one recalculates the crypto_hash for the selection in the attack, then is_valid_encryption() fails with
"CiphertextBallot.Contest mismatching crypto hash".

If one recalculates the crypto_hash for the contest in the attack, then is_valid_encryption() fails with
"CiphertextBallot mismatching crypto hash".

If one recalculates the crypto_hash for the ballot in the attack, then is_valid_encryption() succeeds, and CiphertextTally
is incorrect. However, the verifier fails on ballot chaining. One would need to be able to recalculate the entire chain.