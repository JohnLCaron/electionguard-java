package com.sunya.electionguard.standard;

import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ballot.EncryptedBallot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper to assist in the decryption process particularly for testing.
 */
public class DecryptionHelper {

  // This only works when there are no missing guardians, since all_guardians_keys must include missing.
  public static void perform_decryption_setup(
          List<Guardian> available_guardians,
          DecryptionMediator mediator,
          ElectionCryptoContext context,
          EncryptedTally ciphertext_tally,
          List<EncryptedBallot> spoiled_ballots) {

    announcement(
            available_guardians,
            available_guardians.stream().map(g -> g.share_key()).toList(),
            mediator,
            context,
            ciphertext_tally,
            spoiled_ballots);
  }

  // Perform the necessary setup to ensure that a mediator can decrypt when there are guardians missing
  public static void perform_compensated_decryption_setup(
          List<Guardian> all_guardians,
          int quorum,
          DecryptionMediator mediator,
          ElectionCryptoContext context,
          EncryptedTally ciphertext_tally,
          List<EncryptedBallot> spoiled_ballots) {

    announcement(
            all_guardians.subList(0, quorum),
            all_guardians.stream().map(g -> g.share_key()).toList(),
            mediator,
            context,
            ciphertext_tally,
            spoiled_ballots);

    exchange_compensated_decryption_shares(
            all_guardians.subList(0, quorum), mediator, context, ciphertext_tally, spoiled_ballots);
  }

  // Perform the necessary setup to ensure that a mediator can decrypt when there are guardians missing
  public static void perform_compensated_decryption_setup(
          List<Guardian> available_guardians,
          List<KeyCeremony.ElectionPublicKey> all_guardians_keys,
          DecryptionMediator mediator,
          ElectionCryptoContext context,
          EncryptedTally ciphertext_tally,
          List<EncryptedBallot> spoiled_ballots) {

            announcement(
            available_guardians,
            all_guardians_keys,
            mediator,
            context,
            ciphertext_tally,
            spoiled_ballots);

    exchange_compensated_decryption_shares(
            available_guardians, mediator, context, ciphertext_tally, spoiled_ballots);
  }

  /** Each available guardian announces their presence. The missing guardians are also announced. */
  public static void announcement(
          List<Guardian> available_guardians,
          List<KeyCeremony.ElectionPublicKey> all_guardians_keys,
          DecryptionMediator mediator,
          ElectionCryptoContext context,
          EncryptedTally ciphertext_tally,
          List<EncryptedBallot> spoiled_ballots) {

    if (spoiled_ballots == null) {
      spoiled_ballots = new ArrayList<>();
    }

    // Announce available guardians
    for (Guardian available_guardian : available_guardians) {
      KeyCeremony.ElectionPublicKey guardian_key = available_guardian.share_key();
      DecryptionShare tally_share = available_guardian.compute_tally_share(ciphertext_tally, context).orElseThrow();
      Map<String, Optional<DecryptionShare>> ballot_shares = available_guardian.compute_ballot_shares(spoiled_ballots, context);
      mediator.announce(guardian_key, tally_share, ballot_shares);
    }

    // Announce missing guardians
    // Get all guardian keys and filter to determine the missing guardians
    List<String> available_guardian_ids = available_guardians.stream()
            .map(g -> g.object_id)
            .toList();
    List<KeyCeremony.ElectionPublicKey> missing_guardians = all_guardians_keys.stream()
            .filter(k -> !available_guardian_ids.contains(k.owner_id()))
            .toList();

    for (KeyCeremony.ElectionPublicKey missing_guardian_key : missing_guardians) {
      mediator.announce_missing(missing_guardian_key);
    }
  }

  // Available guardians generate the compensated decryption shares for the missing guardians and send to the mediator.
 static void exchange_compensated_decryption_shares(
          List<Guardian> available_guardians,
          DecryptionMediator mediator,
          ElectionCryptoContext context,
          EncryptedTally ciphertext_tally,
          @Nullable List<EncryptedBallot> spoiled_ballots) {

   if (spoiled_ballots == null) {
     spoiled_ballots = new ArrayList<>();
   }

   // Exchange compensated shares
   List<KeyCeremony.ElectionPublicKey> missing_guardians = mediator.get_missing_guardians();
   for (Guardian available_guardian : available_guardians) {
     for (KeyCeremony.ElectionPublicKey missing_guardian : missing_guardians) {
       Optional<DecryptionShare.CompensatedDecryptionShare>  tally_share = available_guardian.compute_compensated_tally_share(
               missing_guardian.owner_id(),
               ciphertext_tally,
               context);

       tally_share.ifPresent(mediator::receive_tally_compensation_share);

       Map<String, Optional<DecryptionShare.CompensatedDecryptionShare>> ballot_shares = available_guardian.compute_compensated_ballot_shares(
                       missing_guardian.owner_id(),
                       spoiled_ballots,
                       context);

       Map<String, DecryptionShare.CompensatedDecryptionShare> valid_ballot_shares = get_valid_ballot_shares(ballot_shares);

       mediator.receive_ballot_compensation_shares(valid_ballot_shares);
     }
   }

   // Combine compensated shares into decryption share for missing guardians
   mediator.reconstruct_shares_for_tally(ciphertext_tally);
   mediator.reconstruct_shares_for_ballots(spoiled_ballots);
 }

  private static Map<String, DecryptionShare.CompensatedDecryptionShare> get_valid_ballot_shares(
          Map<String, Optional<DecryptionShare.CompensatedDecryptionShare>> ballot_shares) {
    return ballot_shares.entrySet().stream().filter(e -> e.getValue().isPresent()).collect(
            Collectors.toMap(e -> e.getKey(), e -> e.getValue().get()));
  }

}
