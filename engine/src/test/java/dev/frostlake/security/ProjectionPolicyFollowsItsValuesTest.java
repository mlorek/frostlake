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
 * A projection policy that disallows projection refuses the query that finally projects the column's values, however
 * many relations they passed through on the way: a derived table, an alias, a CTE (recursive too), a set operation, a
 * lateral or table function over the column, a subquery in the select list, a function handed every column, and the
 * statements that write the values out (CTAS, INSERT ... SELECT, COPY INTO a location). The check read only the
 * outermost select's own sources, so any of those returned the protected value. Using the column outside the
 * projection stays allowed, as before: WHERE, JOIN, GROUP BY, ORDER BY, a window's ordering and COUNT (live-verified
 * for the outermost select).
 */
public class ProjectionPolicyFollowsItsValuesTest {

    private static final String RESTRICTED = "The following columns are restricted by a Projection Policy.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE pv_db");
        engine.execute("USE SCHEMA pv_db.PUBLIC");
        engine.execute("CREATE PROJECTION POLICY deny AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE TABLE pt (id INT, p VARCHAR WITH PROJECTION POLICY deny, v VARIANT)");
        engine.execute("INSERT INTO pt SELECT 1, 'hidden', PARSE_JSON('[1, 2]')");
        engine.execute("CREATE TABLE other (id INT)");
        engine.execute("INSERT INTO other VALUES (1)");
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
    public void derivedTablesAndTheirAliasesAreRefused() {
        assertRestricted("SELECT * FROM (SELECT p FROM pt)");
        assertRestricted("SELECT x FROM (SELECT p AS x FROM pt)");
        assertRestricted("SELECT d.x FROM (SELECT p FROM pt) d (x)");
        assertRestricted("SELECT x FROM (SELECT UPPER(p) || '!' AS x FROM pt)");
        assertRestricted("SELECT * FROM (SELECT * FROM (SELECT id, p FROM pt))");
        assertRestricted("SELECT b FROM pt AS t (a, b, c)");
        assertTrue(refusal("SELECT x FROM (SELECT p AS x FROM pt)").endsWith("\n X"));
    }

    @Test
    public void commonTableExpressionsRecursiveOnesIncludedAreRefused() {
        assertRestricted("WITH c AS (SELECT p FROM pt) SELECT p FROM c");
        assertRestricted("WITH c (q) AS (SELECT p FROM pt) SELECT q FROM c");
        assertRestricted("WITH RECURSIVE r (q, n) AS (SELECT p, 1 FROM pt UNION ALL SELECT q, n + 1 FROM r WHERE n < 2)"
            + " SELECT q FROM r");
    }

    @Test
    public void setOperationsAreRefusedWhicheverArmCarriesTheColumn() {
        assertRestricted("SELECT * FROM (SELECT p FROM pt UNION ALL SELECT 'x')");
        assertRestricted("SELECT * FROM (SELECT 'x' UNION SELECT p FROM pt)");
        assertEquals("[1]; [1]", rows("SELECT * FROM (SELECT id FROM pt UNION ALL SELECT id FROM other) ORDER BY 1"));
    }

    @Test
    public void subqueriesInTheSelectListAreRefused() {
        // Not measured live: an aggregate over the column inside a scalar subquery hands its value out.
        assertRestricted("SELECT (SELECT MAX(p) FROM pt) AS m");
        assertRestricted("SELECT (SELECT MAX(p) FROM pt WHERE pt.id = o.id) FROM other o");
        assertRestricted("SELECT 'hidden' IN (SELECT p FROM pt)");
        assertRestricted("SELECT EXISTS (SELECT p FROM pt)");
        assertRestricted("SELECT * FROM (SELECT (SELECT MAX(p) FROM pt) AS m)");
        assertEquals("[true]", rows("SELECT EXISTS (SELECT 1 FROM pt WHERE p = 'hidden')"));
        assertEquals("[1]", rows("SELECT (SELECT COUNT(*) FROM pt WHERE p = 'hidden')"));
    }

    @Test
    public void lateralAndTableFunctionsOverTheColumnAreRefused() {
        assertRestricted("SELECT f.value FROM pt, LATERAL FLATTEN(input => PARSE_JSON('[\"' || pt.p || '\"]')) f");
        assertRestricted("SELECT s.value FROM pt, TABLE(SPLIT_TO_TABLE(pt.p, 'd')) s");
        assertRestricted("SELECT * FROM TABLE(SPLIT_TO_TABLE((SELECT MAX(p) FROM pt), 'd'))");
        assertEquals("[1, 1]; [1, 2]", rows("SELECT pt.id, f.value FROM pt, LATERAL FLATTEN(input => pt.v) f"
            + " WHERE pt.p = 'hidden' ORDER BY 2"));
        assertEquals("[1]", rows("SELECT pt.id FROM pt, LATERAL FLATTEN(input => PARSE_JSON('[\"' || pt.p || '\"]')) f"));
    }

    @Test
    public void aFunctionHandedEveryColumnIsRefusedAndCountIsNot() {
        assertRestricted("SELECT OBJECT_CONSTRUCT(*) FROM pt");
        assertRestricted("SELECT ARRAY_CONSTRUCT(*) FROM pt");
        assertRestricted("SELECT HASH(*) FROM pt");
        assertRestricted("SELECT {*} FROM pt");
        assertRestricted("SELECT OBJECT_CONSTRUCT(*) FROM (SELECT p FROM pt)");
        assertEquals("[1]", rows("SELECT COUNT(*) FROM pt"));
    }

    @Test
    public void anAggregateThatHandsOutTheValueIsRefusedAndCountingIsNot() {
        assertRestricted("SELECT MAX(p) FROM pt");
        assertRestricted("SELECT p, COUNT(*) FROM pt GROUP BY p");
        assertRestricted("SELECT id, FIRST_VALUE(p) OVER (ORDER BY id) FROM pt");
        assertRestricted("SELECT m FROM (SELECT MAX(p) AS m FROM pt)");
        assertEquals("[1]", rows("SELECT COUNT(p) FROM pt"));
        assertEquals("[1]", rows("SELECT COUNT(DISTINCT p) FROM pt"));
        assertEquals("[1]", rows("SELECT COUNT(*) FROM pt GROUP BY p"));
        assertEquals("[1, 1]", rows("SELECT id, ROW_NUMBER() OVER (ORDER BY p) FROM pt"));
        assertEquals("[1]", rows("SELECT c FROM (SELECT COUNT(p) AS c FROM pt)"));
    }

    @Test
    public void statementsThatWriteTheValuesOutAreRefused() {
        engine.execute("CREATE TABLE sink (x VARCHAR)");
        engine.execute("CREATE STAGE st");
        assertRestricted("CREATE TABLE copied AS SELECT x FROM (SELECT p AS x FROM pt)");
        assertRestricted("INSERT INTO sink SELECT p FROM pt");
        assertRestricted("INSERT INTO sink SELECT x FROM (SELECT p AS x FROM pt)");
        assertRestricted("COPY INTO @st/whole FROM pt FILE_FORMAT = (TYPE = CSV)");
        assertRestricted("COPY INTO @st/query FROM (SELECT * FROM (SELECT p FROM pt)) FILE_FORMAT = (TYPE = CSV)");
        assertRestricted("CREATE VIEW v AS SELECT * FROM (SELECT p FROM pt)");
        assertEquals("", rows("SELECT * FROM sink"));
        engine.execute("INSERT INTO sink SELECT id::VARCHAR FROM pt WHERE p = 'hidden'");
        assertEquals("[1]", rows("SELECT * FROM sink"));
    }

    @Test
    public void usingTheColumnOutsideTheProjectionStaysAllowed() {
        assertEquals("[1]", rows("SELECT COUNT(*) FROM pt WHERE p = 'hidden'"));
        assertEquals("[1]", rows("SELECT a.id FROM pt a JOIN pt b ON a.p = b.p"));
        assertEquals("[1]", rows("SELECT id FROM pt ORDER BY p"));
        assertEquals("[1]", rows("SELECT id FROM (SELECT id, p FROM pt)"));
        assertEquals("[1]", rows("SELECT COUNT(*) FROM (SELECT p FROM pt)"));
        assertEquals("[1]", rows("SELECT x.id FROM (SELECT * FROM pt) x WHERE x.p = 'hidden'"));
        assertEquals("[1]", rows("SELECT a.id FROM pt a JOIN (SELECT id, p FROM pt) b ON a.id = b.id"
            + " WHERE b.p = 'hidden'"));
        assertEquals("[1, [1,2]]", rows("SELECT * EXCLUDE p FROM (SELECT * FROM pt)"));
        assertEquals("[1]", rows("WITH c AS (SELECT p FROM pt) SELECT 1 WHERE EXISTS (SELECT 1 FROM c)"));
    }

    @Test
    public void aPolicyThatAllowsTheReadingRoleLetsTheValuesThrough() {
        engine.execute("CREATE ROLE analyst");
        engine.execute("GRANT ROLE analyst TO ROLE SYSADMIN");
        engine.execute("GRANT USAGE ON DATABASE pv_db TO ROLE analyst");
        engine.execute("GRANT USAGE ON SCHEMA pv_db.PUBLIC TO ROLE analyst");
        engine.execute("CREATE PROJECTION POLICY by_role AS () RETURNS PROJECTION_CONSTRAINT -> CASE WHEN"
            + " CURRENT_ROLE() = 'SYSADMIN' THEN PROJECTION_CONSTRAINT(ALLOW => true)"
            + " ELSE PROJECTION_CONSTRAINT(ALLOW => false) END");
        engine.execute("CREATE TABLE rt (r VARCHAR WITH PROJECTION POLICY by_role)");
        engine.execute("GRANT SELECT ON TABLE rt TO ROLE analyst");
        engine.execute("INSERT INTO rt VALUES ('seen')");
        assertEquals("[seen]", rows("SELECT * FROM (SELECT r FROM rt)"));
        engine.execute("USE ROLE analyst");
        assertRestricted("SELECT * FROM (SELECT r FROM rt)");
        assertRestricted("WITH c AS (SELECT r FROM rt) SELECT r FROM c");
    }

    @Test
    public void aStreamCarriesItsTablesRestriction() {
        engine.execute("CREATE STREAM ps ON TABLE pt");
        engine.execute("INSERT INTO pt SELECT 2, 'h2', NULL");
        assertRestricted("SELECT p FROM ps");
        assertRestricted("SELECT * FROM (SELECT p FROM ps)");
        assertEquals("[2]", rows("SELECT id FROM ps"));
    }
}
