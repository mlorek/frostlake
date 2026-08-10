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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An ALIAS after AS takes a WIDER vocabulary than a column reference does. Live accepts
 * {@code SELECT 1 AS case}, {@code AS cast}, {@code AS constraint}, {@code AS default},
 * {@code AS when} and the join words {@code AS cross}, {@code AS inner}, {@code AS join} — eight words
 * that may NOT name a column in an expression, because each of them leads something there.
 *
 * <p>Measured word by word over all 490 keywords the grammar lexes, for the select-item and the
 * table-alias positions, which agree exactly. They cannot simply join the general identifier rule: the
 * expression grammar has to go on reading CASE as the start of a CASE expression.
 */
public class AliasKeywordTest extends BaseDatabaseTest {

    /** The eight words legal as an alias and illegal as a column reference. */
    private static final String[] ALIAS_ONLY = {
        "case", "cast", "constraint", "cross", "default", "inner", "join", "when",
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ak (x NUMBER)");
        engine.execute("INSERT INTO ak VALUES (7)");
    }

    /** Every one of them names a SELECT item, and the column comes back under that name. */
    @Test
    public void eachIsALegalSelectItemAlias() {
        for (final String word : ALIAS_ONLY) {
            final ResultSet rs = engine.executeQuery("SELECT x AS " + word + " FROM ak");
            rs.next();
            assertEquals(7, ((Number) rs.getValue(word)).intValue(),
                word + " should name the select item");
        }
    }

    /** And every one of them names a TABLE after AS. */
    @Test
    public void eachIsALegalTableAlias() {
        for (final String word : ALIAS_ONLY) {
            final ResultSet rs = engine.executeQuery("SELECT x FROM ak AS " + word);
            rs.next();
            assertEquals(7, ((Number) rs.getValue("x")).intValue(),
                word + " should name the table");
        }
    }

    /** The alias really is the name — a qualified reference through it resolves. */
    @Test
    public void theTableAliasQualifiesAColumn() {
        final ResultSet rs = engine.executeQuery("SELECT inner.x FROM ak AS inner");
        rs.next();
        assertEquals(7, ((Number) rs.getValue("x")).intValue());
    }

    /** An ordinary alias still works, and so does the ordinary keyword-as-name vocabulary. */
    @Test
    public void theOrdinaryAliasesStillWork() {
        final ResultSet plain = engine.executeQuery("SELECT x AS c FROM ak AS t");
        plain.next();
        assertEquals(7, ((Number) plain.getValue("c")).intValue());
        final ResultSet keyword = engine.executeQuery("SELECT x AS auto FROM ak AS limit");
        keyword.next();
        assertEquals(7, ((Number) keyword.getValue("auto")).intValue());
    }

    /** A reserved word is still refused as an alias — the fix widened the set, it did not remove it. */
    @Test
    public void aReservedWordIsStillRefusedAsAnAlias() {
        for (final String reserved : new String[]{"select", "from", "where", "order"}) {
            String outcome = "accepted";
            try {
                engine.executeQuery("SELECT x AS " + reserved + " FROM ak");
            } catch (final RuntimeException refused) {
                outcome = "refused";
            }
            assertEquals("refused", outcome, reserved + " is reserved and must stay refused");
        }
    }
}
