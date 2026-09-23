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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A pivoted column declares its aggregate's type over the aggregated column, as the same aggregate does in
 * a grouped SELECT. AVG widens an integer to NUMBER(38,6), SUM keeps the scale and widens the precision,
 * COUNT declares the counter's NUMBER(18,0), and MIN and MAX pass the column's own type through. A driver
 * that reads the declared scale rounds a BIGINT-declared average of 92.5 to 92.
 */
public class PivotColumnTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ints (student VARCHAR, subject VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO ints VALUES ('Alice', 'Math', 90), ('Alice', 'Math', 95)");
        engine.execute("CREATE TABLE decs (student VARCHAR, subject VARCHAR, grade NUMBER(10,2))");
        engine.execute("INSERT INTO decs VALUES ('Alice', 'Math', 1.25), ('Alice', 'Math', 2.50)");
        engine.execute("CREATE TABLE texts (student VARCHAR, subject VARCHAR, note VARCHAR(20))");
        engine.execute("INSERT INTO texts VALUES ('Alice', 'Math', 'good'), ('Alice', 'Math', 'fine')");
    }

    /** The declared type of the pivoted column, the second of the result. */
    private String pivotedType(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(1).getDataType();
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return numeric.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return String.valueOf(type);
    }

    @Test
    public void anAverageDeclaresTheScaleItsValueCarries() {
        final String sql = "SELECT * FROM ints PIVOT (AVG(score) FOR subject IN ('Math'))";
        assertEquals("NUMBER(38,6)", pivotedType(sql));
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(0, new BigDecimal("92.5").compareTo(new BigDecimal(result.getRows().get(0).getValue(1).toString())));
    }

    @Test
    public void aSumKeepsTheScaleAndACountDeclaresTheCounter() {
        assertEquals("NUMBER(38,0)", pivotedType("SELECT * FROM ints PIVOT (SUM(score) FOR subject IN ('Math'))"));
        assertEquals("NUMBER(22,2)", pivotedType("SELECT * FROM decs PIVOT (SUM(grade) FOR subject IN ('Math'))"));
        assertEquals("NUMBER(18,0)", pivotedType("SELECT * FROM ints PIVOT (COUNT(score) FOR subject IN ('Math'))"));
    }

    @Test
    public void anAverageOfDecimalsWidensTheirScale() {
        assertEquals("NUMBER(28,8)", pivotedType("SELECT * FROM decs PIVOT (AVG(grade) FOR subject IN ('Math'))"));
    }

    @Test
    public void minAndMaxPassTheColumnTypeThrough() {
        assertEquals("NUMBER(38,0)", pivotedType("SELECT * FROM ints PIVOT (MAX(score) FOR subject IN ('Math'))"));
        assertEquals("NUMBER(10,2)", pivotedType("SELECT * FROM decs PIVOT (MIN(grade) FOR subject IN ('Math'))"));
        assertEquals("VARCHAR(20)", pivotedType("SELECT * FROM texts PIVOT (MAX(note) FOR subject IN ('Math'))"));
    }
}
