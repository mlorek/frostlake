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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A word written after a bare SHOW scope makes it a class-instance scope, {@code IN <class> <instance>}. No
 * class exists, so each listing refuses it in its own words: TABLES, SCHEMAS and STAGES with a syntax error
 * line carrying no prefix, FUNCTIONS and PROCEDURES by resolving the class, every other listing as a kind it
 * cannot show in an instance.
 */
public class ShowClassInstanceScopeTest extends BaseDatabaseTest {

    /** Every row's cells, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValues());
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String unprefixed(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|" + unprefixed(1, position, token);
    }

    private static String inInstance(final String kind) {
        return "SQL compilation error:|Unsupported statement type 'Cannot show objects of type " + kind
            + " in INSTANCE'.";
    }

    private static String noClass(final String name) {
        return "SQL compilation error: Object type or Class '" + name + "' does not exist or not authorized.";
    }

    @Test
    public void tablesSchemasAndStagesRefuseTheClassNameAsWritten() {
        assertEquals(unprefixed(1, 15, "abc"), answer("SHOW TABLES IN abc y"));
        assertEquals(unprefixed(1, 15, "abc"), answer("SHOW TABLES IN abc \"y\""));
        assertEquals(unprefixed(1, 21, "abc"), answer("SHOW TERSE TABLES IN abc y"));
        assertEquals(unprefixed(1, 16, "a.b"), answer("SHOW SCHEMAS IN a.b y"));
        assertEquals(unprefixed(1, 15, "a.b.c"), answer("SHOW STAGES IN a.b.c y"));
        assertEquals(unprefixed(1, 15, "\"a b\".c"), answer("SHOW TABLES IN \"a b\".c y"));
        assertEquals(unprefixed(1, 15, "\"T\""), answer("SHOW TABLES IN \"T\" y"));
        assertEquals(unprefixed(1, 15, "a"), answer("SHOW TABLES IN a..c y"));
        assertEquals(unprefixed(1, 26, "TOK_OBJECT_LITERAL"), answer("SHOW TABLES IN IDENTIFIER('abc') y"));
        assertEquals(unprefixed(1, 24, "abc"), answer("SHOW TABLES LIKE 'x' IN abc y"));
        assertEquals(unprefixed(1, 15, "abc"), answer("SHOW TABLES IN abc LIMIT"));
        assertEquals(unprefixed(1, 15, "abc"), answer("SHOW TABLES IN abc y LIMIT 1"));
        assertEquals(unprefixed(1, 15, "abc"), answer("SHOW TABLES IN abc y STARTS WITH 'a'"));
        assertEquals(unprefixed(2, 5, "abc"), answer("""
            SHOW TABLES
              IN abc y"""));
        assertEquals(syntax(21, "z"), answer("SHOW TABLES IN abc y z"));
        assertEquals(syntax(32, "y"), answer("SHOW TABLES IN DATABASE test_db y"));
    }

    @Test
    public void otherListingsCannotShowTheirKindInAnInstance() {
        assertEquals(inInstance("VIEW"), answer("SHOW VIEWS IN abc y"));
        assertEquals(inInstance("VIEW"), answer("SHOW TERSE VIEWS IN a..c y LIMIT 1"));
        assertEquals(inInstance("MATERIALIZED VIEW"), answer("SHOW MATERIALIZED VIEWS IN abc y"));
        assertEquals(inInstance("DYNAMIC TABLE"), answer("SHOW DYNAMIC TABLES IN abc y"));
        assertEquals(inInstance("ICEBERG TABLE"), answer("SHOW ICEBERG TABLES IN abc y"));
        assertEquals(inInstance("KEY VALUE TABLE"), answer("SHOW HYBRID TABLES IN abc y"));
        assertEquals(inInstance("COLUMN"), answer("SHOW COLUMNS IN abc y"));
        assertEquals(inInstance("COLUMN"), answer("SHOW COLUMNS IN a.b.c.d y"));
        assertEquals(inInstance("STREAM"), answer("SHOW STREAMS IN abc y"));
        assertEquals(inInstance("TASK"), answer("SHOW TASKS IN abc y"));
        assertEquals(inInstance("PIPE"), answer("SHOW PIPES IN abc y"));
        assertEquals(inInstance("SEQUENCE"), answer("SHOW SEQUENCES IN abc y"));
        assertEquals(inInstance("CORTEX SEARCH SERVICE"), answer("SHOW CORTEX SEARCH SERVICES IN abc y"));
        assertEquals(inInstance("FILE FORMAT"), answer("SHOW FILE FORMATS IN abc y"));
        assertEquals(inInstance("TAG"), answer("SHOW TAGS IN abc y"));
        assertEquals(inInstance("MASKING POLICY"), answer("SHOW MASKING POLICIES IN abc y"));
        assertEquals(inInstance("ROW ACCESS POLICY"), answer("SHOW ROW ACCESS POLICIES IN abc y"));
        assertEquals(inInstance("PROJECTION POLICY"), answer("SHOW PROJECTION POLICIES IN abc y"));
        assertEquals(inInstance("AGGREGATION POLICY"), answer("SHOW AGGREGATION POLICIES IN abc y"));
        assertEquals(inInstance("JOIN POLICY"), answer("SHOW JOIN POLICIES IN abc y"));
        assertEquals(inInstance("CONTACT"), answer("SHOW CONTACTS IN abc y"));
        assertEquals(inInstance("OBJECT"), answer("SHOW OBJECTS IN abc y"));
        assertEquals(inInstance("CONSTRAINT"), answer("SHOW PRIMARY KEYS IN abc y"));
        assertEquals(inInstance("CONSTRAINT"), answer("SHOW IMPORTED KEYS IN abc LIMIT"));
    }

    @Test
    public void routineListingsResolveTheClass() {
        assertEquals(noClass("ABC"), answer("SHOW FUNCTIONS IN abc y"));
        assertEquals(noClass("\"abc\""), answer("SHOW USER FUNCTIONS IN \"abc\" y"));
        assertEquals(noClass("ABC"), answer("SHOW BUILTIN FUNCTIONS IN abc y"));
        assertEquals(noClass("ABC"), answer("SHOW PROCEDURES IN IDENTIFIER('abc') y"));
        assertEquals(noClass("TEST_DB.TEST_SCHEMA.CLS"), answer("SHOW FUNCTIONS IN test_schema.cls y"));
        assertEquals(noClass("TEST_DB.TEST_SCHEMA.CLS"), answer("SHOW PROCEDURES IN test_db.test_schema.cls y"));
        assertEquals(noClass("TEST_DB.PUBLIC.CLS"), answer("SHOW FUNCTIONS IN test_db..cls y"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSUCH' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN nosuch.cls y"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCHDB' does not exist or not authorized."),
            answer("SHOW FUNCTIONS IN nosuchdb.s.cls y"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("SHOW PROCEDURES IN a.b.c.d y"));
    }

    @Test
    public void aKeywordOrAnEscapeWordIsNoClassName() {
        assertEquals(syntax(20, "y"), answer("SHOW TABLES IN USER y"));
        assertEquals(syntax(20, "y"), answer("SHOW TABLES IN DATA y"));
        assertEquals(syntax(17, "y"), answer("SHOW TABLES IN T y"));
        assertEquals(syntax(17, "y"), answer("SHOW TABLES IN t y"));
        assertEquals(syntax(18, "y"), answer("SHOW TABLES IN TS y"));
        assertEquals(syntax(21, "y"), answer("SHOW TABLES IN ALERT y"));
        assertEquals(syntax(18, "Y"), answer("SHOW COLUMNS IN T Y"));
        assertEquals(syntax(23, "y"), answer("SHOW FUNCTIONS IN DATA y"));
        assertEquals(unprefixed(1, 15, "USER.x"), answer("SHOW TABLES IN USER.x y"));
        assertEquals(unprefixed(1, 15, "D.x"), answer("SHOW TABLES IN D.x y"));
    }

    @Test
    public void aCountAfterLimitKeepsTheScopePlain() {
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("SHOW TABLES IN abc LIMIT 1"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            answer("SHOW TABLES IN abc STARTS WITH 'a'"));
    }

    @Test
    public void aScopeWithAnEmptyPartTakesItsNextWordForTheInstance() {
        assertEquals(syntax(33, "1"), answer("SHOW TABLES IN P440C_DB..X LIMIT 1"));
        assertEquals(syntax(33, "1"), answer("SHOW TABLES IN P440C_DB..X LIMIT 1 FROM 'a'"));
        assertEquals(syntax(39, "'A'"), answer("SHOW TABLES IN P440C_DB..X STARTS WITH 'A'"));
        assertEquals(syntax(27, "LIKE"), answer("SHOW TABLES IN P440C_DB..X LIKE 'A'"));
        assertEquals(unprefixed(1, 15, "P440C_DB"), answer("SHOW TABLES IN P440C_DB..X Y"));
        assertEquals(unprefixed(1, 16, "P440C_DB"), answer("SHOW SCHEMAS IN P440C_DB..X Y"));
        assertEquals(syntax(29, "Z"), answer("SHOW TABLES IN P440C_DB..X Y Z"));
        assertEquals(inInstance("COLUMN"), answer("SHOW COLUMNS IN P440B_DB..T X"));
        assertEquals(inInstance("VIEW"), answer("SHOW VIEWS IN P440C_DB..X Y"));
    }
}
