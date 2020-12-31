# 🗳 ElectionGuard Java

This is a port of https://github.com/microsoft/electionguard-python/ to Java.
It  closely follows the classes, naming and file structure of the original, to facilitate 
line-by-line comparisions.

It is offered under the same license to be freely used, modified, developed, etc. 
Im also happy to donate it to another organization for hosting, etc.

## TODO

  * Not yet compatible with Python library JSON serialization, due to different ways to serialize Optional.

## Notes

 * Uses Java's BigInteger for cryptographic computations. Will look at performance later to see if using GMP is justified.
 * Provisionally using AutoValue for (some) immutable value classes.
 * Uses JUnit5 / jqwik library for property based tesing.
 * Uses Gson and plugins for JSON serialization.
 * Uses Flogger for logging.
 * Uses gradle for building.

## License

This repository is licensed under the [MIT License]


