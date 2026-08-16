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
 * Refusals both engines already raise, spelled the way the account spells them. Two shapes:
 *
 * <p>The DECODERS report the offending value in one sentence, whatever is wrong with it. Frostlake
 * had two messages of its own for hex — "Invalid hex string length" when the digit count was odd, and
 * a raw Java "For input string: \"zz\" under radix 16" when a digit was not hex — where the account
 * says the same thing for both. The BINARY decoder was already right, because it reads its bytes
 * through the shared decoder; the STRING one parsed the digits itself.
 *
 * <p>The ARGUMENT-TYPE refusals are compile-time and carry a POSITION. Without the marker and its
 * anchor the refusal reads as data-dependent, which is what let a view over one be created with no
 * columns at all.
 *
 * <p>The TRY_ spellings are pinned beside them: they answer NULL and must not acquire a sentence.
 */
public class DecodeRefusalSentencesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rs (v VARCHAR(5), w VARCHAR(9), b BINARY(4))");
        engine.execute("INSERT INTO rs SELECT 'ab', 'cdefg', TO_BINARY('41')");
    }

    /** The refusal's message, or the answer when there is none. */
    private String outcome(final String sql) {
        try {
            final ResultSet result = engine.executeQuery(sql);
            result.next();
            return "OK[" + String.valueOf(result.getValue(0)) + "]";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
    }

    /** One sentence for hex, whether the length is odd or a digit is not hex at all. */
    @Test
    public void hexDecodingNamesTheOffendingValue() {
        assertEquals("The following string is not a legal hex-encoded value: 'cdefg'",
            outcome("SELECT HEX_DECODE_STRING(w) AS x FROM rs"), "an odd digit count");
        assertEquals("The following string is not a legal hex-encoded value: 'zz'",
            outcome("SELECT HEX_DECODE_STRING('zz') AS x FROM rs"), "an even count, but not hex");
        assertEquals("The following string is not a legal hex-encoded value: 'cdefg'",
            outcome("SELECT HEX_DECODE_BINARY(w) AS x FROM rs"), "the BINARY decoder agrees");
    }

    /** And the base64 pair says its own equivalent. */
    @Test
    public void base64DecodingNamesTheOffendingValue() {
        assertEquals("The following string is not a legal base64-encoded value: 'cdefg'",
            outcome("SELECT BASE64_DECODE_STRING(w) AS x FROM rs"));
        assertEquals("The following string is not a legal base64-encoded value: 'cdefg'",
            outcome("SELECT BASE64_DECODE_BINARY(w) AS x FROM rs"));
    }

    /** An argument-type refusal is a COMPILE-time error, anchored where the call sits. */
    @Test
    public void anArgumentTypeRefusalCarriesItsPosition() {
        assertEquals("SQL compilation error: error line 1 at position 7 | "
                + "Invalid argument types for function 'TYPEOF': (VARCHAR(5))",
            outcome("SELECT TYPEOF(v) AS x FROM rs"));
        assertEquals("SQL compilation error: error line 1 at position 7 | "
                + "Invalid argument types for function 'TYPEOF': (BINARY(4))",
            outcome("SELECT TYPEOF(b) AS x FROM rs"));
    }

    /** The TRY_ spellings answer NULL, and none of this reaches them. */
    @Test
    public void theTryFormsStillAnswerNull() {
        assertEquals("OK[<NULL>]",
            outcome("SELECT COALESCE(TRY_HEX_DECODE_STRING(w), '<NULL>') AS x FROM rs"));
        assertEquals("OK[<NULL>]",
            outcome("SELECT COALESCE(TRY_BASE64_DECODE_STRING(w), '<NULL>') AS x FROM rs"));
        assertEquals("OK[<NULL>]",
            outcome("SELECT COALESCE(TRY_PARSE_JSON(w), '<NULL>') AS x FROM rs"));
    }

    /** Valid input still decodes, so none of the refusals fire on the ordinary path. */
    @Test
    public void validInputIsUnaffected() {
        assertEquals("OK[A]", outcome("SELECT HEX_DECODE_STRING('41') AS x FROM rs"));
        assertEquals("OK[A]", outcome("SELECT BASE64_DECODE_STRING('QQ==') AS x FROM rs"));
        assertEquals("OK[41]", outcome("SELECT HEX_DECODE_BINARY('41') AS x FROM rs"));
    }
}
