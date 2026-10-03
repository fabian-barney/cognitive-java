#!/usr/bin/env python3
"""Exercise inventory and graph preservation for Gradle's bundled core."""

import copy
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare_component_sboms", Path(__file__).with_name("prepare-component-sboms.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EmbeddedCoreTest(unittest.TestCase):
    def setUp(self):
        self.core = {
            "metadata": {"component": {"group": "media.barney", "name": "cognitive-java-core", "version": "0.7.1", "bom-ref": "core", "licenses": [{"license": {"id": "Apache-2.0"}}]}},
            "components": [{"group": "dev.toonformat", "name": "jtoon", "version": "2.0.4", "bom-ref": "maven-jtoon"}],
            "dependencies": [{"ref": "core", "dependsOn": ["maven-jtoon"]}, {"ref": "maven-jtoon", "dependsOn": []}],
        }
        self.gradle = {
            "metadata": {"component": {"bom-ref": "gradle"}},
            "components": [{"group": "dev.toonformat", "name": "jtoon", "version": "2.0.4", "bom-ref": "gradle-jtoon"}],
            "dependencies": [{"ref": "gradle", "dependsOn": ["gradle-jtoon"]}],
        }

    def test_preserves_resolved_gradle_references_and_adds_embedded_core(self):
        original = copy.deepcopy(self.core)
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "core.jar"
            jar.write_bytes(b"core artifact")
            module.merge_embedded_core(self.gradle, self.core, jar)
        self.assertEqual(original, self.core)
        self.assertEqual(2, len(self.gradle["components"]))
        graph = {edge["ref"]: set(edge["dependsOn"]) for edge in self.gradle["dependencies"]}
        self.assertEqual({"core", "gradle-jtoon"}, graph["gradle"])
        self.assertEqual({"gradle-jtoon"}, graph["core"])
        core = next(item for item in self.gradle["components"] if item["bom-ref"] == "core")
        self.assertEqual("SHA-256", core["hashes"][0]["alg"])
        self.assertIn({"name": "cognitive-java:bundled", "value": "true"}, core["properties"])

    def test_rejects_a_conflicting_resolved_version(self):
        self.gradle["components"][0]["version"] = "1.0.9"
        with self.assertRaisesRegex(ValueError, "Conflicting runtime dependency version"):
            module.merge_embedded_core(self.gradle, self.core, Path("unused.jar"))


if __name__ == "__main__":
    unittest.main()
