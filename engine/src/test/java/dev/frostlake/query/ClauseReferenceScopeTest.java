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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reference that resolves to nothing is refused in EVERY clause, at the reference's own offset, and
 * refused at COMPILE time — an empty table gets the same answer as a full one. Frostlake asked the
 * question per clause and per shape, so three of the four clauses let a qualified reference through:
 *
 * <pre>
 *   WHERE test_schema.t.nosuchcol = 1        refused, but with no position
 *   ORDER BY test_schema.t.nosuchcol         refused only once ROWS existed; empty table accepted
 *   GROUP BY test_schema.t.nosuchcol         ACCEPTED — the key evaluated to NULL, so one group
 *   HAVING MAX(test_schema.t.nosuchcol) > 0  ACCEPTED — the predicate filtered every group out
 * </pre>
 *
 * <p>The last two are the worse kind of divergence: not a missing message but a wrong ANSWER. The
 * clause walk skipped multi-part qualifiers outright, and HAVING was never walked at all.
 */
public class ClauseReferenceScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cr (a INT, b INT)");
        engine.execute("INSERT INTO cr VALUES (1, 2), (3, 4)");
        engine.execute("CREATE OR REPLACE TABLE cre (a INT, b INT)");
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private int value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return ((Number) rs.getValue(0)).intValue();
    }

    /** A bare unresolvable name is refused in all four clauses, each at its own offset. */
    @Test
    public void aBareNameIsRefusedInEveryClause() {
        assertTrue(refusal("SELECT a FROM cr WHERE nosuchcol = 1")
            .contains("error line 1 at position 23 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM cr WHERE nosuchcol = 1"));
        assertTrue(refusal("SELECT a FROM cr ORDER BY nosuchcol")
            .contains("error line 1 at position 26 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM cr ORDER BY nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cr GROUP BY nosuchcol")
            .contains("error line 1 at position 33 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cr GROUP BY nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cr HAVING MAX(nosuchcol) > 0")
            .contains("error line 1 at position 35 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cr HAVING MAX(nosuchcol) > 0"));
    }

    /** And so is a three-part qualified one, named in full however many parts it carries. */
    @Test
    public void aQualifiedReferenceIsRefusedInEveryClause() {
        assertTrue(refusal("SELECT a FROM cr WHERE test_schema.cr.nosuchcol = 1")
            .contains("error line 1 at position 23 invalid identifier 'TEST_SCHEMA.CR.NOSUCHCOL'"),
            refusal("SELECT a FROM cr WHERE test_schema.cr.nosuchcol = 1"));
        assertTrue(refusal("SELECT a FROM cr ORDER BY test_schema.cr.nosuchcol")
            .contains("error line 1 at position 26 invalid identifier 'TEST_SCHEMA.CR.NOSUCHCOL'"),
            refusal("SELECT a FROM cr ORDER BY test_schema.cr.nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cr GROUP BY test_schema.cr.nosuchcol")
            .contains("error line 1 at position 33 invalid identifier 'TEST_SCHEMA.CR.NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cr GROUP BY test_schema.cr.nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cr HAVING MAX(test_schema.cr.nosuchcol) > 0")
            .contains("error line 1 at position 35 invalid identifier 'TEST_SCHEMA.CR.NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cr HAVING MAX(test_schema.cr.nosuchcol) > 0"));
    }

    /** A two-part qualifier is the same question of its last part. */
    @Test
    public void aTwoPartQualifierIsRefusedTheSameWay() {
        assertTrue(refusal("SELECT a FROM cr ORDER BY cr.nosuchcol")
            .contains("error line 1 at position 26 invalid identifier 'CR.NOSUCHCOL'"),
            refusal("SELECT a FROM cr ORDER BY cr.nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cr GROUP BY cr.nosuchcol")
            .contains("error line 1 at position 33 invalid identifier 'CR.NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cr GROUP BY cr.nosuchcol"));
    }

    /**
     * The refusal is a COMPILE-time one, so an EMPTY table answers it identically. This is the half a
     * row-time check can never reproduce: with no rows there is nothing to evaluate, and the query
     * used to succeed.
     */
    @Test
    public void anEmptyTableIsRefusedIdentically() {
        assertTrue(refusal("SELECT a FROM cre WHERE nosuchcol = 1")
            .contains("error line 1 at position 24 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM cre WHERE nosuchcol = 1"));
        assertTrue(refusal("SELECT a FROM cre ORDER BY test_schema.cre.nosuchcol")
            .contains("error line 1 at position 27 invalid identifier 'TEST_SCHEMA.CRE.NOSUCHCOL'"),
            refusal("SELECT a FROM cre ORDER BY test_schema.cre.nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cre GROUP BY nosuchcol")
            .contains("error line 1 at position 34 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cre GROUP BY nosuchcol"));
        assertTrue(refusal("SELECT COUNT(*) FROM cre HAVING MAX(nosuchcol) > 0")
            .contains("error line 1 at position 36 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT COUNT(*) FROM cre HAVING MAX(nosuchcol) > 0"));
    }

    /** A qualifier that names the relation and carries the column reads it, in every clause. */
    @Test
    public void aResolvingReferenceStillReadsTheColumn() {
        assertEquals(1, value("SELECT a FROM cr WHERE test_schema.cr.b = 2"));
        assertEquals(1, value("SELECT a FROM cr ORDER BY test_schema.cr.b"));
        assertEquals(1, value("SELECT COUNT(*) FROM cr GROUP BY test_schema.cr.b ORDER BY 1"));
        assertEquals(2, value("SELECT COUNT(*) FROM cr HAVING MAX(test_schema.cr.b) > 0"));
    }

    /** ORDER BY's own two exemptions stand: a SELECT alias and an ordinal name no column at all. */
    @Test
    public void anAliasAndAnOrdinalStayLegal() {
        assertEquals(1, value("SELECT a AS x FROM cr ORDER BY x"));
        assertEquals(1, value("SELECT a FROM cr ORDER BY 1"));
        assertEquals(2, value("SELECT COUNT(*) AS n FROM cr HAVING n > 1"));
    }
}
