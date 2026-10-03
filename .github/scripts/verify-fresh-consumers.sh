#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || { echo "Usage: $0 <version> <verified-download-directory>" >&2; exit 2; }
version="$1"
downloads="$(cd "$2" && pwd)"
repository_root="$(pwd)"
consumer_root="$(mktemp -d)"
mkdir -p "$consumer_root/src/main/java/demo"
cat > "$consumer_root/src/main/java/demo/Sample.java" <<'JAVA'
package demo;
public class Sample {
    public int choose(boolean enabled) {
        if (enabled) { return 1; }
        return 0;
    }
}
JAVA
cat > "$consumer_root/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId><artifactId>release-consumer</artifactId><version>1.0</version>
</project>
XML
(
  cd "$consumer_root"
  java -jar "$downloads/cognitive-java-cli-$version.jar" --format json --output cli-report.json
  mvn -B -ntp "-Dmaven.repo.local=$consumer_root/maven-repository" \
    "media.barney:cognitive-java-maven-plugin:$version:check" \
    -DcognitiveJava.format=json -DcognitiveJava.output=maven-report.json
)
cat > "$consumer_root/settings.gradle.kts" <<'KOTLIN'
rootProject.name = "release-consumer"
KOTLIN
cat > "$consumer_root/build.gradle.kts" <<KOTLIN
plugins {
    java
    id("media.barney.cognitive-java") version "$version"
}
cognitiveJava {
    format.set("json")
    output.set(layout.projectDirectory.file("gradle-report.json"))
}
KOTLIN
for attempt in first second; do
  "$repository_root/gradle-plugin/gradlew" --no-daemon --project-dir "$consumer_root" \
    --gradle-user-home "$consumer_root/gradle-home" --configuration-cache cognitive-java-check \
    > "$consumer_root/gradle-$attempt.log" 2>&1 || { cat "$consumer_root/gradle-$attempt.log"; exit 1; }
done
grep --fixed-strings 'Configuration cache entry reused.' "$consumer_root/gradle-second.log"
python3 - "$consumer_root" <<'PY'
import json
import pathlib
import sys
root = pathlib.Path(sys.argv[1])
for name in ('cli', 'maven', 'gradle'):
    report = json.loads((root / f'{name}-report.json').read_text())
    assert report['status'] == 'passed' and report['threshold'] == 8, name
    assert len(report['methods']) == 1 and report['methods'][0]['cc'] == 1, name
print('Fresh CLI, Maven, and Gradle consumers verified, including configuration-cache reuse.')
PY
