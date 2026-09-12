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
 * The THIRD character of a base64 custom alphabet, which stands in for the {@code =} PADDING.
 *
 * <p>Frostlake accepted a three-character alphabet and then substituted only the first two, so a
 * custom pad was silently written as {@code =}. Live really does write it: {@code BASE64_ENCODE('a',
 * 0, '$%^')} is {@code YQ^^}.
 *
 * <p>★ AND NAMING A CUSTOM PAD MAKES {@code =} ILLEGAL, which is the half that is easy to miss.
 * {@code BASE64_DECODE_STRING('YQ==', '$%^')} is refused live even though {@code =} is base64's own
 * padding — once the alphabet names a different pad, {@code =} is simply a character with no reading,
 * exactly like any other character outside the alphabet.
 *
 * <p>A TWO-character alphabet leaves the padding alone, which is why {@code '$%='} and the default are
 * indistinguishable — and why the cells that existed before this never caught the bug.
 */
public class Base64CustomPadTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE b64 (g VARCHAR(10))");
        engine.execute("INSERT INTO b64 VALUES ('zz')");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The custom pad is WRITTEN, once and twice. */
    @Test
    public void theCustomPadIsWritten() {
        assertEquals("YQ^^", answer("SELECT BASE64_ENCODE('a', 0, '$%^')"), "two pad characters");
        assertEquals("YWI^", answer("SELECT BASE64_ENCODE('ab', 0, '$%^')"), "one");
        assertEquals("YWJj", answer("SELECT BASE64_ENCODE('abc', 0, '$%^')"), "and none needed");
    }

    /** A shorter alphabet leaves the padding at its default. */
    @Test
    public void aShorterAlphabetLeavesThePaddingAlone() {
        assertEquals("YQ==", answer("SELECT BASE64_ENCODE('a')"));
        assertEquals("YQ==", answer("SELECT BASE64_ENCODE('a', 0, '$%')"), "two characters");
        assertEquals("YQ==", answer("SELECT BASE64_ENCODE('a', 0, '$%=')"),
            "and a third that IS '=' is the default spelled out");
    }

    /** The custom pad decodes back, and a round trip closes. */
    @Test
    public void theCustomPadDecodesBack() {
        assertEquals("a", answer("SELECT BASE64_DECODE_STRING('YQ^^', '$%^')"));
        assertEquals("a", answer("SELECT BASE64_DECODE_STRING(BASE64_ENCODE('a', 0, '$%^'), '$%^')"));
    }

    /** ★ Naming a custom pad makes the standard '=' illegal. */
    @Test
    public void aCustomPadMakesTheStandardPaddingIllegal() {
        assertEquals("The following string is not a legal base64-encoded value: 'YQ=='",
            answer("SELECT BASE64_DECODE_STRING('YQ==', '$%^')"));
        assertEquals("a", answer("SELECT BASE64_DECODE_STRING('YQ==', '$%=')"),
            "while an alphabet whose pad IS '=' still reads it");
    }

    /** The TRY_ decoders answer NULL where the plain forms raise on an unusable alphabet. */
    @Test
    public void theTryDecodersSwallowAnUnusableAlphabet() {
        assertEquals("null",
            answer("SELECT TRY_BASE64_DECODE_STRING(BASE64_ENCODE(g), 'abc') FROM b64"));
        assertEquals("null",
            answer("SELECT TRY_BASE64_DECODE_BINARY(BASE64_ENCODE(g), 'abc') FROM b64"));
        assertEquals("Invalid Base64 custom alphabet or padding characters: 'abc'",
            answer("SELECT BASE64_DECODE_STRING(BASE64_ENCODE(g), 'abc') FROM b64"),
            "the plain form raises");
    }

    /** Both ends of the row-time int range for the line length. */
    @Test
    public void bothEndsOfTheLineLengthRange() {
        assertEquals("Numeric value '-2147483649' is out of range",
            answer("SELECT BASE64_ENCODE(g, 0 - 2147483649) FROM b64"));
        assertEquals("eno=", answer("SELECT BASE64_ENCODE(g, 0 - 2147483648) FROM b64"),
            "the most negative int is still a legal argument — a negative length just does not wrap");
    }
}
