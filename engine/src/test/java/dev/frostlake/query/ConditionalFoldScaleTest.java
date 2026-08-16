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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional's VALUE is presented at its FOLDED type's scale — live plans every branch as a CAST
 * to the fold, so an INT beside a NUMBER(10,2) answers 1.00 from every conditional in the family
 * (live-verified cell by cell). NULLIF is the deliberate exception: it keeps its first argument's own
 * flavour.
 *
 * <p>★ THE FOLD MUST SEE A CONVERSION'S DECLARED PAIR. TO_DECIMAL(x, 5, 3) types as NUMBER(5,3) —
 * not the registry's nominal NUMBER(38,0) — and this is what four earlier attempts at the rule
 * missed: with the nominal type, a CASE over TO_DECIMAL(x, 5, 3) folded at scale 0 and ROUNDED THE
 * FRACTION AWAY, which surfaced as derived metric scores collapsing to zero several
 * loaders downstream.
 *
 * <p>★ THE SCALED VALUE SURVIVES STORAGE AND SET OPERATIONS: inserted into a NUMBER(5,3) column it
 * reads back 0.300, and EXCEPT against the literal 0.300 is empty in both directions.
 */
public class ConditionalFoldScaleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cfs (i INT, n NUMBER(10,2))");
        engine.execute("INSERT INTO cfs VALUES (1, 2.50)");
        engine.execute("CREATE OR REPLACE TABLE otr (r NUMBER(28,25))");
        engine.execute("INSERT INTO otr VALUES (2.995602416992188e+01)");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void everyFoldingConditionalPresentsTheFoldedScale() {
        assertEquals("1.00", cell("SELECT COALESCE(i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT IFF(TRUE, i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT NVL(i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT IFNULL(i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT NVL2(i, i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT CASE WHEN TRUE THEN i ELSE n END FROM cfs"));
        assertEquals("1.00", cell("SELECT DECODE(1, 1, i, n) FROM cfs"));
        assertEquals("2.50", cell("SELECT GREATEST(i, n) FROM cfs"));
        assertEquals("1.00", cell("SELECT LEAST(i, n) FROM cfs"));
        // A string beside the scaled column coerces INTO the numeric fold — GREATEST included; the
        // old "GREATEST is not coerced" reading came from a scale-0 table where the coercion is
        // invisible (live-verified over the scale-2 column).
        assertEquals("5.00", cell("SELECT GREATEST(n, '5') FROM cfs"));
        assertEquals("2.50", cell("SELECT COALESCE(n, i) FROM cfs"));
    }

    @Test
    public void nullifKeepsItsFirstArgumentsFlavour() {
        assertEquals("1", cell("SELECT NULLIF(i, 99) FROM cfs"));
    }

    @Test
    public void aLiteralDefaultTakesTheColumnsScale() {
        assertEquals("2.50", cell("SELECT IFNULL(n, 0) FROM cfs"));
        assertEquals("2.50", cell("SELECT IFNULL(NULL, n) FROM cfs"));
    }

    @Test
    public void theFoldSeesAConversionsDeclaredPair() {
        assertEquals("0.300",
            cell("SELECT CASE WHEN TRUE THEN TO_DECIMAL(r/100, 5, 3) ELSE NULL END FROM otr"));
        assertEquals("0.300",
            cell("SELECT CASE WHEN TRUE THEN TO_DECIMAL(IFNULL(r, 0)/100, 5, 3) ELSE NULL END FROM otr"));
    }

    @Test
    public void theScaledValueSurvivesStorageAndSetOperations() {
        engine.execute("CREATE OR REPLACE TABLE cfsm (vd NUMBER(5,3))");
        engine.execute("INSERT INTO cfsm SELECT CASE WHEN TRUE THEN TO_DECIMAL(IFNULL(r, 0)/100, 5, 3)"
            + " ELSE NULL END FROM otr");
        assertEquals("0.300", cell("SELECT vd FROM cfsm"));
        assertEquals(0, engine.executeQuery("SELECT 0.300 AS vd EXCEPT SELECT vd FROM cfsm")
            .getRows().size());
        assertEquals(0, engine.executeQuery("SELECT vd FROM cfsm EXCEPT SELECT 0.300 AS vd")
            .getRows().size());
    }
}
