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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Snowflake Scripting DML-status globals — {@code SQLROWCOUNT}, {@code SQLFOUND},
 * {@code SQLNOTFOUND} and {@code ACTIVITY_COUNT} — exercised with the documentation's own examples
 * plus the boundary cells, every expectation live-verified:
 * <ul>
 *   <li>a DML statement sets all four (the trio from its affected total, ACTIVITY_COUNT to the
 *       same number);</li>
 *   <li>EVERY other completed statement — SELECT, DDL, and TRUNCATE TABLE too — resets the trio to
 *       NULL and sets ACTIVITY_COUNT to its own result's row count (a DDL status line counts 1);</li>
 *   <li>scripting-internal statements (LET, assignments, control flow) touch none of them;</li>
 *   <li>all four start NULL.</li>
 * </ul>
 */
public class DmlStatusVariablesTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void docExampleSqlRowCountAfterInsert() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        assertEquals("3", scalar("""
            BEGIN
              LET sql_row_count_var INT := 0;
              INSERT INTO my_values VALUES (1), (2), (3);
              sql_row_count_var := SQLROWCOUNT;
              SELECT * from my_values;
              RETURN sql_row_count_var;
            END;
            """));
    }

    @Test
    public void docExampleSqlFoundAfterUpdate() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        engine.execute("INSERT INTO my_values VALUES (1), (2), (3)");
        assertEquals("Updated 2 rows.", scalar("""
            BEGIN
              LET sql_row_count_var INT := 0;
              LET sql_found_var BOOLEAN := NULL;
              LET sql_notfound_var BOOLEAN := NULL;
              IF ((SELECT MAX(value) FROM my_values) > 2) THEN
                UPDATE my_values SET value = 4 WHERE value < 3;
                sql_row_count_var := SQLROWCOUNT;
                sql_found_var := SQLFOUND;
                sql_notfound_var := SQLNOTFOUND;
              END IF;
              SELECT * from my_values;
              IF (sql_found_var = true) THEN
                RETURN 'Updated ' || sql_row_count_var || ' rows.';
              ELSEIF (sql_notfound_var = true) THEN
                RETURN 'No rows updated.';
              ELSE
                RETURN 'No DML statements executed.';
              END IF;
            END;
            """));
    }

    @Test
    public void docExampleActivityCount() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        assertEquals("Inserted 3 rows, query returned 2 rows.", scalar("""
            BEGIN
              INSERT INTO my_values VALUES (1), (2), (3);
              LET insert_count INT := ACTIVITY_COUNT;
              SELECT * FROM my_values WHERE value > 1;
              LET select_count INT := ACTIVITY_COUNT;
              RETURN 'Inserted ' || insert_count || ' rows, query returned ' || select_count || ' rows.';
            END;
            """));
    }

    @Test
    public void nonDmlStatementResetsTheTrioToNull() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        assertEquals("rc=<null> f=<null> nf=<null>", scalar("""
            BEGIN
              INSERT INTO my_values VALUES (7);
              SELECT 1;
              RETURN 'rc=' || COALESCE(SQLROWCOUNT::VARCHAR, '<null>')
                || ' f=' || COALESCE(SQLFOUND::VARCHAR, '<null>')
                || ' nf=' || COALESCE(SQLNOTFOUND::VARCHAR, '<null>');
            END;
            """));
    }

    @Test
    public void allFourStartNull() {
        assertEquals("rc=<null> f=<null> nf=<null> ac=<null>", scalar("""
            BEGIN
              RETURN 'rc=' || COALESCE(SQLROWCOUNT::VARCHAR, '<null>')
                || ' f=' || COALESCE(SQLFOUND::VARCHAR, '<null>')
                || ' nf=' || COALESCE(SQLNOTFOUND::VARCHAR, '<null>')
                || ' ac=' || COALESCE(ACTIVITY_COUNT::VARCHAR, '<null>');
            END;
            """));
    }

    @Test
    public void zeroRowUpdateAnswersNotFound() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        assertEquals("rc=0 f=false nf=true", scalar("""
            BEGIN
              UPDATE my_values SET value = 9 WHERE value = -1;
              RETURN 'rc=' || SQLROWCOUNT::VARCHAR || ' f=' || SQLFOUND::VARCHAR
                || ' nf=' || SQLNOTFOUND::VARCHAR;
            END;
            """));
    }

    @Test
    public void truncateResetsTheTrioLikeAnyNonDml() {
        engine.execute("CREATE OR REPLACE TABLE my_values (value NUMBER)");
        assertEquals("rc=<null> f=<null> nf=<null>", scalar("""
            BEGIN
              INSERT INTO my_values VALUES (1), (2);
              TRUNCATE TABLE my_values;
              RETURN 'rc=' || COALESCE(SQLROWCOUNT::VARCHAR, '<null>')
                || ' f=' || COALESCE(SQLFOUND::VARCHAR, '<null>')
                || ' nf=' || COALESCE(SQLNOTFOUND::VARCHAR, '<null>');
            END;
            """));
    }

    @Test
    public void activityCountAfterDdlIsItsStatusRow() {
        assertEquals("before=<null> after_ddl=1", scalar("""
            BEGIN
              LET before INT := ACTIVITY_COUNT;
              CREATE OR REPLACE TABLE act_probe (v INT);
              LET after_ddl INT := ACTIVITY_COUNT;
              RETURN 'before=' || COALESCE(before::VARCHAR, '<null>')
                || ' after_ddl=' || COALESCE(after_ddl::VARCHAR, '<null>');
            END;
            """));
    }
}
