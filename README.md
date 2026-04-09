# cognitive-java

`cognitive-java` is a shared Cognitive Complexity toolkit for Java projects.

It analyzes Java source statically using the Sonar Cognitive Complexity rules, reports the worst methods first, and fails the build when any method exceeds the default Sonar threshold of `25`.

## Modules

- `core`: Cognitive Complexity engine, CLI orchestration, source discovery, and report formatting
- `cli`: executable entrypoint that bundles the core as a runnable jar
- `gradle-plugin`: self-contained Gradle plugin build exposing `media.barney.cognitive-java`
- `maven-plugin`: native Maven plugin exposing the `check` goal

## Metric

- The implementation follows the SonarSource Cognitive Complexity white paper.
- The tool is pure static analysis. It does not run tests, generate coverage, or read JaCoCo reports.
- The threshold is fixed at `25`.

## Build and Test

```bash
mvn -B -pl cli -am package
```

Build and test the Gradle plugin module after packaging the core jar:

```bash
mvn -B -pl core -am package
cd gradle-plugin
./gradlew test
```

Build and test the Maven plugin module, including its invoker integration fixtures:

```bash
mvn -B -pl maven-plugin -am verify
```

## Shared CRAP Gate

Repository CI also runs the shared `crap-java` gate so this project is checked the same way as the other Java repositories in this GitHub account.

The gate resolves the published CLI from Maven Central:

- `media.barney:crap-java-cli:0.4.1`

CI invokes that CLI in Maven mode for `core`, `cli`, and `maven-plugin`, and in Gradle mode for `gradle-plugin`.

## Self Gate

Repository CI also runs the published `cognitive-java` Maven plugin against
this repository as a separate `cognitive-java Gate` job:

- `media.barney:cognitive-java-maven-plugin:0.3.0`

From the repository root, run the same gate locally with:

```bash
mvn -B -N media.barney:cognitive-java-maven-plugin:0.3.0:check
```

This repo uses the published plugin in non-recursive mode because binding the
plugin into the same Maven reactor would create a project cycle.

## Self-Hosting Gate Scope

Consumer Maven repositories should standardize local and AI-agent validation on:

```bash
mvn -B -ntp verify
```

This repository keeps self-hosting exceptions in CI so the metric jobs still own
the full repository scope, including the embedded `gradle-plugin/` source tree:

- `crap-java Gate` owns CRAP and coverage failures across `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `cognitive-java Gate` owns Cognitive Complexity failures across `core`, `cli`, `maven-plugin`, and `gradle-plugin/src/main/java`
- `Gradle Plugin` validates plugin build and test behavior only; it does not own metric failures

## Run

Build the CLI jar:

```bash
mvn -B -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/cognitive-java-cli-0.3.0.jar
```

## CLI

```text
--help      Print usage to stdout
(no args)   Analyze all Java files under any nested src/main/java tree
--changed   Analyze changed Java files under any nested src/main/java tree
<file ...>  Analyze only these files
<dir ...>   Analyze all Java files under each directory's nested src/main/java trees
```

Examples:

```bash
java -jar cli/target/cognitive-java-cli-0.3.0.jar --help
java -jar cli/target/cognitive-java-cli-0.3.0.jar
java -jar cli/target/cognitive-java-cli-0.3.0.jar --changed
java -jar cli/target/cognitive-java-cli-0.3.0.jar src/main/java/demo/Sample.java
java -jar cli/target/cognitive-java-cli-0.3.0.jar module-a module-b
```

## Distribution

Public releases ship through Maven Central, with the Gradle Plugin Portal as the primary Gradle plugin channel once approved:

- `media.barney:cognitive-java-core:<version>`
- `media.barney:cognitive-java-cli:<version>`
- `media.barney:cognitive-java-maven-plugin:<version>`
- Gradle plugin id `media.barney.cognitive-java` version `<version>`

### Gradle Plugin Portal

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "<version>"
}
```

No custom `pluginManagement` repository configuration is required for published releases.

Run:

```bash
./gradlew cognitive-java-check
```

### Maven Central Gradle Plugin

If you want to resolve the Gradle plugin from Maven Central instead of waiting for Plugin Portal availability, add Maven Central to plugin resolution in `settings.gradle(.kts)`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply the same plugin id in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "<version>"
}
```

The marker publication lives at `media.barney.cognitive-java:media.barney.cognitive-java.gradle.plugin:<version>` and resolves to the implementation artifact `media.barney:cognitive-java-gradle-plugin:<version>`.

### Maven Central

Add the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>cognitive-java-maven-plugin</artifactId>
      <version>&lt;version&gt;</version>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

No custom `<pluginRepositories>` or consumer-side authentication are required for published releases.

Run:

```bash
mvn verify
```

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` Cognitive Complexity threshold exceeded (`> 25`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
