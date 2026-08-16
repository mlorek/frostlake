/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every complete example from Snowflake's Scripting "loops" documentation page, with the answer each
 * one gives on a real account.
 *
 * <p>Doc examples are the cheapest fidelity check there is — self-contained, and their answers can be
 * measured rather than reasoned about. These fourteen cover all six loop constructs (counter, cursor
 * and RESULTSET FOR loops; WHILE; REPEAT; LOOP; BREAK; CONTINUE) and found two grammar gaps that the
 * unit tests had not: {@code LIMIT :bind}, and a loop LABEL spelled INNER.
 *
 * <p>The expected values below were recorded from the account BEFORE the engine was changed, which is
 * the point — a doc page states what an example does, and the account states what it answers.
 */
public class ScriptingLoopsDocExamplesTest extends BaseDatabaseTest {

    /** {label, block, the answer live gives}. */
    private static final String[][] EXAMPLES = {
        {"1 FOR counter", """
            DECLARE
              counter INTEGER DEFAULT 0;
              maximum_count INTEGER default 5;
            BEGIN
              FOR i IN 1 TO maximum_count DO
                counter := counter + 1;
              END FOR;
              RETURN counter;
            END""", "5"},
        {"2 FOR + INSERT", """
            DECLARE
              counter INTEGER DEFAULT 0;
              maximum_count INTEGER default 5;
            BEGIN
              CREATE OR REPLACE TABLE test_for_loop_insert(i INTEGER);
              FOR i IN 1 TO maximum_count DO
                INSERT INTO test_for_loop_insert VALUES (:i);
                counter := counter + 1;
              END FOR;
              RETURN counter || ' rows inserted';
            END""", "5 rows inserted"},
        {"3 FOR date dimension", """
            DECLARE
              start_date DATE DEFAULT '2025-01-01';
              current_date_val DATE;
            BEGIN
              CREATE OR REPLACE TABLE date_dimension (
                date_key INTEGER, full_date DATE, day_of_week VARCHAR,
                month_name VARCHAR, quarter INTEGER, year INTEGER);
              FOR i IN 1 TO 7 DO
                current_date_val := DATEADD('day', :i - 1, :start_date);
                INSERT INTO date_dimension
                  SELECT :i, :current_date_val, DAYNAME(:current_date_val),
                    MONTHNAME(:current_date_val), QUARTER(:current_date_val),
                    YEAR(:current_date_val);
              END FOR;
              RETURN 'Populated date dimension with 7 rows';
            END""", "Populated date dimension with 7 rows"},
        {"4 FOR cursor", """
            DECLARE
              total_price FLOAT;
              c1 CURSOR FOR SELECT price FROM invoices;
            BEGIN
              total_price := 0.0;
              FOR record IN c1 DO
                total_price := total_price + record.price;
              END FOR;
              RETURN total_price;
            END""", "33.33"},
        {"5 FOR cursor salary", """
            DECLARE
              rows_updated INTEGER DEFAULT 0;
              raise_pct INTEGER;
              new_salary NUMBER(12,2);
              cur_emp_id INTEGER;
              cur_salary NUMBER(12,2);
              c1 CURSOR FOR SELECT emp_id, department, salary FROM loop_test_employees;
            BEGIN
              FOR record IN c1 DO
                cur_emp_id := record.emp_id;
                cur_salary := record.salary;
                IF (record.department = 'Engineering') THEN
                  raise_pct := 10;
                ELSE
                  raise_pct := 5;
                END IF;
                new_salary := :cur_salary * (1 + :raise_pct / 100);
                UPDATE loop_test_employees SET salary = :new_salary WHERE emp_id = :cur_emp_id;
                INSERT INTO salary_audit
                  SELECT :cur_emp_id, :cur_salary, :new_salary, CURRENT_TIMESTAMP();
                rows_updated := rows_updated + 1;
              END FOR;
              RETURN rows_updated || ' employees updated';
            END""", "2 employees updated"},
        {"6 FOR resultset", """
            DECLARE
              total_price FLOAT;
              rs RESULTSET;
            BEGIN
              total_price := 0.0;
              rs := (SELECT price FROM invoices);
              FOR record IN rs DO
                total_price := total_price + record.price;
              END FOR;
              RETURN total_price;
            END""", "33.33"},
        {"7 FOR resultset validate", """
            DECLARE
              rs RESULTSET;
              valid_count INTEGER DEFAULT 0;
              invalid_count INTEGER DEFAULT 0;
              cur_customer_id INTEGER;
            BEGIN
              rs := (SELECT customer_id, customer_email, customer_phone FROM loop_test_customers WHERE status = 'pending_review');
              FOR record IN rs DO
                cur_customer_id := record.customer_id;
                IF (record.customer_email IS NOT NULL AND record.customer_phone IS NOT NULL) THEN
                  UPDATE loop_test_customers SET status = 'verified' WHERE customer_id = :cur_customer_id;
                  valid_count := valid_count + 1;
                ELSE
                  UPDATE loop_test_customers SET status = 'incomplete' WHERE customer_id = :cur_customer_id;
                  invalid_count := invalid_count + 1;
                END IF;
              END FOR;
              RETURN 'Verified: ' || valid_count || ', Incomplete: ' || invalid_count;
            END""", "Verified: 1, Incomplete: 1"},
        {"8 WHILE basic", """
            BEGIN
              LET counter := 0;
              WHILE (counter < 5) DO
                counter := counter + 1;
              END WHILE;
              RETURN counter;
            END""", "5"},
        {"9 WHILE daily sales", """
            DECLARE
              next_date DATE;
            BEGIN
              next_date := (SELECT MIN(txn_date) FROM loop_test_raw_transactions WHERE NOT loaded);
              WHILE (next_date IS NOT NULL) DO
                INSERT INTO loop_test_daily_sales_summary
                  SELECT txn_date, SUM(amount), COUNT(*)
                  FROM loop_test_raw_transactions
                  WHERE txn_date = :next_date AND NOT loaded
                  GROUP BY txn_date;
                UPDATE loop_test_raw_transactions SET loaded = TRUE WHERE txn_date = :next_date;
                next_date := (SELECT MIN(txn_date) FROM loop_test_raw_transactions WHERE NOT loaded);
              END WHILE;
              RETURN 'Daily summaries created for all transaction dates';
            END""", "Daily summaries created for all transaction dates"},
        {"10 REPEAT basic", """
            BEGIN
              LET counter := 5;
              LET number_of_iterations := 0;
              REPEAT
                counter := counter - 1;
                number_of_iterations := number_of_iterations + 1;
              UNTIL (counter = 0)
              END REPEAT;
              RETURN number_of_iterations;
            END""", "5"},
        {"11 REPEAT batches", """
            DECLARE
              batch_size INTEGER DEFAULT 2;
              batch_id INTEGER DEFAULT 1;
              remaining INTEGER;
            BEGIN
              remaining := (SELECT COUNT(*) FROM loop_test_orders_staging);
              REPEAT
                INSERT INTO loop_test_orders_processed
                  SELECT order_id, customer, amount, :batch_id
                  FROM loop_test_orders_staging
                  ORDER BY order_id
                  LIMIT :batch_size;
                DELETE FROM loop_test_orders_staging WHERE order_id IN (
                  SELECT order_id FROM loop_test_orders_processed WHERE batch_id = :batch_id);
                batch_id := batch_id + 1;
                remaining := (SELECT COUNT(*) FROM loop_test_orders_staging);
              UNTIL (remaining = 0)
              END REPEAT;
              RETURN 'Processed all orders in ' || (batch_id - 1) || ' batches';
            END""", "Processed all orders in 3 batches"},
        {"12 LOOP + BREAK", """
            BEGIN
              LET counter := 5;
              LOOP
                IF (counter = 0) THEN
                  BREAK;
                END IF;
                counter := counter - 1;
              END LOOP;
              RETURN counter;
            END""", "0"},
        {"13 LOOP archival", """
            DECLARE
              cutoff_date DATE DEFAULT DATEADD('month', -1, CURRENT_DATE());
              oldest_date DATE;
              archived_total INTEGER DEFAULT 0;
              batch_count INTEGER;
            BEGIN
              LOOP
                oldest_date := (SELECT MIN(event_date) FROM loop_test_event_log WHERE event_date < :cutoff_date);
                IF (oldest_date IS NULL) THEN
                  BREAK;
                END IF;
                batch_count := (SELECT COUNT(*) FROM loop_test_event_log WHERE event_date = :oldest_date);
                INSERT INTO loop_test_event_log_archive
                  SELECT event_id, event_date, event_description, CURRENT_DATE()
                    FROM loop_test_event_log WHERE event_date = :oldest_date;
                DELETE FROM loop_test_event_log WHERE event_date = :oldest_date;
                archived_total := archived_total + batch_count;
              END LOOP;
              RETURN 'Archived ' || archived_total || ' events';
            END""", "Archived 2 events"},
        {"14 labelled BREAK/CONTINUE", """
            BEGIN
              LET inner_counter := 0;
              LET outer_counter := 0;
              LOOP
                LOOP
                  IF (inner_counter < 5) THEN
                    inner_counter := inner_counter + 1;
                    CONTINUE OUTER;
                  ELSE
                    BREAK OUTER;
                  END IF;
                END LOOP INNER;
                outer_counter := outer_counter + 1;
                BREAK;
              END LOOP OUTER;
              RETURN ARRAY_CONSTRUCT(outer_counter, inner_counter);
            END""", "[0,5]"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE invoices (price FLOAT)");
        engine.execute("INSERT INTO invoices VALUES (11.11), (22.22)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_employees (emp_id INT, department VARCHAR, salary NUMBER(12,2))");
        engine.execute("INSERT INTO loop_test_employees VALUES (1,'Engineering',100000), (2,'Sales',80000)");
        engine.execute("CREATE OR REPLACE TABLE salary_audit (emp_id INT, old_salary NUMBER(12,2), new_salary NUMBER(12,2), changed_at TIMESTAMP_NTZ)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_customers (customer_id INT, customer_email VARCHAR, customer_phone VARCHAR, status VARCHAR)");
        engine.execute("INSERT INTO loop_test_customers VALUES (1,'a@b.c','555','pending_review'), (2,NULL,'556','pending_review')");
        engine.execute("CREATE OR REPLACE TABLE loop_test_raw_transactions (txn_date DATE, amount NUMBER, loaded BOOLEAN)");
        engine.execute("INSERT INTO loop_test_raw_transactions VALUES ('2025-01-01',10,FALSE), ('2025-01-01',20,FALSE), ('2025-01-02',30,FALSE)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_daily_sales_summary (txn_date DATE, total NUMBER, cnt NUMBER)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_orders_staging (order_id INT, customer VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO loop_test_orders_staging VALUES (1,'a',10),(2,'b',20),(3,'c',30),(4,'d',40),(5,'e',50)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_orders_processed (order_id INT, customer VARCHAR, amount NUMBER, batch_id INT)");
        engine.execute("CREATE OR REPLACE TABLE loop_test_event_log (event_id INT, event_date DATE, event_description VARCHAR)");
        engine.execute("INSERT INTO loop_test_event_log VALUES (1,'2020-01-01','old one'), (2,'2020-01-02','old two')");
        engine.execute("CREATE OR REPLACE TABLE loop_test_event_log_archive (event_id INT, event_date DATE, event_description VARCHAR, archived_on DATE)");
    }

    @Test
    public void everyLoopsDocExampleAnswersWhatLiveAnswers() {
        for (final String[] example : EXAMPLES) {
            final ResultSet rs = engine.executeQuery(example[1]);
            final StringBuilder actual = new StringBuilder();
            for (int row = 0; row < rs.getRowCount(); row++) {
                actual.append(String.valueOf(rs.getRows().get(row).getValue(0))
                    .replace("\n", "").replace("  ", ""));
            }
            assertEquals(example[2], actual.toString(), "loops doc example " + example[0]);
        }
    }
}
