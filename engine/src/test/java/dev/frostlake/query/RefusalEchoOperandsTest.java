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

/**
 * The ARITY refusal echoes an expression argument the way every other refusal does — from live's
 * plan, not from the source: a call operand is bracketed and a column or literal is bare, the plan's
 * conversions are printed (a literal beside a NUMBER(10,2) column is cast to that width, a literal
 * beside a FLOAT to FLOAT, a NUMBER(5,0) column beside 1.5 to NUMBER(6,1)), NEGATE and NOT are calls,
 * Live-verified cell by cell; the
 * same rule serves the nesting sentence, which is why a HEX_ENCODE with one argument too many is the
 * cheapest place to watch it. A call holding a window call is refused the same way, at its own place and
 * before any row is evaluated — so over an empty table too — like every other item of a windowed query.
 */
public class RefusalEchoOperandsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eo (n102 NUMBER(10,2), i NUMBER(5,0), f FLOAT, t VARCHAR(10),"
            + " b BOOLEAN, bn BINARY)");
        engine.execute("INSERT INTO eo SELECT 1.5, 1, 1.5, 'a', TRUE, TO_BINARY('ab')");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String tooMany(final String call) {
        return "SQL compilation error: error line 1 at position 7|too many arguments for function ["
            + call + "] expected 2, got 3";
    }

    private static String refusedAt(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    @Test
    public void operandsAreBracketedAndConvertedOnTheAritySurface() {
        assertEquals(tooMany("HEX_ENCODE(EO.T || 'x', 1, 1)"), answer("SELECT HEX_ENCODE(t || 'x', 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, EO.N102 + (CAST(1 AS NUMBER(10,2))), 1)"),
            answer("SELECT HEX_ENCODE(bn, n102 + 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, (CAST(EO.I AS NUMBER(6,1))) + 1.5, 1)"),
            answer("SELECT HEX_ENCODE(bn, i + 1.5, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, (SUM(EO.N102)) + (CAST(1 AS NUMBER(22,2))), 1)"),
            answer("SELECT HEX_ENCODE(bn, SUM(n102) + 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, EO.F + (CAST(1 AS FLOAT)), 1)"),
            answer("SELECT HEX_ENCODE(bn, f + 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, (SUM(EO.F)) + (CAST(1 AS FLOAT)), 1)"),
            answer("SELECT HEX_ENCODE(bn, SUM(f) + 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, EO.N102 > (CAST(1 AS NUMBER(10,2))), 1)"),
            answer("SELECT HEX_ENCODE(bn, n102 > 1, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, EO.B AND EO.B, 1)"), answer("SELECT HEX_ENCODE(bn, b AND b, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, NOT(EO.B), 1)"), answer("SELECT HEX_ENCODE(bn, NOT b, 1) FROM eo"));
        assertEquals(tooMany("HEX_ENCODE(EO.BN, NEGATE(EO.I), 1)"), answer("SELECT HEX_ENCODE(bn, -i, 1) FROM eo"));
    }

    @Test
    public void aCallHoldingAWindowIsRefusedAtItsOwnPlace() {
        assertEquals(tooMany("HEX_ENCODE(EO.BN, ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST) + 1, 1)"),
            answer("SELECT HEX_ENCODE(bn, ROW_NUMBER() OVER (ORDER BY i) + 1, 1) FROM eo"));
        assertEquals(refusedAt(7, "too many arguments for function [ABS(ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST), 1)]"
            + " expected 1, got 2"), answer("SELECT ABS(ROW_NUMBER() OVER (ORDER BY i), 1) FROM eo"));
        assertEquals(refusedAt(10, "too many arguments for function [LEFT('abc', ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST), 1)]"
            + " expected 2, got 3"), answer("SELECT i, LEFT('abc', ROW_NUMBER() OVER (ORDER BY i), 1) FROM eo"));
        assertEquals(refusedAt(7, "too many arguments for function [ABS(SUM(EO.I) OVER (), 2)] expected 1, got 2"),
            answer("SELECT ABS(SUM(i) OVER (), 2) FROM eo"));
        assertEquals(refusedAt(10, "too many arguments for function [UPPER(ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST), 2, 3)]"
            + " expected 1, got 3"), answer("SELECT 1, UPPER(ROW_NUMBER() OVER (ORDER BY i), 2, 3) AS u FROM eo"));
        assertEquals(refusedAt(11, "too many arguments for function [ABS(ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST), 1)]"
            + " expected 1, got 2"), answer("SELECT 1 + ABS(ROW_NUMBER() OVER (ORDER BY i), 1) FROM eo"));
        // A plain item beside a window is checked the same way.
        assertEquals(tooMany("HEX_ENCODE(EO.BN, 1, 1)"),
            answer("SELECT HEX_ENCODE(bn, 1, 1), ROW_NUMBER() OVER (ORDER BY i) FROM eo"));
    }

    @Test
    public void aWindowedQueryOverAnEmptyTableIsRefusedAlike() {
        engine.execute("CREATE OR REPLACE TABLE ee (i NUMBER(5,0), bn BINARY, t VARCHAR(10))");
        assertEquals(tooMany("HEX_ENCODE(EE.BN, ROW_NUMBER() OVER (ORDER BY EE.I ASC NULLS LAST) + 1, 1)"),
            answer("SELECT HEX_ENCODE(bn, ROW_NUMBER() OVER (ORDER BY i) + 1, 1) FROM ee"));
        assertEquals(tooMany("HEX_ENCODE(EE.BN, 1, 1)"),
            answer("SELECT HEX_ENCODE(bn, 1, 1), ROW_NUMBER() OVER (ORDER BY i) FROM ee"));
        assertEquals(tooMany("HEX_ENCODE(EE.BN, 1, 1)"),
            answer("SELECT HEX_ENCODE(bn, 1, 1) AS h, ROW_NUMBER() OVER (ORDER BY i) FROM ee QUALIFY h IS NULL"));
        assertEquals(refusedAt(7, "too many arguments for function [ABS(ROW_NUMBER() OVER (ORDER BY EE.I ASC NULLS LAST), 1)]"
            + " expected 1, got 2"), answer("SELECT ABS(ROW_NUMBER() OVER (ORDER BY i), 1) FROM ee"));
        // Grouped rows, and a window over them.
        assertEquals(tooMany("HEX_ENCODE(MAX(EE.BN), 1, 1)"),
            answer("SELECT HEX_ENCODE(MAX(bn), 1, 1), ROW_NUMBER() OVER (ORDER BY MAX(i)) FROM ee"));
        assertEquals(refusedAt(10, "too many arguments for function [ABS(SUM(EE.I) OVER (PARTITION BY EE.I), 1)]"
            + " expected 1, got 2"), answer("SELECT i, ABS(SUM(i) OVER (PARTITION BY i), 1) FROM ee GROUP BY i"));
        // What the walk must leave alone.
        assertEquals("ACCEPTED:", answer("SELECT LAG(i, 1, 0) OVER (ORDER BY i), FIRST_VALUE(t IGNORE NULLS) OVER (ORDER BY i), "
            + "NTH_VALUE(i, 2) FROM FIRST OVER (ORDER BY i), RATIO_TO_REPORT(i) OVER (), COUNT(*) OVER (), "
            + "CONDITIONAL_TRUE_EVENT(i > 0) OVER (ORDER BY i) FROM ee"));
        assertEquals("ACCEPTED:", answer("SELECT i AS a, a + ROW_NUMBER() OVER (ORDER BY i) FROM ee"));
        assertEquals("ACCEPTED:", answer("SELECT LISTAGG(t, ',') WITHIN GROUP (ORDER BY t) OVER (PARTITION BY i) FROM ee"));
        assertEquals("ACCEPTED:", answer("SELECT UPPER(t) || ROW_NUMBER() OVER (ORDER BY i) FROM ee"));
    }
}
