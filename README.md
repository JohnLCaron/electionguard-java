# 🗳 ElectionGuard Java

This is a port of https://github.com/microsoft/electionguard-python/ to Java.

It is offered under the same license to be freely used, modified, developed, etc. 
Im also happy to donate it to another organization for hosting, etc.

## Notes

 * Full validation spec is implemented.
 * Assumes Java 11.
 * Most classes are immutable, anticipating need for multithreading. 
 * Uses Java's BigInteger for cryptographic computations.
 * Uses Gson for JSON serialization. 
 * Can also serialize using protobuf.
 * Using AutoValue for (some) immutable value classes.
 * Uses Flogger for logging.
 * Uses JUnit5 / jqwik library for property based testing.
 * Uses gradle for building.
 
## TODO

  * Not yet compatible with Python library JSON serialization, due to different ways to serialize Optional.
  * Measure computational performance.
  * Look at BigInteger performance, consider if using GMP is justified.
  * Investigate parallelization strategies.

## License

This repository is licensed under the [MIT License]


