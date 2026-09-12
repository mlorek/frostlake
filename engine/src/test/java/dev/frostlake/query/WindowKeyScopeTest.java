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
 * What an {@code OVER} clause references is checked like anything else. Frostlake could not see inside
 * one at all — a window call parses to a single expression node carrying its call TEXT, so the keys
 * were invisible to the walk and a name that resolved to nothing produced a perfectly ordinary result:
 *
 * <pre>
 *   … OVER (ORDER BY nosuchcol)                    ACCEPTED, one row      live: position 35
 *   … OVER (PARTITION BY q.nosuchcol ORDER BY b)   ACCEPTED, one row      live: position 39
 *   QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) ACCEPTED, one row      live: position 52
 * </pre>
 *
 * <p>The keys come from the PARSE TREE, which is the only place they survive. Aliases stay legal
 * inside a window — {@code SELECT a AS x, ROW_NUMBER() OVER (ORDER BY x)} reads on both engines.
 */
public class WindowKeyScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE q (a INT, b INT)");
        engine.execute("INSERT INTO q VALUES (1, 2), (3, 4)");
        engine.execute("CREATE OR REPLACE TABLE qe (a INT, b INT)");
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

    /** A window ORDER BY key that resolves to nothing is refused at its own offset. */
    @Test
    public void aWindowOrderKeyIsRefusedAtItsOffset() {
        assertTrue(refusal("SELECT ROW_NUMBER() OVER (ORDER BY nosuchcol) FROM q")
            .contains("error line 1 at position 35 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT ROW_NUMBER() OVER (ORDER BY nosuchcol) FROM q"));
    }

    /** So is a PARTITION BY key, at one, two and three parts, named in full. */
    @Test
    public void aPartitionKeyIsRefusedAtEveryQualifierLength() {
        assertTrue(refusal("SELECT ROW_NUMBER() OVER (PARTITION BY nosuchcol) FROM q")
            .contains("error line 1 at position 39 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT ROW_NUMBER() OVER (PARTITION BY nosuchcol) FROM q"));
        assertTrue(refusal("SELECT ROW_NUMBER() OVER (PARTITION BY q.nosuchcol ORDER BY b) FROM q")
            .contains("error line 1 at position 39 invalid identifier 'Q.NOSUCHCOL'"),
            refusal("SELECT ROW_NUMBER() OVER (PARTITION BY q.nosuchcol ORDER BY b) FROM q"));
        assertTrue(refusal(
            "SELECT ROW_NUMBER() OVER (PARTITION BY test_schema.q.nosuchcol ORDER BY b) FROM q")
            .contains("error line 1 at position 39 invalid identifier 'TEST_SCHEMA.Q.NOSUCHCOL'"),
            refusal("SELECT ROW_NUMBER() OVER (PARTITION BY test_schema.q.nosuchcol ORDER BY b) FROM q"));
    }

    /** The window call's ARGUMENT is reported at the argument, not at the call. */
    @Test
    public void aWindowArgumentIsReportedAtTheArgument() {
        assertTrue(refusal("SELECT SUM(nosuchcol) OVER (ORDER BY b) FROM q")
            .contains("error line 1 at position 11 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT SUM(nosuchcol) OVER (ORDER BY b) FROM q"));
    }

    /** QUALIFY carries both shapes: its own predicate, and the windows written inline in it. */
    @Test
    public void qualifyIsCheckedInBothOfItsShapes() {
        assertTrue(refusal("SELECT a FROM q QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1")
            .contains("error line 1 at position 52 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM q QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1"));
        assertTrue(refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM q QUALIFY nosuchcol = 1")
            .contains("error line 1 at position 58 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM q QUALIFY nosuchcol = 1"));
        assertTrue(refusal(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM q QUALIFY test_schema.q.nosuchcol = 1")
            .contains("error line 1 at position 58 invalid identifier 'TEST_SCHEMA.Q.NOSUCHCOL'"),
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM q QUALIFY test_schema.q.nosuchcol = 1"));
    }

    /** All of it is COMPILE-time: an empty table is refused identically. */
    @Test
    public void anEmptyTableIsRefusedIdentically() {
        assertTrue(refusal("SELECT a FROM qe QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1")
            .contains("error line 1 at position 53 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a FROM qe QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1"));
        assertTrue(refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM qe QUALIFY nosuchcol = 1")
            .contains("error line 1 at position 59 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY b) r FROM qe QUALIFY nosuchcol = 1"));
    }

    /**
     * The two groups' sums are 30 and 5, not 30 and 30: an ORDER BY over the aggregate must be able to
     * pick a winner. With both groups summing to 30 the key TIED, so which row a window numbered 1 was
     * unspecified — Frostlake answered in group order every time and live sometimes answered the other.
     */
    private void createGrouped() {
        engine.execute("CREATE OR REPLACE TABLE g (a INT, b INT, c INT)");
        engine.execute("INSERT INTO g VALUES (1, 10, 100), (1, 20, 200), (2, 5, 300)");
    }

    /** A GROUPED query's window keys are walked too, in the select list and in QUALIFY alike. */
    @Test
    public void groupedWindowKeysAreWalkedToo() {
        createGrouped();
        assertTrue(refusal("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1")
            .contains("error line 1 at position 73 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a, SUM(b) s FROM g GROUP BY a"
                + " QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1"));
        assertTrue(refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY nosuchcol) r FROM g GROUP BY a")
            .contains("error line 1 at position 38 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT a, ROW_NUMBER() OVER (ORDER BY nosuchcol) r FROM g GROUP BY a"));
    }

    /**
     * The grouped shapes that must NOT be refused, which is why the walk runs against the BASE
     * relation rather than the projected one: a GROUP BY key the SELECT list never projects, and a
     * raw aggregate over a column that is not a key at all.
     */
    @Test
    public void everyLegitimateGroupedKeyStillReads() {
        createGrouped();
        assertEquals(1, value("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals(2, value("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY s) = 1"),
            "the smaller sum is group 2, so the alias key picks a DIFFERENT row than the key above");
        assertEquals(2, value("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY SUM(b)) = 1"),
            "and the raw aggregate agrees with its alias");
        assertEquals(1, value("SELECT a, SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY MAX(c)) = 1"));
        assertEquals(30, value("SELECT SUM(b) s FROM g GROUP BY a"
            + " QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
    }

    /**
     * A call with SEVERAL arguments reports the argument that is wrong, at its own offset, however it
     * is spaced. The check re-forms the call to type its arguments, so the argument list is copied
     * verbatim — a fixed {@code ", "} between them moved every later argument of a tightly written
     * call.
     */
    @Test
    public void aMultiArgumentCallReportsTheRightArgument() {
        createGrouped();
        assertTrue(refusal("SELECT LEAD(a,nosuchcol) OVER (ORDER BY b) FROM g")
            .contains("error line 1 at position 14 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT LEAD(a,nosuchcol) OVER (ORDER BY b) FROM g"));
        assertTrue(refusal("SELECT LEAD(a, nosuchcol) OVER (ORDER BY b) FROM g")
            .contains("error line 1 at position 15 invalid identifier 'NOSUCHCOL'"),
            refusal("SELECT LEAD(a, nosuchcol) OVER (ORDER BY b) FROM g"));
    }

    /** And every legitimate key still reads — a SELECT alias among them. */
    @Test
    public void legitimateWindowKeysStillRead() {
        assertEquals(1, value("SELECT a AS x, ROW_NUMBER() OVER (ORDER BY x) FROM q"));
        assertEquals(1, value("SELECT ROW_NUMBER() OVER (PARTITION BY a ORDER BY b) FROM q"));
        assertEquals(1, value("SELECT ROW_NUMBER() OVER (PARTITION BY test_schema.q.a ORDER BY b) FROM q"));
        assertEquals(1, value("SELECT a FROM q QUALIFY ROW_NUMBER() OVER (ORDER BY b) = 1"));
    }
}
