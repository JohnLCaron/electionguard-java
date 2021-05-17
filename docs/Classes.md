# 🗳 ElectionGuard Java Classes

## KeyCeremony

| python | standard java | remote java |
| --- | --- | --- |
| key_ceremony | KeyCeremony | KeyCeremony2 |
| key_ceremony_helper | KeyCeremonyHelper |  |
| key_ceremony_mediator | KeyCeremonyMediator | KeyCeremonyRemoteMediator |
| PublicKeySet | KeyCeremony.PublicKeySet | KeyCeremonyTrustee |

## Decryption

| python | standard java | remote java |
| --- | --- | --- |
| decryption | Decryptions | RemoteDecryptions |
| decryption_helper | DecryptionHelper | |
| decryption_mediator | DecryptionMediator | DecryptingMediator |
| decrypt_with_shares | DecryptWithShares | same |
| decryption_share | DecryptionShare | same |
| ElectionPublicKey | KeyCeremony.ElectionPublicKey | DecryptingTrustee |

