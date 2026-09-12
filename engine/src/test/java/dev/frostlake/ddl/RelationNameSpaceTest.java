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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class RelationNameSpaceTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void oneNameSpaceHoldsEveryRelationKind() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P470_DB");
            engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
            engine.execute("INSERT INTO kt VALUES (1)");
            engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 2 AS a");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW km AS SELECT a FROM kt");
            engine.execute("CREATE OR REPLACE STREAM kstr ON TABLE kt");
            assertRefused("CREATE VIEW kt AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KT' already exists as TABLE");
            assertRefused("CREATE TABLE kv (a INT)",
                "SQL compilation error:\nObject 'KV' already exists as VIEW");
            engine.execute("CREATE TEMPORARY TABLE km (a INT)");
            assertRefused("CREATE TEMPORARY TABLE kstr (a INT)",
                "SQL compilation error:\nObject 'KSTR' already exists as STREAM");
            engine.execute("CREATE TEMPORARY TABLE kv (a INT)");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM kv"));
            engine.execute("INSERT INTO kv VALUES (5)");
            assertEquals("5",
                rows("SELECT a FROM kv"));
            engine.execute("DROP TABLE kv");
            assertEquals("2",
                rows("SELECT a FROM kv"));
            engine.execute("CREATE TEMPORARY VIEW kt AS SELECT 1 AS a");
            assertRefused("CREATE VIEW IF NOT EXISTS kt AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KT' already exists as TABLE");
            assertRefused("CREATE OR REPLACE TABLE kv (a INT)",
                "SQL compilation error:\nObject 'KV' already exists as VIEW");
            assertRefused("CREATE TABLE IF NOT EXISTS kstr (a INT)",
                "SQL compilation error:\nObject 'KSTR' already exists as STREAM");
            assertRefused("CREATE STREAM kv ON TABLE kt",
                "SQL compilation error:\nObject 'KV' already exists as VIEW");
            assertRefused("ALTER VIEW kv RENAME TO kt",
                "SQL compilation error:\nObject 'KT' already exists.");
            engine.execute("CREATE SEQUENCE kv");
            engine.execute("DROP VIEW kt");
            assertRefused("DROP TABLE kv",
                "SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.");
            assertRefused("CREATE OR REPLACE VIEW kt AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KT' already exists as TABLE");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P470_DB");
        }
    }

    @Test
    public void aDynamicTableHoldsTheNameToo() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P470C_DB");
            engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
            engine.execute("INSERT INTO kt VALUES (1)");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE kd TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT a FROM kt");
            assertRefused("CREATE TABLE kd (a INT)",
                "SQL compilation error:\nObject 'KD' already exists as DYNAMIC_TABLE");
            engine.execute("CREATE TEMPORARY TABLE kd (a INT)");
            assertRefused("CREATE VIEW kd AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KD' already exists as DYNAMIC_TABLE");
            assertRefused("CREATE OR REPLACE VIEW kd AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KD' already exists as DYNAMIC_TABLE");
            assertRefused("CREATE TABLE IF NOT EXISTS kd (a INT)",
                "SQL compilation error:\nObject 'KD' already exists as DYNAMIC_TABLE");
            assertRefused("CREATE DYNAMIC TABLE kt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KT' already exists as TABLE");
            assertRefused("CREATE OR REPLACE DYNAMIC TABLE kt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KT' already exists as TABLE");
            assertRefused("CREATE MATERIALIZED VIEW kd AS SELECT a FROM kt",
                "SQL compilation error:\nObject 'KD' already exists as DYNAMIC_TABLE");
            assertRefused("CREATE STREAM kd ON TABLE kt",
                "SQL compilation error:\nObject 'KD' already exists as TABLE");
            assertRefused("CREATE TEMPORARY VIEW kd AS SELECT 1 AS a",
                "SQL compilation error:\nObject 'KD' already exists as TABLE");
            engine.execute("DROP TABLE kd");
            assertRefused("DROP VIEW kd",
                "SQL compilation error: Object found is of type 'DYNAMIC_TABLE', not specified type 'VIEW'.");
            engine.execute("DROP DYNAMIC TABLE kd");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P470C_DB");
        }
    }

    @Test
    public void aDropNamesTheKindItFinds() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P470D_DB");
            engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
            engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW km AS SELECT a FROM kt");
            assertRefused("DROP TABLE IF EXISTS kv",
                "SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.");
            assertRefused("DROP VIEW IF EXISTS kt",
                "SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'.");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM kv"));
            assertRefused("DROP MATERIALIZED VIEW IF EXISTS kt",
                "SQL compilation error: Object found is of type 'TABLE', not specified type 'MATERIALIZED_VIEW'.");
            assertRefused("DROP TABLE km",
                "SQL compilation error: Object found is of type 'MATERIALIZED_VIEW', not specified type 'TABLE'.");
            assertRefused("DROP STREAM IF EXISTS kt",
                "SQL compilation error: Object found is of type 'TABLE', not specified type 'STREAM'.");
            assertRefused("DROP TABLE kv",
                "SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM kv"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P470D_DB");
        }
    }

    @Test
    public void aTemporaryTableShadowsWhatItCovers() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P470E_DB");
            engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
            engine.execute("INSERT INTO kt VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW km AS SELECT a FROM kt");
            engine.execute("CREATE OR REPLACE DYNAMIC TABLE kd TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT a FROM kt");
            engine.execute("CREATE TEMPORARY TABLE km (b INT)");
            engine.execute("INSERT INTO km VALUES (7)");
            assertEquals("7",
                rows("SELECT * FROM km"));
            engine.execute("CREATE TEMPORARY TABLE kd (b INT)");
            engine.execute("INSERT INTO kd VALUES (9)");
            assertEquals("9",
                rows("SELECT * FROM kd"));
            engine.execute("DROP TABLE km");
            assertEquals("1 | 2",
                rows("SELECT * FROM km"));
            engine.execute("DROP TABLE kd");
            assertEquals("1 | 2",
                rows("SELECT * FROM kd"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P470E_DB");
        }
    }

    @Test
    public void aRenameOntoATakenNameSaysItPlainly() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P470F_DB");
            engine.execute("CREATE OR REPLACE TABLE kt (a INT)");
            engine.execute("CREATE OR REPLACE VIEW kv AS SELECT 1 AS a");
            assertRefused("ALTER VIEW kv RENAME TO kv",
                "SQL compilation error:\nObject 'KV' already exists.");
            assertRefused("ALTER TABLE kt RENAME TO kt",
                "SQL compilation error:\nObject 'KT' already exists.");
            assertRefused("ALTER TABLE kt RENAME TO kv",
                "SQL compilation error:\nObject 'KV' already exists.");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW km AS SELECT a FROM kt");
            assertRefused("ALTER VIEW kv RENAME TO km",
                "SQL compilation error:\nObject 'KM' already exists.");
            assertRefused("ALTER TABLE kt RENAME TO km",
                "SQL compilation error:\nObject 'KM' already exists.");
            assertEquals("1",
                rows("SELECT COUNT(*) FROM kv"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P470F_DB");
        }
    }
}
