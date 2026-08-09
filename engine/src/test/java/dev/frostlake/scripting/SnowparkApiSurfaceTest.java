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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Snowpark Java classes an inline handler names: {@code Row}, {@code Column}, {@code Functions},
 * and the {@code types} trio {@code StructType} / {@code StructField} / {@code DataTypes}.
 *
 * <p>The point of each test is that a handler written against the REAL Snowpark API compiles and runs
 * unchanged. Before these classes existed {@code collect()} handed back Frostlake's own storage row,
 * whose only accessor is {@code getValue(int)}, so {@code rows[0].getString(0)} did not compile at all.
 *
 * <p>These run embedded only: on live the same handler is compiled by Snowflake against the real
 * Snowpark, which is the thing being imitated rather than a second opinion on it.
 */
public class SnowparkApiSurfaceTest extends BaseDatabaseTest {

    private static final String PACKAGES = " PACKAGES=('com.snowflake:snowpark:latest')";

    /** One row of every interesting type, to read back through the typed accessors. */
    private void sampleRow() {
        engine.execute("CREATE OR REPLACE TABLE sp_t"
            + " (id INTEGER, name VARCHAR, price NUMBER(10,2), ok BOOLEAN, d DATE, v VARIANT)");
        engine.execute("INSERT INTO sp_t SELECT 7, 'alice', 12.50, TRUE,"
            + " '2026-01-02'::DATE, PARSE_JSON('{\"k\":1}')");
    }

    private Object call(final String name, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVA"
            + PACKAGES + " HANDLER='H.go' AS $$\n" + body + "\n$$");
        return engine.executeQuery("CALL " + name + "()").getRows().get(0).getValue(0);
    }

    /** Every accessor coerces from whatever the engine stored, rather than demanding an exact class. */
    @Test
    public void rowExposesTheTypedAccessors() {
        sampleRow();
        assertEquals("7/alice/12.50/true/size=4", call("sp_row", """
            import com.snowflake.snowpark_java.*;
            class H { public String go(Session s) {
              Row[] r = s.sql("SELECT id, name, price, ok FROM sp_t").collect();
              return r[0].getInt(0) + "/" + r[0].getString(1) + "/" + r[0].getDecimal(2)
                   + "/" + r[0].getBoolean(3) + "/size=" + r[0].size();
            } }"""));
    }

    /** Dates, variants and the NULL probe — the three that need more than a toString. */
    @Test
    public void rowReadsTemporalVariantAndNull() {
        sampleRow();
        assertEquals("2026-01-02/{\"k\":1}/null=true", call("sp_row2", """
            import com.snowflake.snowpark_java.*;
            class H { public String go(Session s) {
              Row[] r = s.sql("SELECT d, v, NULL AS n FROM sp_t").collect();
              return r[0].getDate(0) + "/" + r[0].getVariant(1) + "/null=" + r[0].isNullAt(2);
            } }"""));
    }

    /** A primitive getter on a NULL says so rather than returning a silent zero. */
    @Test
    public void aNullPrimitiveIsRefusedNotZeroed() {
        sampleRow();
        assertEquals("refused", call("sp_null", """
            import com.snowflake.snowpark_java.*;
            class H { public String go(Session s) {
              Row[] r = s.sql("SELECT NULL AS n FROM sp_t").collect();
              try { r[0].getInt(0); return "returned " + r[0].getInt(0); }
              catch (NullPointerException e) { return "refused"; }
            } }"""));
    }

    /** schema() describes the result the way Snowpark names types, and StructType iterates. */
    @Test
    public void schemaDescribesTheResult() {
        sampleRow();
        assertEquals("ID:Long NAME:String PRICE:Decimal(10, 2) | size=3", call("sp_schema", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            class H { public String go(Session s) {
              StructType st = s.sql("SELECT id, name, price FROM sp_t").schema();
              StringBuilder b = new StringBuilder();
              for (StructField f : st) b.append(f.name()).append(':').append(f.dataType().typeName()).append(' ');
              return b.toString().trim() + " | size=" + st.size();
            } }"""));
    }

    /** Functions and Column compose into the SQL they stand for, operands parenthesised. */
    @Test
    public void functionsAndColumnBuildSql() {
        sampleRow();
        assertEquals("((price > 10) AND (name = 'alice')) -> ALICE", call("sp_fn", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              Column c = col("price").gt(lit(10)).and(col("name").equal_to(lit("alice")));
              Row[] r = s.sql("SELECT " + upper(col("name")).getSql() + " AS u FROM sp_t WHERE "
                  + c.getSql()).collect();
              return c.getSql() + " -> " + r[0].getString(0);
            } }"""));
    }

    /** A literal carrying an apostrophe must not end the literal early. */
    @Test
    public void aLiteralIsQuotedSafely() {
        sampleRow();
        assertEquals("1", call("sp_lit", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              Row[] r = s.sql("SELECT " + lit("O'Brien").getSql() + " = 'O''Brien' AS same").collect();
              return r[0].getBoolean(0) ? "1" : "0";
            } }"""));
    }

    /**
     * A constant keeps its own class so {@code StringType s = DataTypes.StringType} compiles, and CAST
     * renders the SQL name rather than the name Snowpark prints — {@code VARCHAR}, not {@code String}.
     */
    @Test
    public void dataTypesKeepTheirClassAndTheirSqlName() {
        sampleRow();
        assertEquals("String/Decimal(10, 2)/StructField(id, Long, Nullable = false)/CAST(id AS VARCHAR)",
            call("sp_types", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              StringType st = DataTypes.StringType;
              DataType dec = DataTypes.createDecimalType(10, 2);
              StructField f = new StructField("id", DataTypes.LongType, false);
              return st.typeName() + "/" + dec + "/" + f + "/" + col("id").cast(st).getSql();
            } }"""));
    }
}
