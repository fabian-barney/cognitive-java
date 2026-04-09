# cognitive-java

`cognitive-java` is a Cognitive Complexity toolkit for Java projects.

It analyzes Java source statically, reports the worst methods first, and fails when any method exceeds the default threshold of `15`.

## Behavior

- Pure static analysis
- Does not run tests, generate coverage, or read JaCoCo reports
- Threshold is fixed at `15`

## CLI

Published artifact:

- `media.barney:cognitive-java-cli:<version>`

Run the shaded jar after downloading it from Maven Central:

```bash
java -jar cognitive-java-cli-<version>.jar [args...]
```

```text
--help      Print usage to stdout
(no args)   Analyze all Java files under any nested src/main/java tree
--changed   Analyze changed Java files under any nested src/main/java tree
<file ...>  Analyze only these files
<dir ...>   Analyze all Java files under each directory's nested src/main/java trees
```

Examples:

```bash
java -jar cognitive-java-cli-<version>.jar --help
java -jar cognitive-java-cli-<version>.jar
java -jar cognitive-java-cli-<version>.jar --changed
java -jar cognitive-java-cli-<version>.jar src/main/java/demo/Sample.java
java -jar cognitive-java-cli-<version>.jar module-a module-b
```

## Gradle Plugin

Published plugin:

- Plugin id `media.barney.cognitive-java`
- Version `<version>`

Apply the plugin in `build.gradle(.kts)`:

```kotlin
plugins {
    id("media.barney.cognitive-java") version "<version>"
}
```

Published releases work through the Gradle Plugin Portal without extra `pluginManagement` configuration.

Run:

```bash
./gradlew cognitive-java-check
```

If you want Gradle to check Maven Central before the Gradle Plugin Portal, add both repositories to `pluginManagement.repositories` in `settings.gradle(.kts)`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The marker publication is `media.barney.cognitive-java:media.barney.cognitive-java.gradle.plugin:<version>` and resolves to `media.barney:cognitive-java-gradle-plugin:<version>`.

## Maven Plugin

Published artifact:

- `media.barney:cognitive-java-maven-plugin:<version>`

The plugin exposes the `check` goal. Bind it in your build:

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

Published releases do not require custom `<pluginRepositories>` entries or consumer-side authentication.

Run:

```bash
mvn verify
```

## Exit Codes

- `0` success, threshold respected
- `1` invalid CLI usage
- `2` Cognitive Complexity threshold exceeded (`> 15`)

## Contributing

See `CONTRIBUTING.md` for repository layout, local validation commands, CI ownership notes, and the issue-linked contribution workflow used in this repository.
