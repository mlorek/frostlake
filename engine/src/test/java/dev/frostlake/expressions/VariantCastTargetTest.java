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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casting a VARIANT to a target its CONTENTS cannot become.
 *
 * <p>★ THE CAST ITSELF DID NOT CAST. {@code PARSE_JSON('1')::DATE} handed the number 1 straight back —
 * a DATE-declared value that is not a date, with nothing to mark it — where live refuses:
 * {@code Failed to cast variant value 1 to DATE}. That is the foundation under the conditional fold
 * this was found through: a folded branch cannot be cast correctly while the cast is a no-op.
 *
 * <p>★ THE DISCRIMINATING CASE IS THE ONE THAT MUST STILL WORK. A VARIANT holding a JSON STRING
 * reaches these targets through its CONTENT, so {@code PARSE_JSON('"2020-01-01"')::DATE} is that date
 * — and its quotes disappear, which they did not before. Refusing everything would have looked like a
 * fix and been a regression.
 *
 * <p>★ ONLY THE TEMPORAL TARGETS UNWRAP HERE, which the engine suite settled: the numeric cast reads
 * its own source and its refusal names the VARIANT's own JSON TEXT — {@code Failed to cast variant
 * value "abc" to FIXED}, quotes included — so unwrapping before it would have thrown away the very
 * thing that message prints. OBJECT is judged by its own shape test, which now names the value live's
 * way rather than with a sentence of its own.
 *
 * <p>TIMESTAMP is deliberately absent from the refusing set: a VARIANT number DOES cast to one, as
 * epoch seconds.
 *
 * <p>Left for its own task: the CONDITIONAL fold — COALESCE / IFF / CASE / NVL still hand a VARIANT
 * branch back uncast, so a DATE-declared conditional column can still answer a number. That is one
 * mechanism with two other open entries and should be done once.
 */
public class VariantCastTargetTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vc (a INT, ob OBJECT)");
        engine.execute("INSERT INTO vc SELECT 1, OBJECT_CONSTRUCT('k', 1)");
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

    /** ★ A VARIANT holding a NUMBER cannot become a DATE, a TIME or an OBJECT. */
    @Test
    public void anumericVariantCannotBecomeAdateAtimeOrAnObject() {
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT PARSE_JSON('1')::DATE FROM vc"));
        assertEquals("Failed to cast variant value 1 to TIME",
            answer("SELECT PARSE_JSON('1')::TIME FROM vc"));
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT PARSE_JSON('1')::OBJECT FROM vc"));
    }

    /** ★ A VARIANT holding a JSON STRING reaches them through its CONTENT, quotes gone. */
    @Test
    public void atextualVariantReachesThemThroughItsContent() {
        assertEquals("ACCEPTED: 2020-01-01",
            answer("SELECT PARSE_JSON('\"2020-01-01\"')::DATE FROM vc"));
        assertEquals("ACCEPTED: 10:11:12",
            answer("SELECT PARSE_JSON('\"10:11:12\"')::TIME FROM vc"));
        assertEquals("ACCEPTED: abc", answer("SELECT PARSE_JSON('\"abc\"')::VARCHAR FROM vc"));
    }

    /** An OBJECT-shaped VARIANT still becomes an OBJECT — the shape test decides, not the wrapper. */
    @Test
    public void anObjectShapedVariantStillBecomesAnObject() {
        assertEquals("ACCEPTED: {\"k\":1}", answer("SELECT ob::OBJECT FROM vc"));
        assertEquals("ACCEPTED: {\"k\":1}",
            answer("SELECT PARSE_JSON('{\"k\":1}')::OBJECT FROM vc"));
    }

    /** ★ The NUMERIC refusal still names the VARIANT's own JSON text, quotes included. */
    @Test
    public void thenumericRefusalStillNamesTheJsonText() {
        assertEquals("Failed to cast variant value \"abc\" to FIXED",
            answer("SELECT PARSE_JSON('\"abc\"')::NUMBER FROM vc"));
        assertEquals("ACCEPTED: 1", answer("SELECT PARSE_JSON('1')::NUMBER FROM vc"),
            "and a numeric one still casts");
    }
}
