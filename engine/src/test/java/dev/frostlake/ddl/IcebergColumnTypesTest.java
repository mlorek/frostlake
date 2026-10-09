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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The types an Iceberg table's columns take: the format's own, at its one string length, binary length and time
 * scale, with every other type refused in the sentence the account gives for it.
 */
public class IcebergColumnTypesTest extends BaseDatabaseTest {

    private static final String STRING_LENGTH = "For Iceberg tables, only max length (134,217,728) is supported for"
        + " 'VARCHAR(L)/STRING(L)' or use STRING directly.";
    private static final String BINARY_LENGTH = "For Iceberg tables, only max length (67,108,864) is supported for"
        + " 'BINARY(L)'. Alternatively, use BINARY directly.";
    private static final String NUMBER_FORM = "data type 'NUMBER/DECIMAL' without precision and scale is not supported"
        + " for Iceberg tables.";

    private List<String> described(final String relation) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + relation);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("name")) + " " + row.getValue(rs.getColumnIndex("type")));
        }
        return out;
    }

    private List<String> rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
            out.add(line.toString());
        }
        return out;
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().contains(fragment), sql + " => " + refusal.getMessage());
    }

    private void refusedType(final String type, final String fragment) {
        refused("CREATE ICEBERG TABLE ice_never (c " + type + ")", fragment);
    }

    private static String unsupported(final String echoed) {
        return "Unsupported data type '" + echoed + "' for iceberg tables.";
    }

    private static String scale(final String column, final String type) {
        return "Invalid time type scale specified for column '" + column + "' with data type '" + type + "'. Please"
            + " consult the Snowflake documentation for supported scales of TIME and TIMESTAMP types in Iceberg tables.";
    }

    @Test
    public void aColumnHoldsTheTypeTheFormatHasForIt() {
        engine.executeQuery("""
            CREATE ICEBERG TABLE ice_types (a INT, b VARCHAR, c STRING, d TEXT, e NUMBER(10,2), f BINARY,
                g VARCHAR(134217728), h TIMESTAMP_NTZ, i BOOLEAN, j DATE, k FLOAT, l BIGINT, m TIME, n TIMESTAMP_LTZ,
                o BINARY(67108864), p TIMESTAMP_NTZ(6), q NVARCHAR, r INTEGER, s DOUBLE, t UUID, u DECIMAL(12,4),
                v CHARACTER VARYING, w DATETIME, x TIME(6), y NUMBER(38,0), z REAL)""");
        assertEquals("[A NUMBER(10,0), B VARCHAR(134217728), C VARCHAR(134217728), D VARCHAR(134217728),"
            + " E NUMBER(10,2), F BINARY(67108864), G VARCHAR(134217728), H TIMESTAMP_NTZ(6), I BOOLEAN, J DATE,"
            + " K FLOAT, L NUMBER(19,0), M TIME(6), N TIMESTAMP_LTZ(6), O BINARY(67108864), P TIMESTAMP_NTZ(6),"
            + " Q VARCHAR(134217728), R NUMBER(10,0), S FLOAT, T UUID, U NUMBER(12,4), V VARCHAR(134217728),"
            + " W TIMESTAMP_NTZ(6), X TIME(6), Y NUMBER(38,0), Z FLOAT]", described("ice_types").toString());
        assertEquals("[A|null|null|10|0|null, B|134217728|134217728|null|null|null, H|null|null|null|null|6]", rows("""
            SELECT column_name, character_maximum_length, character_octet_length, numeric_precision, numeric_scale,
                   datetime_precision
              FROM information_schema.columns
             WHERE table_name = 'ICE_TYPES' AND column_name IN ('A', 'B', 'H')
             ORDER BY ordinal_position""").toString());
        engine.executeQuery("INSERT INTO ice_types (a, b, f) VALUES (1, 'x', TO_BINARY('00'))");
        assertEquals("[NUMBER(10,0)[SB1]|VARCHAR(134217728)[LOB]|BINARY(67108864)[LOB]]",
            rows("SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(f) FROM ice_types").toString());
    }

    @Test
    public void aStructuredColumnHoldsTheFormatsTypesInItsMembers() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_nested (n ARRAY(INT), o OBJECT(k VARCHAR, j BIGINT),"
            + " m MAP(INT, ARRAY(VARCHAR)), t ARRAY(TIMESTAMP_NTZ))");
        assertEquals("[N ARRAY(NUMBER(10,0)), O OBJECT(k VARCHAR(134217728), j NUMBER(19,0)),"
            + " M MAP(NUMBER(10,0), ARRAY(VARCHAR(134217728))), T ARRAY(TIMESTAMP_NTZ(6))]",
            described("ice_nested").toString());
        refusedType("OBJECT(a VARCHAR(5))", STRING_LENGTH);
        refusedType("ARRAY(VARCHAR(5))", STRING_LENGTH);
        refusedType("MAP(VARCHAR(5), INT)", STRING_LENGTH);
        refusedType("ARRAY(CHAR(3))", unsupported("CHAR(3)"));
        refusedType("ARRAY(SMALLINT)", unsupported("SMALLINT"));
        refusedType("OBJECT(a VARIANT)", unsupported("OBJECT(a VARIANT)"));
        refusedType("MAP(VARCHAR, VARIANT)", unsupported("MAP(VARCHAR(134217728), VARIANT)"));
        refusedType("ARRAY(NUMBER)", NUMBER_FORM);
        refusedType("ARRAY(BINARY(10))", BINARY_LENGTH);
        refusedType("ARRAY(TIMESTAMP_NTZ(9))", scale("C", "ARRAY(TIMESTAMP_NTZ(9))"));
    }

    @Test
    public void aLengthOrAScaleOtherThanTheFormatsIsRefused() {
        for (final String type : new String[] {"VARCHAR(10)", "STRING(5)", "TEXT(10)", "VARCHAR(16777216)",
            "NVARCHAR(10)", "NVARCHAR2(10)", "VARCHAR2(10)", "CHAR VARYING(10)", "NCHAR VARYING(10)", "string(10)"}) {
            refusedType(type, STRING_LENGTH);
        }
        for (final String type : new String[] {"BINARY(10)", "BINARY(8388608)"}) {
            refusedType(type, BINARY_LENGTH);
        }
        for (final String type : new String[] {"NUMBER", "NUMBER(10)", "NUMBER(38)", "DECIMAL", "DECIMAL(5)", "DEC(5)",
            "NUMERIC", "BYTEINT", "Number"}) {
            refusedType(type, NUMBER_FORM);
        }
        refusedType("TIMESTAMP_NTZ(9)", scale("C", "TIMESTAMP_NTZ(9)"));
        refusedType("TIMESTAMP_NTZ(0)", scale("C", "TIMESTAMP_NTZ(0)"));
        refusedType("timestamp_ntz(7)", scale("C", "TIMESTAMP_NTZ(7)"));
        refusedType("TIMESTAMP_LTZ(9)", scale("C", "TIMESTAMP_LTZ(9)"));
        refusedType("timestamp_ltz(3)", scale("C", "TIMESTAMP_LTZ(3)"));
        refusedType("TIME(9)", scale("C", "TIME(9)"));
        refusedType("TIME(3)", scale("C", "TIME(3)"));
        refused("CREATE ICEBERG TABLE ice_never (\"my c\" TIME(3))", scale("my c", "TIME(3)"));
        // A length or a scale no type takes is refused as it is for any table.
        refusedType("VARCHAR(0)", "Invalid character length: 0. Must be between 1 and 134,217,728.");
        refusedType("VARCHAR(134217729)", "Invalid character length: 134,217,729. Must be between 1 and 134,217,728.");
        refusedType("NUMBER(39,0)", "Invalid number precision: 39. Must be between 0 and 38.");
        refusedType("TIME(10)", "Invalid time scale: 10. Must be between 0 and 9.");
        refusedType("BINARY(0)", "Invalid binary length: 0. Must be between 1 and 67,108,864.");
    }

    @Test
    public void aTypeTheFormatLacksIsRefusedByItsName() {
        // A name the format has no type for is echoed as it was written.
        for (final String type : new String[] {"CHAR", "CHAR(3)", "char(3)", "Char( 3 )", "CHARACTER", "CHARACTER(3)",
            "NCHAR", "NCHAR(3)", "SMALLINT", "smallint", "TINYINT", "tinyint", "VARBINARY", "VARBINARY(10)",
            "varbinary( 10 )", "VECTOR(FLOAT, 3)", "vector(int, 4)", "VECTOR(INT,4)", "DECFLOAT", "FILE",
            "INTERVAL DAY"}) {
            refusedType(type, unsupported(type));
        }
        // A type the format knows and does not hold is echoed as the account spells it.
        refusedType("VARIANT", unsupported("VARIANT"));
        refusedType("Variant", unsupported("VARIANT"));
        refusedType("OBJECT", unsupported("OBJECT"));
        refusedType("ARRAY", unsupported("ARRAY"));
        refusedType("GEOGRAPHY", unsupported("GEOGRAPHY"));
        refusedType("geometry", unsupported("GEOMETRY(4326)"));
        refusedType("TIMESTAMP_TZ", unsupported("TIMESTAMP_TZ(9)"));
        refusedType("timestamptz", unsupported("TIMESTAMP_TZ(9)"));
        refusedType("timestamp_tz(3)", unsupported("TIMESTAMP_TZ(3)"));
        refusedType("TIMESTAMP_TZ(6)", unsupported("TIMESTAMP_TZ(6)"));
        // A word that is no type at all.
        refusedType("nosuchtype", unsupported("nosuchtype"));
        refusedType("\"VARCHAR\"", unsupported("\"VARCHAR\""));
        refusedType("nosuchtype(5,2)", "Unsupported data type 'nosuchtype(5, 2)'.");
    }

    @Test
    public void theTypesAreJudgedColumnByColumnBeforeAnythingElseTheStatementNames() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_first (a INT)");
        refused("CREATE ICEBERG TABLE ice_first (v VARCHAR(10))", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE IF NOT EXISTS ice_first (v VARCHAR(10))", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE nosuch_schema.ice_never (v VARCHAR(10))", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10)) EXTERNAL_VOLUME = 'nosuch_vol'", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10)) CATALOG = 'no_catalog'", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10)) BASE_LOCATION = 'mine/'", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10)) CHANGE_TRACKING = FALSE", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10), v INT)", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10), c CHAR(3))", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (c CHAR(3), v VARCHAR(10))", unsupported("CHAR(3)"));
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10), w VARCHAR(0))", STRING_LENGTH);
        refused("CREATE ICEBERG TABLE ice_never (v VARCHAR(10), n TIME(10))", STRING_LENGTH);
        // A column's options are a table's.
        engine.executeQuery("CREATE ICEBERG TABLE ice_options (c1 INT NOT NULL, c2 VARCHAR DEFAULT 'x', c3 INT DEFAULT 5)");
        assertEquals("[C1 NUMBER(10,0), C2 VARCHAR(134217728), C3 NUMBER(10,0)]", described("ice_options").toString());
    }

    @Test
    public void anAlteredColumnIsHeldToTheSameTypes() {
        engine.executeQuery("CREATE ICEBERG TABLE ice_alt (a INT, b VARCHAR)");
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN i VARCHAR(20)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN k STRING(5)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN m CHAR(3)", unsupported("CHAR(3)"));
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN x NUMBER", NUMBER_FORM);
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN x2 TIMESTAMP_NTZ(9)", scale("X2", "TIMESTAMP_NTZ(9)"));
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN x4 BINARY(10)", BINARY_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN x5 VARIANT", unsupported("VARIANT"));
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN x9 nosuchtype", unsupported("nosuchtype"));
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN n1 INT, n2 VARCHAR(7)", STRING_LENGTH);
        // The type is judged before the column and before the table are looked for.
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN a VARCHAR(10)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ADD COLUMN IF NOT EXISTS a VARCHAR(10)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_nosuch ADD COLUMN x VARCHAR(10)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE IF EXISTS ice_nosuch ADD COLUMN x VARCHAR(10)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ALTER COLUMN b SET DATA TYPE VARCHAR(100)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ALTER COLUMN nosuchcol SET DATA TYPE VARCHAR(10)", STRING_LENGTH);
        refused("ALTER ICEBERG TABLE ice_alt ALTER COLUMN b SET DATA TYPE CHAR(3)", unsupported("CHAR(3)"));
        refused("ALTER ICEBERG TABLE ice_alt ALTER COLUMN b SET DATA TYPE TIMESTAMP_NTZ(9)",
            scale("B", "TIMESTAMP_NTZ(9)"));
        refused("ALTER ICEBERG TABLE ice_alt ALTER COLUMN a SET DATA TYPE NUMBER", NUMBER_FORM);
        for (final String action : new String[] {"ADD COLUMN h VARCHAR", "ADD COLUMN l VARCHAR(134217728)",
            "ADD COLUMN x1 INT", "ADD COLUMN x3 TIMESTAMP_NTZ", "ADD COLUMN x6 BIGINT", "ADD COLUMN x7 TIME",
            "ADD COLUMN x8 BINARY", "ALTER COLUMN b SET DATA TYPE VARCHAR",
            "ALTER COLUMN b SET DATA TYPE VARCHAR(134217728)", "ALTER COLUMN a SET DATA TYPE BIGINT"}) {
            engine.executeQuery("ALTER ICEBERG TABLE ice_alt " + action);
        }
        assertEquals("[A NUMBER(19,0), B VARCHAR(134217728), H VARCHAR(134217728), L VARCHAR(134217728),"
            + " X1 NUMBER(10,0), X3 TIMESTAMP_NTZ(6), X6 NUMBER(19,0), X7 TIME(6), X8 BINARY(67108864)]",
            described("ice_alt").toString());
    }

    @Test
    public void aTableFromAQueryTakesItsColumnsAtTheFormatsWidths() {
        engine.executeQuery("CREATE TABLE src_wide (v VARCHAR(134217728), w VARCHAR(16777217), s VARCHAR(10),"
            + " b BINARY(10), n INT)");
        engine.executeQuery("CREATE ICEBERG TABLE ice_ctas AS SELECT * FROM src_wide");
        assertEquals("[V VARCHAR(134217728), W VARCHAR(134217728), S VARCHAR(134217728), B BINARY(67108864),"
            + " N NUMBER(38,0)]", described("ice_ctas").toString());
        engine.executeQuery("CREATE ICEBERG TABLE ice_ctas2 AS SELECT 'abc' AS s, 1 AS n, 1.5 AS d, NULL AS nul");
        assertEquals("[S VARCHAR(134217728), N NUMBER(1,0), D NUMBER(2,1), NUL VARCHAR(134217728)]",
            described("ice_ctas2").toString());
        engine.executeQuery("CREATE ICEBERG TABLE ice_ctas3 (x INT, y VARCHAR) AS SELECT n, v FROM src_wide");
        assertEquals("[X NUMBER(10,0), Y VARCHAR(134217728)]", described("ice_ctas3").toString());
        engine.executeQuery("CREATE ICEBERG TABLE ice_ctas4 AS SELECT '2020-01-01'::TIMESTAMP_NTZ(6) AS n6,"
            + " '2020-01-01'::TIMESTAMP_LTZ(6) AS l6, '12:00'::TIME(6) AS t6");
        assertEquals("[N6 TIMESTAMP_NTZ(6), L6 TIMESTAMP_LTZ(6), T6 TIME(6)]", described("ice_ctas4").toString());
        refused("CREATE ICEBERG TABLE ice_never AS SELECT '2020-01-01'::TIMESTAMP_NTZ AS ntz9",
            scale("NTZ9", "TIMESTAMP_NTZ(9)"));
        refused("CREATE ICEBERG TABLE ice_never AS SELECT '2020-01-01'::TIMESTAMP_TZ AS tz",
            scale("TZ", "TIMESTAMP_TZ(9)"));
        refused("CREATE ICEBERG TABLE ice_never AS SELECT '2020-01-01'::TIMESTAMP_TZ(6) AS tz6",
            unsupported("TIMESTAMP_TZ(6)"));
        refused("CREATE ICEBERG TABLE ice_never AS SELECT TO_VARIANT(1) AS v", unsupported("VARIANT"));
        refused("CREATE ICEBERG TABLE ice_never AS SELECT ARRAY_CONSTRUCT(1) AS arr", unsupported("ARRAY"));
        refused("CREATE ICEBERG TABLE ice_never AS SELECT OBJECT_CONSTRUCT('a', 1) AS o", unsupported("OBJECT"));
        refused("CREATE ICEBERG TABLE ice_never (s VARCHAR, t VARCHAR(5)) AS SELECT 'abc', 'x'", STRING_LENGTH);
        // A table created from an Iceberg table takes a table's widths again.
        engine.executeQuery("CREATE TABLE plain_from_ice AS SELECT * FROM ice_ctas");
        assertEquals("[V VARCHAR(16777216), W VARCHAR(16777216), S VARCHAR(16777216), B BINARY(8388608),"
            + " N NUMBER(38,0)]", described("plain_from_ice").toString());
    }
}
