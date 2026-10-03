#!/usr/bin/env python3
"""Freeze unsigned registry payloads before any public upload."""

import argparse
import shutil
import json
import hashlib
from pathlib import Path


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("destination", type=Path)
    parser.add_argument("--assets-directory", type=Path, default=Path("target/release-assets"))
    args = parser.parse_args()
    destination = args.destination
    destination.mkdir(parents=True, exist_ok=False)
    files = {f"cognitive-java-parent-{args.version}.pom": Path("pom.xml")}
    for module in ("core", "cli", "maven-plugin", "gradle-plugin"):
        artifact = f"cognitive-java-{module}-{args.version}"
        directory = Path(module) / ("build/libs" if module == "gradle-plugin" else "target")
        for classifier in ("", "-sources", "-javadoc"):
            files[f"{artifact}{classifier}.jar"] = directory / f"{artifact}{classifier}.jar"
        sbom = json.loads((args.assets_directory / f"cognitive-java-{module}-{args.version}.cdx.json").read_text())
        digest = hashlib.sha256(files[f"{artifact}.jar"].read_bytes()).hexdigest()
        if {"alg": "SHA-256", "content": digest} not in sbom["metadata"]["component"]["hashes"]:
            raise ValueError(f"{module} binary changed after its SBOM was verified")
        files[f"{artifact}.pom"] = Path("gradle-plugin/build/publications/pluginMaven/pom-default.xml") if module == "gradle-plugin" else Path(module) / "pom.xml"
    files[f"media.barney.cognitive-java.gradle.plugin-{args.version}.pom"] = Path("gradle-plugin/build/publications/cognitive-javaPluginMarkerMaven/pom-default.xml")
    for name, source in files.items():
        shutil.copyfile(source, destination / name)
    print(f"Frozen {len(files)} registry payloads for digest comparison.")
