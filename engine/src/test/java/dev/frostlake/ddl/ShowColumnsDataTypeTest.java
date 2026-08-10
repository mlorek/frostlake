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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW COLUMNS answers a JSON DESCRIPTOR in its {@code data_type} cell, not a type name — and spells
 * the type in Snowflake's internal vocabulary, where a VARCHAR is TEXT, every exact numeric is FIXED
 * and the approximate ones are REAL.
 *
 * <p>Every expectation below is live-measured. The traps worth naming: a string's {@code byteLength}
 * is four bytes per character while a binary's is one; {@code fixed} is false for the whole string
 * family, CHAR included, and true only for BINARY; and a time-of-day or timestamp column reports its
 * declared precision as the {@code scale}, leaving {@code precision} pinned at zero.
 */
public class ShowColumnsDataTypeTest extends BaseDatabaseTest {

    /** Declared type, then the descriptor live reports for a nullable column of it. */
    private static final String[][] TYPES = {
        {"NUMBER(10,2)", """
            {"type":"FIXED","precision":10,"scale":2,"nullable":true}"""},
        {"INT", """
            {"type":"FIXED","precision":38,"scale":0,"nullable":true}"""},
        {"FLOAT", """
            {"type":"REAL","nullable":true}"""},
        {"DOUBLE", """
            {"type":"REAL","nullable":true}"""},
        {"VARCHAR(9)", """
            {"type":"TEXT","length":9,"byteLength":36,"nullable":true,"fixed":false}"""},
        {"VARCHAR", """
            {"type":"TEXT","length":16777216,"byteLength":67108864,"nullable":true,"fixed":false}"""},
        {"CHAR(4)", """
            {"type":"TEXT","length":4,"byteLength":16,"nullable":true,"fixed":false}"""},
        {"BOOLEAN", """
            {"type":"BOOLEAN","nullable":true}"""},
        {"DATE", """
            {"type":"DATE","nullable":true}"""},
        {"TIME(3)", """
            {"type":"TIME","precision":0,"scale":3,"nullable":true}"""},
        {"TIME", """
            {"type":"TIME","precision":0,"scale":9,"nullable":true}"""},
        {"TIMESTAMP", """
            {"type":"TIMESTAMP_NTZ","precision":0,"scale":9,"nullable":true}"""},
        {"TIMESTAMP_LTZ", """
            {"type":"TIMESTAMP_LTZ","precision":0,"scale":9,"nullable":true}"""},
        {"TIMESTAMP_TZ(6)", """
            {"type":"TIMESTAMP_TZ","precision":0,"scale":6,"nullable":true}"""},
        {"BINARY(20)", """
            {"type":"BINARY","length":20,"byteLength":20,"nullable":true,"fixed":true}"""},
        {"BINARY", """
            {"type":"BINARY","length":8388608,"byteLength":8388608,"nullable":true,"fixed":true}"""},
        {"VARIANT", """
            {"type":"VARIANT","nullable":true}"""},
        {"OBJECT", """
            {"type":"OBJECT","nullable":true}"""},
        {"ARRAY", """
            {"type":"ARRAY","nullable":true}"""},
        {"GEOGRAPHY", """
            {"type":"GEOGRAPHY","outputType":"OBJECT","nullable":true}"""},
        {"GEOMETRY", """
            {"type":"GEOMETRY","outputType":"OBJECT","nullable":true}"""}
    };

    @Override
    protected void setupTest() {
        final StringBuilder columns = new StringBuilder();
        for (int i = 0; i < TYPES.length; i++) {
            columns.append(i == 0 ? "" : ", ").append("c").append(i).append(' ').append(TYPES[i][0]);
        }
        engine.execute("CREATE TABLE sc_t (" + columns + ")");
    }

    private String dataTypeOf(final String table, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE " + table);
        return cell(rs, soleRowWhere(rs, "column_name", column), "data_type");
    }

    @Test
    public void everyTypeDescribesItselfInSnowflakesVocabulary() {
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE sc_t");
        for (int i = 0; i < TYPES.length; i++) {
            assertEquals(TYPES[i][1], cell(rs, soleRowWhere(rs, "column_name", "C" + i), "data_type"),
                "column c" + i + " declared " + TYPES[i][0]);
        }
    }

    /** A NOT NULL column carries the fact TWICE: inside the descriptor and in the null? cell. */
    @Test
    public void aNotNullColumnIsNotNullableInsideTheDescriptorToo() {
        engine.execute("CREATE TABLE sc_nn (id NUMBER(5,1) NOT NULL, txt VARCHAR(4))");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE sc_nn");
        assertEquals("""
            {"type":"FIXED","precision":5,"scale":1,"nullable":false}""",
            cell(rs, soleRowWhere(rs, "column_name", "ID"), "data_type"));
        assertEquals("NOT_NULL", cell(rs, soleRowWhere(rs, "column_name", "ID"), "null?"));
        assertEquals("true", cell(rs, soleRowWhere(rs, "column_name", "TXT"), "null?"));
    }

    /** A structured OBJECT nests one descriptor per field, keeping each field's declared case. */
    @Test
    public void aStructuredObjectNestsItsFields() {
        engine.execute("CREATE TABLE sc_so (o OBJECT(a VARCHAR, b NUMBER))");
        assertEquals("""
            {"type":"OBJECT","nullable":true,"fields":[\
            {"fieldName":"a","fieldType":{"type":"TEXT","length":16777216,"byteLength":67108864,\
            "nullable":true,"fixed":false}},\
            {"fieldName":"b","fieldType":{"type":"FIXED","precision":38,"scale":0,"nullable":true}}]}""",
            dataTypeOf("sc_so", "O"));
    }

    @Test
    public void aStructuredArrayNestsItsElementType() {
        engine.execute("CREATE TABLE sc_sa (a ARRAY(NUMBER))");
        assertEquals("""
            {"type":"ARRAY","nullable":true,"elementType":\
            {"type":"FIXED","precision":38,"scale":0,"nullable":true}}""",
            dataTypeOf("sc_sa", "A"));
    }

    /** A MAP nests both halves, and its KEY is never nullable where the value is. */
    @Test
    public void aMapNestsAKeyThatIsNeverNullable() {
        engine.execute("CREATE TABLE sc_sm (m MAP(VARCHAR, NUMBER))");
        assertEquals("""
            {"type":"MAP","nullable":true,"keyType":{"type":"TEXT","length":16777216,\
            "byteLength":67108864,"nullable":false,"fixed":false},\
            "valueType":{"type":"FIXED","precision":38,"scale":0,"nullable":true}}""",
            dataTypeOf("sc_sm", "M"));
    }

    /** A VECTOR reports its dimension, and describes a FLOAT element as REAL. */
    @Test
    public void aFloatVectorDescribesARealElement() {
        engine.execute("CREATE TABLE sc_vf (v VECTOR(FLOAT, 3))");
        assertEquals("""
            {"type":"VECTOR","nullable":true,"vectorElementType":{"type":"REAL","nullable":false},\
            "dimension":3}""",
            dataTypeOf("sc_vf", "V"));
    }

    /** An INT element is a full NUMBER(38,0) inside the descriptor. */
    @Test
    public void anIntVectorDescribesAFixedElement() {
        engine.execute("CREATE TABLE sc_vi (v VECTOR(INT, 2))");
        assertEquals("""
            {"type":"VECTOR","nullable":true,"vectorElementType":\
            {"type":"FIXED","precision":38,"scale":0,"nullable":false},"dimension":2}""",
            dataTypeOf("sc_vi", "V"));
    }
}
