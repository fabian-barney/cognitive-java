# Contributing

All changes in this repository are expected to be issue-linked.

## Workflow

1. Create or confirm the GitHub issue first.
2. Create a descriptive branch that includes the issue number.
3. Reference the issue number in every commit message.
4. Open a PR that closes the issue and keeps the change scoped to that issue.
5. After each push, review new PR feedback, fix valid findings in a follow-up push, reply when a finding is not applicable, and resolve threads only after the fix or explicit invalidation response.
6. Merge only after the latest push has a newer clean review and all required checks are green.

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

- `crap-java Gate` owns CRAP and coverage failures across `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `cognitive-java Gate` owns Cognitive Complexity failures across the same source scope
- `Gradle Plugin` validates plugin build and wrapper behavior only; it does not own metric failures

The build workflow now validates:

- Maven verification on JDK `17`, `21`, and `25`
- Windows Maven verification for path-sensitive behavior
- Gradle plugin validation on Linux and Windows
- uploaded JUnit sidecars from both self-hosted `cognitive-java Gate` scans

Run the self-hosted gates locally from the repository root with the built or published CLIs as needed:

```bash
mvn -B -pl cli -am package
java -jar cli/target/cognitive-java-cli-<version>.jar --format text core/src/main/java cli/src/main/java maven-plugin/src/main/java
java -jar cli/target/cognitive-java-cli-<version>.jar --format text gradle-plugin/src/main/java
```
