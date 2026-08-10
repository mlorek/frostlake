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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PARSE_JSON's parameter is a VARCHAR, so a VARIANT argument is coerced to VARCHAR before the text is
 * read — and coercing a variant STRING hands over its raw content, without the JSON quotes. That one
 * rule decides a pair which otherwise looks contradictory:
 *
 * <pre>
 * PARSE_JSON(PARSE_JSON('{"k":"[1,2]"}'):k)  -&gt; ARRAY    a VARIANT argument, coerced to [1,2]
 * PARSE_JSON('"[1,2,3]"')                    -&gt; VARCHAR  a VARCHAR whose own text carries the quotes
 * </pre>
 *
 * <p>Frostlake used to decide from the CONTENT instead — any quoted string whose inside looked like
 * JSON was unwrapped — so the second answered an ARRAY, and a string that merely reads as JSON stopped
 * being a string. The two values are identical once evaluated, because path extraction hands back the
 * quoted text, so the argument's DECLARED type is the only thing that can tell them apart.
 */
public class ParseJsonArgumentTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE js_t (js VARCHAR(50), v VARIANT)");
        engine.execute("INSERT INTO js_t SELECT '[1,2]', PARSE_JSON('{\"k\":\"[3,4]\"}')");
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** The bug this class was written for: a quoted string stays a string, whatever it reads like. */
    @Test
    public void aQuotedJsonStringStaysAString() {
        assertEquals("VARCHAR", one("SELECT TYPEOF(PARSE_JSON('\"[1,2,3]\"'))"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(PARSE_JSON('\"{}\"'))"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(PARSE_JSON('\"abc\"'))"));
        assertEquals("VARCHAR", one("SELECT TYPEOF(TRY_PARSE_JSON('\"[1,2,3]\"'))"));
    }

    /** Unquoted text is the JSON itself, so the same content read without quotes IS the container. */
    @Test
    public void unquotedTextIsTheJsonItself() {
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON('[1,2,3]'))"));
        assertEquals("OBJECT", one("SELECT TYPEOF(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("INTEGER", one("SELECT TYPEOF(PARSE_JSON('7'))"));
    }

    /**
     * A VARIANT argument is coerced to VARCHAR first, which unwraps a variant string — so an embedded
     * JSON document held as a string parses to its container. This is the shape the old content guess
     * existed to serve, and it must keep working.
     */
    @Test
    public void aVariantStringIsCoercedToItsContentFirst() {
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('{\"k\":\"[1,2]\"}'):k))"));
        assertEquals("OBJECT",
            one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('{\"k\":\"{\\\\\"a\\\\\":1}\"}'):k))"));
        assertEquals("ARRAY", one("SELECT TYPEOF(TRY_PARSE_JSON(PARSE_JSON('{\"k\":\"[1,2]\"}'):k))"));
        // and through a column, which is how it is actually written
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON(v:k)) FROM js_t"));
    }

    /** A VARCHAR COLUMN holding JSON text parses to its container — its text carries no quotes. */
    @Test
    public void aVarcharColumnHoldingJsonParsesToItsContainer() {
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON(js)) FROM js_t"));
    }

    /** A VARIANT that is not a string contributes its own JSON text, so re-parsing is a no-op. */
    @Test
    public void reparsingANonStringVariantKeepsIt() {
        assertEquals("ARRAY", one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('[1,2]')))"));
        assertEquals("OBJECT", one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('{\"a\":1}')))"));
        assertEquals("INTEGER", one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('7')))"));
    }

    /**
     * The JSON null coerces to SQL NULL on its way to VARCHAR, and PARSE_JSON(NULL) is NULL — so
     * re-parsing it gives SQL NULL rather than the JSON null a second time. (PARSE_JSON('null') on its
     * own is still the JSON null; it is the coercion that flattens it.)
     */
    @Test
    public void reparsingTheJsonNullYieldsSqlNull() {
        assertNull(one("SELECT PARSE_JSON(PARSE_JSON('null'))"));
        assertNull(one("SELECT TYPEOF(PARSE_JSON(PARSE_JSON('null')))"));
        assertNull(one("SELECT TRY_PARSE_JSON(PARSE_JSON('null'))"));
        assertEquals("NULL_VALUE", one("SELECT TYPEOF(PARSE_JSON('null'))"));
    }
}
