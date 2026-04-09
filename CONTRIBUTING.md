# Contributing

All changes in this repository are expected to be issue-linked.

## Workflow

1. Create or confirm the GitHub issue first.
2. Create a descriptive branch that includes the issue number, for example `25-downstream-docs`.
3. Reference the issue number in every commit message.
4. Open a PR that closes the issue.
5. After each push, review new PR feedback, fix valid findings in a follow-up push, reply when a finding is not applicable, and resolve threads only after the fix or explicit invalidation response.
6. Merge only after the latest push has no valid findings left and all required checks are green.

## Repository Layout

- `core`: Cognitive Complexity engine, source discovery, and report formatting
- `cli`: runnable CLI entrypoint that shades `core`
- `maven-plugin`: Maven plugin exposing the `check` goal
- `gradle-plugin`: Gradle plugin build exposing `media.barney.cognitive-java`

## Local Validation

Build the CLI and run the core tests:

```bash
mvn -B -pl cli -am package
```

Build the core jar before running the Gradle plugin tests:

```bash
mvn -B -pl core -am package
cd gradle-plugin
./gradlew test
```

Run the Maven plugin module, including its invoker integration fixtures:

```bash
mvn -B -pl maven-plugin -am verify
```

Consumer repositories should standardize normal validation on:

```bash
mvn -B -ntp verify
```

## Repository CI and Self-Hosting Notes

Repository CI also runs the shared `crap-java` gate. It resolves the published CLI from Maven Central:

- `media.barney:crap-java-cli:0.4.1`

The gate runs Maven-mode checks for `core`, `cli`, and `maven-plugin`, and a separate Gradle-mode check for `gradle-plugin/src/main/java`.

Repository CI also runs the published `cognitive-java` Maven plugin as a separate `cognitive-java Gate` job:

- `media.barney:cognitive-java-maven-plugin:0.4.0`

Run the same gate locally from the repository root with:

```bash
mvn -B -N media.barney:cognitive-java-maven-plugin:0.4.0:check
```

This repository uses the published plugin in non-recursive mode because binding the plugin into the same Maven reactor would create a project cycle.

Self-hosting exceptions keep metric ownership on the full repository scope, including `gradle-plugin/src/main/java`:

- `crap-java Gate` owns CRAP and coverage failures across `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `cognitive-java Gate` owns Cognitive Complexity failures across `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `Gradle Plugin` validates plugin build and test behavior only; it does not own metric failures
