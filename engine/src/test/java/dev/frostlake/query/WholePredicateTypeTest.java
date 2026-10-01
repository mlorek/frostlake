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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Only a BOOLEAN is a predicate. A WHERE, a join condition, a HAVING or a QUALIFY — an UPDATE's or a DELETE's WHERE
 * and a MERGE's ON too — whose whole type is a VARIANT, an OBJECT, an ARRAY (a path into one included), a date or
 * time, a binary, a vector, a map or a geospatial value is refused while compiling, over empty tables too, as
 * {@code Invalid data type [T] for predicate [...]}, the predicate echoed from the plan. A VARIANT as an operand of
 * AND, OR or NOT is converted instead, row by row, as a cast to BOOLEAN converts it.
 */
public class WholePredicateTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE VT (a INT, v VARIANT, s VARCHAR, d DATE, o OBJECT, ar ARRAY)");
        engine.execute("CREATE TABLE FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO FULL_T VALUES (1, 2)");
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

    private static String noPredicate(final String type, final String echo) {
        return "SQL compilation error:\nInvalid data type [" + type + "] for predicate [" + echo + "]";
    }

    @Test
    public void aVariantConditionInAWhereOrAnOnIsRefusedOverEmptyTables() {
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT a FROM VT WHERE v"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT a FROM VT WHERE (v)"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT f.a FROM FULL_T f JOIN VT ON VT.v"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT f.a FROM FULL_T f LEFT JOIN VT ON VT.v"));
        assertEquals(noPredicate("VARIANT", "CAST(F.A AS VARIANT)"),
            refusal("SELECT f.a FROM FULL_T f JOIN VT ON f.a::VARIANT"));
    }

    @Test
    public void aVariantConditionOverRowsIsRefusedToo() {
        engine.execute("CREATE TABLE PV (a INT, v VARIANT, o OBJECT, ar ARRAY)");
        engine.execute("INSERT INTO PV SELECT 1, TO_VARIANT(TRUE), OBJECT_CONSTRUCT('x', TRUE), ARRAY_CONSTRUCT(TRUE)");
        assertEquals(noPredicate("VARIANT", "PV.V"), refusal("SELECT a FROM PV WHERE v"));
        assertEquals(noPredicate("VARIANT", "GET(PV.O, 'x')"), refusal("SELECT a FROM PV WHERE o:x"));
        assertEquals(noPredicate("VARIANT", "GET(PV.AR, 0)"), refusal("SELECT a FROM PV WHERE ar[0]"));
    }

    @Test
    public void objectsArraysAndPathsAreNoPredicateEither() {
        assertEquals(noPredicate("OBJECT", "VT.O"), refusal("SELECT a FROM VT WHERE o"));
        assertEquals(noPredicate("ARRAY", "VT.AR"), refusal("SELECT a FROM VT WHERE ar"));
        assertEquals(noPredicate("OBJECT", "VT.O"), refusal("SELECT f.a FROM FULL_T f JOIN VT ON VT.o"));
        assertEquals(noPredicate("VARIANT", "GET(VT.V, 'x')"), refusal("SELECT a FROM VT WHERE v:x"));
        assertEquals(noPredicate("VARIANT", "GET(VT.V, 'x')"), refusal("SELECT a FROM VT WHERE v['x']"));
        assertEquals(noPredicate("VARIANT", "GET(VT.V, 'x')"), refusal("SELECT a FROM VT WHERE GET(v, 'x')"));
        assertEquals(noPredicate("VARIANT", "PARSE_JSON('true')"), refusal("SELECT a FROM VT WHERE PARSE_JSON('true')"));
        assertEquals(noPredicate("VARIANT", "CAST(TRUE AS VARIANT)"), refusal("SELECT a FROM VT WHERE TO_VARIANT(TRUE)"));
        assertEquals(noPredicate("VARIANT", "SYSTEM$NULL_TO_VARIANT(null)"),
            refusal("SELECT a FROM VT WHERE NULL::VARIANT"));
        assertEquals(noPredicate("ARRAY", "ARRAY_CONSTRUCT()"), refusal("SELECT a FROM VT WHERE ARRAY_CONSTRUCT()"));
        assertEquals(noPredicate("ARRAY", "ARRAY_CONSTRUCT(1)"), refusal("SELECT a FROM VT WHERE [1]"));
        assertEquals(noPredicate("OBJECT", "OBJECT_CONSTRUCT('x', 1)"), refusal("SELECT a FROM VT WHERE {'x': 1}"));
        assertEquals(noPredicate("OBJECT", "CAST(PARSE_JSON('{}') AS OBJECT)"),
            refusal("SELECT a FROM VT WHERE TO_OBJECT(PARSE_JSON('{}'))"));
    }

    @Test
    public void everyOtherNonBooleanFamily() {
        engine.execute("""
            CREATE TABLE TT (a INT, d DATE, t TIME, ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bn BINARY,
              vec VECTOR(INT, 3), m MAP(VARCHAR, INT), sa ARRAY(INT), so OBJECT(x INT), g GEOGRAPHY, f FLOAT,
              bo BOOLEAN)""");
        assertEquals(noPredicate("DATE", "TT.D"), refusal("SELECT a FROM TT WHERE d"));
        assertEquals(noPredicate("TIME(9)", "TT.T"), refusal("SELECT a FROM TT WHERE t"));
        assertEquals(noPredicate("TIMESTAMP_NTZ(9)", "TT.TS"), refusal("SELECT a FROM TT WHERE ts"));
        assertEquals(noPredicate("TIMESTAMP_TZ(9)", "TT.TZ"), refusal("SELECT a FROM TT WHERE tz"));
        assertEquals(noPredicate("BINARY(8388608)", "TT.BN"), refusal("SELECT a FROM TT WHERE bn"));
        assertEquals(noPredicate("VECTOR(INT, 3)", "TT.VEC"), refusal("SELECT a FROM TT WHERE vec"));
        assertEquals(noPredicate("MAP(VARCHAR(16777216), NUMBER(38,0))", "TT.M"), refusal("SELECT a FROM TT WHERE m"));
        assertEquals(noPredicate("ARRAY(NUMBER(38,0))", "TT.SA"), refusal("SELECT a FROM TT WHERE sa"));
        assertEquals(noPredicate("OBJECT(x NUMBER(38,0))", "TT.SO"), refusal("SELECT a FROM TT WHERE so"));
        assertEquals(noPredicate("GEOGRAPHY", "TT.G"), refusal("SELECT a FROM TT WHERE g"));
        assertEquals(noPredicate("FLOAT", "TT.F"), refusal("SELECT a FROM TT WHERE f"));
        assertEquals(0, engine.executeQuery("SELECT a FROM TT WHERE bo").getRowCount());
    }

    @Test
    public void everyPredicateClause() {
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT a FROM VT GROUP BY a, v HAVING v"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT a FROM VT QUALIFY v"));
        assertEquals(noPredicate("VARIANT", "Z"), refusal("SELECT v AS z FROM VT WHERE z"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("SELECT a FROM VT WHERE a IN (SELECT a FROM VT WHERE v)"));
        assertEquals(noPredicate("DATE", "MAX(VT.D)"), refusal("SELECT a FROM VT GROUP BY a HAVING MAX(d)"));
        assertEquals(noPredicate("VARIANT", "ANY_VALUE(VT.V)"), refusal("SELECT a FROM VT GROUP BY a HAVING ANY_VALUE(v)"));
        assertEquals(noPredicate("VARIANT", "LAG(VT.V) OVER (ORDER BY VT.A ASC NULLS LAST)"),
            refusal("SELECT a FROM VT QUALIFY LAG(v) OVER (ORDER BY a)"));
        assertEquals(noPredicate("DATE", "VT.D"), refusal("SELECT f.a FROM FULL_T f JOIN VT ON VT.d"));
        assertEquals(noPredicate("TIMESTAMP_LTZ(9)", "CURRENT_TIMESTAMP()"),
            refusal("SELECT a FROM VT WHERE a IN (SELECT a FROM FULL_T WHERE CURRENT_TIMESTAMP())"));
    }

    @Test
    public void aFromlessWhere() {
        assertEquals(noPredicate("VARIANT", "PARSE_JSON('true')"), refusal("SELECT 1 WHERE PARSE_JSON('true')"));
        assertEquals(noPredicate("DATE", "CURRENT_DATE()"), refusal("SELECT 1 WHERE CURRENT_DATE()"));
        assertEquals(noPredicate("VARIANT", "X"), refusal("SELECT PARSE_JSON('true') AS x WHERE x"));
    }

    @Test
    public void anOperandOfALogicalOperatorIsConvertedInstead() {
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE v AND TRUE").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE TRUE AND v").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE v OR TRUE").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE NOT v").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE a = 1 AND v").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE v::BOOLEAN").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE v:x::BOOLEAN").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE v IS NULL").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM VT WHERE NULL").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT f.a FROM FULL_T f JOIN VT ON f.a::VARIANT AND TRUE").getRowCount());
    }

    @Test
    public void aVariantOperandOfALogicalOperatorConvertsAsACastDoesOverRows() {
        engine.execute("CREATE TABLE LV (a INT, v VARIANT)");
        engine.execute("INSERT INTO LV SELECT 1, TO_VARIANT(TRUE)");
        engine.execute("INSERT INTO LV SELECT 2, PARSE_JSON('\"no\"')");
        engine.execute("INSERT INTO LV SELECT 3, PARSE_JSON('null')");
        engine.execute("INSERT INTO LV SELECT 4, NULL");
        final ResultSet rs = engine.executeQuery("SELECT a, NOT v, v AND TRUE, v OR FALSE, v OR TRUE FROM LV ORDER BY a");
        final Object[][] expected = {
            {Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE},
            {Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE},
            {null, null, null, Boolean.TRUE},
            {null, null, null, Boolean.TRUE},
        };
        assertEquals(4, rs.getRowCount());
        for (int row = 0; row < expected.length; row++) {
            for (int column = 0; column < expected[row].length; column++) {
                assertEquals(expected[row][column], rs.getRows().get(row).getValue(column + 1),
                    "row " + (row + 1) + ", column " + (column + 2));
            }
        }
        assertEquals("[2]", keys("SELECT a FROM LV WHERE NOT v ORDER BY a"));
        assertEquals("[1]", keys("SELECT a FROM LV WHERE v AND TRUE ORDER BY a"));
        assertEquals("[1, 4]", keys("SELECT a FROM LV WHERE v OR a = 4 ORDER BY a"));
        assertEquals(Boolean.FALSE, engine.executeQuery("SELECT NOT PARSE_JSON('{\"f\": true}'):f").getRows().get(0).getValue(0));
    }

    @Test
    public void aVariantHoldingNoBooleanFailsTheRowUnlessTheOtherOperandDecides() {
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT NOT PARSE_JSON('1')"));
        assertEquals("Failed to cast variant value 0 to BOOLEAN", refusal("SELECT NOT PARSE_JSON('0')"));
        assertEquals("Failed to cast variant value \"x\" to BOOLEAN", refusal("SELECT NOT PARSE_JSON('\"x\"')"));
        assertEquals("Failed to cast variant value [] to BOOLEAN", refusal("SELECT NOT PARSE_JSON('[]')"));
        assertEquals("Failed to cast variant value {} to BOOLEAN", refusal("SELECT NOT PARSE_JSON('{}')"));
        // A DOUBLE is named as a client reads it, by the operator and by the cast alike.
        assertEquals("Failed to cast variant value 1.500000000000000e+00 to BOOLEAN",
            refusal("SELECT NOT TO_VARIANT(1.5::FLOAT)"));
        assertEquals("Failed to cast variant value 1.500000000000000e+00 to BOOLEAN",
            refusal("SELECT TO_VARIANT(1.5::FLOAT)::BOOLEAN"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT PARSE_JSON('1') AND TRUE"));
        assertEquals("Failed to cast variant value 1 to BOOLEAN", refusal("SELECT PARSE_JSON('1') OR FALSE"));
        assertEquals(Boolean.FALSE, engine.executeQuery("SELECT PARSE_JSON('1') AND FALSE").getRows().get(0).getValue(0));
        assertEquals(Boolean.TRUE, engine.executeQuery("SELECT PARSE_JSON('1') OR TRUE").getRows().get(0).getValue(0));
        assertEquals(Boolean.FALSE, engine.executeQuery("SELECT FALSE AND PARSE_JSON('\"x\"')").getRows().get(0).getValue(0));
    }

    private String keys(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<Long> keys = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            keys.add(((Number) row.getValue(0)).longValue());
        }
        return keys.toString();
    }

    @Test
    public void anUpdateOrDeleteWhereAndAMergeOnAreJudgedAlike() {
        engine.execute("CREATE OR REPLACE TABLE VT (a INT, v VARIANT, s VARCHAR, d DATE, bo BOOLEAN)");
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("DELETE FROM VT WHERE v"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("UPDATE VT SET a = 1 WHERE v"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("UPDATE VT SET a = 1 WHERE (v)"));
        assertEquals(noPredicate("DATE", "VT.D"), refusal("UPDATE VT SET a = 1 WHERE d"));
        assertEquals(noPredicate("DATE", "VT.D"), refusal("DELETE FROM VT WHERE d"));
        assertEquals(noPredicate("VARCHAR(16777216)", "VT.S"), refusal("DELETE FROM VT WHERE s"));
        assertEquals(noPredicate("NUMBER(38,0)", "VT.A"), refusal("UPDATE VT SET a = 1 WHERE a"));
        assertEquals(noPredicate("VARCHAR(16777216)", "VT.S"), refusal("UPDATE VT SET a = 1 FROM FULL_T WHERE VT.s"));
        assertEquals(noPredicate("NUMBER(38,0)", "FULL_T.A"), refusal("DELETE FROM VT USING FULL_T WHERE FULL_T.a"));
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("MERGE INTO VT USING FULL_T s ON VT.v WHEN MATCHED THEN DELETE"));
        assertEquals(noPredicate("DATE", "VT.D"), refusal("MERGE INTO VT USING FULL_T s ON VT.d WHEN MATCHED THEN DELETE"));
        assertEquals(noPredicate("NUMBER(38,0)", "VT.A"), refusal("MERGE INTO VT USING FULL_T s ON VT.a WHEN MATCHED THEN DELETE"));
        engine.execute("MERGE INTO VT USING FULL_T s ON VT.a = s.a AND VT.v WHEN MATCHED THEN DELETE");
    }

    @Test
    public void aDmlPredicateTypeComesAfterTheArgumentTypesAndBeforeTheColumnMatch() {
        engine.execute("CREATE OR REPLACE TABLE VT (a INT, v VARIANT, s VARCHAR, d DATE, bo BOOLEAN)");
        assertEquals(noPredicate("VARIANT", "VT.V"), refusal("UPDATE VT SET a = bo WHERE v"));
        assertEquals("SQL compilation error: error line 1 at position 18\ntoo many arguments for function [UPPER(1, 2)] "
            + "expected 1, got 2", refusal("UPDATE VT SET a = UPPER(1, 2) WHERE v"));
        assertEquals("SQL compilation error: error line 1 at position 14\ninvalid identifier 'NOSUCH'",
            refusal("UPDATE VT SET nosuch = 1 WHERE v"));
        assertEquals("SQL compilation error: error line 1 at position 27\ninvalid identifier 'NOSUCH'",
            refusal("DELETE FROM VT WHERE v AND nosuch = 1"));
        assertEquals(noPredicate("VARIANT", "VT.V"),
            refusal("MERGE INTO VT USING FULL_T s ON VT.v WHEN MATCHED THEN UPDATE SET a = bo"));
        assertEquals(noPredicate("VARIANT", "VT.V"),
            refusal("MERGE INTO VT USING FULL_T s ON VT.v WHEN MATCHED THEN UPDATE SET a = UPPER(1, 2)"));
    }

    @Test
    public void aNameAnywhereInTheWhereSpeaksFirst() {
        assertEquals("SQL compilation error: error line 1 at position 23\ninvalid identifier 'NOSUCH'",
            refusal("SELECT a FROM VT WHERE nosuch = 1 AND v"));
        assertEquals("SQL compilation error: error line 1 at position 29\ninvalid identifier 'NOSUCH'",
            refusal("SELECT a FROM VT WHERE v AND nosuch = 1"));
    }
}
