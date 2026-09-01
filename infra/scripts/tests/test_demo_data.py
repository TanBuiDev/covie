from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "demo-data.py"
SPEC = importlib.util.spec_from_file_location("demo_data", SCRIPT)
assert SPEC and SPEC.loader
DEMO_DATA = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DEMO_DATA)


class DemoDataCountSqlTest(unittest.TestCase):
    def test_names_pair_columns_used_by_final_projection(self) -> None:
        sql = DEMO_DATA.count_sql().decode()

        self.assertIn("pairs(metric, value) as (", sql)
        self.assertIn(
            "select metric || '=' || value from pairs order by metric;",
            sql,
        )


if __name__ == "__main__":
    unittest.main()
