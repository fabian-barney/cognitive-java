# cognitive-java

`cognitive-java` is a static Cognitive Complexity toolkit for Java projects.

It analyzes Java source without running tests, generating coverage, or reading JaCoCo reports. By default it fails when any analyzed method exceeds the Cognitive Complexity threshold of `8`.

## Modules

- `core`: analysis engine, source discovery, report formatting, and CLI orchestration
- `cli`: runnable shaded jar
- `maven-plugin`: Maven `check` goal
- `gradle-plugin`: Gradle plugin exposing `cognitive-java-check`

The `core` artifact supports the CLI and plugins. It is internal implementation,
with no direct Java binary or source compatibility guarantee.

## Requirements and support

Artifacts contain Java 17-compatible bytecode. Analysis requires a full JDK
with the `jdk.compiler` module, rather than a JRE. The supported combinations are:

| Integration | Supported versions | Tested boundaries |
| --- | --- | --- |
| CLI | JDK 17, 21, and 25 | JDK 17, 21, and 25 |
| Maven plugin | Maven 3.9.x on JDK 17, 21, or 25 | Maven 3.9.0 and 3.9.16 on each JDK |
| Gradle plugin | Gradle 8.14.x on JDK 17/21; Gradle 9.8.x on JDK 17/21/25 | Gradle 8.14.5 and 9.8.0 |

Java 17 and Maven 3.9.0 are enforced by the build and Maven plugin metadata.
The wrapper uses Gradle 9.8.0. Gradle 8.14 does not run on JDK 25; use Gradle
9.8.x for that combination. Linux and Windows are covered by CI. Other versions
and operating systems may work but are outside the v1 support commitment.

The parser uses the running JDK's javac syntax support. Run on a JDK that
supports the Java syntax being analyzed. Preview-language support is not
enabled; analysis does not resolve project dependencies or perform type checking.

## Cognitive Complexity model

The model follows the Cognitive Complexity paper's structural and nesting
increments, with regression tests for its examples. Loops, conditional branches,
catch clauses, ternaries, switches, logical operator sequences, labeled jumps,
and recursion contribute to the score. Constructors, compact record constructors,
and methods in local and anonymous classes are analyzed.

Recursion detection uses source declarations and calls matched by owner, name,
and argument count. It does not resolve overload parameter types, inheritance,
or dynamic dispatch. Exact parity with a particular SonarJava release is not
part of the compatibility promise.

## v1 compatibility contract

Semantic Versioning applies to CLI options and behavior, Maven and Gradle plugin
configuration, process exit codes, and JSON, TOON, and JUnit report schemas.
Within 1.x, existing names and meanings will not change incompatibly. Additions
must preserve correct existing configurations and report consumers. Human-readable
text is intended for people and is not a stable machine schema.

The v1 TOON contract is fixed by the JToon 2.0.4 empty and populated report
fixtures. An incompatible upstream encoding change requires a cognitive-java
major version. See [Migrating from 0.7.1 to 1.0.0](MIGRATING.md) and
[dependency selection](DEPENDENCIES.md).

Release downloads include signed checksums and component SBOMs. See
[artifact verification](RELEASE-ARTIFACTS.md) and the [release runbook](RELEASING.md)
for signatures, provenance, and SBOM attestations.

## CLI

Published artifact:

- `media.barney:cognitive-java-cli:1.0.0`

Run the shaded JAR after downloading it from [Maven Central](https://repo.maven.apache.org/maven2/media/barney/cognitive-java-cli/1.0.0/cognitive-java-cli-1.0.0.jar):

```bash
java -jar cognitive-java-cli-1.0.0.jar [args...]
```

The [v1.0.0 GitHub release](https://github.com/fabian-barney/cognitive-java/releases/tag/v1.0.0)
also ships [cognitive-java-1.0.0.jar](https://github.com/fabian-barney/cognitive-java/releases/download/v1.0.0/cognitive-java-1.0.0.jar)
with identical executable bytes. Follow [artifact verification](RELEASE-ARTIFACTS.md)
for signed checksums, detached signatures, SBOMs, and attestations.

Usage:

```text
--help                               Print usage to stdout
(no args)                            Analyze all Java files under nested src/main/java roots
--changed                            Analyze changed Java files under nested src/main/java roots
--format <toon|json|text|junit|none>
--agent
--failures-only[=true|false]
--omit-redundancy[=true|false]
--source-root <path>                 Repeatable source-root selection
--exclude <glob>                     Repeatable path exclusion
--exclude-class <regex>              Repeatable FQCN exclusion
--exclude-annotation <name>          Repeatable annotation exclusion
--use-default-exclusions[=true|false]
--output <path>
--junit-report <path>
--threshold <integer>
<file ...>                           Analyze only these files
<dir ...>                            Analyze all Java files under each directory argument
```

Examples:

```bash
java -jar cognitive-java-cli-1.0.0.jar
java -jar cognitive-java-cli-1.0.0.jar --changed
java -jar cognitive-java-cli-1.0.0.jar --format json --output target/cognitive-java/report.json
java -jar cognitive-java-cli-1.0.0.jar --format none --junit-report target/cognitive-java/TEST-cognitive-java.xml
java -jar cognitive-java-cli-1.0.0.jar --agent
java -jar cognitive-java-cli-1.0.0.jar --threshold 12
java -jar cognitive-java-cli-1.0.0.jar --exclude 'module-a/**' --exclude-class '(^|.*\\.)Dagger[^.]*$'
```

## Report Behavior

- Primary formats: `toon`, `json`, `text`, `junit`, `none`
- CLI primary format defaults to `toon`
- `--agent` defaults the primary report to `toon`, failures-only, and omit-redundancy unless explicitly overridden
- `--failures-only` and `--omit-redundancy` affect only the primary report
- `--junit-report` always writes the complete unfiltered JUnit sidecar
- JUnit testcases include GitLab-visible metric details in `name` as
  `method:lineStart [CC=complexity]` and in testcase-level `system-out`
- `--format none` suppresses primary stdout output and writes an empty primary file if `--output` is set
- Machine-readable reports expose top-level `status` and `threshold`
- Method entries use `status`, `cc`, `method`, `src`, `lineStart`, and `lineEnd`

Report paths resolve against the analysis root, must stay inside it after normalization/canonicalization, and cannot target the filesystem root, the analysis root itself, an existing directory, or the same file for both primary and JUnit output.

## Source Discovery And Exclusions

Default source discovery walks nested `src/main/java` trees.

Directory traversal does not follow symlinks.

Built-in exclusions stay conservative and generated-code-focused:

- any directory segment containing `generated`
- `**/src/main/java-gen/**`
- generated-focused FQCN patterns such as `generated`, `gen`, `*MapperImpl`, `Dagger*`, `Hilt_*`, and `AutoValue_*`
- classes annotated with any annotation whose simple name is `Generated`

Full JSON, text, and JUnit outputs include exclusion audit counts. Compact agent-mode primary reports stay focused on actionable failures.

Additional source selection and exclusion controls:

- `--source-root <path>` repeatable
- `--exclude <glob>` repeatable
- `--exclude-class <regex>` repeatable
- `--exclude-annotation <name>` repeatable
- `--use-default-exclusions[=true|false]`

Custom source roots are resolved against the analysis root. Directory traversal does not follow symlinks.

## Gradle Plugin

Published plugin:

- plugin id `media.barney.cognitive-java`
- version `1.0.0`

Apply the plugin:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "1.0.0"
}
```

Run:

```bash
./gradlew cognitive-java-check
```

Gradle configuration also supports:

- `sourceRoots`
- `excludes`
- `excludeClasses`
- `excludeAnnotations`
- `useDefaultExclusions`

```kotlin
cognitiveJava {
    threshold.set(12)
    format.set("json")
    agent.set(false)
    failuresOnly.set(false)
    omitRedundancy.set(true)
    output.set(layout.buildDirectory.file("reports/cognitive-java/report.json"))
    junit.set(true)
    junitReport.set(layout.buildDirectory.file("reports/cognitive-java/TEST-cognitive-java.xml"))
    sourceRoots.set(listOf("module-a/src/main/java", "module-b/src/main/java"))
    excludes.set(listOf("generated/**"))
    excludeClasses.set(listOf("(^|.*\\.)Dagger[^.]*$"))
    excludeAnnotations.set(listOf("Generated"))
    useDefaultExclusions.set(true)
}
```

Published releases work through the Gradle Plugin Portal without extra `pluginManagement` configuration. The marker publication is `media.barney.cognitive-java:media.barney.cognitive-java.gradle.plugin:1.0.0` and resolves to `media.barney:cognitive-java-gradle-plugin:1.0.0`.

## Maven Plugin

Published artifact:

- `media.barney:cognitive-java-maven-plugin:1.0.0`

Bind the `check` goal:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>media.barney</groupId>
      <artifactId>cognitive-java-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <format>text</format>
        <threshold>12</threshold>
        <output>target/cognitive-java/report.txt</output>
        <junit>true</junit>
        <junitReport>target/cognitive-java/TEST-cognitive-java.xml</junitReport>
        <sourceRoots>
          <sourceRoot>module-a/src/main/java</sourceRoot>
          <sourceRoot>module-b/src/main/java</sourceRoot>
        </sourceRoots>
        <excludes>
          <exclude>generated/**</exclude>
        </excludes>
        <excludeClasses>
          <excludeClass>(^|.*\.)Dagger[^.]*$</excludeClass>
        </excludeClasses>
        <excludeAnnotations>
          <excludeAnnotation>Generated</excludeAnnotation>
        </excludeAnnotations>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Equivalent properties are available:

- `cognitiveJava.format`
- `cognitiveJava.agent`
- `cognitiveJava.failuresOnly`
- `cognitiveJava.omitRedundancy`
- `cognitiveJava.output`
- `cognitiveJava.junit`
- `cognitiveJava.junitReport`
- `cognitiveJava.threshold`
- `cognitiveJava.sourceRoots`
- `cognitiveJava.excludes`
- `cognitiveJava.excludeClasses`
- `cognitiveJava.excludeAnnotations`
- `cognitiveJava.useDefaultExclusions`

Published releases do not require custom `<pluginRepositories>` entries or consumer-side authentication.

## Exit Codes

- `0` success, threshold respected
- `1` parse or configuration error
- `2` threshold exceeded
- `3` unexpected internal error

## Contributing

See `CONTRIBUTING.md` for repository layout, local validation commands, self-hosting gate ownership, and the issue-linked workflow used in this repository.
