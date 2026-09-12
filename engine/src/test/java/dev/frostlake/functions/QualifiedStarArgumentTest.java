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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A star ARGUMENT in every spelling the account takes (live-verified cell by cell): qualified
 * ({@code COUNT(t.*)}), filtered ({@code EXCLUDE}, {@code ILIKE}), windowed, DISTINCT, beside a
 * derived table or a CTE — and the two spellings it refuses, RENAME and REPLACE.
 *
 * <p>★ ONLY THE BARE STAR COUNTS ROWS. {@code COUNT(*)} is the row count; {@code COUNT(t.*)},
 * {@code COUNT(* EXCLUDE (a))}, {@code COUNT(* ILIKE '%')} and the written-out {@code COUNT(a, b, c)}
 * count the rows in which EVERY listed column is non-NULL — over (1, 2, 3), (4, NULL, 6) and
 * (NULL, NULL, NULL) that is 3 for the bare star and 1 for the rest.
 *
 * <p>★ A QUALIFIER NAMES ONE RELATION: over a join {@code HASH_AGG(sa.*)} hashes sa's columns only,
 * an alias hides the base name ({@code COUNT(sa.*) FROM sa AS t} is "Object 'SA' does not exist"),
 * and a database- or schema-qualified spelling resolves by its last part.
 *
 * <p>★ THE HASH VALUES ARE ASSERTED AS RELATIONS, never pinned: Frostlake's HASH is its own.
 */
public class QualifiedStarArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sa (a INT, b INT, c INT)");
        engine.execute("INSERT INTO sa VALUES (1, 2, 3), (4, NULL, 6), (NULL, NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE sb (a INT, d INT)");
        engine.execute("INSERT INTO sb VALUES (1, 7), (4, 8)");
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void onlyTheBareStarCountsRows() {
        assertEquals("3", cell("SELECT COUNT(*) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(sa.*) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(t.*) FROM sa t"));
        assertEquals("1", cell("SELECT COUNT(SA.*) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(\"SA\".*) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(a, b) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(a, b, c) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(* EXCLUDE (a)) FROM sa"));
        assertEquals("2", cell("SELECT COUNT(* EXCLUDE (b)) FROM sa"));
        assertEquals("2", cell("SELECT COUNT(* EXCLUDE b) FROM sa"));
        assertEquals("2", cell("SELECT COUNT(* ILIKE 'a') FROM sa"));
        assertEquals("1", cell("SELECT COUNT(* ILIKE '%') FROM sa"));
        assertEquals("1", cell("SELECT COUNT(sa.* ILIKE 'b') FROM sa"));
        assertEquals("1", cell("SELECT COUNT(DISTINCT sa.*) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(DISTINCT a, b) FROM sa"));
        assertEquals("1", cell("SELECT COUNT(d.*) FROM (SELECT a, b FROM sa) d"));
        assertEquals("1", cell("WITH w AS (SELECT a, b FROM sa) SELECT COUNT(w.*) FROM w"));
        assertEquals("1", cell("SELECT COUNT(* EXCLUDE (a)) FROM sa WHERE c IS NOT NULL"));
        assertEquals("1,0,0", cell("SELECT LISTAGG(cnt, ',') WITHIN GROUP (ORDER BY c)"
            + " FROM (SELECT c, COUNT(* EXCLUDE (a)) AS cnt FROM sa GROUP BY c)"));
        // Over a comma join the qualified star still counts sa's whole tuples, once per joined row.
        assertEquals("2", cell("SELECT COUNT(sa.*) FROM sa, sb"));
        assertEquals("6", cell("SELECT COUNT(*) FROM sa, sb"));
    }

    @Test
    public void aQualifiedOrFilteredStarIsTheWrittenOutList() {
        assertEquals("true", cell("SELECT HASH_AGG(sa.*) = HASH_AGG(*) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(sa.*) = HASH_AGG(a, b, c) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(sa.*) = HASH_AGG(sa.a, sa.b, sa.c) FROM sa JOIN sb ON sa.a = sb.a"));
        assertEquals("true", cell("SELECT HASH_AGG(*) = HASH_AGG(sa.a, sa.b, sa.c, sb.a, sb.d) FROM sa JOIN sb ON sa.a = sb.a"));
        assertEquals("true", cell("SELECT HASH_AGG(* EXCLUDE (a)) = HASH_AGG(b, c) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(sa.* EXCLUDE (a)) = HASH_AGG(b, c) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(* ILIKE '%a%') = HASH_AGG(a) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(sa.* ILIKE 'b') = HASH_AGG(b) FROM sa"));
        assertEquals("true", cell("SELECT HASH_AGG(DISTINCT sa.*) = HASH_AGG(DISTINCT a, b, c) FROM sa"));
        assertTrue(cell("SELECT HASH_AGG(* ILIKE '%a%') FROM sa").matches("-?[0-9]+"));
    }

    @Test
    public void theWindowedFormsExpandTheSameWay() {
        assertEquals("1", cell("SELECT COUNT(sa.*) OVER () FROM sa LIMIT 1"));
        assertEquals("1", cell("SELECT COUNT(* EXCLUDE (a)) OVER () FROM sa LIMIT 1"));
        assertEquals("1", cell("SELECT COUNT(sa.* EXCLUDE (a)) OVER () FROM sa LIMIT 1"));
        assertEquals("3", cell("SELECT COUNT(*) OVER () FROM sa LIMIT 1"));
        assertEquals("true", cell("SELECT HASH_AGG(sa.*) OVER () = HASH_AGG(a, b, c) OVER () FROM sa LIMIT 1"));
        assertEquals("true", cell("SELECT HASH_AGG(* EXCLUDE (a)) OVER () = HASH_AGG(b, c) OVER () FROM sa LIMIT 1"));
    }

    @Test
    public void theExpandedListIsWhatTheArityRuleJudges() {
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [ARRAY_AGG(SA.A, SA.B, SA.C)] expected 1, got 3", refusal("SELECT ARRAY_AGG(sa.*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [SUM(SA.A, SA.B, SA.C)] expected 1, got 3", refusal("SELECT SUM(sa.*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [MIN(SA.A, SA.B, SA.C)] expected 1, got 3", refusal("SELECT MIN(sa.*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [OBJECT_AGG(SA.A, SA.B, SA.C)] expected 2, got 3", refusal("SELECT OBJECT_AGG(sa.*) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [COUNT()], expected 1, got 0", refusal("SELECT COUNT(* EXCLUDE (a, b, c)) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [HASH_AGG()], expected 1, got 0", refusal("SELECT HASH_AGG(* EXCLUDE (a, b, c)) FROM sa"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [COUNT()], expected 1, got 0", refusal("SELECT COUNT(* ILIKE 'zz') FROM sa"));
        assertEquals("[3,6]", cell("SELECT ARRAY_AGG(* EXCLUDE (a, b)) FROM sa"));
        assertEquals("[3,6]", cell("SELECT ARRAY_AGG(sa.* EXCLUDE (a, b)) FROM sa"));
    }

    @Test
    public void theQualifierAndTheFiltersAreChecked() {
        assertEquals("SQL compilation error:\nObject 'SB' does not exist or not authorized.",
            refusal("SELECT COUNT(sb.*) FROM sa"));
        assertEquals("SQL compilation error:\nObject 'SA' does not exist or not authorized.",
            refusal("SELECT COUNT(sa.*) FROM sa AS t"));
        assertEquals("SQL compilation error:\ncolumn 'ZZ' does not exist",
            refusal("SELECT COUNT(* EXCLUDE (zz)) FROM sa"));
        assertEquals("SQL compilation error:\nduplicate column name 'A'",
            refusal("SELECT COUNT(* EXCLUDE (a, a)) FROM sa"));
    }

    @Test
    public void renameAndReplaceAreASelectItemsModifiersNotAnArguments() {
        assertTrue(refusal("SELECT HASH_AGG(* RENAME (a AS z)) FROM sa")
            .contains("syntax error line 1 at position 18 unexpected 'RENAME'."));
        assertTrue(refusal("SELECT HASH_AGG(* REPLACE (a + 1 AS a)) FROM sa")
            .contains("syntax error line 1 at position 18 unexpected 'REPLACE'."));
        assertTrue(refusal("SELECT COUNT(* RENAME (a AS z)) FROM sa")
            .contains("syntax error line 1 at position 15 unexpected 'RENAME'."));
        assertTrue(refusal("SELECT HASH_AGG(* EXCLUDE (a) RENAME (b AS z)) FROM sa")
            .contains("syntax error line 1 at position 30 unexpected 'RENAME'."));
    }
}
