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
 * Documents PARSE_JSON reads that strict JSON does not. Snowflake's reader is lenient in specific,
 * measured places, and the point of this file is that the leniency is NOT uniform — so the flags are
 * named one at a time and the neighbours that stay REFUSED are asserted beside them.
 *
 * <p>★ THE NEIGHBOUR THAT PINS IT is {@code {$a$:1}}. Turning on Jackson's unquoted-property-name
 * feature reads {@code {a:1}}, which live does accept — but it also reads {@code {$a$:1}}, which live
 * REFUSES. Accepting a document live rejects is the worse of the two errors, so the feature stays off
 * and {@code {a:1}} stays a known divergence rather than being bought at that price.
 *
 * <p>A SINGLE-quoted key is a different matter and is accepted here, because live accepts it — which
 * is the opposite of what the note that filed this work assumed, and only measuring said so.
 */
public class ParseJsonReaderLeniencyTest extends BaseDatabaseTest {

    private String json(final String document) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT PARSE_JSON(" + document + ")");
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A number written the way JSON forbids and Snowflake allows. */
    @Test
    public void theLenientNumberSpellings() {
        assertEquals("1", json("'01'"), "a leading zero");
        assertEquals("1", json("'+1'"), "a leading plus");
        assertEquals("1", json("'1.'"), "a trailing decimal point");
        assertEquals("0.5", json("'.5'"), "and a leading one, which gains its zero");
    }

    /** A SINGLE-quoted key reads, and its output is normalised to double quotes. */
    @Test
    public void aSingleQuotedKeyReads() {
        assertEquals("{\"a\":1}", json("'{''a'':1}'"));
    }

    /** ★ The neighbours that stay REFUSED, which is what keeps the leniency honest. */
    @Test
    public void theNeighboursThatStayRefused() {
        assertEquals("Error parsing JSON: invalid character outside of a string: '$', pos 2",
            json("'{$a$:1}'"),
            "a dollar-delimited key is refused live, so the wider unquoted-name feature stays off");
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 6", json("'cdefg'"));
        assertEquals("Error parsing JSON: incomplete object value, pos 7", json("'{\"a\":1'"));
        assertEquals("Error parsing JSON: garbage after valid input document",
            json("'{\"a\":1} x'"));
    }

    /** Ordinary documents are untouched — the leniency only runs after a strict parse has failed. */
    @Test
    public void ordinaryDocumentsAreUnchanged() {
        assertEquals("{\"a\":1}", json("'{\"a\":1}'"));
        assertEquals("[1,2]", json("'[1,2]'"));
        assertEquals("1.5", json("'1.5'"));
        assertEquals("null", json("'null'"));
        assertEquals("true", json("'true'"));
    }
}
