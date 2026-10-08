"""Exercise the repair in a disposable PostgreSQL container, never application volumes."""
import pathlib
import re
import subprocess
import time
import unittest
import uuid

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "repair-demo-notifications-20261006.sql"


class NotificationRepairTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = "covie-notification-repair-test-" + uuid.uuid4().hex[:12]
        subprocess.run(["docker", "run", "--pull=never", "--rm", "-d", "--name", cls.container,
                        "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgis/postgis:18-3.6"],
                       check=True, capture_output=True, text=True)
        cls.addClassCleanup(lambda: subprocess.run(["docker", "stop", cls.container],
                                                   check=True, capture_output=True))
        for _ in range(60):
            result = subprocess.run(["docker", "exec", cls.container, "pg_isready", "-h", "127.0.0.1", "-U", "postgres"],
                                    capture_output=True)
            if result.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("Disposable PostgreSQL did not become ready")
        cls.sql = SCRIPT.read_text(encoding="utf-8")
        cls.fixture_values = re.search(r"INSERT INTO expected_notifications VALUES\s*(.*?);", cls.sql, re.S).group(1)

    def run_sql(self, sql, *variables, database="covie_demo_recovered", success=True):
        command = ["docker", "exec", "-i", self.container, "psql", "-X", "-h", "127.0.0.1", "-U", "postgres",
                   "-d", database, "-v", "ON_ERROR_STOP=1", "-At"]
        for variable in variables:
            command += ["-v", variable]
        result = subprocess.run(command, input=sql, text=True, capture_output=True, encoding="utf-8")
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
        return result

    def setUp(self):
        self.run_sql("DROP DATABASE IF EXISTS covie_demo_recovered; CREATE DATABASE covie_demo_recovered;",
                     database="postgres")
        self.run_sql("""
          CREATE TABLE orders(id uuid PRIMARY KEY,buyer_user_id uuid,status text,order_code text);
          CREATE TABLE notifications(id uuid PRIMARY KEY,user_id uuid,notification_type text,
            related_entity_type text,related_entity_id uuid,payload jsonb,
            read_at timestamptz,created_at timestamptz DEFAULT now());
          CREATE TEMP TABLE fixtures(ordinal integer,id uuid,order_id uuid,user_id uuid,
            expected_status text,new_type text);
          INSERT INTO fixtures VALUES """ + self.fixture_values + """;
          INSERT INTO orders SELECT order_id,user_id,expected_status,'CV-DEMO-'||lpad(ordinal::text,4,'0') FROM fixtures;
          INSERT INTO notifications(id,user_id,notification_type,related_entity_type,related_entity_id,payload,read_at)
            SELECT id,user_id,'ORDER_STATUS_CHANGED','ORDER',order_id,
              jsonb_build_object('orderId',order_id::text,'retained','extra'),
              CASE WHEN ordinal%3=0 THEN '2026-10-06'::timestamptz END FROM fixtures;
        """)

    def test_dry_run_apply_and_rollback_preserve_unrelated_fields(self):
        original = self.run_sql("SELECT row_to_json(n) FROM notifications n ORDER BY id;").stdout
        self.run_sql(self.sql)
        self.assertEqual(original, self.run_sql("SELECT row_to_json(n) FROM notifications n ORDER BY id;").stdout)
        self.assertEqual(self.run_sql("SELECT to_regclass('demo_notification_backup_20261007') IS NULL;").stdout.strip(), "t")
        self.run_sql(self.sql, "apply=true")
        counts = self.run_sql("SELECT notification_type,count(*) FROM notifications GROUP BY 1 ORDER BY 1;").stdout
        self.assertIn("ORDER_COMPLETED|18", counts)
        self.assertIn("ORDER_FULFILLMENT_UPDATED|3", counts)
        self.assertEqual(self.run_sql("SELECT count(*) FROM notifications WHERE payload->>'deepLink'='/orders/'||related_entity_id::text AND payload->>'retained'='extra';").stdout.strip(), "24")
        self.run_sql(self.sql, "restore=true")
        self.assertEqual(original, self.run_sql("SELECT row_to_json(n) FROM notifications n ORDER BY id;").stdout)

    def test_changed_order_aborts_without_backup(self):
        self.run_sql("UPDATE orders SET status='REJECTED_BY_SELLER' WHERE order_code='CV-DEMO-0001';")
        self.run_sql(self.sql, "apply=true", success=False)
        self.assertEqual(self.run_sql("SELECT count(*) FROM notifications WHERE notification_type='ORDER_STATUS_CHANGED';").stdout.strip(), "24")
        self.assertEqual(self.run_sql("SELECT to_regclass('demo_notification_backup_20261007') IS NULL;").stdout.strip(), "t")

    def test_rollback_aborts_if_payload_changed(self):
        self.run_sql(self.sql, "apply=true")
        self.run_sql("UPDATE notifications SET payload=payload||'{\"later\":true}'::jsonb;")
        self.run_sql(self.sql, "restore=true", success=False)
        self.assertEqual(self.run_sql("SELECT count(*) FROM notifications WHERE notification_type='ORDER_STATUS_CHANGED';").stdout.strip(), "0")

    def test_wrong_database_aborts(self):
        result = self.run_sql(self.sql, database="postgres", success=False)
        self.assertIn("Wrong database", result.stderr)


if __name__ == "__main__":
    unittest.main()
