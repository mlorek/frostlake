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
 * A name whose middle part is left empty, {@code db..t}, names the database's PUBLIC schema: in DML, DDL,
 * a FROM, a column reference ({@code db..t.c}), a qualified star, a function or procedure call, a table
 * function, a sequence, DESCRIBE, TRUNCATE, COMMENT and IDENTIFIER() alike. A SHOW scope refuses the
 * spelling, and a schema name still takes at most two parts. Every cell is live-verified.
 */
public class EmptySchemaPartTest extends BaseDatabaseTest {

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

    /** INSERT, UPDATE, DELETE, MERGE, SELECT, CREATE VIEW / TABLE / LIKE, ALTER, DROP, TRUNCATE, COMMENT and DESCRIBE; a missing object is named with PUBLIC; SHOW COLUMNS IN TABLE refuses the spelling. */
    @Test
    public void dmlAndDdlReadTheEmptyPartAsPublic() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P440_DB");
            engine.execute("CREATE OR REPLACE TABLE P440_DB.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P440_DB.PUBLIC.T VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE SCHEMA P440_DB.S1");
            engine.execute("CREATE OR REPLACE DATABASE P440_IDLE");
            engine.execute("DROP DATABASE P440_IDLE");
            engine.execute("INSERT INTO P440_DB..T VALUES (3)");
            engine.execute("UPDATE P440_DB..T SET x = 30 WHERE x = 3");
            engine.execute("DELETE FROM P440_DB..T WHERE x = 30");
            engine.execute("MERGE INTO P440_DB..T t USING (SELECT 5 AS x) s ON t.x = s.x WHEN NOT MATCHED THEN INSERT VALUES (s.x)");
            assertEquals("3",
                rows("SELECT COUNT(*) FROM P440_DB..T"));
            engine.execute("CREATE OR REPLACE VIEW P440_DB..V AS SELECT COUNT(*) AS n FROM P440_DB..T");
            assertEquals("3",
                rows("SELECT n FROM P440_DB.PUBLIC.V"));
            engine.execute("ALTER TABLE P440_DB..T ADD COLUMN y INT");
            engine.execute("CREATE OR REPLACE TABLE P440_DB..T9 (a INT)");
            assertEquals("3",
                rows("SELECT COUNT(*) FROM P440_DB..\"T\""));
            engine.execute("DROP TABLE P440_DB..T9");
            assertRefused("SHOW COLUMNS IN TABLE P440_DB..T",
                "syntax error line 1 at position 30 unexpected '.'.");
            assertRefused("SELECT * FROM P440_DB..T9",
                "Object 'P440_DB.PUBLIC.T9' does not exist or not authorized.");
            assertEquals("1 | 2 | 5",
                rows("SELECT P440_DB..T.x FROM P440_DB..T"));
            engine.execute("CREATE OR REPLACE TABLE P440_DB..T11 LIKE P440_DB..T");
            assertRefused("SELECT * FROM P440_DB...T",
                "syntax error line 1 at position 23 unexpected '.'.");
            assertEquals("X, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null | Y, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE P440_DB..T"));
            assertRefused("SELECT COUNT(*) FROM P440_DB..MISSING",
                "Object 'P440_DB.PUBLIC.MISSING' does not exist or not authorized.");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM P440_DB.PUBLIC.T11"));
            engine.execute("TRUNCATE TABLE P440_DB..T11");
            engine.execute("COMMENT ON TABLE P440_DB..T IS 'c'");
            assertEquals("1, null",
                rows("SELECT * FROM P440_DB..T AS q WHERE q.x = 1"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P440_DB");
        }
    }

    /** A qualified star, function and procedure calls, a sequence, CTAS, INSERT … SELECT, RENAME, IDENTIFIER(), a stage and a sequence named with it; three-part schema names are refused. */
    @Test
    public void everyOtherNamePositionReadsItToo() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P440B_DB");
            engine.execute("CREATE OR REPLACE TABLE P440B_DB.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P440B_DB.PUBLIC.T VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE SCHEMA P440B_DB.S1");
            engine.execute("CREATE OR REPLACE FUNCTION P440B_DB.PUBLIC.F() RETURNS INT AS '7'");
            engine.execute("CREATE OR REPLACE SEQUENCE P440B_DB.PUBLIC.SQ");
            engine.execute("CREATE OR REPLACE PROCEDURE P440B_DB.PUBLIC.P() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE OR REPLACE TABLE P440B_DB.S1.T (x INT)");
            assertEquals("1 | 2",
                rows("SELECT P440B_DB..T.* FROM P440B_DB..T"));
            assertEquals("7",
                rows("SELECT P440B_DB..F()"));
            assertEquals("1",
                rows("CALL P440B_DB..P()"));
            assertEquals("1",
                rows("SELECT P440B_DB..SQ.NEXTVAL"));
            assertRefused("SELECT P440B_DB.PUBLIC..x FROM P440B_DB..T",
                "syntax error line 1 at position 23 unexpected '.'.");
            assertEquals("1",
                rows("SELECT x FROM P440B_DB..T WHERE P440B_DB..T.x = 1"));
            assertRefused("CREATE OR REPLACE SCHEMA P440B_DB..S2",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("DROP SCHEMA P440B_DB..S1",
                "Object does not exist, or operation cannot be performed.");
            assertEquals("1 | 2",
                rows("SELECT * FROM P440B_DB. .T"));
            assertEquals("1 | 2",
                rows("SELECT * FROM P440B_DB . . T"));
            assertRefused("SELECT * FROM ..T",
                "syntax error line 1 at position 14 unexpected '.'.");
            assertRefused("SELECT * FROM P440B_DB.S1..T",
                "syntax error line 1 at position 26 unexpected '.'.");
            assertEquals("1 | 2",
                rows("SELECT P440B_DB..T.x FROM P440B_DB.PUBLIC.T"));
            assertEquals("1 | 2",
                rows("SELECT PUBLIC.T.x FROM P440B_DB..T"));
            engine.execute("CREATE OR REPLACE TABLE P440B_DB..T2 AS SELECT * FROM P440B_DB..T");
            engine.execute("INSERT INTO P440B_DB..T SELECT * FROM P440B_DB..T");
            engine.execute("ALTER TABLE P440B_DB..T RENAME TO P440B_DB..T3");
            engine.execute("ALTER TABLE P440B_DB..T3 RENAME TO P440B_DB..T");
            assertEquals("4",
                rows("SELECT COUNT(*) FROM IDENTIFIER('P440B_DB..T')"));
            engine.execute("CREATE OR REPLACE STAGE P440B_DB..ST");
            engine.execute("CREATE OR REPLACE SEQUENCE P440B_DB..SQ2");
            assertEquals("1 | 2 | 1 | 2",
                rows("SELECT \"P440B_DB\"..T.x FROM \"P440B_DB\"..T"));
            assertEquals("1 | 2 | 1 | 2",
                rows("SELECT T.x FROM P440B_DB..T"));
            assertRefused("SELECT P440B_DB..T..x FROM P440B_DB..T",
                "syntax error line 1 at position 19 unexpected '.'.");
            engine.execute("DROP SEQUENCE P440B_DB..SQ2");
            assertEquals("7 | 7 | 7 | 7",
                rows("SELECT P440B_DB..F() FROM P440B_DB..T"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P440B_DB");
        }
    }

    /** SHOW … IN SCHEMA refuses the spelling at its second dot; a table function, GRANT, SWAP, a stream and an ORDER BY or JOIN reference take it; CREATE and USE SCHEMA refuse a three-part name. */
    @Test
    public void showScopesAndLongerNamesAreRefused() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P440C_DB");
            engine.execute("CREATE OR REPLACE TABLE P440C_DB.PUBLIC.T (x INT)");
            engine.execute("INSERT INTO P440C_DB.PUBLIC.T VALUES (1), (2)");
            engine.execute("CREATE OR REPLACE SCHEMA P440C_DB.S1");
            engine.execute("CREATE OR REPLACE FUNCTION P440C_DB.PUBLIC.TF() RETURNS TABLE (a INT) AS 'SELECT 1'");
            engine.execute("CREATE OR REPLACE STAGE P440C_DB.PUBLIC.ST");
            engine.execute("CREATE OR REPLACE TABLE P440C_DB.PUBLIC.T2 (x INT)");
            assertRefused("SHOW COLUMNS IN P440C_DB.PUBLIC.T.X",
                "Object does not exist, or operation cannot be performed.");
            assertEquals("T, PUBLIC, X, {\"type\":\"FIXED\",\"precision\":38,\"scale\":0,\"nullable\":true}, true, , COLUMN, , , P440C_DB, , null, null",
                rows("SHOW COLUMNS IN P440C_DB.PUBLIC.T"));
            assertRefused("SHOW COLUMNS IN TABLE P440C_DB.PUBLIC.T.X",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("CREATE OR REPLACE SCHEMA P440C_DB.PUBLIC.S3",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("USE SCHEMA P440C_DB..S1",
                "Object does not exist, or operation cannot be performed.");
            assertEquals("1",
                rows("SELECT * FROM TABLE(P440C_DB..TF())"));
            engine.execute("GRANT SELECT ON TABLE P440C_DB..T TO ROLE PUBLIC");
            engine.execute("ALTER TABLE P440C_DB..T SWAP WITH P440C_DB..T2");
            engine.execute("CREATE OR REPLACE STREAM P440C_DB..STR ON TABLE P440C_DB..T");
            assertRefused("CREATE OR REPLACE TABLE P440C_DB.PUBLIC.T5.C (x INT)",
                "Object does not exist, or operation cannot be performed.");
            assertEquals("",
                rows("SELECT x FROM P440C_DB..T ORDER BY P440C_DB..T.x"));
            engine.execute("INSERT INTO P440C_DB..T (x) VALUES (5)");
            assertEquals("X, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE VIEW P440C_DB..T"));
            assertRefused("SHOW TABLES IN SCHEMA P440C_DB..S1",
                "syntax error line 1 at position 31 unexpected '.'.");
            assertRefused("SELECT GET_DDL('TABLE', 'P440C_DB..T')",
                "Schema 'P440C_DB.\"\"' does not exist or not authorized.");
            assertRefused("DROP SCHEMA P440C_DB.PUBLIC.S1",
                "Object does not exist, or operation cannot be performed.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P440C_DB");
        }
    }

    /** A three-part schema name is refused under IF EXISTS and IF NOT EXISTS too, with or without an empty part, and nothing is created or dropped. */
    @Test
    public void aSchemaNameKeepsTwoParts() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P440E_DB");
            engine.execute("CREATE OR REPLACE SCHEMA P440E_DB.S1");
            engine.execute("CREATE OR REPLACE TABLE P440E_DB.PUBLIC.T (x INT)");
            assertRefused("DROP SCHEMA IF EXISTS P440E_DB.PUBLIC.S1",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("DROP SCHEMA IF EXISTS P440E_DB..S1",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("CREATE SCHEMA IF NOT EXISTS P440E_DB.PUBLIC.S3",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("CREATE SCHEMA IF NOT EXISTS P440E_DB..S3",
                "Object does not exist, or operation cannot be performed.");
            engine.execute("SHOW SCHEMAS IN DATABASE P440E_DB");
            assertEquals("INFORMATION_SCHEMA | PUBLIC | S1",
                rows("SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1"));
            assertRefused("DROP SCHEMA P440E_DB.S1.X",
                "Object does not exist, or operation cannot be performed.");
            assertRefused("CREATE SCHEMA P440E_DB.PUBLIC.S4 CLONE P440E_DB.S1",
                "Object does not exist, or operation cannot be performed.");
            engine.execute("USE SCHEMA P440E_DB.PUBLIC");
            assertEquals("0",
                rows("SELECT COUNT(*) FROM T"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P440E_DB");
        }
    }
}
