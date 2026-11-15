import hashlib
import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("fetch_corpus", HERE / "fetch-corpus.py")
fetch_corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fetch_corpus)


class DownloadTest(unittest.TestCase):
    def test_a_file_that_fails_its_sha512_is_not_kept_in_the_cache(self):
        url = fetch_corpus.CDN + "data/AbCdEfGh/versions/12345678/mod.jar"
        self.assertTrue(fetch_corpus.IMMUTABLE.match(url), url)
        version = {"files": [{"primary": True, "url": url, "filename": "mod.jar",
                              "hashes": {"sha512": hashlib.sha512(b"the real jar").hexdigest()}}]}
        with tempfile.TemporaryDirectory() as cache, tempfile.TemporaryDirectory() as out, \
                mock.patch.dict(os.environ, {"MODRINTH_CACHE": cache}), \
                mock.patch.object(fetch_corpus, "fetch", return_value=b"a damaged jar"):
            with self.assertRaises(ValueError):
                fetch_corpus.download(version, out)
            self.assertEqual([], os.listdir(cache))
            self.assertEqual([], os.listdir(out))

    def test_a_file_that_passes_stays_cached(self):
        url = fetch_corpus.CDN + "data/AbCdEfGh/versions/12345678/mod.jar"
        version = {"files": [{"primary": True, "url": url, "filename": "mod.jar",
                              "hashes": {"sha512": hashlib.sha512(b"the real jar").hexdigest()}}]}
        with tempfile.TemporaryDirectory() as cache, tempfile.TemporaryDirectory() as out, \
                mock.patch.dict(os.environ, {"MODRINTH_CACHE": cache}), \
                mock.patch.object(fetch_corpus, "fetch", return_value=b"the real jar"):
            self.assertEqual("mod.jar", fetch_corpus.download(version, out))
            self.assertEqual(1, len(os.listdir(cache)))


if __name__ == "__main__":
    unittest.main()
