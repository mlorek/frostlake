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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A misspelled keyword MID-STATEMENT anchors on the first token no continuation can use
 * (live-verified across the family). The misspelling itself reads as an ALIAS, the statement ends,
 * and the parser opens a fresh statement at the next bare word — which only an assignment could
 * begin — so live names THAT identifier, not wherever the doomed assignment alternative died.
 *
 * <p>★ WHEN NO ALIAS IS VIABLE, THE MISSPELLING BLAMES ITSELF: after {@code GROUP BY i} nothing can
 * be aliased, so {@code HAVNG} is the first unusable token and is named directly.
 *
 * <p>★ THE CELL NO IMPLEMENTATION MAY BREAK: {@code SELECT i FROM lk WHER} is a VALID statement —
 * WHER is simply the relation's alias — and returns the rows.
 */
public class MidStatementKeywordAnchorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE lk (i INT)");
        engine.execute("INSERT INTO lk VALUES (1), (2)");
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void theDoomedFreshStatementBlamesItsOpeningIdentifier() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected 'i'.",
            refusal("SELECT i FROM lk WHER i = 1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 24 unexpected 'i'.",
            refusal("SELECT i FROM lk QUALFY i > 1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected 'HAVNG'.",
            refusal("SELECT i FROM lk GROUP BY i HAVNG i > 1"));
    }

    @Test
    public void theTruncatedFormAlreadyAnchoredRight() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected 'i'.",
            refusal("SELECT i FROM lk WHER i"));
    }

    @Test
    public void aBareMisspellingIsAValidAlias() {
        assertEquals(2, engine.executeQuery("SELECT i FROM lk WHER").getRows().size());
    }

    @Test
    public void keywordAndLiteralFollowersKeepTheirOwnAnchors() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected 'BY'.",
            refusal("SELECT i FROM lk GROP BY i"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected 'BY'.",
            refusal("SELECT i FROM lk ORDR BY i"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected '1'.",
            refusal("SELECT i FROM lk LIMT 1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 13 unexpected '+'.",
            refusal("SELECT i FRM + 1 FROM lk"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 7 unexpected 'FROM'.",
            refusal("SELECT FROM lk"));
    }

    @Test
    public void theRuleHoldsInsideSubqueriesAndCtes() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 37 unexpected 'i'.",
            refusal("SELECT * FROM (SELECT i FROM lk WHER i = 1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 33 unexpected 'i'.",
            refusal("WITH c AS (SELECT i FROM lk WHER i = 1) SELECT * FROM c"));
    }
}
