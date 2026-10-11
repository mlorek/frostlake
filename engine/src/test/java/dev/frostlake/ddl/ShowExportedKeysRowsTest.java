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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SHOW EXPORTED KEYS lists the foreign keys that reference the tables of its scope, wherever the referencing table
 * lives: the rows SHOW IMPORTED KEYS lists for the referencing side, read from the referenced one. A table lists
 * the keys other tables import from it and none it imports itself; a schema, a database and the unscoped listing —
 * the current schema — list by the schema of the referenced table. A key of several columns is one row per column,
 * each with its place in the key and the constraint's name. Every answer is live-verified.
 */
public class ShowExportedKeysRowsTest extends MeasuredCellsTestSupport {

    private static final String KEYS = """
        SELECT "pk_schema_name", "pk_table_name", "pk_column_name", "fk_schema_name", "fk_table_name",
            "fk_column_name", "key_sequence", "update_rule", "delete_rule", "deferrability", "rely"
        FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))
        ORDER BY "pk_table_name", "fk_schema_name", "fk_table_name", "key_sequence"
        """;

    private static final String FROM_CHILD =
        "TEST_SCHEMA|EK_PARENT|A|TEST_SCHEMA|EK_CHILD|B|1|NO ACTION|NO ACTION|NOT DEFERRABLE|false";

    private static final String FROM_FAR_CHILD =
        "TEST_SCHEMA|EK_PARENT|A|EK_OTHER|FAR_CHILD|C|1|NO ACTION|NO ACTION|NOT DEFERRABLE|false";

    private static final String FROM_PAIR_CHILD =
        "EK_OTHER|PAIR|P|EK_OTHER|PAIR_CHILD|Y|1|NO ACTION|NO ACTION|NOT DEFERRABLE|false"
            + " ;; EK_OTHER|PAIR|Q|EK_OTHER|PAIR_CHILD|X|2|NO ACTION|NO ACTION|NOT DEFERRABLE|false";

    @BeforeEach
    public void createTheKeys() {
        engine.execute("CREATE OR REPLACE TABLE ek_parent (a INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE ek_child (b INT REFERENCES ek_parent (a))");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.ek_other");
        engine.execute("""
            CREATE OR REPLACE TABLE test_db.ek_other.far_child (
                c INT REFERENCES test_db.test_schema.ek_parent (a))
            """);
        engine.execute("CREATE OR REPLACE TABLE test_db.ek_other.pair (p INT, q INT, PRIMARY KEY (p, q))");
        engine.execute("""
            CREATE OR REPLACE TABLE test_db.ek_other.pair_child (
                x INT, y INT, CONSTRAINT fk_pair FOREIGN KEY (y, x) REFERENCES test_db.ek_other.pair (p, q))
            """);
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    private void assertKeys(final String expected, final String listing) {
        answer(listing);
        assertAnswer(expected, KEYS);
    }

    @Test
    public void aTableListsTheKeysOtherTablesImportFromIt() {
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD, "SHOW EXPORTED KEYS IN TABLE ek_parent");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD, "SHOW EXPORTED KEYS IN ek_parent");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD,
            "SHOW TERSE EXPORTED KEYS IN TABLE test_db.test_schema.ek_parent");
        assertKeys("", "SHOW EXPORTED KEYS IN TABLE ek_child");
        assertKeys(FROM_CHILD, "SHOW IMPORTED KEYS IN TABLE ek_child");
        assertKeys("", "SHOW EXPORTED KEYS IN TABLE test_db.ek_other.far_child");
    }

    @Test
    public void aContainerListsByTheSchemaOfTheReferencedTable() {
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD, "SHOW EXPORTED KEYS");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD, "SHOW EXPORTED KEYS IN SCHEMA");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD, "SHOW EXPORTED KEYS IN SCHEMA test_db.test_schema");
        assertKeys(FROM_PAIR_CHILD, "SHOW EXPORTED KEYS IN SCHEMA test_db.ek_other");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD + " ;; " + FROM_PAIR_CHILD,
            "SHOW EXPORTED KEYS IN DATABASE test_db");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_CHILD + " ;; " + FROM_PAIR_CHILD, "SHOW EXPORTED KEYS IN DATABASE");
        assertKeys(FROM_FAR_CHILD + " ;; " + FROM_PAIR_CHILD, "SHOW IMPORTED KEYS IN SCHEMA test_db.ek_other");
        assertKeys(FROM_CHILD, "SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema");
    }

    @Test
    public void aKeyOfSeveralColumnsIsOneRowPerColumn() {
        assertKeys(FROM_PAIR_CHILD, "SHOW EXPORTED KEYS IN TABLE test_db.ek_other.pair");
        assertKeys(FROM_PAIR_CHILD, "SHOW IMPORTED KEYS IN TABLE test_db.ek_other.pair_child");
        answer("SHOW EXPORTED KEYS IN TABLE test_db.ek_other.pair");
        assertAnswer("FK_PAIR|1 ;; FK_PAIR|2", """
            SELECT "fk_name", "key_sequence" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY "key_sequence"
            """);
    }
}
