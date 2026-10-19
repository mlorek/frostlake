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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A condition is a BOOLEAN. A WHERE, HAVING, QUALIFY or ON that is a value of any other family - a date, a time, a
 * number, a text, a binary, a semi-structured value - is refused while the statement compiles, the type and the
 * condition echoed from the plan.
 */
public class PredicateTypeFamiliesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE OR REPLACE TABLE tt (d DATE, v VARIANT, n INT, s VARCHAR(5), b BOOLEAN, f FLOAT, bi BINARY,
                a ARRAY, o OBJECT, t TIME, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ)""");
        engine.execute("""
            INSERT INTO tt SELECT '2024-01-01'::DATE, PARSE_JSON('{"x": true}'), 1, 'abc', TRUE, 1.5,
                TO_BINARY('00'), [1], {'a': 1}, '10:00:00'::TIME, '2024-01-01 10:00:00'::TIMESTAMP_NTZ,
                '2024-01-01 10:00:00 +00:00'::TIMESTAMP_TZ""");
    }

    private static String refused(final String type, final String predicate) {
        return "SQL compilation error:\nInvalid data type [" + type + "] for predicate [" + predicate + "]";
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void everyFamilyButBooleanIsRefusedInAWhere() {
        final String[][] columns = {{"d", "DATE"}, {"v", "VARIANT"}, {"n", "NUMBER(38,0)"}, {"s", "VARCHAR(5)"},
            {"f", "FLOAT"}, {"bi", "BINARY(8388608)"}, {"a", "ARRAY"}, {"o", "OBJECT"}, {"t", "TIME(9)"},
            {"ts", "TIMESTAMP_NTZ(9)"}, {"tz", "TIMESTAMP_TZ(9)"}};
        for (final String[] column : columns) {
            assertEquals(refused(column[1], "TT." + column[0].toUpperCase(Locale.ROOT)),
                refusalOf("SELECT n FROM tt WHERE " + column[0]));
        }
        assertEquals(1, engine.executeQuery("SELECT n FROM tt WHERE b").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT n FROM tt WHERE NULL").getRowCount());
    }

    @Test
    public void havingQualifyAndOnAreHeldToTheSameRule() {
        for (final String[] column : new String[][] {{"d", "DATE"}, {"v", "VARIANT"}, {"f", "FLOAT"}, {"a", "ARRAY"}}) {
            final String name = column[0].toUpperCase(Locale.ROOT);
            assertEquals(refused(column[1], "TT." + name),
                refusalOf("SELECT n FROM tt GROUP BY n, " + column[0] + " HAVING " + column[0]));
            assertEquals(refused(column[1], "TT." + name),
                refusalOf("SELECT n, " + column[0] + " FROM tt QUALIFY " + column[0]));
            assertEquals(refused(column[1], "T1." + name),
                refusalOf("SELECT t1.n FROM tt t1 JOIN tt t2 ON t1." + column[0]));
        }
    }

    @Test
    public void anExpressionAnAliasAndALiteralAreEchoedFromThePlan() {
        assertEquals(refused("VARIANT", "PARSE_JSON('true')"), refusalOf("SELECT n FROM tt WHERE PARSE_JSON('true')"));
        assertEquals(refused("VARIANT", "GET(TT.V, 'x')"), refusalOf("SELECT n FROM tt WHERE v:x"));
        assertEquals(refused("DATE", "CURRENT_DATE()"), refusalOf("SELECT n FROM tt WHERE CURRENT_DATE"));
        assertEquals(refused("DATE", "Z"), refusalOf("SELECT CURRENT_DATE AS z FROM tt WHERE z"));
        assertEquals(refused("VARIANT", "Z"), refusalOf("SELECT PARSE_JSON('1') AS z FROM tt WHERE z"));
        assertEquals(refused("VARCHAR(4)", "'true'"), refusalOf("SELECT n FROM tt WHERE 'true'"));
        assertEquals(refused("NUMBER(2,1)", "1.5"), refusalOf("SELECT n FROM tt WHERE 1.5"));
        assertEquals(refused("ARRAY", "ARRAY_CONSTRUCT(1)"), refusalOf("SELECT n FROM tt WHERE [1]"));
        assertEquals(refused("VARIANT", "CAST(TRUE AS VARIANT)"), refusalOf("SELECT n FROM tt WHERE TO_VARIANT(TRUE)"));
    }

    @Test
    public void aStatementThatChangesRowsIsHeldToItToo() {
        assertEquals(refused("DATE", "TT.D"), refusalOf("DELETE FROM tt WHERE d"));
        assertEquals(refused("DATE", "TT.D"), refusalOf("UPDATE tt SET n = 1 WHERE d"));
        assertEquals(refused("DATE", "TT.D"),
            refusalOf("MERGE INTO tt USING (SELECT 1 x) s ON tt.d WHEN MATCHED THEN DELETE"));
    }
}
