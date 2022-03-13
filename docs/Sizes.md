# 🗳 ElectionGuard Java

## Protobuf Sizes
Mar 12 13:07

### jefferson-county-primary = 6 total selections

| Primes   | # Ballots | Encrypted Size | Encryption per | # Cast | Accumulate | Decrypting | Verify  |
|----------|-----------|----------------|----------------|--------|------------|------------|---------|
| Standard | 10        | 23K            | 804 ms         | 5/5    | 1 sec      | 14 sec     | 3  sec  | 34
| Standard | 100       | 23K            | 715 ms         | 53/47  | 12 sec     | 66 sec     | 30  sec | 187
| Standard | 100       | 23K            | 727 ms         | 53/47  | 11 sec     | 8 sec *    | 23  sec | 122
| Standard | 200       | 23K            | 716 ms         | 105/95 | 22 sec     | 120 sec    | 59  sec | 352

### kickstart 25 contests x 4 selections = 100 total selections

| Primes     | # Ballots | Encrypted Size | Encryption per | # Cast | Accumulate | Decrypting | Verify  |
|------------|-----------|----------------|----------------|--------|------------|------------|---------|
| Standard   | 10        | 480K           | 15.419 sec     | 5/5    | 22 sec     | 154 sec    | 72 sec  |
| Standard   | 10        | 480K           | 15.115 sec     | 6/4    | 27 sec     | 28 sec *   | 52 sec  | 266
| Standard   | 30        | 480K           | 15.107 sec     | 22/8   | 99 sec     | 228 sec    | 181 sec | 969
| LargeTest  | 100       | 20K            | 00.023 sec     | 45/55  | 1 sec      | 10 sec     | 1 sec   | 22
| MediumTest | 100       | 15K            | 00.023 sec     | 47/53  | 1 sec      | 9 sec      | 1 sec   | 20

| SmallTest      | invalid Schnorr proof 
| ExtraSmallTest | encryption failed 