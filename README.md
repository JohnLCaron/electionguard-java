# 🗳 ElectionGuard Java

This is a port of https://github.com/microsoft/electionguard-python/ to Java.
It  closely follows the classes, naming and file structure of the original, to facilitate 
line-by-line comparison.

It is offered under the same license to be freely used, modified, developed, etc. 
Im also happy to donate it to another organization for hosting, etc.

## Notes

 * Most classes are immutable. 
 * Assumes Java 11. No Java 11 features are used (yet), but I dont see any reason to stay compatible with 8.
 * Uses Java's BigInteger for cryptographic computations.
 * Provisionally using AutoValue for (some) immutable value classes.
 * Uses Gson and related plugins for JSON serialization. 
 * Uses Flogger for logging.
 * Uses JUnit5 / jqwik library for property based testing.
 * Uses gradle for building.
 
## TODO

  * Fix issues in validation.
  * Not yet compatible with Python library JSON serialization, due to different ways to serialize Optional.
  * Look at BigInteger performance, consider using GMP through jni or jna.
  * Measure performance.
  * Investigate parallelization strategies.

## License

This repository is licensed under the [MIT License]


