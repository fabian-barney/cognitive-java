# cognitive-java

`cognitive-java` is a static Cognitive Complexity toolkit for Java projects.

It analyzes Java source without running tests, generating coverage, or reading JaCoCo reports. By default it fails when any analyzed method exceeds the Cognitive Complexity threshold of `15`.

## Modules

- `core`: analysis engine, source discovery, report formatting, and CLI orchestration
- `cli`: runnable shaded jar
- `maven-plugin`: Maven `check` goal
- `gradle-plugin`: Gradle plugin exposing `cognitive-java-check`

## CLI

Published artifact:

- `media.barney:cognitive-java-cli:<version>`

Run the shaded jar after downloading it from Maven Central:

```bash
java -jar cognitive-java-cli-<version>.jar [args...]
```

Usage:

```text
--help                               Print usage to stdout
(no args)                            Analyze all Java files under nested src/main/java roots
--changed                            Analyze changed Java files under nested src/main/java roots
--format <toon|json|text|junit|none>
--agent
--failures-only[=true|false]
--omit-redundancy[=true|false]
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
java -jar cognitive-java-cli-<version>.jar
java -jar cognitive-java-cli-<version>.jar --changed
java -jar cognitive-java-cli-<version>.jar --format json --output target/cognitive-java/report.json
java -jar cognitive-java-cli-<version>.jar --format none --junit-report target/cognitive-java/TEST-cognitive-java.xml
java -jar cognitive-java-cli-<version>.jar --agent
java -jar cognitive-java-cli-<version>.jar --threshold 12
java -jar cognitive-java-cli-<version>.jar --exclude 'module-a/**' --exclude-class '(^|.*\\.)Dagger[^.]*$'
```

## Report Behavior

- Primary formats: `toon`, `json`, `text`, `junit`, `none`
- CLI primary format defaults to `toon`
- `--agent` defaults the primary report to `toon`, failures-only, and omit-redundancy unless explicitly overridden
- `--failures-only` and `--omit-redundancy` affect only the primary report
- `--junit-report` always writes the complete unfiltered JUnit sidecar
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

## Gradle Plugin

Published plugin:

- plugin id `media.barney.cognitive-java`
- version `<version>`

Apply the plugin:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "<version>"
}
```

Run:

```bash
./gradlew cognitive-java-check
```

Configure report controls, threshold, and exclusions:

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
    excludes.set(listOf("generated/**"))
    excludeClasses.set(listOf("(^|.*\\.)Dagger[^.]*$"))
    excludeAnnotations.set(listOf("Generated"))
    useDefaultExclusions.set(true)
}
```

Published releases work through the Gradle Plugin Portal without extra `pluginManagement` configuration. The marker publication is `media.barney.cognitive-java:media.barney.cognitive-java.gradle.plugin:<version>` and resolves to `media.barney:cognitive-java-gradle-plugin:<version>`.

## Maven Plugin

Published artifact:

- `media.barney:cognitive-java-maven-plugin:<version>`

Bind the `check` goal:

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
      <configuration>
        <format>text</format>
        <threshold>12</threshold>
        <output>target/cognitive-java/report.txt</output>
        <junit>true</junit>
        <junitReport>target/cognitive-java/TEST-cognitive-java.xml</junitReport>
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
