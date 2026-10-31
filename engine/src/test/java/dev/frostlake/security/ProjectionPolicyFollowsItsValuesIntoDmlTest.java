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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A DML statement that writes a projection-restricted value into a table is refused as a projection of it is: an
 * UPDATE's SET list (from a subquery, a FROM source or the table itself), a MERGE's UPDATE SET and INSERT VALUES, an
 * INSERT's VALUES and a multi-table INSERT's values, and the same statements run by a procedure or EXECUTE
 * IMMEDIATE. They are the INSERT ... SELECT leak by another statement. The column stays usable in the statement's
 * WHERE and ON, and under a counting aggregate. Not measured live, but for a VALUES item that is a whole query of
 * the table: it is refused as VALUES refuses a query the plan does not fold, the table's one row folded into the
 * echo (live-verified), and a MAX of the one row, which the plan folds, is refused as a projection here, where the
 * account writes the value into the table.
 */
public class ProjectionPolicyFollowsItsValuesIntoDmlTest {

    private static final String RESTRICTED = "The following columns are restricted by a Projection Policy.";

    private static final String FOLDED_ROW = "Invalid expression [(SELECT 'hidden' AS \"P\" FROM TABLE (GENERATOR("
        + "ROWCOUNT => 1, rowCount => 1)))] in VALUES clause";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE pd_db");
        engine.execute("USE SCHEMA pd_db.PUBLIC");
        engine.execute("CREATE PROJECTION POLICY deny AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE TABLE pt (id INT, p VARCHAR WITH PROJECTION POLICY deny, x VARCHAR)");
        engine.execute("INSERT INTO pt VALUES (1, 'hidden', 'x1')");
        engine.execute("CREATE TABLE t (id INT, x VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private void assertRestricted(final String sql) {
        final String refusal = refusal(sql);
        assertTrue(refusal.contains(RESTRICTED), sql + " -> " + refusal);
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    @Test
    public void anUpdateThatWritesTheValueIsRefused() {
        assertRestricted("UPDATE t SET x = (SELECT MAX(p) FROM pt)");
        assertRestricted("UPDATE t SET x = pt.p FROM pt WHERE t.id = pt.id");
        assertRestricted("UPDATE t SET x = q.p FROM (SELECT id, p FROM pt) q WHERE t.id = q.id");
        assertRestricted("UPDATE t SET x = (SELECT MAX(p) FROM (SELECT p FROM pt))");
        assertRestricted("UPDATE pt SET x = p");
        assertTrue(refusal("UPDATE t SET x = pt.p FROM pt WHERE t.id = pt.id").endsWith("\n PT.P"));
        assertEquals("[1, a]; [2, b]", rows("SELECT * FROM t ORDER BY id"));
    }

    @Test
    public void aMergeThatWritesTheValueIsRefused() {
        assertRestricted("MERGE INTO t USING pt ON t.id = pt.id WHEN MATCHED THEN UPDATE SET x = pt.p");
        assertRestricted("MERGE INTO t USING pt ON t.id = pt.id + 10"
            + " WHEN NOT MATCHED THEN INSERT (id, x) VALUES (pt.id, pt.p)");
        assertRestricted("MERGE INTO t USING pt s ON t.id = s.id WHEN MATCHED THEN UPDATE SET x = UPPER(s.p)");
        assertEquals("[1, a]; [2, b]", rows("SELECT * FROM t ORDER BY id"));
    }

    @Test
    public void insertedValuesThatHandTheValueOutAreRefused() {
        assertEquals("SQL compilation error:\n" + FOLDED_ROW,
            refusal("INSERT INTO t VALUES (3, (SELECT p FROM pt LIMIT 1))"));
        assertRestricted("INSERT INTO t (x) VALUES ((SELECT MAX(p) FROM pt))");
        assertRestricted("INSERT ALL INTO t (id, x) VALUES (id, p) SELECT id, p FROM pt");
        assertRestricted("INSERT ALL INTO t (id, x) VALUES (id, (SELECT MAX(p) FROM pt)) SELECT id FROM pt");
        assertEquals("[1, a]; [2, b]", rows("SELECT * FROM t ORDER BY id"));
    }

    @Test
    public void aProcedureOrABlockRunningTheStatementIsRefusedToo() {
        assertRestricted("EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET x = (SELECT MAX(p) FROM pt); RETURN 'done'; END; $$");
        engine.execute("CREATE PROCEDURE leak() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN INSERT INTO t VALUES (5, (SELECT p FROM pt LIMIT 1)); RETURN 'done'; END $$");
        assertTrue(refusal("CALL leak()").contains(FOLDED_ROW));
        engine.execute("CREATE OR REPLACE PROCEDURE leak() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN INSERT INTO t (x) VALUES ((SELECT MAX(p) FROM pt)); RETURN 'done'; END $$");
        assertRestricted("CALL leak()");
        assertEquals("[1, a]; [2, b]", rows("SELECT * FROM t ORDER BY id"));
    }

    @Test
    public void theColumnStaysUsableInTheStatementsConditionsAndUnderACount() {
        engine.execute("UPDATE t SET x = 'w' FROM pt WHERE t.id = pt.id AND pt.p = 'hidden'");
        engine.execute("UPDATE t SET x = 'c' || (SELECT COUNT(p) FROM pt) WHERE id = 2");
        engine.execute("MERGE INTO t USING pt ON t.id = pt.id AND pt.p = 'hidden' WHEN MATCHED THEN UPDATE SET x = 'm'");
        engine.execute("INSERT INTO t VALUES (3, (SELECT COUNT(*) FROM pt WHERE p = 'hidden')::VARCHAR)");
        engine.execute("INSERT ALL INTO t (id, x) VALUES (id, x) SELECT id + 3 AS id, x FROM pt WHERE p = 'hidden'");
        engine.execute("UPDATE pt SET x = 'seen' WHERE p = 'hidden'");
        assertEquals("[1, m]; [2, c1]; [3, 1]; [4, x1]", rows("SELECT * FROM t ORDER BY id"));
        assertEquals("[seen]", rows("SELECT x FROM pt"));
    }
}
