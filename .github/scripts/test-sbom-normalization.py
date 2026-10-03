#!/usr/bin/env python3
"""Regression tests for deterministic, attestable CycloneDX normalization."""

from __future__ import annotations

import copy
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
NORMALIZER = SCRIPT_DIRECTORY / "normalize-sbom.py"
VERIFIER = SCRIPT_DIRECTORY / "verify-release-assets.py"
SOURCE_DATE_EPOCH = 1_789_108_029


def sample_document(component_name: str = "cognitive-java-core") -> dict[str, object]:
    return {
        "specVersion": "1.6",
        "bomFormat": "CycloneDX",
        "serialNumber": "urn:uuid:00000000-0000-4000-8000-000000000000",
        "version": 1,
        "metadata": {
            "component": {
                "group": "media.barney",
                "bom-ref": component_name,
                "version": "1.0.1",
                "name": component_name,
                "type": "library",
                "hashes": [{"alg": "SHA-256", "content": hashlib.sha256(b"test archive").hexdigest()}],
            },
            "timestamp": "2099-01-01T00:00:00Z",
        },
        "components": [
            {
                "version": "2.0.0",
                "bom-ref": "second",
                "name": "second",
                "type": "library",
                "licenses": [{"license": {"id": "Apache-2.0"}}],
            },
            {
                "name": "first",
                "bom-ref": "first",
                "type": "library",
                "version": "1.0.0",
                "licenses": [{"license": {"id": "MIT"}}],
            },
        ],
        "dependencies": [
            {"dependsOn": ["second", "first"], "ref": component_name},
        ],
    }


def normalize(path: Path, epoch: int = SOURCE_DATE_EPOCH) -> dict[str, object]:
    subprocess.run(
        [sys.executable, "-B", str(NORMALIZER), str(path), str(epoch)],
        check=True,
    )
    return json.loads(path.read_text(encoding="utf-8"))


class SbomNormalizationTest(unittest.TestCase):
    def test_equivalent_documents_are_byte_identical_and_attestable(self) -> None:
        first = sample_document()
        second = copy.deepcopy(first)
        second["serialNumber"] = "urn:uuid:11111111-1111-4111-8111-111111111111"
        second["components"] = list(reversed(second["components"]))
        second["dependencies"][0]["dependsOn"].reverse()

        with tempfile.TemporaryDirectory() as directory:
            first_path = Path(directory) / "first.json"
            second_path = Path(directory) / "second.json"
            first_path.write_text(json.dumps(first), encoding="utf-8")
            second_path.write_text(json.dumps(second), encoding="utf-8")

            normalized = normalize(first_path)
            normalize(second_path)

            self.assertEqual(first_path.read_bytes(), second_path.read_bytes())
            serial_number = normalized["serialNumber"]
            self.assertTrue(serial_number.startswith("urn:uuid:"))
            self.assertEqual(5, uuid.UUID(serial_number.removeprefix("urn:uuid:")).version)
            self.assertTrue(
                all(normalized.get(field) for field in ("bomFormat", "specVersion", "serialNumber"))
            )
            self.assertEqual("2026-09-11T06:27:09Z", normalized["metadata"]["timestamp"])

    def test_distinct_component_documents_have_distinct_serial_numbers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            core_path = Path(directory) / "core.json"
            cli_path = Path(directory) / "cli.json"
            core_path.write_text(json.dumps(sample_document()), encoding="utf-8")
            cli_path.write_text(
                json.dumps(sample_document("cognitive-java-cli")),
                encoding="utf-8",
            )

            core = normalize(core_path)
            cli = normalize(cli_path)

            self.assertNotEqual(core["serialNumber"], cli["serialNumber"])

    def test_release_verifier_accepts_attestable_serials_and_rejects_missing_one(self) -> None:
        components = {
            "core": "cognitive-java-core",
            "cli": "cognitive-java-cli",
            "maven-plugin": "cognitive-java-maven-plugin",
            "gradle-plugin": "cognitive-java-gradle-plugin",
        }
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            (assets / "cognitive-java-1.0.1.jar").write_bytes(b"test archive")
            for suffix, component_name in components.items():
                sbom = assets / f"cognitive-java-{suffix}-1.0.1.cdx.json"
                document = sample_document(component_name)
                if suffix != "core":
                    document["components"].append({"group": "media.barney", "name": "cognitive-java-core", "version": "1.0.1", "type": "library", "bom-ref": "core"})
                    document["dependencies"][0]["dependsOn"].append("core")
                sbom.write_text(
                    json.dumps(document),
                    encoding="utf-8",
                )
                normalize(sbom)

            payloads = sorted(path for path in assets.iterdir())
            for manifest, algorithm in (("SHA256SUMS", "sha256"), ("SHA512SUMS", "sha512")):
                (assets / manifest).write_text("".join(f"{hashlib.new(algorithm, path.read_bytes()).hexdigest()}  {path.name}\n" for path in payloads), encoding="utf-8")
            for path in list(assets.iterdir()):
                (assets / f"{path.name}.asc").write_text("signature verified by shell preflight", encoding="utf-8")

            environment = os.environ.copy()
            environment["SOURCE_DATE_EPOCH"] = str(SOURCE_DATE_EPOCH)
            verification_command = [
                sys.executable,
                "-B",
                str(VERIFIER),
                str(assets),
                "1.0.1",
            ]
            subprocess.run(verification_command, check=True, env=environment)

            extra = assets / "unexpected.txt"
            extra.write_text("unexpected", encoding="utf-8")
            rejected = subprocess.run(verification_command, capture_output=True, env=environment, text=True)
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("expected exactly 14", rejected.stderr)
            extra.unlink()

            cli_sbom = assets / "cognitive-java-cli-1.0.1.cdx.json"
            original_cli = cli_sbom.read_bytes()
            cli_document = json.loads(original_cli)
            cli_document["components"] = [item for item in cli_document["components"] if item["name"] != "cognitive-java-core"]
            cli_document["dependencies"][0]["dependsOn"].remove("core")
            cli_sbom.write_text(json.dumps(cli_document), encoding="utf-8")
            normalize(cli_sbom)
            rejected = subprocess.run(verification_command, capture_output=True, env=environment, text=True)
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("missing core or its runtime dependencies", rejected.stderr)
            cli_sbom.write_bytes(original_cli)

            executable = assets / "cognitive-java-1.0.1.jar"
            executable.write_bytes(b"tampered archive")
            rejected = subprocess.run(verification_command, capture_output=True, env=environment, text=True)
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("does not match the executable", rejected.stderr)
            executable.write_bytes(b"test archive")

            core_sbom = assets / "cognitive-java-core-1.0.1.cdx.json"
            core_document = json.loads(core_sbom.read_text(encoding="utf-8"))
            core_document["metadata"]["timestamp"] = "2099-01-01T00:00:00Z"
            core_sbom.write_text(json.dumps(core_document), encoding="utf-8")
            bad_timestamp = subprocess.run(
                verification_command,
                check=False,
                capture_output=True,
                env=environment,
                text=True,
            )

            self.assertNotEqual(0, bad_timestamp.returncode)
            self.assertIn("has a non-reproducible timestamp", bad_timestamp.stderr)

            core_document.pop("serialNumber")
            core_sbom.write_text(json.dumps(core_document), encoding="utf-8")
            rejected = subprocess.run(
                verification_command,
                check=False,
                capture_output=True,
                env=environment,
                text=True,
            )

            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("not recognized as CycloneDX by actions/attest", rejected.stderr)


if __name__ == "__main__":
    unittest.main()
