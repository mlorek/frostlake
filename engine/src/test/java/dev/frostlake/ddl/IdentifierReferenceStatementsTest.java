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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE VIEW, CREATE MATERIALIZED VIEW, TRUNCATE, ALTER TABLE, ALTER VIEW and a rename's target all take an
 * {@code IDENTIFIER('name')} reference, as DROP and DESCRIBE do. A reference the lexer finds broken is read
 * as each statement reads it: CREATE VIEW and TRUNCATE take the word itself for the name, while ALTER and the
 * SHOW scopes read the reference to its end. Frostlake refused every one as a syntax error (live-verified).
 */
public class IdentifierReferenceStatementsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
    }

    /** Each row's first cell, rows joined by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "").append(result.getRows().get(r).getValue(0));
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void everyOneOfTheseStatementsTakesAReference() {
        engine.execute("CREATE OR REPLACE VIEW IDENTIFIER('v1') AS SELECT 1 AS a");
        assertEquals("1", rows("SELECT a FROM v1"));
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW IDENTIFIER('mv1') AS SELECT x FROM t1");
        assertEquals("1", rows("SELECT x FROM mv1"));
        engine.execute("ALTER TABLE IDENTIFIER('t1') ADD COLUMN b INT");
        assertEquals("B | X", rows("SHOW COLUMNS IN TABLE IDENTIFIER('t1') ->> SELECT \"column_name\" FROM $1 ORDER BY 1"));
        engine.execute("ALTER VIEW IDENTIFIER('v1') SET COMMENT = 'c'");
        engine.execute("ALTER TABLE IDENTIFIER('t1') SET COMMENT = 'd'");
        engine.execute("TRUNCATE TABLE IDENTIFIER('t1')");
        assertEquals("0", rows("SELECT COUNT(*) FROM t1"));
        engine.execute("TRUNCATE IDENTIFIER('t1')");
        engine.execute("TRUNCATE TABLE IF EXISTS IDENTIFIER('t1')");
        // A rename reads its target as a reference too, and the whole path may be written in one.
        engine.execute("ALTER TABLE IDENTIFIER('t1') RENAME TO IDENTIFIER('t2')");
        assertEquals("0", rows("SELECT COUNT(*) FROM t2"));
        engine.execute("ALTER TABLE IDENTIFIER('t2') RENAME COLUMN x TO y");
        assertEquals("B | Y", rows("DESCRIBE TABLE IDENTIFIER('t2') ->> SELECT \"name\" FROM $1 ORDER BY 1"));
        engine.execute("CREATE OR REPLACE VIEW IDENTIFIER('test_db.test_schema.v2') AS SELECT 2 AS a");
        assertEquals("2", rows("SELECT a FROM v2"));
        assertEquals("T2", rows("SHOW TABLES IN SCHEMA IDENTIFIER('test_schema') ->> SELECT \"name\" FROM $1 ORDER BY 1"));
    }

    @Test
    public void aBrokenReferenceIsReadAsTheStatementReadsIt() {
        // CREATE VIEW and TRUNCATE take the word itself for the name, so what follows is unexpected.
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 34 unexpected ''v''.",
            refusal("CREATE OR REPLACE VIEW IDENTIFIER('v' || '1') AS SELECT 1 AS a"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 25 unexpected '('.",
            refusal("TRUNCATE TABLE IDENTIFIER('t' || '1')"));
        // ALTER and a SHOW scope read the reference to its end, and name the operator inside it.
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected '||'.",
            refusal("ALTER TABLE IDENTIFIER('t' || '1') ADD COLUMN c INT"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 37 unexpected '||'.",
            refusal("SHOW COLUMNS IN TABLE IDENTIFIER('t' || '1')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected '||'.",
            refusal("SHOW TABLES IN SCHEMA IDENTIFIER('PUB' || 'LIC')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected '||'.",
            refusal("SHOW VIEWS IN SCHEMA IDENTIFIER('PUB' || 'LIC')"));
        // A rename's target is read whole or not at all, and the statement is refused at its RENAME.
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 15 unexpected 'RENAME'.",
            refusal("ALTER TABLE t1 RENAME TO IDENTIFIER('t' || '2')"));
    }
}
