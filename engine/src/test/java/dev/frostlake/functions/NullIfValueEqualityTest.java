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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code NULLIF(a, b)} is {@code CASE WHEN a = b THEN NULL ELSE a END}, so it decides equality the way
 * the {@code =} operator does — by VALUE, across the type families. Frostlake decided it with
 * {@link Object#equals} instead, which made the answer depend on a runtime class the SQL cannot see:
 *
 * <pre>
 *   NULLIF(1.00, 1)   a BigDecimal is not a Long          returned 1.00, live NULL
 *   NULLIF(f, i)      a Double is not a Long              returned 1.0,  live NULL
 *   NULLIF('1', 1)    a String is not a Long              returned '1',  live NULL
 *   NULLIF(bo, i)     a Boolean is not a Long             returned TRUE, live NULL
 *   NULLIF(d, ts)     a LocalDate is not a LocalDateTime  returned the date, live NULL
 * </pre>
 *
 * <p>Two that must NOT collapse are pinned beside them: a string compares case-sensitively, and a
 * NULL on either side is never compared at all — SQL NULL equals nothing, so the first argument
 * stands.
 *
 * <p>The last group is the one that made this hard. Sharing the operator's comparison also means
 * sharing its refusals, so {@code NULLIF(v, i)} over unreadable text raises rather than answering —
 * correct, and matching live. But a VARIANT holding a number must still compare as TEXT, or the very
 * common {@code NULLIF(<json path>, '')} idiom raises where live simply returns the number.
 */
public class NullIfValueEqualityTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ni (i INT, n NUMBER(10,2), m NUMBER(5,4), f FLOAT,"
            + " v VARCHAR(10), d DATE, ts TIMESTAMP_NTZ, bo BOOLEAN, src VARIANT)");
        engine.execute("INSERT INTO ni SELECT 1, 1.00, 1.0000, 1.0, 'ab', '2020-01-01',"
            + " '2020-01-01 00:00:00', TRUE, PARSE_JSON('{\"score\": 7.5, \"empty\": \"\"}')");
    }

    /** The expression's answer, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM ni");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Equal numbers are equal whatever scale or Java class carries them. */
    @Test
    public void aNumberComparesByValueNotByScale() {
        assertEquals("<NULL>", outcome("NULLIF(1, 1.00)"));
        assertEquals("<NULL>", outcome("NULLIF(1.00, 1)"));
        assertEquals("<NULL>", outcome("NULLIF(1.0, 1)"));
        assertEquals("<NULL>", outcome("NULLIF(n, i)"));
        assertEquals("<NULL>", outcome("NULLIF(i, n)"));
        assertEquals("<NULL>", outcome("NULLIF(m, i)"), "four decimals against an integer");
        assertEquals("1", outcome("NULLIF(1, 2)"), "unequal numbers still answer the first");
    }

    /** An approximate number meets an exact one on value too. */
    @Test
    public void anApproximateNumberComparesByValue() {
        assertEquals("<NULL>", outcome("NULLIF(f, i)"));
        assertEquals("<NULL>", outcome("NULLIF(i, f)"));
    }

    /** The shape that first exposed this: a folded ordering answer beside the bound it hit. */
    @Test
    public void aFoldedOrderingAnswerMatchesItsBound() {
        assertEquals("<NULL>", outcome("NULLIF(GREATEST(n, 100), 100)"));
        assertEquals("<NULL>", outcome("NULLIF(LEAST(n, 100), 1)"));
    }

    /** A string reads as the number beside it, a boolean as the number, a DATE as its own text. */
    @Test
    public void theFamiliesMeetTheWayTheOperatorMeetsThem() {
        assertEquals("<NULL>", outcome("NULLIF('1', 1)"));
        assertEquals("<NULL>", outcome("NULLIF(bo, i)"));
        assertEquals("<NULL>", outcome("NULLIF(i, bo)"));
        assertEquals("<NULL>", outcome("NULLIF(d, ts)"), "the same instant");
        assertEquals("<NULL>", outcome("NULLIF(ts, d)"));
        assertEquals("<NULL>", outcome("NULLIF(d, '2020-01-01')"), "a DATE against its own text");
    }

    /** Sharing the operator's comparison means sharing its refusals. */
    @Test
    public void unreadableTextRaisesAsItDoesForTheOperator() {
        assertEquals("Numeric value 'ab' is not recognized", outcome("NULLIF(v, i)"));
        assertEquals("Date 'ab' is not recognized", outcome("NULLIF(v, d)"));
        assertEquals("Date 'ab' is not recognized", outcome("NULLIF(d, v)"));
    }

    /** A string compares as a string when the other side is one — case and all. */
    @Test
    public void aStringStillComparesAsAString() {
        assertEquals("<NULL>", outcome("NULLIF('ab', 'ab')"));
        assertEquals("<NULL>", outcome("NULLIF(v, 'ab')"));
        assertEquals("ab", outcome("NULLIF('ab', 'AB')"), "case-sensitive, so no match");
        assertEquals("ab", outcome("NULLIF('ab', 'cd')"));
    }

    /** A NULL on either side is never compared. */
    @Test
    public void nullsKeepTheirRule() {
        assertEquals("<NULL>", outcome("NULLIF(NULL, 1)"), "a NULL first argument IS the answer");
        assertEquals("1", outcome("NULLIF(1, NULL)"), "nothing equals NULL, so the value stands");
    }

    /**
     * And the idiom that has to keep working: a VARIANT compares as TEXT, so blanking a JSON number
     * against the empty string answers the number rather than raising on a coercion it never makes.
     */
    @Test
    public void aVariantComparesAsTextSoTheBlankingIdiomAnswers() {
        assertEquals("7.5", outcome("NULLIF(src:score, '')"));
        assertEquals("7.5", outcome("NULLIF(src:score, 'abc')"));
        assertEquals("7.5", outcome("NULLIF(src:score, '7.50')"), "text, so no match");
        assertEquals("<NULL>", outcome("NULLIF(src:score, '7.5')"), "the text that does match");
        assertEquals("<NULL>", outcome("NULLIF(src:score, 7.5)"), "against a NUMBER it is numeric");
        assertEquals("<NULL>", outcome("NULLIF(src:empty, '')"), "an empty JSON string does match");
    }
}
