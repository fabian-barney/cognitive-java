#!/usr/bin/env python3
"""Fail closed if any release coordinate is already public or cannot be checked."""

import argparse
import urllib.error
import urllib.request


def assert_unpublished(urls: list[str]) -> None:
    for url in urls:
        try:
            request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "cognitive-java-release"})
            with urllib.request.urlopen(request, timeout=30) as response:
                raise ValueError(f"Version is already public at {url}; preserve existing history and use the next patch version")
        except urllib.error.HTTPError as error:
            error.close()
            if error.code != 404:
                raise ValueError(f"Cannot establish absence at {url}: HTTP {error.code}") from error
        except urllib.error.URLError as error:
            raise ValueError(f"Cannot establish absence at {url}: {error.reason}") from error


def coordinates(version: str) -> list[str]:
    central = "https://repo.maven.apache.org/maven2"
    urls = [f"{central}/media/barney/cognitive-java-{name}/{version}/cognitive-java-{name}-{version}.pom"
            for name in ("parent", "core", "cli", "maven-plugin", "gradle-plugin")]
    marker = f"media/barney/cognitive-java/media.barney.cognitive-java.gradle.plugin/{version}/media.barney.cognitive-java.gradle.plugin-{version}.pom"
    urls.extend([f"{central}/{marker}", f"https://plugins.gradle.org/m2/{marker}"])
    return urls


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    args = parser.parse_args()
    assert_unpublished(coordinates(args.version))
    print(f"No public registry collision for {args.version}.")
