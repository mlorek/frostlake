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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a result column DECLARES, compared against the account. Until the live harness learned to read
 * the driver's type metadata it answered VARCHAR for every column, so nothing in this file could have
 * been written — a type assertion would have passed vacuously or failed for the wrong reason.
 *
 * <p>Each case here is a column whose type both engines agree on, parameters included, so the file is
 * the regression net for the mapping as much as for the engine. The FLOAT family is compared by NAME
 * only: the driver reports no precision or scale for it.
 */
public class DeclaredResultTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ty (i INT, n NUMBER(10,2), f FLOAT,"
            + " v VARCHAR(5), t TEXT, bi BINARY(8), bo BOOLEAN, d DATE, tm TIME,"
            + " ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, va VARIANT,"
            + " ob OBJECT, ar ARRAY)");
    }

    /** The declared type of the query's single result column, rendered with its parameters. */
    private String typeOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final ResultSetColumn column = rs.getColumns().get(0);
        final DataType type = column.getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return type.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        if (type instanceof DateTimeType) {
            final DateTimeType dateTime = (DateTimeType) type;
            return type.getName() + "(" + dateTime.getPrecision() + ",tz="
                + dateTime.hasTimeZone() + ")";
        }
        return type.getName();
    }

    /** A NUMBER keeps the precision and scale it was declared with. */
    @Test
    public void aNumberKeepsItsPrecisionAndScale() {
        assertEquals("NUMBER(10,2)", typeOf("SELECT n FROM ty"));
    }

    /** A VARCHAR keeps its length, and an unbounded one is the 16MB maximum. */
    @Test
    public void aVarcharKeepsItsLength() {
        assertEquals("VARCHAR(5)", typeOf("SELECT v FROM ty"));
        assertEquals("VARCHAR(16777216)", typeOf("SELECT t FROM ty"));
        assertEquals("VARCHAR(16777216)", typeOf("SELECT SUBSTR(t, 1, 2) FROM ty"));
    }

    /** BINARY keeps its declared size, and BOOLEAN carries none. */
    @Test
    public void binaryKeepsItsSizeAndBooleanHasNone() {
        assertEquals("BINARY(8)", typeOf("SELECT bi FROM ty"));
        assertEquals("BOOLEAN", typeOf("SELECT bo FROM ty"));
    }

    /** The datetime family keeps its fractional-second precision and its time-zone bit. */
    @Test
    public void theDatetimeFamilyKeepsPrecisionAndZone() {
        assertEquals("DATE(0,tz=false)", typeOf("SELECT d FROM ty"));
        assertEquals("TIME(9,tz=false)", typeOf("SELECT tm FROM ty"));
        assertEquals("TIMESTAMP_NTZ(9,tz=false)", typeOf("SELECT ts FROM ty"));
        assertEquals("TIMESTAMP_LTZ(9,tz=true)", typeOf("SELECT tl FROM ty"));
        assertEquals("TIMESTAMP_TZ(9,tz=true)", typeOf("SELECT tz FROM ty"));
    }

    /** The three semi-structured types stay distinct, though JDBC hands them all over as strings. */
    @Test
    public void theSemiStructuredTypesStayDistinct() {
        assertEquals("VARIANT", typeOf("SELECT va FROM ty"));
        assertEquals("OBJECT", typeOf("SELECT ob FROM ty"));
        assertEquals("ARRAY", typeOf("SELECT ar FROM ty"));
        assertEquals("VARIANT", typeOf("SELECT PARSE_JSON('[1,2]') FROM ty"));
    }

    /** Arithmetic keeps the numeric type, with the precision and scale the operands imply. */
    @Test
    public void arithmeticKeepsTheNumericType() {
        assertEquals("NUMBER(38,0)", typeOf("SELECT i + 1 FROM ty"));
        assertEquals("NUMBER(38,0)", typeOf("SELECT i * 2 FROM ty"));
        assertEquals("NUMBER(11,2)", typeOf("SELECT n + 1 FROM ty"));
        assertEquals("NUMBER(38,6)", typeOf("SELECT i / 2 FROM ty"));
    }

    /** A function declares its OWN result type, not its argument's. */
    @Test
    public void aFunctionDeclaresItsOwnType() {
        assertEquals("NUMBER(18,0)", typeOf("SELECT LENGTH(t) FROM ty"));
        assertEquals("NUMBER(38,0)", typeOf("SELECT ABS(i) FROM ty"));
        assertEquals("DATE(0,tz=false)", typeOf("SELECT TO_DATE('2020-01-01') FROM ty"));
    }

    /** A literal carries the width it was written with — a one-digit number, a one-character string. */
    @Test
    public void aLiteralCarriesItsOwnWidth() {
        assertEquals("NUMBER(1,0)", typeOf("SELECT 1 FROM ty"));
        assertEquals("VARCHAR(1)", typeOf("SELECT 'x' FROM ty"));
        assertEquals("BOOLEAN", typeOf("SELECT TRUE FROM ty"));
    }

    /** A predicate is BOOLEAN wherever it is projected. */
    @Test
    public void aProjectedPredicateIsBoolean() {
        assertEquals("BOOLEAN", typeOf("SELECT i IS NULL FROM ty"));
    }

    /** COUNT and the ranking functions are 18-digit counters, not 38-digit ones. */
    @Test
    public void countersAreEighteenDigits() {
        assertEquals("NUMBER(18,0)", typeOf("SELECT COUNT(*) FROM ty"));
        assertEquals("NUMBER(18,0)", typeOf("SELECT ROW_NUMBER() OVER (ORDER BY i) FROM ty"));
    }

    /** The semi-structured constructors declare their container. */
    @Test
    public void theSemiStructuredConstructorsDeclareTheirContainer() {
        assertEquals("OBJECT", typeOf("SELECT OBJECT_CONSTRUCT('k', 1) FROM ty"));
        assertEquals("ARRAY", typeOf("SELECT ARRAY_CONSTRUCT(1, 2) FROM ty"));
    }

    /** SUM widens the precision by twelve and keeps the scale; AVG adds eighteen and six. */
    @Test
    public void sumAndAvgWidenTheirArgument() {
        assertEquals("NUMBER(38,0)", typeOf("SELECT SUM(i) FROM ty"));
        assertEquals("NUMBER(22,2)", typeOf("SELECT SUM(n) FROM ty"));
        assertEquals("NUMBER(38,6)", typeOf("SELECT AVG(i) FROM ty"));
        assertEquals("NUMBER(28,8)", typeOf("SELECT AVG(n) FROM ty"));
    }

    /** MIN, MAX and ANY_VALUE answer one of the VALUES, so they carry its type exactly. */
    @Test
    public void minAndMaxCarryTheirArgumentsType() {
        assertEquals("NUMBER(10,2)", typeOf("SELECT MIN(n) FROM ty"));
        assertEquals("VARCHAR(5)", typeOf("SELECT MAX(v) FROM ty"));
        assertEquals("DATE(0,tz=false)", typeOf("SELECT MIN(d) FROM ty"));
        assertEquals("BINARY(8)", typeOf("SELECT MIN(bi) FROM ty"));
        assertEquals("VARCHAR(5)", typeOf("SELECT ANY_VALUE(v) FROM ty"));
    }

    /** COUNT counts however it is written, and MEDIAN and LISTAGG have widths of their own. */
    @Test
    public void theOtherAggregatesDeclareTheirOwnWidths() {
        assertEquals("NUMBER(18,0)", typeOf("SELECT COUNT(i) FROM ty"));
        assertEquals("NUMBER(18,0)", typeOf("SELECT COUNT(DISTINCT i) FROM ty"));
        assertEquals("NUMBER(38,3)", typeOf("SELECT MEDIAN(i) FROM ty"));
        assertEquals("VARCHAR(134217728)", typeOf("SELECT LISTAGG(v) FROM ty"));
        assertEquals("ARRAY", typeOf("SELECT ARRAY_AGG(i) FROM ty"));
    }

    /** The same answers under an explicit GROUP BY — the grouping does not change a declared type. */
    @Test
    public void groupingChangesNoDeclaredType() {
        assertEquals("NUMBER(22,2)", typeOf("SELECT SUM(n) FROM ty GROUP BY i"));
        assertEquals("VARCHAR(5)", typeOf("SELECT MAX(v) FROM ty GROUP BY i"));
        assertEquals("NUMBER(18,0)", typeOf("SELECT COUNT(*) FROM ty GROUP BY i"));
    }

    /** FLOAT is compared by family: the driver carries no parameters for it. */
    @Test
    public void floatIsComparedByFamily() {
        assertEquals("FLOAT", typeOf("SELECT f FROM ty").replaceAll("\\(.*", ""));
    }
}
