# Changelog

## 0.3.0 - 2026-04-08

### Added

- Published release metadata and workflows for Maven Central and the Gradle Plugin Portal.
- Added CI publishing preflight coverage for signed Maven deploys and Gradle publication validation.
- Added a Gradle functional test that verifies `cognitive-java-check` reuses the configuration cache across consecutive runs.

### Changed

- Retired GitHub Packages from consumer documentation and release automation.
- Updated the shared `crap-java` gate to the published `0.4.1` CLI from Maven Central.
- Updated the contribution guide to recommend repository-neutral branch names.
- Reworked GitHub release automation to generate release notes from this changelog.

### Publishing

- Maven artifacts now publish through Sonatype Central Portal with sources, javadocs, and GPG signing.
- The Gradle plugin now publishes both Maven Central publications and the `media.barney.cognitive-java` Plugin Portal release.
