# Changelog

## Unreleased

## 0.7.0 - 2026-08-09

### Added

- Added GitLab-visible Cognitive Complexity metrics to JUnit testcase names and testcase-level `system-out` output.

### Changed

- Reduced the default Cognitive Complexity threshold from `15` to `8`.
- Refreshed compatible dependency, build-tool, and release-publication versions.

### Fixed

- Fixed Windows Gradle wrapper failure-path handling and stabilized locale-sensitive JUnit testcase names.

## 0.6.0 - 2026-06-01

### Changed

- Expanded Java parser coverage so constructors, compact record constructors, and methods in local or anonymous classes are included in reports and threshold checks.
- Hardened parser diagnostics for positionless javac errors and preserved AST-based package parsing when package-like text appears in comments or strings.
- Added granular CI quality gates for NullAway and SpotBugs across repository modules.

## 0.5.1 - 2026-05-24

### Added

- Added aligned primary-report controls, JUnit sidecars, report output paths, threshold overrides, and source-exclusion configuration across the CLI, Maven plugin, and Gradle plugin.
- Added consumer-facing documentation for CLI report controls, threshold overrides, report outputs, source roots, and exclusion configuration across the CLI, Maven plugin, and Gradle plugin.
- Added split self-hosted `cognitive-java Gate` JUnit sidecars for Maven-source and Gradle-plugin scans in CI.

### Changed

- Expanded repository CI validation to cover JDK `17`, `21`, and `25`, plus Windows path-sensitive verification.
- Updated release automation to run the repository `crap-java` and `cognitive-java` gates, signed Maven publication preflight, and Gradle publication metadata validation before publication.
- Refreshed source discovery, report publishing, and contributor documentation to match the static-analysis-only workflow and current published plugin behavior.

### Fixed

- Release validation now fails on Maven Javadoc errors and runs a signed Maven publication preflight before release publication.

### Publishing

- Reissued the interrupted `0.5.0` release as `0.5.1` while preserving `v0.5.0` as the half-published Maven-only source pointer.

## 0.4.0 - 2026-04-10

### Changed

- Reduced the fixed Cognitive Complexity threshold from `25` to `15` for the published CLI and plugins.
- Updated the threshold failure messaging, tests, and consumer-facing documentation to match the stricter default gate.

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
