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
 * <p>On a live run the very same handler source is compiled by Snowflake against the REAL Snowpark, so
 * every line here is a two-sided claim: a method the stub offers but Snowpark does not will not compile
 * there, and a value the stub prints differently will not match. That is what makes this class the
 * stub's specification rather than a description of it — so assert on values and on the vocabulary the
 * real API prints, never on generated SQL text, which Snowpark builds its own way and never exposes.
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

    /**
     * schema() describes the result in Snowpark's two vocabularies at once: {@code typeName()} is the
     * type's CLASS name — {@code LongType}, not {@code Long} — while {@code toString()} matches it
     * except for DECIMAL, the one type that carries parameters. Both live-verified.
     */
    @Test
    public void schemaDescribesTheResult() {
        sampleRow();
        assertEquals("ID:LongType=LongType NAME:StringType=StringType"
            + " PRICE:DecimalType=Decimal(10, 2) | size=3", call("sp_schema", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            class H { public String go(Session s) {
              StructType st = s.sql("SELECT id, name, price FROM sp_t").schema();
              StringBuilder b = new StringBuilder();
              for (StructField f : st) b.append(f.name()).append(':').append(f.dataType().typeName())
                  .append('=').append(f.dataType().toString()).append(' ');
              return b.toString().trim() + " | size=" + st.size();
            } }"""));
    }

    /**
     * Functions and Column compose a predicate and a projection, used the only way the real API allows:
     * handed to {@code filter} and {@code select}. Snowpark exposes no accessor for a Column's SQL —
     * its {@code toString()} prints an internal expression tree — so the result rows, not the generated
     * text, are what both sides can be held to.
     */
    @Test
    public void functionsAndColumnDriveFilterAndSelect() {
        sampleRow();
        assertEquals("1:ALICE", call("sp_fn", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              Column c = col("price").gt(lit(10)).and(col("name").equal_to(lit("alice")));
              Row[] r = s.sql("SELECT * FROM sp_t").filter(c).select(upper(col("name")).as("u")).collect();
              return r.length + ":" + r[0].getString(0);
            } }"""));
    }

    /** A predicate the rows fail keeps none of them, through the same plan operations. */
    @Test
    public void aFilterThatMatchesNothingReturnsNoRows() {
        sampleRow();
        assertEquals("0", call("sp_fn_empty", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              Column c = col("name").equal_to(lit("nobody"));
              return String.valueOf(s.sql("SELECT * FROM sp_t").where(c).collect().length);
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
              Row[] r = s.sql("SELECT 'O''Brien' AS expected FROM sp_t")
                  .select(lit("O'Brien").equal_to(col("expected")).as("same")).collect();
              return r[0].getBoolean(0) ? "1" : "0";
            } }"""));
    }

    /**
     * A constant keeps its own class so {@code StringType s = DataTypes.StringType} compiles, and the
     * three printed forms are the ones live prints: {@code typeName()} is the class name,
     * {@code DecimalType.toString()} is the only one carrying parameters, and a StructField folds an
     * unquoted name to upper case and names its type in the SHORT vocabulary ({@code Long}). The cast
     * is checked by its result — {@code VARCHAR}, the SQL name, is not something the API will show us.
     */
    @Test
    public void dataTypesKeepTheirClassAndTheirPrintedForm() {
        sampleRow();
        assertEquals("StringType/DecimalType/Decimal(10, 2)"
            + "/StructField(ID, Long, Nullable = false)/7", call("sp_types", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              StringType st = DataTypes.StringType;
              DataType dec = DataTypes.createDecimalType(10, 2);
              StructField f = new StructField("id", DataTypes.LongType, false);
              Row[] r = s.sql("SELECT id FROM sp_t").select(col("id").cast(st).as("c")).collect();
              return st.typeName() + "/" + dec.typeName() + "/" + dec + "/" + f + "/" + r[0].getString(0);
            } }"""));
    }

    /** A quoted field name keeps its case AND its quotes, where an unquoted one is folded. */
    @Test
    public void aQuotedFieldNameIsKeptVerbatim() {
        sampleRow();
        assertEquals("MIXED/\"mixedName\"", call("sp_field", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            class H { public String go(Session s) {
              return new StructField("mixed", DataTypes.StringType).name() + "/"
                  + new StructField("\\"mixedName\\"", DataTypes.StringType).name();
            } }"""));
    }

    /** The container types print their element types in the same class-name vocabulary. */
    @Test
    public void containerTypesPrintTheirElementType() {
        sampleRow();
        assertEquals("ArrayType/ArrayType[StringType]", call("sp_arr", """
            import com.snowflake.snowpark_java.*;
            import com.snowflake.snowpark_java.types.*;
            class H { public String go(Session s) {
              ArrayType a = DataTypes.createArrayType(DataTypes.StringType);
              return a.typeName() + "/" + a;
            } }"""));
    }

    /** Negation is spelled {@code unary_not()} on the real Column, not {@code not()}. */
    @Test
    public void negationIsSpelledUnaryNot() {
        sampleRow();
        assertEquals("false", call("sp_not", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              Row[] r = s.sql("SELECT ok FROM sp_t").select(col("ok").unary_not().as("n")).collect();
              return String.valueOf(r[0].getBoolean(0));
            } }"""));
    }

    /**
     * The wider Column surface, used through a plan the way the real API requires. Every signature
     * here was measured by reflection on the account before it was written, so this test compiles
     * against the REAL Snowpark on a live run — which is the whole point: a method with the right
     * name but the wrong parameter type would pass here and fail there.
     */
    @Test
    public void theWiderColumnSurfaceWorks() {
        sampleRow();
        assertEquals("1:7:false:ALICE:true", call("sp_col2", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              DataFrame d = s.sql("SELECT * FROM sp_t");
              Row[] r = d.filter(col("id").between(lit(1), lit(10)))
                         .select(col("id"), col("id").unary_minus().as("neg"),
                                 col("name").isNull().as("n"), upper(col("name")).as("u"),
                                 col("id").equal_null(lit(7)).as("e")).collect();
              return r.length + ":" + (-r[0].getInt(1)) + ":" + r[0].getBoolean(2)
                   + ":" + r[0].getString(3) + ":" + r[0].getBoolean(4);
            } }"""));
    }

    /** {@code getName()} answers a name for a plain column and nothing for a composed expression. */
    @Test
    public void aColumnKnowsItsNameOnlyWhenItHasOne() {
        sampleRow();
        assertEquals("\"NAME\"/absent", call("sp_colname", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              return col("name").getName().orElse("absent") + "/"
                   + upper(col("name")).getName().orElse("absent");
            } }"""));
    }

    /**
     * The wider DataFrame surface — sort, limit, distinct, set operations and the row actions — each
     * a PLAN operation that submits nothing until an action asks for rows.
     */
    @Test
    public void theWiderDataFrameSurfaceWorks() {
        engine.execute("CREATE OR REPLACE TABLE sp_n (n INTEGER)");
        engine.execute("INSERT INTO sp_n VALUES (3), (1), (2), (2)");
        assertEquals("1|3|3|1|4|2", call("sp_df2", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              DataFrame d = s.sql("SELECT n FROM sp_n");
              int firstAsc = d.sort(col("n").asc()).first().get().getInt(0);
              int distinct = d.distinct().collect().length;
              int limited  = d.limit(3).collect().length;
              int inter    = d.intersect(s.sql("SELECT 1 AS n")).collect().length;
              long total   = d.union(s.sql("SELECT 9 AS n")).count();
              int firstTwo = d.first(2).length;
              return firstAsc + "|" + distinct + "|" + limited + "|" + inter + "|" + total
                   + "|" + firstTwo;
            } }"""));
    }

    /** withColumn appends, drop removes by name, and agg folds the whole frame. */
    @Test
    public void withColumnDropAndAggReshapeTheFrame() {
        sampleRow();
        assertEquals("1:14:1", call("sp_df3", """
            import com.snowflake.snowpark_java.*;
            import static com.snowflake.snowpark_java.Functions.*;
            class H { public String go(Session s) {
              DataFrame d = s.sql("SELECT id, name FROM sp_t");
              Row[] wide = d.withColumn("twice", col("id").multiply(lit(2))).collect();
              int thin = d.drop("name").collect()[0].size();
              Row[] agg  = d.agg(count(col("id")).as("c")).collect();
              return thin + ":" + wide[0].getInt(2) + ":" + agg[0].getInt(0);
            } }"""));
    }
}
