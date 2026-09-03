from __future__ import annotations

import gzip
import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "generate-demo-seed.py"
SPEC = importlib.util.spec_from_file_location("generate_demo_seed", SCRIPT)
assert SPEC and SPEC.loader
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class DemoSeedGeneratorTest(unittest.TestCase):
    def test_rendered_sql_is_guarded_complete_and_has_pinned_fixture_counts(self) -> None:
        sql = GENERATOR.render_sql()

        self.assertIn("current_setting('covie.demo.allow_seed', true)", sql)
        self.assertTrue(sql.rstrip().endswith("commit;"))
        self.assertEqual(GENERATOR.EXPECTED["users"], 22)
        self.assertEqual(GENERATOR.EXPECTED["merchants"], 10)
        self.assertEqual(GENERATOR.EXPECTED["catalog_items"], 45)
        self.assertEqual(GENERATOR.EXPECTED["orders"], 26)
        self.assertIn("buyer-01@demo.covie.invalid", sql)
        self.assertIn("demo-mer-10", sql)
        self.assertIn("svc-015-giao-tai-lieu-do-nho", sql)

    def test_gzip_is_deterministic_and_round_trips_full_sql(self) -> None:
        first = GENERATOR.render_gzip()
        second = GENERATOR.render_gzip()

        self.assertEqual(first, second)
        self.assertEqual(gzip.decompress(first).decode("utf-8"), GENERATOR.render_sql())

    def test_checked_in_snapshot_matches_generator(self) -> None:
        snapshot = SCRIPT.parents[1] / "demo" / "seed-demo-data-v1.sql.gz"

        self.assertEqual(snapshot.read_bytes(), GENERATOR.render_gzip())


if __name__ == "__main__":
    unittest.main()
