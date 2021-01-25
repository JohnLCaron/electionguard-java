# 🗳 ElectionGuard Java

This is a port of https://github.com/microsoft/electionguard-python/ to Java.
It  closely follows the classes, naming and file structure of the original, to facilitate 
line-by-line comparison.

It is offered under the same license to be freely used, modified, developed, etc. 
Im also happy to donate it to another organization for hosting, etc.

## Notes

 * Most classes are immutable. 
 * Assumes Java 11.
 * Uses Java's BigInteger for cryptographic computations.
 * Uses Gson for JSON serialization. 
 * Can also serialize to protobuf.
 * Using AutoValue for (some) immutable value classes.
 * Uses Flogger for logging.
 * Uses JUnit5 / jqwik library for property based testing.
 * Uses gradle for building.
 
## TODO

  * Fix issues in validation.
  * Review uses of Optional and null.
  * Not yet compatible with Python library JSON serialization, due to different ways to serialize Optional.
  * Measure computational performance.
  * Look at BigInteger performance, consider if using GMP is justified.
  * Investigate parallelization strategies.

## License

This repository is licensed under the [MIT License]


