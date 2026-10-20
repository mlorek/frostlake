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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ObjectStreamClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$TYPEOF names a structured cast target's leaf written with no width bare — {@code OBJECT(a VARCHAR)},
 * {@code OBJECT(a BINARY)} — where it used to name the width the leaf is held at. DESCRIBE RESULT and a refusal
 * still name the width, an unsized BINARY leaf's being the 64MB maximum, and a width that was written is kept as
 * written (all live-verified). A column's structured type, and a stored shape built over a cast, stay sized.
 */
public class StructuredLeafWidthTest extends BaseDatabaseTest {

    private String scalarText(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void typeofNamesAnUnsizedLeafBare() {
        assertEquals("OBJECT(a VARCHAR)[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a VARCHAR))"));
        assertEquals("OBJECT(a VARCHAR)[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a STRING))"));
        assertEquals("OBJECT(a VARCHAR)[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a TEXT))"));
        assertEquals("ARRAY(VARCHAR)[LOB]", scalarText("SELECT SYSTEM$TYPEOF(['x']::ARRAY(VARCHAR))"));
        assertEquals("MAP(VARCHAR, VARCHAR)[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::MAP(VARCHAR, VARCHAR))"));
        assertEquals("OBJECT(a BINARY)[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a BINARY))"));
        assertEquals("MAP(VARCHAR, NUMBER(10,2))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 1}::MAP(VARCHAR, NUMBER(10,2)))"));
    }

    @Test
    public void typeofKeepsAWidthThatWasWritten() {
        assertEquals("OBJECT(a VARCHAR(16777216))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a VARCHAR(16777216)))"));
        assertEquals("OBJECT(a VARCHAR(134217728))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 'x'}::OBJECT(a VARCHAR(134217728)))"));
        assertEquals("OBJECT(a VARCHAR(3))[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'ab'}::OBJECT(a VARCHAR(3)))"));
        assertEquals("MAP(VARCHAR(5), NUMBER(38,0))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 1}::MAP(VARCHAR(5), NUMBER))"));
    }

    @Test
    public void describeResultNamesTheWidthAnUnsizedLeafIsHeldAt() {
        engine.executeQuery("SELECT {'k': 'ab'}::MAP(VARCHAR, BINARY) m, {'a': 'x'}::OBJECT(a VARCHAR) o,"
            + " {'t': '2024-01-01 10:00:00'}::OBJECT(t TIMESTAMP_NTZ) t");
        final ResultSet described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        assertEquals("MAP(VARCHAR(134217728), BINARY(67108864))",
            cell(described, soleRowWhere(described, "name", "M"), "type"));
        assertEquals("OBJECT(a VARCHAR(134217728))", cell(described, soleRowWhere(described, "name", "O"), "type"));
        assertEquals("OBJECT(t TIMESTAMP_NTZ(9))", cell(described, soleRowWhere(described, "name", "T"), "type"));
    }

    @Test
    public void aRefusalNamesTheWidthAnUnsizedLeafIsHeldAt() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT ARRAY_CONTAINS(1, {'a': 'x'}::OBJECT(a VARCHAR))");
            }
        });
        assertTrue(refused.getMessage().contains("Invalid argument types for function 'ARRAY_CONTAINS':"
            + " (NUMBER(1,0), OBJECT(a VARCHAR(134217728)))"), refused.getMessage());
    }

    @Test
    public void aRefusalNamesABinaryLeafAtItsOwnWidth() {
        // Live-verified: an unsized leaf at the 64MB maximum, a sized one at its width; both read BINARY(8388608).
        assertTrue(refusalOf("SELECT ARRAY_CONTAINS(1, {'a': 'ab'}::OBJECT(a BINARY))").contains(
            "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), OBJECT(a BINARY(67108864)))"));
        assertTrue(refusalOf("SELECT ARRAY_CONTAINS(1, {'a': 'ab'}::OBJECT(a BINARY(4)))").contains(
            "Invalid argument types for function 'ARRAY_CONTAINS': (NUMBER(1,0), OBJECT(a BINARY(4)))"));
        assertEquals("OBJECT(a BINARY(4))[LOB]", scalarText("SELECT SYSTEM$TYPEOF({'a': 'ab'}::OBJECT(a BINARY(4)))"));
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aColumnsLeavesAreSizedByItsDeclaration() {
        engine.execute("CREATE TABLE col (o OBJECT(x VARCHAR, y INT))");
        assertEquals("OBJECT(x VARCHAR(16777216), y NUMBER(38,0))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF(o) FROM (SELECT o FROM col UNION ALL SELECT NULL)"));
    }

    @Test
    public void aStoredShapeOverACastKeepsItsSizedLeaves() {
        engine.execute("CREATE TABLE ct AS SELECT {'k': 'ab'}::MAP(VARCHAR, BINARY) m, {'a': 'x'}::OBJECT(a VARCHAR) o");
        assertEquals("MAP(VARCHAR(16777216), BINARY(8388608))", describeCell("ct", "M", "type"));
        assertEquals("OBJECT(a VARCHAR(16777216))", describeCell("ct", "O", "type"));
        assertEquals("OBJECT(a VARCHAR(16777216))[LOB]", scalarText("SELECT SYSTEM$TYPEOF(o) FROM ct"));
        engine.execute("CREATE VIEW cv AS SELECT {'k': 'ab'}::MAP(VARCHAR, BINARY) m, {'a': 'x'}::OBJECT(a VARCHAR) o");
        final ResultSet view = engine.executeQuery("DESCRIBE VIEW cv");
        assertEquals("MAP(VARCHAR(16777216), BINARY(8388608))", cell(view, soleRowWhere(view, "name", "M"), "type"));
        assertEquals("OBJECT(a VARCHAR(16777216))", cell(view, soleRowWhere(view, "name", "O"), "type"));
    }

    @Test
    public void anUnsizedLeafStillBindsAndFolds() {
        engine.execute("CREATE FUNCTION sf(o OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'bound' $$");
        assertEquals("bound", scalarText("SELECT sf({'a': 'x'}::OBJECT(a VARCHAR))"));
        assertEquals("OBJECT(a VARCHAR)[LOB]", scalarText("SELECT SYSTEM$TYPEOF(o) FROM (SELECT {'a': 'x'}::OBJECT(a VARCHAR) o"
            + " UNION ALL SELECT {'a': 'y'}::OBJECT(a VARCHAR))"));
    }

    @Test
    public void aSnapshotWrittenBeforeTheMarkerStillReads() {
        // The serial form a string had before it could be unsized, so a snapshot holding one deserializes.
        assertEquals(7234115287022531106L, ObjectStreamClass.lookup(StringType.class).getSerialVersionUID());
    }
}
