# cognitive-java

`cognitive-java` is a shared Cognitive Complexity toolkit for Java projects.

It analyzes Java source statically using the Sonar Cognitive Complexity rules, reports the worst methods first, and fails the build when any method exceeds the default Sonar threshold of `25`.

## Modules

- `core`: Cognitive Complexity engine, CLI orchestration, source discovery, and report formatting
- `cli`: executable entrypoint that packages the core as a runnable jar
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

Repository CI also runs the shared `crap-java` gate so this project is checked
the same way as the other Java repositories in this GitHub account.

The gate resolves the published CLI from Maven Central:

- `media.barney:crap-java-cli:0.3.2`

CI invokes that CLI in Maven mode for `core`, `cli`, and `maven-plugin`, and in
Gradle mode for `gradle-plugin`.

## Run

Build the CLI jar:

```bash
mvn -B -pl cli -am -DskipTests package
```

From the project root you want to analyze:

```bash
java -jar cli/target/cognitive-java-cli-0.2.0.jar
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
java -jar cli/target/cognitive-java-cli-0.2.0.jar --help
java -jar cli/target/cognitive-java-cli-0.2.0.jar
java -jar cli/target/cognitive-java-cli-0.2.0.jar --changed
java -jar cli/target/cognitive-java-cli-0.2.0.jar src/main/java/demo/Sample.java
java -jar cli/target/cognitive-java-cli-0.2.0.jar module-a module-b
```

## GitHub Packages

Release `0.2.0` publishes these coordinates to GitHub Packages:

- `media.barney:cognitive-java-core:0.2.0`
- `media.barney:cognitive-java-cli:0.2.0`
- `media.barney:cognitive-java-maven-plugin:0.2.0`
- Gradle plugin id `media.barney.cognitive-java` version `0.2.0`

### Gradle

Configure the plugin repository in `settings.gradle(.kts)`:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/fabian-barney/cognitive-java")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "0.2.0"
}
```

Run:

```bash
./gradlew cognitive-java-check
```

### Maven

Configure GitHub Packages as a plugin repository:

```xml
<pluginRepositories>
  <pluginRepository>
    <id>github</id>
    <url>https://maven.pkg.github.com/fabian-barney/cognitive-java</url>
  </pluginRepository>
</pluginRepositories>
```

Authenticate Maven with a matching `github` server entry, for example in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>${env.GITHUB_ACTOR}</username>
    <password>${env.GITHUB_TOKEN}</password>
  </server>
</servers>
```

The token used here needs package read access.

Add the plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>cognitive-java-maven-plugin</artifactId>
      <version>0.2.0</version>
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

Run:

```bash
mvn verify
```

## Release

Tag `v0.2.0` from `main` after the pull request checks are green. The tag-triggered release workflow publishes the Maven artifacts, publishes the Gradle plugin publications, and creates the GitHub release.

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` Cognitive Complexity threshold exceeded (`> 25`)

## Contributing

See `CONTRIBUTING.md` for the issue-linked branch, commit, and PR flow used in this repository.
