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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A foreign key stays on the table it was declared against: renamed, swapped, or moved with its schema, the parent
 * is the one SHOW IMPORTED KEYS and GET_DDL name, under its new name, and the one DROP ... RESTRICT keeps, while a
 * new table taking the old name is no parent (live-verified).
 */
public class ForeignKeyParentRenameTest extends BaseDatabaseTest {

    private static final String HELD = "SQL compilation error:|Cannot drop the table because of dependencies";
    private static final String DONE = "Statement executed successfully.";
    private static final String KEYS = " ->> SELECT \"pk_database_name\", \"pk_schema_name\", \"pk_table_name\", "
        + "\"pk_column_name\", \"fk_schema_name\", \"fk_table_name\", \"fk_column_name\" FROM $1";

    /** A statement's answer on one line: rows as "a, b | c, d", or the refusal with its line breaks as "|". */
    private String answer(final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString().replace('\n', '|');
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aRenamedParentKeepsItsKeys() {
        engine.execute("CREATE TABLE parent (k NUMBER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (k NUMBER REFERENCES parent (k))");
        engine.execute("CREATE TABLE child2 (k NUMBER, FOREIGN KEY (k) REFERENCES parent (k))");
        assertAnswers(new String[][] {
            {"ALTER TABLE parent RENAME TO parent2", DONE},
            {"SHOW IMPORTED KEYS IN TABLE child" + KEYS, "TEST_DB, TEST_SCHEMA, PARENT2, K, TEST_SCHEMA, CHILD, K"},
            {"SHOW IMPORTED KEYS IN TABLE child2" + KEYS, "TEST_DB, TEST_SCHEMA, PARENT2, K, TEST_SCHEMA, CHILD2, K"},
            {"SELECT GET_DDL('TABLE', 'child2')",
                "create or replace TABLE CHILD2 (|\tK NUMBER(38,0),|\tforeign key (K) references "
                    + "TEST_DB.TEST_SCHEMA.PARENT2(K)|);"},
            {"SELECT constraint_name IS NOT NULL, unique_constraint_name IS NOT NULL FROM "
                + "information_schema.referential_constraints WHERE constraint_schema = 'TEST_SCHEMA'",
                "true, true | true, true"},
            {"DROP TABLE parent2 RESTRICT", HELD},
            {"CREATE TABLE parent (z VARCHAR, k NUMBER UNIQUE)", "Table PARENT successfully created."},
            {"SHOW IMPORTED KEYS IN TABLE child" + KEYS, "TEST_DB, TEST_SCHEMA, PARENT2, K, TEST_SCHEMA, CHILD, K"},
            {"DROP TABLE parent RESTRICT", "PARENT successfully dropped."},
        });
    }

    @Test
    public void aSwappedParentKeepsItsKeys() {
        engine.execute("CREATE TABLE pa (k NUMBER PRIMARY KEY)");
        engine.execute("CREATE TABLE pb (k NUMBER PRIMARY KEY, z INT)");
        engine.execute("CREATE TABLE cc (k NUMBER REFERENCES pa (k))");
        assertAnswers(new String[][] {
            {"ALTER TABLE pa SWAP WITH pb", DONE},
            {"SHOW IMPORTED KEYS IN TABLE cc" + KEYS, "TEST_DB, TEST_SCHEMA, PB, K, TEST_SCHEMA, CC, K"},
            {"SELECT GET_DDL('TABLE', 'cc')", "create or replace TABLE CC (|\tK NUMBER(38,0),|\tforeign key (K) "
                + "references TEST_DB.TEST_SCHEMA.PB(K)|);"},
            {"DROP TABLE pb RESTRICT", HELD},
            {"DROP TABLE pa RESTRICT", "PA successfully dropped."},
        });
    }

    @Test
    public void aParentMovedWithItsSchemaKeepsItsKeys() {
        try {
            engine.execute("CREATE SCHEMA test_db.fk_sa");
            engine.execute("CREATE TABLE test_db.fk_sa.parent (k NUMBER PRIMARY KEY)");
            engine.execute("CREATE TABLE test_db.fk_sa.child (k NUMBER REFERENCES test_db.fk_sa.parent (k))");
            engine.execute("CREATE SCHEMA test_db.fk_sb");
            engine.execute("CREATE TABLE test_db.fk_sb.parent (k NUMBER PRIMARY KEY)");
            engine.execute("USE SCHEMA test_db.test_schema");
            assertAnswers(new String[][] {
                {"ALTER SCHEMA test_db.fk_sa SWAP WITH test_db.fk_sb", DONE},
                {"SHOW IMPORTED KEYS IN TABLE test_db.fk_sb.child" + KEYS,
                    "TEST_DB, FK_SB, PARENT, K, FK_SB, CHILD, K"},
                {"SELECT GET_DDL('TABLE', 'test_db.fk_sb.child')", "create or replace TABLE CHILD (|\tK NUMBER(38,0),|"
                    + "\tforeign key (K) references TEST_DB.FK_SB.PARENT(K)|);"},
                {"DROP TABLE test_db.fk_sb.parent RESTRICT", HELD},
                {"DROP TABLE test_db.fk_sa.parent RESTRICT", "PARENT successfully dropped."},
                {"ALTER SCHEMA test_db.fk_sb RENAME TO test_db.fk_sc", DONE},
                {"SHOW IMPORTED KEYS IN TABLE test_db.fk_sc.child" + KEYS,
                    "TEST_DB, FK_SC, PARENT, K, FK_SC, CHILD, K"},
                {"CREATE TABLE mp (k NUMBER PRIMARY KEY)", "Table MP successfully created."},
                {"CREATE TABLE mc (k NUMBER, FOREIGN KEY (k) REFERENCES mp (k))", "Table MC successfully created."},
                {"ALTER TABLE mp RENAME TO test_db.fk_sc.mp_moved", DONE},
                {"SHOW IMPORTED KEYS IN TABLE mc" + KEYS, "TEST_DB, FK_SC, MP_MOVED, K, TEST_SCHEMA, MC, K"},
            });
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("DROP TABLE IF EXISTS mc");
            engine.execute("DROP SCHEMA IF EXISTS test_db.fk_sa CASCADE");
            engine.execute("DROP SCHEMA IF EXISTS test_db.fk_sb CASCADE");
            engine.execute("DROP SCHEMA IF EXISTS test_db.fk_sc CASCADE");
        }
    }
}
