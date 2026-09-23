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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VARIANT compared with a value of another type converts one side on the row. A DATE, a TIME or a timestamp
 * takes the VARIANT cast to it whichever side it is written on; a BOOLEAN, an ARRAY or an OBJECT takes it when
 * written on the left, and is read as a VARIANT when written on the right; a number is read as a VARIANT either
 * way round, and a text compares with the VARIANT's text. Live-verified.
 */
public class VariantTypedComparisonTest extends BaseDatabaseTest {

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE vtc (va VARIANT, b BOOLEAN, ar ARRAY, ob OBJECT, d DATE, tm TIME, "
            + "n NUMBER(38,0), s VARCHAR, ts TIMESTAMP_NTZ, vs VARIANT, vd VARIANT, vt VARIANT, v1s VARIANT, "
            + "vts VARIANT)");
        engine.execute("INSERT INTO vtc SELECT TO_VARIANT(1), TRUE, ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a', 1), "
            + "'2020-01-01'::DATE, '10:00:00'::TIME, 1, '1', '2020-01-01 10:00:00'::TIMESTAMP_NTZ, TO_VARIANT('abc'), "
            + "TO_VARIANT('2020-01-01'), TO_VARIANT('10:00:00'), TO_VARIANT('1'), TO_VARIANT('2020-01-01 10:00:00')");
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    @Test
    public void aTemporalCastsTheVariantEitherWayRound() {
        createTable();
        final String date = "Failed to cast variant value 1 to DATE";
        assertEquals(date, refusal("SELECT d = va FROM vtc"));
        assertEquals(date, refusal("SELECT va = d FROM vtc"));
        assertEquals(date, refusal("SELECT d < va FROM vtc"));
        assertEquals(date, refusal("SELECT va < d FROM vtc"));
        assertEquals(date, refusal("SELECT va <> d FROM vtc"));
        assertEquals(date, refusal("SELECT va >= d FROM vtc"));
        assertEquals(date, refusal("SELECT 1 FROM vtc WHERE va = d"));
        final String time = "Failed to cast variant value 1 to TIME";
        assertEquals(time, refusal("SELECT tm = va FROM vtc"));
        assertEquals(time, refusal("SELECT va < tm FROM vtc"));
        assertEquals("Failed to cast variant value \"abc\" to DATE", refusal("SELECT vs = d FROM vtc"));
        assertEquals("Failed to cast variant value \"abc\" to TIMESTAMP_NTZ", refusal("SELECT ts = vs FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT vd = d FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT vt = tm FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT vts = ts FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT ts = vts FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT va = ts FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT va < ts FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT '2020-01-01'::DATE = TO_VARIANT('2020-01-01 10:00:00')"));
        assertNull(scalar("SELECT '2020-01-01'::DATE = PARSE_JSON('null')"));
    }

    @Test
    public void aBooleanOrAContainerOnTheLeftCastsTheVariant() {
        createTable();
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT b = va FROM vtc"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT b < va FROM vtc"));
        assertEquals("Failed to cast variant value 1 to OBJECT", refusal("SELECT ob = va FROM vtc"));
        assertEquals("Failed to cast variant value 0 to BOOLEAN", refusal("SELECT FALSE = TO_VARIANT(0)"));
        assertEquals(Boolean.TRUE, scalar("SELECT ar = va FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT TRUE = TO_VARIANT('true')"));
        assertEquals(Boolean.TRUE, scalar("SELECT FALSE = TO_VARIANT('off')"));
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAY_CONSTRUCT('a') = TO_VARIANT('a')"));
        assertEquals(Boolean.TRUE, scalar("SELECT ARRAY_CONSTRUCT(1) < TO_VARIANT(2)"));
        assertNull(scalar("SELECT OBJECT_CONSTRUCT('a', 1) = PARSE_JSON('null')"));
    }

    @Test
    public void aBooleanOrAContainerOnTheRightIsReadAsAVariant() {
        createTable();
        assertEquals(Boolean.FALSE, scalar("SELECT va = b FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT va < b FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT va > b FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT va != b FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT va = ob FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT va = ar FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT TO_VARIANT('true') = TRUE"));
        assertEquals(Boolean.TRUE, scalar("SELECT TO_VARIANT('a') > TRUE"));
        assertEquals(Boolean.FALSE, scalar("SELECT PARSE_JSON('null') < TRUE"));
        assertEquals(Boolean.TRUE, scalar("SELECT TO_VARIANT(ARRAY_CONSTRUCT()) > TRUE"));
        assertEquals(0, engine.executeQuery("SELECT 1 FROM vtc WHERE va = b").getRowCount());
    }

    @Test
    public void aNumberIsReadAsAVariantAndATextComparesAsText() {
        createTable();
        assertEquals(Boolean.TRUE, scalar("SELECT va = n FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT n = va FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT v1s = n FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT n = v1s FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT vs = n FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT n < vs FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT va = s FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT s = va FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT TO_VARIANT(1) = '1.0'"));
    }

    @Test
    public void inBetweenAndDistinctConvertAsTheComparisonDoes() {
        createTable();
        final String date = "Failed to cast variant value 1 to DATE";
        assertEquals(date, refusal("SELECT va IN (d) FROM vtc"));
        assertEquals(date, refusal("SELECT d IN (va) FROM vtc"));
        assertEquals(date, refusal("SELECT va BETWEEN 0 AND d FROM vtc"));
        assertEquals(date, refusal("SELECT d BETWEEN va AND va FROM vtc"));
        assertEquals(date, refusal("SELECT va IS DISTINCT FROM d FROM vtc"));
        assertEquals(date, refusal("SELECT EQUAL_NULL(va, d) FROM vtc"));
        assertEquals(date, refusal("SELECT CASE va WHEN d THEN 1 ELSE 0 END FROM vtc"));
        assertEquals(date, refusal("SELECT CASE d WHEN va THEN 1 ELSE 0 END FROM vtc"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT b IN (va) FROM vtc"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT b IS DISTINCT FROM va FROM vtc"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT EQUAL_NULL(b, va) FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT va IN (b) FROM vtc"));
        assertEquals(Boolean.TRUE, scalar("SELECT va IS DISTINCT FROM b FROM vtc"));
        assertEquals(Boolean.FALSE, scalar("SELECT EQUAL_NULL(va, b) FROM vtc"));
    }

    /** What an anonymous block returns, as text. */
    private String blockAnswer(final String block) {
        return String.valueOf(scalar("EXECUTE IMMEDIATE $$" + block + "$$")).toLowerCase();
    }

    /** The block's refusal, which must end with {@code sentence}. */
    private void assertBlockRefused(final String block, final String sentence) {
        final String refused = refusal("EXECUTE IMMEDIATE $$" + block + "$$");
        assertTrue(refused.endsWith(sentence), refused);
    }

    @Test
    public void aBlockVariableIsComparedAsItsDeclaredType() {
        final String variant = "BEGIN LET v VARIANT := TO_VARIANT(1); ";
        final String date = "Failed to cast variant value 1 to DATE";
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN v = d; END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN d = v; END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN v <> d; END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN :v = :d; END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN v IN (d); END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; RETURN CASE WHEN v = d THEN 1 ELSE 0 END; END;", date);
        assertBlockRefused(variant + "LET d DATE := '2020-01-01'; IF (v = d) THEN RETURN 'eq'; END IF; RETURN 'ne'; END;",
            date);
        assertBlockRefused("BEGIN LET d DATE := '2020-01-01'; RETURN TO_VARIANT(1) = d; END;", date);
        assertBlockRefused(variant + "LET b BOOLEAN := TRUE; RETURN b = v; END;",
            "Failed to cast variant value 1 to BOOLEAN");
        assertBlockRefused(variant + "LET o OBJECT := OBJECT_CONSTRUCT('a', 1); RETURN o = v; END;",
            "Failed to cast variant value 1 to OBJECT");
        assertBlockRefused("BEGIN LET v VARIANT := 'abc'; LET d DATE := '2020-01-01'; RETURN v = d; END;",
            "Failed to cast variant value \"abc\" to DATE");
        assertEquals("false", blockAnswer(variant + "LET b BOOLEAN := TRUE; RETURN v < b; END;"));
        assertEquals("true", blockAnswer(variant + "LET b BOOLEAN := TRUE; RETURN v > b; END;"));
        assertEquals("true", blockAnswer(variant + "LET a ARRAY := ARRAY_CONSTRUCT(1); RETURN a = v; END;"));
        assertEquals("true", blockAnswer(variant + "LET n NUMBER := 1; RETURN v = n; END;"));
        assertEquals("true", blockAnswer(variant + "LET s VARCHAR := '1'; RETURN v = s; END;"));
        assertEquals("false", blockAnswer("BEGIN LET v VARIANT := '1'; LET n NUMBER := 1; RETURN v = n; END;"));
        assertEquals("null", blockAnswer(
            "BEGIN LET v VARIANT := PARSE_JSON('null'); LET d DATE := '2020-01-01'; RETURN v = d; END;"));
        assertEquals("true", blockAnswer(
            "BEGIN LET v VARIANT := PARSE_JSON('\"2020-01-01\"'); LET d DATE := '2020-01-01'; RETURN v = d; END;"));
    }

    @Test
    public void aJoinComparesAVariantKeyAsTheComparisonDoes() {
        engine.execute("CREATE OR REPLACE TABLE vja (va VARIANT, v1s VARIANT, vt VARIANT)");
        engine.execute("INSERT INTO vja SELECT TO_VARIANT(1), TO_VARIANT('1'), TO_VARIANT('true')");
        engine.execute("CREATE OR REPLACE TABLE vjb (n NUMBER(38,0), b BOOLEAN, d DATE, s VARCHAR)");
        engine.execute("INSERT INTO vjb SELECT 1, TRUE, '2020-01-01'::DATE, '1'");
        engine.execute("CREATE OR REPLACE TABLE vjc (ar ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO vjc SELECT ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a', 1)");
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vja JOIN vjb ON vja.va = vjb.n")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vjb JOIN vja ON vjb.n = vja.va")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vja, vjb WHERE vja.va = vjb.n")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vja JOIN vjb ON vja.va = vjb.s")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vja, vjb WHERE vja.v1s = vjb.s")));
        assertEquals("0", String.valueOf(scalar("SELECT COUNT(*) FROM vja, vjb WHERE vja.v1s = vjb.n")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vja FULL JOIN vjb ON vja.va = vjb.n")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vjb JOIN vja ON vjb.b = vja.vt")));
        assertEquals("0", String.valueOf(scalar("SELECT COUNT(*) FROM vja, vjb WHERE vja.vt = vjb.b")));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT(*) FROM vjc JOIN vja ON vjc.ar = vja.va")));
        final String date = "Failed to cast variant value 1 to DATE";
        assertEquals(date, refusal("SELECT COUNT(*) FROM vja JOIN vjb ON vja.va = vjb.d"));
        assertEquals(date, refusal("SELECT COUNT(*) FROM vja LEFT JOIN vjb ON vja.va = vjb.d"));
        assertEquals(date, refusal("SELECT COUNT(*) FROM vja, vjb WHERE vja.va = vjb.d"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN",
            refusal("SELECT COUNT(*) FROM vja, vjb WHERE vjb.b = vja.va"));
        assertEquals("Failed to cast variant value 1 to OBJECT",
            refusal("SELECT COUNT(*) FROM vjc, vja WHERE vjc.ob = vja.va"));
    }
}
