
| Python         | Java                 | Kotlin                              | Notes                                                    |
|----------------|----------------------|-------------------------------------|----------------------------------------------------------|
| null, None     | null, Optional.empty | null                                | "null"                                                   |
| Optional[x]    | Optional.of(x)       |                                     | x                                                        |
| ElementModQ    |                      |                                     | 66 char hex encoded                                      |
| ElementModP    |                      |                                     | 1024 char hex encoded                                    |
| CryptoHashable | CryptoHashable       | CryptoHashableElement               | ElementModQ.cryptoHashString()                           |
|                | CryptoHashableString | CryptoHashableString                | cryptoHashString()                                       |
|                |                      | CryptoHashableUInt256               | UInt256.cryptoHashString()                               |
| empty Iterable | empty Iterable       | empty Iterable                      | "null"                                                   |
| Iterable       |                      |                                     | hash_elems(*x).to_hex()                                  |
|                | Iterable             |                                     | hash_elems(asArray).to_hex()                             |
|                |                      | Iterable                            | recursive(it.toList().toTypedArray()).cryptoHashString() |
| str            | String               | String                              | str(x), toString()                                       |
| int            |                      | Number, UInt, ULong, UShort, UByte  | str(x), toString()                                       |
| else           |                      | IllegalArgumentException            | str(x), toString()                                       |

1. Each string is utf encoded.
2. The composite string is prefixed, suffixed and delimited by '|' and utf encoded.
3. The composite string is utf decoded into bytes and fed to sha256 digest.
4. hash signature
    1. hash_elems(*a: CryptoHashableAll) -> ElementModQ
    2. Group.ElementModQ hash_elems(Object... a)
    3. hashElements(vararg elements: Any?): UInt256
5. hex coding is upper case

## python

````
len = 61 hex = A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 62 hex = 9A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 63 hex = 49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 64 hex = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 65 hex = 0C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 66 hex = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 67 hex = 000C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 68 hex = 0000C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
````

## java

````
 len = 61 s1u = UInt256(0x000a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = 0A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 62 s1u = UInt256(0x009a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = 9A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 63 s1u = UInt256(0x049a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = 049A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 64 s1u = UInt256(0xc49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 65 s1u = UInt256(0xc49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 66 s1u = UInt256(0xc49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 67 s1u = UInt256(0xc49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
 len = 68 s1u = UInt256(0xc49a1e8053fba95f6b7cd3f3b30b101cdd595c435a46aecf2872f47f1c601206) s1q = C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
````

## kotlin

````
len = 61 s1u = UInt256(0x000A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 0A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 62 s1u = UInt256(0x009A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 009A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 63 s1u = UInt256(0x049A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 049A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 64 s1u = UInt256(0xC49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 65 s1u = UInt256(0xC49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 66 s1u = UInt256(0xC49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 67 s1u = UInt256(0xC49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
len = 68 s1u = UInt256(0xC49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206) s1q = 00C49A1E8053FBA95F6B7CD3F3B30B101CDD595C435A46AECF2872F47F1C601206
````