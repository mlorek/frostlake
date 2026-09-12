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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WHERE a JSON fault is reported, once the document does not start at its first character.
 *
 * <p>★ THE OFFSET IS INTO THE TEXT AS WRITTEN. Frostlake trims before parsing, and used to report the
 * offset into the TRIMMED document — so the sentence and the keyword were right and only the number was
 * short, by exactly the amount trimmed. Live counts the leading whitespace.
 *
 * <p>★ A NEWLINE ADDS A CLAUSE AND RESETS THE COLUMN. Past the first line the sentence gains
 * {@code line N,} and the position counts from that line's start, not the document's — so {@code \ncdefg}
 * is line 2 pos 6, the same column the unprefixed document reports.
 *
 * <p>★ TRAILING whitespace moves nothing, which is the control: it is only the text BEFORE the fault
 * that counts.
 */
public class JsonFaultPositionTest extends BaseDatabaseTest {

    private String faultOf(final String document) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('" + document + "')");
            }
        });
        return String.valueOf(e.getMessage());
    }

    private String checked(final String document) {
        final ResultSet rs = engine.executeQuery("SELECT CHECK_JSON('" + document + "')");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    void aDocumentAtTheStartIsUnchanged() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 6", faultOf("cdefg"));
    }

    @Test
    void leadingSpacesAreCounted() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 7", faultOf(" cdefg"));
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 8", faultOf("  cdefg"));
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 10", faultOf("    cdefg"));
    }

    @Test
    void aLeadingTabCountsAsOneCharacter() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 7", faultOf("\tcdefg"));
    }

    @Test
    void aNewlineAddsTheLineClauseAndResetsTheColumn() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", line 2, pos 6", faultOf("\ncdefg"));
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", line 3, pos 6", faultOf("\n\ncdefg"));
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", line 2, pos 8", faultOf("\n  cdefg"));
    }

    @Test
    void trailingWhitespaceMovesNothing() {
        assertEquals("Error parsing JSON: unknown keyword \"cdefg\", pos 6", faultOf("cdefg  "));
    }

    @Test
    void theOtherFaultsShiftTheSameWay() {
        assertEquals("Error parsing JSON: misplaced }, pos 6", faultOf("{\"a\":}"));
        assertEquals("Error parsing JSON: misplaced }, pos 8", faultOf("  {\"a\":}"));
        assertEquals("Error parsing JSON: incomplete array value, pos 6", faultOf("  [1,"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1x , pos 5", faultOf("  1x"));
    }

    @Test
    void checkJsonReportsTheSamePlaceWithoutThePrefix() {
        assertEquals("unknown keyword \"cdefg\", pos 8", checked("  cdefg"));
        assertEquals("unknown keyword \"cdefg\", line 2, pos 6", checked("\ncdefg"));
    }
}
