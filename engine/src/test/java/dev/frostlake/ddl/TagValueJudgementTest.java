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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * The value a tag is set to, in SET TAG and in a creation-time TAG list. More than a string sets a tag — a dollar
 * string, a hex literal, a keyword, a quoted or qualified name and a text session variable, each stored as live
 * stores it — and what sets none is refused while the statement compiles, every value in the order written and
 * before the object or the tag is looked up: a number or a boolean, a list or an IDENTIFIER() as an invalid value,
 * a NULL, a plain name, a context function, a bind variable or a numeric session variable as an unsupported data
 * type naming the tag. Every cell is live-verified.
 */
public class TagValueJudgementTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error: Unsupported value data type for tag TG. Only string is supported.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TAG tg");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalid(final String rendered) {
        return "SQL compilation error:|invalid value [" + rendered + "] for parameter 'tagValue'";
    }

    /** Set the table's tag to a value as written and read back what it holds. */
    private String stored(final String value) {
        engine.execute("ALTER TABLE t SET TAG tg = " + value);
        return answer("SELECT SYSTEM$GET_TAG('tg', 't', 'table')");
    }

    @Test
    public void aNumberABooleanAListOrAnIdentifierReferenceIsAnInvalidValue() {
        assertEquals(invalid("1"), answer("ALTER TABLE t SET TAG tg = 1"));
        assertEquals(invalid("-1"), answer("ALTER TABLE t SET TAG tg = -1"));
        assertEquals(invalid("-1"), answer("ALTER TABLE t SET TAG tg = - 1"));
        assertEquals(invalid("1.5"), answer("ALTER TABLE t SET TAG tg = 1.5"));
        assertEquals(invalid("-1.50"), answer("ALTER TABLE t SET TAG tg = -1.50"));
        assertEquals(invalid("1e5"), answer("ALTER TABLE t SET TAG tg = 1e5"));
        assertEquals(invalid("TRUE"), answer("ALTER TABLE t SET TAG tg = TRUE"));
        assertEquals(invalid("true"), answer("ALTER TABLE t SET TAG tg = true"));
        assertEquals(invalid("FALSE"), answer("ALTER TABLE t SET TAG tg = FALSE"));
        assertEquals(invalid("TOK_CONSTANT_LIST"), answer("ALTER TABLE t SET TAG tg = (1)"));
        assertEquals(invalid("TOK_CONSTANT_LIST"), answer("ALTER TABLE t SET TAG tg = ('a', 'b')"));
        assertEquals(invalid("TOK_CONSTANT_LIST"), answer("ALTER TABLE t SET TAG tg = ()"));
        assertEquals(invalid("TOK_OBJECT_LITERAL"), answer("ALTER TABLE t SET TAG tg = IDENTIFIER('x')"));
    }

    @Test
    public void aNullAPlainNameOrABindIsAnUnsupportedDataType() {
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = NULL"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = x"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = abc"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = CURRENT_DATE"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = CURRENT_TIMESTAMP"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = :x"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tG = NULL"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG test_db.test_schema.tg = NULL"));
        assertEquals("SQL compilation error: Unsupported value data type for tag NOSUCHTAG. Only string is supported.",
            answer("ALTER TABLE t SET TAG nosuchtag = NULL"));
        assertEquals("SQL compilation error: Unsupported value data type for tag nosuch. Only string is supported.",
            answer("ALTER TABLE t SET TAG \"nosuch\" = x"));
        engine.execute("SET svn = 5");
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = $svn"));
    }

    @Test
    public void whatSetsATagIsStoredAsLiveStoresIt() {
        assertEquals("v", stored("'v'"));
        assertEquals("v2", stored("$$v2$$"));
        assertEquals("", stored("''"));
        assertEquals("X'00'", stored("X'00'"));
        assertEquals("x'ab'", stored("x'ab'"));
        assertEquals("a.b", stored("a.b"));
        assertEquals("a.b.c", stored("a.b.c"));
        assertEquals("\"a\".b", stored("\"a\".b"));
        assertEquals("xY", stored("\"xY\""));
        assertEquals("DATE", stored("DATE"));
        assertEquals("date", stored("date"));
        assertEquals("TAG", stored("TAG"));
        assertEquals("public", stored("public"));
        assertEquals("value", stored("value"));
        engine.execute("SET sv = 'fromvar'");
        assertEquals("fromvar", stored("$sv"));
        engine.execute("CREATE TABLE t2 (a INT) WITH TAG (tg = \"qq\")");
        assertEquals("qq", answer("SELECT SYSTEM$GET_TAG('tg', 't2', 'table')"));
        engine.execute("CREATE TABLE t3 (a INT) WITH TAG (tg = X'41')");
        assertEquals("X'41'", answer("SELECT SYSTEM$GET_TAG('tg', 't3', 'table')"));
    }

    @Test
    public void everyValueIsJudgedInTheOrderWrittenBeforeAnythingIsLookedUp() {
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG tg = NULL, tg = 1"));
        assertEquals(invalid("1"), answer("ALTER TABLE t SET TAG tg = 1, tg = NULL"));
        assertEquals(invalid("1"), answer("ALTER TABLE t SET TAG tg = 'v', tg2 = 1"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t SET TAG nosuchtag = 'v', tg = NULL"));
        assertEquals(invalid("1"), answer("ALTER TABLE t SET TAG nosuchtag = 1"));
        assertEquals(invalid("1"), answer("ALTER TABLE nosucht SET TAG tg = 1"));
        assertEquals("SQL compilation error: error line 1 at position 27|Session variable '$NOSUCHV' does not exist",
            answer("ALTER TABLE t SET TAG tg = $nosuchv, tg = 1"));
        assertEquals(invalid("1"), answer("ALTER TABLE t SET TAG tg = 1, tg = $nosuchv"));
        assertEquals(hinted("SQL compilation error:|Tag 'NOSUCHTAG' does not exist or not authorized."),
            answer("ALTER TABLE t SET TAG nosuchtag = 'v'"));
    }

    @Test
    public void everySurfaceThatSetsATagJudgesItsValue() {
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertEquals(invalid("1"), answer("ALTER MASKING POLICY mp SET TAG tg = 1"));
        assertEquals(invalid("-1"), answer("ALTER MASKING POLICY mp SET TAG tg = -1"));
        assertEquals(UNSUPPORTED, answer("ALTER MASKING POLICY mp SET TAG tg = NULL"));
        assertEquals(invalid("1"), answer("ALTER TABLE t MODIFY COLUMN a SET TAG tg = 1"));
        assertEquals(UNSUPPORTED, answer("ALTER TABLE t MODIFY COLUMN a SET TAG tg = NULL"));
        assertEquals(invalid("1"), answer("CREATE TABLE t4 (a INT) WITH TAG (tg = 1)"));
        assertEquals(invalid("1"), answer("CREATE TABLE t5 (a INT WITH TAG (tg = 1))"));
        assertEquals(invalid("1"), answer("CREATE TABLE t6 (a INT) WITH TAG (nosuchtag = 1)"));
        assertEquals(UNSUPPORTED, answer("CREATE TABLE t7 (a INT) WITH TAG (tg = NULL)"));
        assertEquals(invalid("TRUE"), answer("CREATE SCHEMA s1 WITH TAG (tg = TRUE)"));
        assertEquals(invalid("1"), answer("CREATE VIEW v1 WITH TAG (tg = 1) AS SELECT 1 AS x"));
    }

    @Test
    public void aValueOutsideTheAllowedValuesIsRefusedInLiveWords() {
        engine.execute("CREATE TAG tga ALLOWED_VALUES 'a'");
        assertEquals("Value 'b' is not allowed by the specified allowed_values for tag 'TGA'.",
            answer("ALTER TABLE t SET TAG tga = $$b$$"));
        assertEquals("Value 'DATE' is not allowed by the specified allowed_values for tag 'TGA'.",
            answer("ALTER TABLE t SET TAG tga = DATE"));
        engine.execute("ALTER TABLE t SET TAG tga = \"a\"");
        assertEquals("a", answer("SELECT SYSTEM$GET_TAG('tga', 't', 'table')"));
    }

    @Test
    public void aCreationTimeTagOutsideTheAllowedValuesCreatesNothing() {
        engine.execute("CREATE TAG tga ALLOWED_VALUES 'a'");
        final String refused = "Value 'b' is not allowed by the specified allowed_values for tag 'TGA'.";
        assertEquals(refused, answer("CREATE TABLE t9 (a INT) WITH TAG (tga = 'b')"));
        assertEquals(refused, answer("CREATE TABLE t10 (a INT WITH TAG (tga = 'b'))"));
        assertEquals(refused, answer("CREATE SCHEMA s9 WITH TAG (tga = 'b')"));
        assertEquals(refused, answer("CREATE VIEW v9 WITH TAG (tga = 'b') AS SELECT 1 AS x"));
        assertEquals("", answer("SHOW TABLES LIKE 'T9'"));
        assertEquals("", answer("SHOW SCHEMAS LIKE 'S9'"));
        engine.execute("CREATE TABLE t11 (a INT) WITH TAG (tga = 'a')");
        assertEquals("a", answer("SELECT SYSTEM$GET_TAG('tga', 't11', 'table')"));
    }
}
