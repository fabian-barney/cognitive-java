#!/usr/bin/env python3
"""Bind component SBOMs to binaries and describe core embedded in Gradle's JAR."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path


def identity(component: dict) -> tuple:
    return component.get("group"), component.get("name"), component.get("version")


def merge_embedded_core(document: dict, core: dict, core_jar: Path) -> None:
    components = document.setdefault("components", [])
    by_identity = {identity(component): component for component in components}
    reference_map = {}
    for source in [core["metadata"]["component"], *core["components"]]:
        key = identity(source)
        existing = by_identity.get(key)
        if existing is None:
            # A different resolved version would misrepresent the bundled core's dependencies.
            if any(identity(item)[:2] == key[:2] for item in components):
                raise ValueError(f"Conflicting runtime dependency version: {key}")
            existing = copy.deepcopy(source)
            components.append(existing)
            by_identity[key] = existing
        reference_map[source["bom-ref"]] = existing["bom-ref"]
    embedded = by_identity[identity(core["metadata"]["component"])]
    embedded["hashes"] = [{"alg": "SHA-256", "content": hashlib.sha256(core_jar.read_bytes()).hexdigest()}]
    embedded.setdefault("properties", []).append({"name": "cognitive-java:bundled", "value": "true"})
    dependencies = document.setdefault("dependencies", [])
    by_reference = {edge["ref"]: edge for edge in dependencies}
    for source in core["dependencies"]:
        reference = reference_map[source["ref"]]
        edge = by_reference.get(reference)
        if edge is None:
            edge = {"ref": reference, "dependsOn": []}
            dependencies.append(edge)
            by_reference[reference] = edge
        edge["dependsOn"] = sorted(set(edge.get("dependsOn", [])) | {
            reference_map[target] for target in source.get("dependsOn", [])
        })
    root = document["metadata"]["component"]
    root_edge = by_reference.get(root["bom-ref"])
    if root_edge is None:
        root_edge = {"ref": root["bom-ref"], "dependsOn": []}
        dependencies.append(root_edge)
    root_edge["dependsOn"] = sorted(set(root_edge.get("dependsOn", [])) | {embedded["bom-ref"]})
    root["licenses"] = copy.deepcopy(core["metadata"]["component"]["licenses"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    paths = {name: args.assets / f"cognitive-java-{name}-{args.version}.cdx.json"
             for name in ("core", "cli", "maven-plugin", "gradle-plugin")}
    documents = {name: json.loads(path.read_text(encoding="utf-8")) for name, path in paths.items()}
    jars = {name: repository / name / "target" / f"cognitive-java-{name}-{args.version}.jar"
            for name in ("core", "cli", "maven-plugin")}
    jars["gradle-plugin"] = repository / "gradle-plugin/build/libs" / f"cognitive-java-gradle-plugin-{args.version}.jar"
    merge_embedded_core(documents["gradle-plugin"], documents["core"], jars["core"])
    for name, document in documents.items():
        document["metadata"]["component"]["hashes"] = [{
            "alg": "SHA-256", "content": hashlib.sha256(jars[name].read_bytes()).hexdigest()
        }]
        paths[name].write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
