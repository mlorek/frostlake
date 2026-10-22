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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A write into a structured column applies the column's type, on INSERT, UPDATE and MERGE alike. The values passed
 * through unconverted: an OBJECT(a INT) held {"zzz":"x"}, an ARRAY(INT) held ["y"], and an OBJECT(a NUMBER(5,2))
 * held 1.555. The column's own type refuses or rounds all of these on an explicit cast.
 *
 * <p>Snowflake's documentation sets the rules. A semi-structured value is castable to a structured type but not
 * coercible to one, nor the reverse, so an INSERT casts it explicitly; written uncast it is "incompatible types"
 * (live-verified). A structured value of the column's family converts as the cast converts it.
 */
public class StructuredColumnWriteTest extends BaseDatabaseTest {

    private static final String SCHEMA_MISMATCH = "Typed object schema mismatch in conversion";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sw (o OBJECT(a INT), p OBJECT(a NUMBER(5,2)), ar ARRAY(INT),"
            + " m MAP(VARCHAR, INT), v VARIANT)");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String mismatch(final String expecting, final String got, final String column) {
        return "Expression type does not match column data type, expecting " + expecting + " but got " + got
            + " for column " + column;
    }

    /** The sentence a semi-structured value written to a structured column gets (live-verified). */
    private static String incompatible(final String got, final String expecting) {
        return "incompatible types: [" + got + "] and [" + expecting + "]";
    }

    @Test
    public void aSemiStructuredValueIsRefusedWhileCompiling() {
        assertTrue(refusal("INSERT INTO sw (o) SELECT OBJECT_CONSTRUCT('zzz', 'x')")
            .contains(incompatible("OBJECT", "OBJECT(a NUMBER(38,0))")));
        assertTrue(refusal("INSERT INTO sw (ar) SELECT ARRAY_CONSTRUCT('y')")
            .contains(incompatible("ARRAY", "ARRAY(NUMBER(38,0))")));
        assertTrue(refusal("INSERT INTO sw (m) SELECT PARSE_JSON('{\"k\": 1}')")
            .contains(incompatible("VARIANT", "MAP(VARCHAR(16777216), NUMBER(38,0))")));
        engine.execute("INSERT INTO sw (o) SELECT NULL");
        assertTrue(refusal("UPDATE sw SET o = OBJECT_CONSTRUCT('zzz', 'x')")
            .contains(incompatible("OBJECT", "OBJECT(a NUMBER(38,0))")), "UPDATE");
        assertTrue(refusal("MERGE INTO sw USING (SELECT 1 k) s ON FALSE"
                + " WHEN NOT MATCHED THEN INSERT (o) VALUES (OBJECT_CONSTRUCT('q', 'w'))")
            .contains(incompatible("OBJECT", "OBJECT(a NUMBER(38,0))")), "MERGE");
    }

    @Test
    public void aStructuredValueIsConvertedToTheColumnsType() {
        engine.execute("INSERT INTO sw (p) SELECT {'a': 1.555}::OBJECT(a NUMBER(10,3))");
        engine.execute("INSERT INTO sw (o) SELECT {'a': '5'}::OBJECT(a VARCHAR)");
        engine.execute("INSERT INTO sw (ar) SELECT ARRAY_CONSTRUCT(1, 2.6)::ARRAY(NUMBER(10,2))");
        engine.execute("INSERT INTO sw (m) SELECT {'k': '7'}::MAP(VARCHAR, VARCHAR)");
        assertEquals("{\"a\":1.56}", first("SELECT p FROM sw WHERE p IS NOT NULL"),
            "as {'a': 1.555}::OBJECT(a NUMBER(10,3))::OBJECT(a NUMBER(5,2)) rounds it");
        assertEquals("{\"a\":5}", first("SELECT o FROM sw WHERE o IS NOT NULL"));
        assertEquals("[1,3]", first("SELECT ar FROM sw WHERE ar IS NOT NULL"));
        assertEquals("{\"k\":7}", first("SELECT m FROM sw WHERE m IS NOT NULL"));
        engine.execute("UPDATE sw SET o = {'a': 2.7}::OBJECT(a NUMBER(5,1)) WHERE o IS NOT NULL");
        assertEquals("{\"a\":3}", first("SELECT o FROM sw WHERE o IS NOT NULL"), "UPDATE converts too");
    }

    @Test
    public void aStructuredValueThatDoesNotFitIsRefusedAsTheCastRefusesIt() {
        assertTrue(refusal("SELECT {'k': 'x'}::MAP(VARCHAR, VARCHAR)::MAP(VARCHAR, INT)").contains(SCHEMA_MISMATCH),
            "the cast's own refusal");
        assertTrue(refusal("INSERT INTO sw (m) SELECT {'k': 'x'}::MAP(VARCHAR, VARCHAR)").contains(SCHEMA_MISMATCH));
        assertTrue(refusal("INSERT INTO sw (o) SELECT {'b': 5}::OBJECT(b INT)").contains(SCHEMA_MISMATCH),
            "a field the column does not declare");
        assertEquals("0", first("SELECT COUNT(*) FROM sw"), "nothing was written");
    }

    @Test
    public void aStructuredValueNeedsACastToReachASemiStructuredColumn() {
        assertTrue(refusal("INSERT INTO sw (v) SELECT [1, 2]::ARRAY(INT)")
            .contains(mismatch("VARIANT", "ARRAY(NUMBER(38,0))", "V")));
        engine.execute("INSERT INTO sw (v) SELECT [1, 2]::ARRAY(INT)::VARIANT");
        assertEquals("[1,2]", first("SELECT v FROM sw"));
    }
}
