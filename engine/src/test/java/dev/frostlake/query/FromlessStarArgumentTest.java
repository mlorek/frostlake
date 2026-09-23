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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * With no FROM, a star written as a function argument stands for no column, whatever it qualifies or filters,
 * and the call is judged by what is left: {@code HASH(fz.*)} is refused for its arity and
 * {@code ARRAY_CONSTRUCT(1, fz.*)} is {@code [1]}. Only COUNT's bare star counts rows; any other star that
 * stands for too few columns, over a GENERATOR's columnless rows too, is refused for its arity. Frostlake
 * refused the qualifier as a missing object, answered the unqualified star, and let a bare star under any
 * aggregate through (live-verified).
 */
public class FromlessStarArgumentTest extends BaseDatabaseTest {

    private static final String AT_7 = "SQL compilation error: error line 1 at position 7\n";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    @Test
    public void aLoneStarArgumentWithNoFromIsRefusedForItsArity() {
        final String hash = "not enough arguments for function [HASH()], expected 1, got 0";
        final String count = "not enough arguments for function [COUNT()], expected 1, got 0";
        assertEquals(AT_7 + hash, refusal("SELECT HASH(*)"));
        assertEquals(AT_7 + hash, refusal("SELECT HASH(fz.*)"));
        assertEquals(AT_7 + hash, refusal("SELECT HASH(* EXCLUDE (a))"));
        assertEquals(AT_7 + hash, refusal("SELECT HASH(* ILIKE 'a%')"));
        assertEquals(AT_7 + hash, refusal("SELECT HASH(fz.*) WHERE FALSE"));
        assertEquals(AT_7 + "not enough arguments for function [CONCAT()], expected 1, got 0", refusal("SELECT CONCAT(*)"));
        assertEquals(AT_7 + "not enough arguments for function [CONCAT()], expected 1, got 0",
            refusal("SELECT CONCAT(fz.*)"));
        assertEquals(AT_7 + count, refusal("SELECT COUNT(fz.*)"));
        assertEquals(AT_7 + count, refusal("SELECT COUNT(nosuch.*)"));
        assertEquals(AT_7 + count, refusal("SELECT COUNT(dual.*)"));
        assertEquals(AT_7 + count, refusal("SELECT COUNT(* EXCLUDE (a))"));
        // At the call, in a FROM-less subquery of a query over FZ too, and beside a plain star or an aggregate.
        assertEquals("SQL compilation error: error line 1 at position 15\n" + count,
            refusal("SELECT (SELECT COUNT(fz.*)) FROM FZ"));
        assertEquals("SQL compilation error: error line 1 at position 15\n" + hash,
            refusal("SELECT (SELECT HASH(fz.*)) FROM FZ"));
        assertEquals("SQL compilation error: error line 1 at position 10\n" + hash, refusal("SELECT *, HASH(*)"));
        assertEquals("SQL compilation error: error line 1 at position 17\n" + hash,
            refusal("SELECT COUNT(*), HASH(fz.*)"));
        assertEquals("1", rows("SELECT COUNT(*)"));
    }

    @Test
    public void aStarBesideOtherArgumentsWithNoFromIsSplicedAsNothing() {
        assertEquals("1", rows("SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(1, fz.*))"));
        assertEquals("1", rows("SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(fz.*, 1, nosuch.*))"));
        assertEquals("1", rows("SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(dual.*, 1))"));
        assertEquals("0", rows("SELECT ARRAY_SIZE(ARRAY_CONSTRUCT(*))"));
        assertEquals("0", rows("SELECT ARRAY_SIZE(OBJECT_KEYS(OBJECT_CONSTRUCT(fz.*)))"));
        assertEquals("null, 0", rows("SELECT *, ARRAY_SIZE(OBJECT_KEYS(OBJECT_CONSTRUCT_KEEP_NULL(*)))"));
        assertEquals("2", rows("SELECT GREATEST(fz.*, 2)"));
        assertEquals("a", rows("SELECT CONCAT(fz.*, 'a')"));
        assertEquals("true", rows("SELECT HASH(1, fz.*) = HASH(1)"));
        assertEquals("5, true | 7, true", rows("SELECT id, (SELECT HASH(fz.*, 1) = HASH(1)) FROM FZ ORDER BY 1"));
        assertEquals(AT_7 + "not enough arguments for function [NVL(1)], expected 2, got 1", refusal("SELECT NVL(fz.*, 1)"));
        assertEquals(AT_7 + "not enough arguments for function [COALESCE(1)], expected 2, got 1",
            refusal("SELECT COALESCE(*, 1)"));
    }

    @Test
    public void onlyCountsBareStarCountsRows() {
        assertEquals(AT_7 + "not enough arguments for function [MAX()], expected 1, got 0", refusal("SELECT MAX(*)"));
        assertEquals(AT_7 + "not enough arguments for function [HASH_AGG()], expected 1, got 0",
            refusal("SELECT HASH_AGG(*)"));
        assertEquals(AT_7 + "not enough arguments for function [MIN()], expected 1, got 0",
            refusal("SELECT MIN(* EXCLUDE (a))"));
        assertEquals(AT_7 + "not enough arguments for function [ARRAY_AGG()], expected 1, got 0",
            refusal("SELECT ARRAY_AGG(fz.*)"));
        assertEquals(AT_7 + "not enough arguments for function [APPROX_COUNT_DISTINCT()], expected 1, got 0",
            refusal("SELECT APPROX_COUNT_DISTINCT(*)"));
        assertEquals(AT_7 + "not enough arguments for function [ARRAY_AGG(DISTINCT )], expected 1, got 0",
            refusal("SELECT ARRAY_AGG(DISTINCT fz.*)"));
        assertEquals(AT_7 + "not enough arguments for function [HASH_AGG()], expected 1, got 0",
            refusal("SELECT HASH_AGG(fz.*) OVER ()"));
        assertEquals("SQL compilation error: error line 1 at position 25\nnot enough arguments for function "
            + "[HASH_AGG()], expected 1, got 0", refusal("SELECT COUNT(*) OVER (), HASH_AGG(*) OVER ()"));
        // Over a GENERATOR's columnless rows, and over a FROM whose star's filter leaves too few columns.
        final String generator = " FROM TABLE(GENERATOR(ROWCOUNT => 2))";
        assertEquals("2", rows("SELECT COUNT(*)" + generator));
        assertEquals(AT_7 + "not enough arguments for function [HASH_AGG()], expected 1, got 0",
            refusal("SELECT HASH_AGG(*)" + generator));
        assertEquals(AT_7 + "not enough arguments for function [MAX()], expected 1, got 0",
            refusal("SELECT MAX(*)" + generator));
        assertEquals(AT_7 + "not enough arguments for function [HASH()], expected 1, got 0",
            refusal("SELECT HASH(*)" + generator));
        assertEquals(AT_7 + "not enough arguments for function [HASH()], expected 1, got 0",
            refusal("SELECT HASH(* ILIKE 'zz%') FROM FZ"));
        assertEquals(AT_7 + "not enough arguments for function [CONCAT()], expected 1, got 0",
            refusal("SELECT CONCAT(* ILIKE 'zz%') FROM FZ"));
        assertEquals(AT_7 + "not enough arguments for function [NVL(FZ.ID)], expected 2, got 1",
            refusal("SELECT NVL(* ILIKE 'id') FROM FZ"));
        assertEquals(AT_7 + "not enough arguments for function [COUNT(DISTINCT )], expected 1, got 0",
            refusal("SELECT COUNT(DISTINCT * ILIKE 'zz%') FROM FZ"));
        // With a FROM, a qualifier naming none of its relations is still no object.
        assertEquals("SQL compilation error:\nObject 'FZ' does not exist or not authorized.",
            refusal("SELECT COUNT(fz.*) FROM FZ AS x"));
    }
}
