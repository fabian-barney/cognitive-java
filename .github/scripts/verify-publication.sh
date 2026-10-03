#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <version> <frozen-payload-directory>" >&2
  exit 2
fi

version="$1"
expected="$(cd "$2" && pwd)"
verification_root="$(mktemp -d)"
central_base="https://repo.maven.apache.org/maven2/media/barney"

download_with_retry() {
  local url="$1"
  local destination="$2"
  local attempts=40
  local delay_seconds=15
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --location --silent --show-error \
      --connect-timeout 15 --max-time 60 \
      --output "$destination" "$url"; then
      return 0
    fi
    if ((attempt == attempts)); then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep "$delay_seconds"
  done
}

verify_file() {
  local base="$1" name="$2"
  download_with_retry "$base/$name" "$verification_root/$name"
  download_with_retry "$base/$name.asc" "$verification_root/$name.asc"
  gpg --batch --verify "$verification_root/$name.asc" "$verification_root/$name"
  cmp "$expected/$name" "$verification_root/$name" \
    || { echo "Published bytes differ from verified artifact: $name" >&2; exit 1; }
}

for artifact in cognitive-java-parent cognitive-java-core cognitive-java-cli cognitive-java-maven-plugin cognitive-java-gradle-plugin; do
  base="${central_base}/${artifact}/${version}"
  verify_file "$base" "$artifact-$version.pom"
  if [[ "$artifact" != cognitive-java-parent ]]; then
    for suffix in .jar -sources.jar -javadoc.jar; do
      verify_file "$base" "$artifact-$version$suffix"
    done
  fi
done
marker="media.barney.cognitive-java.gradle.plugin"
marker_path="media/barney/cognitive-java/$marker/$version/$marker-$version.pom"
verify_file "https://repo.maven.apache.org/maven2/media/barney/cognitive-java/$marker/$version" "$marker-$version.pom"
download_with_retry "https://plugins.gradle.org/m2/$marker_path" "$verification_root/portal-marker.pom"
python3 - "$verification_root/portal-marker.pom" "$version" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse(sys.argv[1]).getroot()
assert root.findtext('m:version', namespaces=ns) == sys.argv[2]
dependency = root.find('m:dependencies/m:dependency', ns)
assert dependency is not None
assert dependency.findtext('m:groupId', namespaces=ns) == 'media.barney'
assert dependency.findtext('m:artifactId', namespaces=ns) == 'cognitive-java-gradle-plugin'
assert dependency.findtext('m:version', namespaces=ns) == sys.argv[2]
PY
portal_base="https://plugins.gradle.org/m2/media/barney/cognitive-java-gradle-plugin/$version"
for suffix in .jar -sources.jar -javadoc.jar .pom; do
  name="cognitive-java-gradle-plugin-$version$suffix"
  download_with_retry "$portal_base/$name" "$verification_root/portal-$name"
  download_with_retry "$portal_base/$name.asc" "$verification_root/portal-$name.asc"
  gpg --batch --verify "$verification_root/portal-$name.asc" "$verification_root/portal-$name"
  cmp "$expected/$name" "$verification_root/portal-$name"
done
bash .github/scripts/verify-fresh-consumers.sh "$version" "$verification_root"
