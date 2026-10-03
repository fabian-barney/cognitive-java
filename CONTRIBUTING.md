# Contributing

All changes in this repository are expected to be issue-linked.

## Workflow

1. Create or confirm the GitHub issue first.
2. Create a descriptive branch that includes the issue number.
3. Reference the issue number in every commit message.
4. Open a PR that closes the issue and keeps the change scoped to that issue.
5. After each push, review new PR feedback, fix valid findings in a follow-up push, reply when a finding is not applicable, and resolve threads only after the fix or explicit invalidation response.
6. Merge only after the latest push has a new clean review and all required checks are green.

## Repository Layout

- `core`: Cognitive Complexity engine, source discovery, exclusions, and report formatting
- `cli`: runnable shaded CLI entrypoint
- `maven-plugin`: Maven plugin exposing the `check` goal
- `gradle-plugin`: Gradle plugin build exposing `media.barney.cognitive-java`

## Local Validation

Run the repository-standard Maven verification:

```bash
mvn -B verify -Dcentral.skipPublishing=true
```

Run the Maven plugin module, including its integration fixtures:

```bash
mvn -B -pl maven-plugin -am verify
```

Run the Gradle plugin validation workflow after packaging the core jar:

```bash
mvn -B -pl core -am package
cd gradle-plugin
./gradlew test validatePlugins publishToMavenLocal
```

On Windows, use:

```powershell
mvn -B -pl core -am package
Set-Location gradle-plugin
.\gradlew.bat test validatePlugins publishToMavenLocal
```

Consumer repositories should standardize normal validation on:

```bash
mvn -B -ntp verify
```

## Repository CI And Self-Hosting Notes

The self-hosted gate jobs stay split by build tool so metric ownership still covers the full repository scope, including `gradle-plugin/src/main/java`.

The normal reactor build runs compilation, tests, and Maven integration fixtures.
The project's own Maven plugin cannot be a reactor-wide build plugin: it depends
on `core`, which would introduce a build-order cycle. Activate the CRAP and
Cognitive Complexity profiles only in individual modules after installing the
reactor. CI runs those separate gates for every production module before merge.

- `verify / quality-crap-*` owns CRAP and coverage failures across all production modules.
- `verify / quality-cognitive-*` owns Cognitive Complexity failures across the same source scope.
- Gradle plugin functional tests validate plugin behavior and configuration-cache reuse.

The build workflow now validates:

- Maven `3.9.0` and `3.9.16` verification on JDK `17`, `21`, and `25`, on Linux and Windows
- Gradle `8.14.5` on JDK `17`/`21` and `9.8.0` on JDK `17`/`21`/`25`, on Linux and Windows
- uploaded JUnit sidecars from the self-hosted `verify / quality-cognitive-*` scans

Run the self-hosted gates locally from the repository root with the built or published CLIs as needed:

```bash
mvn -B -pl cli -am package
java -jar cli/target/cognitive-java-cli-<version>.jar --format text core/src/main/java cli/src/main/java maven-plugin/src/main/java
java -jar cli/target/cognitive-java-cli-<version>.jar --format text gradle-plugin/src/main/java
```
