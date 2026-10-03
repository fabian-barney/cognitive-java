#!/usr/bin/env python3
"""Exercise fail-closed registry collision checks with an HTTP server."""

import importlib.util
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_collisions", Path(__file__).with_name("check-release-collisions.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RegistryHandler(BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.send_response({"/missing": 404, "/public": 200, "/error": 503, "/denied": 403}[self.path])
        self.end_headers()

    def log_message(self, *args):
        pass


class CollisionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), RegistryHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_all_coordinates_must_be_absent(self):
        module.assert_unpublished([self.base + "/missing"])
        with self.assertRaisesRegex(ValueError, "already public"):
            module.assert_unpublished([self.base + "/missing", self.base + "/public"])

    def test_errors_cannot_be_interpreted_as_absence(self):
        for path in ("/error", "/denied"):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "Cannot establish absence"):
                module.assert_unpublished([self.base + path])


if __name__ == "__main__":
    unittest.main()
