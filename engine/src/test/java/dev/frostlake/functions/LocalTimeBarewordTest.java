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
import dev.frostlake.types.DataType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code LOCALTIME} and {@code LOCALTIMESTAMP} are spelled WITHOUT parentheses, the way
 * {@code CURRENT_DATE} and its neighbours already were. Both were registered as functions, so only
 * the parenthesised calls worked; written bare the words were ordinary identifiers and the column
 * came back a VARCHAR.
 *
 * <p>They are the CURRENT_ family, not the SYSDATE one — {@code LOCALTIMESTAMP} declares
 * TIMESTAMP_LTZ where SYSDATE declares TIMESTAMP_NTZ. Registering it as a "now" function had given it
 * SYSDATE's type even in the parenthesised form, so that was wrong before any of the spelling work.
 *
 * <p>No CLOCK VALUE is asserted here: two engines read two different instants, and the account's
 * session timezone is not this engine's. The types and the resolution are what this pins.
 */
public class LocalTimeBarewordTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE lt (i INT)");
        engine.execute("INSERT INTO lt VALUES (1)");
    }

    /** The declared type of a one-column query. */
    private String typeOf(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        return type == null ? "null" : type.getName();
    }

    /** Whether a statement runs, and what it answers when the answer does not move. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Written bare, each word is its function. */
    @Test
    public void theBarewordsAreTheirFunctions() {
        assertEquals("TIME", typeOf("SELECT LOCALTIME AS x FROM lt"));
        assertEquals("TIMESTAMP_LTZ", typeOf("SELECT LOCALTIMESTAMP AS x FROM lt"));
    }

    /** The parenthesised forms agree, which is what fixed LOCALTIMESTAMP's own type. */
    @Test
    public void theParenthesisedFormsAgree() {
        assertEquals("TIME", typeOf("SELECT LOCALTIME() AS x FROM lt"));
        assertEquals("TIMESTAMP_LTZ", typeOf("SELECT LOCALTIMESTAMP() AS x FROM lt"),
            "the CURRENT_TIMESTAMP family, not SYSDATE's");
    }

    /** They sit beside the neighbours they copy, which must not have moved. */
    @Test
    public void theNeighboursAreUnchanged() {
        assertEquals("TIME", typeOf("SELECT CURRENT_TIME AS x FROM lt"));
        assertEquals("TIMESTAMP_LTZ", typeOf("SELECT CURRENT_TIMESTAMP AS x FROM lt"));
        assertEquals("DATE", typeOf("SELECT CURRENT_DATE AS x FROM lt"));
        assertEquals("TIMESTAMP_NTZ", typeOf("SELECT SYSDATE() AS x FROM lt"),
            "SYSDATE keeps the type LOCALTIMESTAMP used to borrow");
    }

    /** The word BEATS a column of the same name, which is how the account resolves it. */
    @Test
    public void theBarewordWinsOverAColumnOfThatName() {
        assertEquals("TIME", typeOf("SELECT localtime FROM (SELECT 1 AS localtime)"));
        assertEquals("TIMESTAMP_LTZ",
            typeOf("SELECT localtimestamp FROM (SELECT 1 AS localtimestamp)"));
    }

    /** And they stay usable as NAMES everywhere the account still allows them. */
    @Test
    public void theyRemainUsableAsNames() {
        assertEquals("1", outcome("SELECT 1 AS localtime FROM lt"));
        assertEquals("1", outcome("SELECT 1 AS localtimestamp FROM lt"));
        assertEquals("1", outcome("WITH localtime AS (SELECT 1 AS a) SELECT a FROM localtime"));
        assertEquals("1", outcome("SELECT 1 AS a FROM lt AS localtime"));
    }

    /** They evaluate where a value is wanted, not just where a type is read. */
    @Test
    public void theyEvaluateAsValues() {
        assertEquals("1", outcome("SELECT i FROM lt WHERE LOCALTIME IS NOT NULL"));
        assertEquals("1", outcome("SELECT i FROM lt WHERE LOCALTIMESTAMP IS NOT NULL"));
    }
}
