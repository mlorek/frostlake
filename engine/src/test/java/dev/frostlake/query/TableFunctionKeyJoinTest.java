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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A table function joined with USING or NATURAL. One reading the left side is judged before it runs: a USING column
 * either side lacks, in list order, is {@code Invalid identifier <COL>} at once, and a key both sides carry is the
 * lateral restriction a join predicate meets, raised after the rest of the statement. One reading nothing of the left
 * side joins by its key as any relation does.
 */
public class TableFunctionKeyJoinTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported feature 'lateral table function called with OUTER JOIN "
        + "syntax or a join predicate (ON clause)'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nc (s VARCHAR)");
        engine.execute("CREATE TABLE fc (s VARCHAR, value INT)");
        engine.execute("CREATE TABLE nc2 (s VARCHAR, k INT)");
    }

    private void populate() {
        engine.execute("INSERT INTO nc VALUES ('[1]')");
        engine.execute("INSERT INTO fc VALUES ('[1]', 1)");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private static String invalid(final String column) {
        return "SQL compilation error:\nInvalid identifier " + column;
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void aUsingColumnTheLeftLacksIsRefusedBeforeTheFunctionRuns() {
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN LATERAL FLATTEN(INPUT => PARSE_JSON(s)) f USING (value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc LEFT JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(invalid("VALUE"), refusal("SELECT COUNT(*) FROM nc JOIN TABLE(SPLIT_TO_TABLE(s, ',')) f USING (value)"));
        populate();
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN LATERAL FLATTEN(INPUT => PARSE_JSON(s)) f USING (value)"));
    }

    @Test
    public void theFirstListedColumnEitherSideLacksIsNamed() {
        assertEquals(invalid("S"), refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (s)"));
        assertEquals(invalid("NOSUCH"),
            refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (nosuch)"));
        assertEquals(invalid("K"),
            refusal("SELECT COUNT(*) FROM nc2 JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (k, value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc2 JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value, k)"));
        assertEquals(invalid("NOSUCH"),
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value, nosuch)"));
        assertEquals(invalid("NOSUCH"),
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (nosuch, value)"));
        assertEquals(invalid("value"),
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (\"value\")"));
        assertEquals(invalid("SEQ"), refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (seq)"));
    }

    @Test
    public void aKeyBothSidesCarryMeetsTheLateralRestriction() {
        populate();
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc JOIN LATERAL FLATTEN(INPUT => PARSE_JSON(s)) f USING (value)"));
        assertEquals(UNSUPPORTED,
            refusal("SELECT COUNT(*) FROM fc LEFT JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc JOIN TABLE(SPLIT_TO_TABLE(s, ',')) f USING (value)"));
        assertEquals(UNSUPPORTED,
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(fc.s))) f USING (value)"));
        assertEquals(UNSUPPORTED,
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s), OUTER => TRUE)) f USING (value)"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (VALUE)"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (f.value)"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT COUNT(*) FROM fc f1 JOIN fc f2 USING (value) JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(f1.s))) f USING (value)"));
    }

    @Test
    public void aNaturalJoinOverACommonColumnOrAnOuterNaturalJoinToo() {
        populate();
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc NATURAL JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM fc NATURAL JOIN LATERAL FLATTEN(INPUT => PARSE_JSON(s)) f"));
        assertEquals(UNSUPPORTED, refusal("SELECT COUNT(*) FROM nc NATURAL LEFT JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f"));
        assertEquals(1L, count("SELECT COUNT(*) FROM nc NATURAL JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f"));
        assertEquals(1L, count("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f"));
    }

    @Test
    public void theRestrictionIsTheStatementsLastWord() {
        populate();
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusal("SELECT nosuch FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals("SQL compilation error: error line 1 at position 90\ninvalid identifier 'NOSUCH'",
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) WHERE nosuch = 1"));
        assertEquals("SQL compilation error: error line 1 at position 95\ninvalid identifier 'NC.NOSUCH'", refusal(
            "SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) JOIN nc ON nc.nosuch = 1"));
        assertEquals(invalid("NOSUCH"), refusal(
            "SELECT COUNT(*) FROM fc NATURAL JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f JOIN nc USING (nosuch)"));
        assertEquals(hinted("SQL compilation error:\nObject 'NOSUCHTABLE' does not exist or not authorized."), refusal(
            "SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) JOIN nosuchtable n ON TRUE"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.", refusal(
            "SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) WHERE NOSUCHFN(1) = 1"));
        assertEquals(UNSUPPORTED, refusal(
            "SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) JOIN nc USING (s)"));
    }

    @Test
    public void aMissingKeyIsRefusedAheadOfEveryClausesNames() {
        assertEquals(invalid("VALUE"),
            refusal("SELECT nosuch FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(s))) f USING (value) WHERE nosuch = 1"));
        assertEquals("SQL compilation error: error line 1 at position 63\ninvalid identifier 'NOSUCH'",
            refusal("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON(nosuch))) f USING (value)"));
    }

    @Test
    public void aFunctionReadingNothingOfTheLeftJoinsByItsKey() {
        populate();
        assertEquals(1L, count("SELECT COUNT(*) FROM fc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))) f USING (value)"));
        assertEquals(0L, count("SELECT COUNT(*) FROM fc NATURAL JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[2]'))) f"));
        assertEquals(1L, count("SELECT COUNT(*) FROM fc NATURAL JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))) f"));
        assertEquals(0L, count("SELECT COUNT(*) FROM fc JOIN LATERAL FLATTEN(INPUT => PARSE_JSON('[2]')) f USING (value)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM fc JOIN LATERAL FLATTEN(INPUT => PARSE_JSON('[1]')) f USING (value)"));
        assertEquals(0L, count("SELECT COUNT(*) FROM fc NATURAL JOIN LATERAL FLATTEN(INPUT => PARSE_JSON('[2]')) f"));
        assertEquals(1L, count("SELECT COUNT(*) FROM fc LEFT JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[2]'))) f USING (value)"));
        assertEquals(1L, count("SELECT COUNT(*) FROM fc JOIN TABLE(SPLIT_TO_TABLE('1,2', ',')) f USING (value)"));
        assertEquals(invalid("VALUE"), refusal("SELECT COUNT(*) FROM nc JOIN TABLE(GENERATOR(ROWCOUNT => 3)) g USING (value)"));
        assertEquals(invalid("VALUE"),
            refusal("SELECT COUNT(*) FROM nc JOIN TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))) f USING (value)"));
    }
}
