#!/usr/bin/env python3
"""Validate the release bundle's names and CycloneDX component contracts."""

from __future__ import annotations

import argparse
import json
import os
import hashlib
import re
from datetime import datetime, timezone
from pathlib import Path

from sbom_identity import deterministic_serial_number


def expected_timestamp() -> str:
    raw_epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if raw_epoch is None:
        raise ValueError("SOURCE_DATE_EPOCH is required")
    try:
        epoch = int(raw_epoch)
    except ValueError as error:
        raise ValueError("SOURCE_DATE_EPOCH must be an integer Unix timestamp") from error
    return datetime.fromtimestamp(epoch, timezone.utc).isoformat(
        timespec="seconds"
    ).replace("+00:00", "Z")


def verify_sbom(path: Path, component_name: str, version: str) -> dict:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("bomFormat") != "CycloneDX" or document.get("specVersion") != "1.6":
        raise ValueError(f"{path.name} is not CycloneDX 1.6 JSON")
    serial_number = document.get("serialNumber")
    if not serial_number:
        raise ValueError(f"{path.name} is not recognized as CycloneDX by actions/attest")
    metadata = document.get("metadata", {})
    if metadata.get("timestamp") != expected_timestamp():
        raise ValueError(f"{path.name} has a non-reproducible timestamp")
    if serial_number != deterministic_serial_number(document):
        raise ValueError(f"{path.name} has a non-deterministic BOM serial number")
    component = metadata.get("component", {})
    if component.get("name") != component_name or component.get("version") != version:
        raise ValueError(f"{path.name} identifies the wrong component")
    components = document.get("components", [])
    if not components or not document.get("dependencies"):
        raise ValueError(f"{path.name} has no dependency inventory")
    if not any(candidate.get("licenses") for candidate in components):
        raise ValueError(f"{path.name} contains no available dependency license data")
    hashes = component.get("hashes", [])
    if not any(item.get("alg") == "SHA-256" and re.fullmatch(r"[0-9a-f]{64}", item.get("content", "")) for item in hashes):
        raise ValueError(f"{path.name} is not bound to its main JAR digest")
    references = [component.get("bom-ref"), *[item.get("bom-ref") for item in components]]
    if None in references or len(set(references)) != len(references):
        raise ValueError(f"{path.name} contains missing or duplicate component references")
    for edge in document["dependencies"]:
        if edge.get("ref") not in references or any(ref not in references for ref in edge.get("dependsOn", [])):
            raise ValueError(f"{path.name} has a dangling dependency reference")
    return document


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("assets_directory", type=Path)
    parser.add_argument("version")
    args = parser.parse_args()
    assets = args.assets_directory

    expected_payloads = {
        f"cognitive-java-{args.version}.jar",
        f"cognitive-java-core-{args.version}.cdx.json",
        f"cognitive-java-cli-{args.version}.cdx.json",
        f"cognitive-java-maven-plugin-{args.version}.cdx.json",
        f"cognitive-java-gradle-plugin-{args.version}.cdx.json",
    }
    unsigned_files = expected_payloads | {"SHA256SUMS", "SHA512SUMS"}
    expected_files = unsigned_files | {f"{name}.asc" for name in unsigned_files}
    actual_files = {path.name for path in assets.iterdir()}
    if actual_files != expected_files or not all(path.is_file() for path in assets.iterdir()):
        raise ValueError(f"Release asset mismatch: expected exactly 14 files, found {sorted(actual_files)}")

    components = {
        "core": "cognitive-java-core",
        "cli": "cognitive-java-cli",
        "maven-plugin": "cognitive-java-maven-plugin",
        "gradle-plugin": "cognitive-java-gradle-plugin",
    }
    documents = {}
    for suffix, component_name in components.items():
        documents[suffix] = verify_sbom(
            assets / f"cognitive-java-{suffix}-{args.version}.cdx.json",
            component_name,
            args.version,
        )
    core = documents["core"]
    core_inventory = {(item.get("group"), item["name"], item["version"]) for item in core["components"]}
    for suffix in ("cli", "maven-plugin", "gradle-plugin"):
        document = documents[suffix]
        inventory = {(item.get("group"), item["name"], item["version"]) for item in document["components"]}
        if not (core_inventory | {("media.barney", "cognitive-java-core", args.version)}) <= inventory:
            raise ValueError(f"{suffix} SBOM is missing core or its runtime dependencies")
        root = document["metadata"]["component"]["bom-ref"]
        core_refs = {item["bom-ref"] for item in document["components"] if item.get("group") == "media.barney" and item["name"] == "cognitive-java-core"}
        if not any(edge["ref"] == root and core_refs.intersection(edge.get("dependsOn", [])) for edge in document["dependencies"]):
            raise ValueError(f"{suffix} SBOM does not link the main component to core")
    cli_digest = hashlib.sha256((assets / f"cognitive-java-{args.version}.jar").read_bytes()).hexdigest()
    if {"alg": "SHA-256", "content": cli_digest} not in documents["cli"]["metadata"]["component"]["hashes"]:
        raise ValueError("CLI SBOM does not match the executable release JAR")
    for manifest, algorithm in (("SHA256SUMS", "sha256"), ("SHA512SUMS", "sha512")):
        expected_lines = [f"{hashlib.new(algorithm, (assets / name).read_bytes()).hexdigest()} *{name}" for name in sorted(expected_payloads)]
        if (assets / manifest).read_text(encoding="utf-8").splitlines() != expected_lines:
            raise ValueError(f"{manifest} does not cover exactly the five payloads with correct digests")


if __name__ == "__main__":
    main()
