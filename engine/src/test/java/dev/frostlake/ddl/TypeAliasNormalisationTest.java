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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A type ALIAS does not survive the catalog. Snowflake keeps no trace of the spelling a column was
 * declared with: INT, INTEGER, BIGINT, SMALLINT, TINYINT and BYTEINT are all NUMBER(38,0) afterwards,
 * and CHAR(3) is VARCHAR(3) — measured on all four surfaces (DESCRIBE, SHOW COLUMNS,
 * INFORMATION_SCHEMA and a result column), which agree with each other for every alias.
 *
 * <p>Frostlake's metadata surfaces already normalised; its CATALOG did not, so a result column reported
 * the alias — INTEGER(38,0), BIGINT(38,0), CHAR(3) — while DESCRIBE of the same column said
 * NUMBER(38,0) and VARCHAR(3). The two now agree because the alias is resolved once, where the column
 * is created.
 */
public class TypeAliasNormalisationTest extends BaseDatabaseTest {

    /** The declared type of a column named {@code c}, rendered with its parameters. */
    private String typeOfColumn(final String declaration) {
        engine.execute("CREATE OR REPLACE TABLE alias_t (c " + declaration + ")");
        final ResultSet rs = engine.executeQuery("SELECT c FROM alias_t");
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return type.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** What DESCRIBE says about the same column — the surface that always normalised. */
    private String describedType(final String declaration) {
        engine.execute("CREATE OR REPLACE TABLE alias_t (c " + declaration + ")");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE alias_t");
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("type")));
    }

    /** Every integer alias is NUMBER(38,0) — six spellings, one type. */
    @Test
    public void everyIntegerAliasIsNumber() {
        assertEquals("NUMBER(38,0)", typeOfColumn("INT"));
        assertEquals("NUMBER(38,0)", typeOfColumn("INTEGER"));
        assertEquals("NUMBER(38,0)", typeOfColumn("BIGINT"));
        assertEquals("NUMBER(38,0)", typeOfColumn("SMALLINT"));
        assertEquals("NUMBER(38,0)", typeOfColumn("TINYINT"));
        assertEquals("NUMBER(38,0)", typeOfColumn("BYTEINT"));
    }

    /** DECIMAL and NUMERIC are NUMBER too, keeping the precision and scale they were given. */
    @Test
    public void decimalAndNumericAreNumber() {
        assertEquals("NUMBER(10,2)", typeOfColumn("DECIMAL(10,2)"));
        assertEquals("NUMBER(10,2)", typeOfColumn("NUMERIC(10,2)"));
        assertEquals("NUMBER(10,2)", typeOfColumn("NUMBER(10,2)"));
    }

    /** Every character alias is VARCHAR — CHAR included, which is NOT a fixed-width type here. */
    @Test
    public void everyCharacterAliasIsVarchar() {
        assertEquals("VARCHAR(16777216)", typeOfColumn("STRING"));
        assertEquals("VARCHAR(16777216)", typeOfColumn("TEXT"));
        assertEquals("VARCHAR(5)", typeOfColumn("VARCHAR(5)"));
        assertEquals("VARCHAR(3)", typeOfColumn("CHAR(3)"));
        assertEquals("VARCHAR(3)", typeOfColumn("CHARACTER(3)"));
        assertEquals("VARCHAR(4)", typeOfColumn("NVARCHAR(4)"));
    }

    /** DOUBLE and REAL are FLOAT; the family is compared by name, as the driver carries no parameters. */
    @Test
    public void doubleAndRealAreFloat() {
        assertEquals("FLOAT", typeOfColumn("DOUBLE").replaceAll("\\(.*", ""));
        assertEquals("FLOAT", typeOfColumn("REAL").replaceAll("\\(.*", ""));
        assertEquals("FLOAT", typeOfColumn("FLOAT").replaceAll("\\(.*", ""));
    }

    /** DATETIME is a timestamp, and BOOLEAN has no alias to shed. */
    @Test
    public void datetimeIsATimestampAndBooleanIsItself() {
        assertEquals("TIMESTAMP_NTZ", typeOfColumn("DATETIME"));
        assertEquals("BOOLEAN", typeOfColumn("BOOLEAN"));
    }

    /** DESCRIBE agrees with the result column, alias for alias — the two surfaces cannot drift now. */
    @Test
    public void describeAgreesWithTheResultColumn() {
        assertEquals("NUMBER(38,0)", describedType("INT"));
        assertEquals("NUMBER(38,0)", describedType("BIGINT"));
        assertEquals("NUMBER(10,2)", describedType("DECIMAL(10,2)"));
        assertEquals("VARCHAR(3)", describedType("CHAR(3)"));
        assertEquals("VARCHAR(16777216)", describedType("STRING"));
        assertEquals("FLOAT", describedType("DOUBLE"));
    }
}
