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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last two type spellings the catalog kept, each settled by its own measurement.
 *
 * <p>VARBINARY is not a type — every surface that NAMES a type says BINARY for it, a result column and
 * an argument-type refusal included, and GET_DDL writes the column back out as BINARY(8). What survives
 * is one metadata cell: SHOW COLUMNS' {@code fixed}, false for the VARBINARY spelling and true for the
 * BINARY one. So fixedness is a property here, not a name.
 *
 * <p>A bare TIMESTAMP is not an alias but a CHOICE, resolved when the column is CREATED against the
 * session's TIMESTAMP_TYPE_MAPPING and kept afterwards:
 *
 * <pre>
 *   mapping TIMESTAMP_LTZ, CREATE TABLE t (c TIMESTAMP)   c is TIMESTAMP_LTZ(9)
 *   … then set the mapping back to TIMESTAMP_NTZ          c is STILL TIMESTAMP_LTZ(9)
 * </pre>
 *
 * <p>The mapping is therefore read once, at CREATE. Reading it again at SELECT would make an existing
 * column change type under a session setting, which live does not do.
 */
public class BareTimestampAndBinaryAliasTest extends BaseDatabaseTest {

    @AfterEach
    public void restoreTheDefaultMapping() {
        // Live sessions outlive a test, so the mapping must not leak into the next one.
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
    }

    /** The declared type of the first result column of {@code sql}. */
    private String resultType(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        if (type instanceof DateTimeType) {
            return type.getName() + "(" + ((DateTimeType) type).getPrecision() + ")";
        }
        return type == null ? "null" : type.getName();
    }

    /** One cell of a single-row query. */
    private String cell(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue(column)));
        }
        return out.toString();
    }

    /** Both spellings report the type BINARY, wherever a type is named. */
    @Test
    public void bothBinarySpellingsReportBinary() {
        engine.execute("CREATE OR REPLACE TABLE ali_b (b BINARY(8), vb VARBINARY(8))");
        assertEquals("BINARY(8)", resultType("SELECT b FROM ali_b"));
        assertEquals("BINARY(8)", resultType("SELECT vb FROM ali_b"));
        assertEquals("BINARY(8)", resultType("SELECT CAST(NULL AS VARBINARY(8)) FROM ali_b"));
        assertEquals("BINARY(8)", resultType("SELECT CAST(NULL AS BINARY(8)) FROM ali_b"));
        assertEquals("BINARY(8) | BINARY(8)", cell("DESCRIBE TABLE ali_b", "type"));
        assertEquals("BINARY | BINARY", cell("SELECT data_type FROM information_schema.columns"
            + " WHERE table_name = 'ALI_B' ORDER BY ordinal_position", "data_type"));
    }

    /** The one cell that tells them apart is SHOW COLUMNS' fixed flag. */
    @Test
    public void onlyTheFixedFlagTellsThemApart() {
        engine.execute("CREATE OR REPLACE TABLE ali_b (b BINARY(8), vb VARBINARY(8))");
        assertEquals("{\"type\":\"BINARY\",\"length\":8,\"byteLength\":8,\"nullable\":true,"
            + "\"fixed\":true} | {\"type\":\"BINARY\",\"length\":8,\"byteLength\":8,"
            + "\"nullable\":true,\"fixed\":false}",
            cell("SHOW COLUMNS IN TABLE ali_b", "data_type"));
    }

    /** Fixedness travels through a CTAS and a view, where the WIDTH travels too. */
    @Test
    public void fixednessTravelsThroughADerivedRelation() {
        engine.execute("CREATE OR REPLACE TABLE ali_b (b BINARY(8), vb VARBINARY(8))");
        engine.execute("CREATE OR REPLACE TABLE ali_c AS SELECT vb, b FROM ali_b");
        assertEquals("{\"type\":\"BINARY\",\"length\":8,\"byteLength\":8,\"nullable\":true,"
            + "\"fixed\":false} | {\"type\":\"BINARY\",\"length\":8,\"byteLength\":8,"
            + "\"nullable\":true,\"fixed\":true}",
            cell("SHOW COLUMNS IN TABLE ali_c", "data_type"));
    }

    /** A bare TIMESTAMP takes the flavour the session's mapping names, at CREATE time. */
    @Test
    public void aBareTimestampTakesTheSessionsMapping() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_t (c TIMESTAMP)");
        assertEquals("TIMESTAMP_NTZ(9)", resultType("SELECT c FROM ali_t"));
        assertEquals("TIMESTAMP_NTZ(9)", cell("DESCRIBE TABLE ali_t", "type"));

        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_t (c TIMESTAMP)");
        assertEquals("TIMESTAMP_LTZ(9)", resultType("SELECT c FROM ali_t"));
        assertEquals("TIMESTAMP_LTZ(9)", cell("DESCRIBE TABLE ali_t", "type"));

        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_TZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_t (c TIMESTAMP)");
        assertEquals("TIMESTAMP_TZ(9)", resultType("SELECT c FROM ali_t"));
        assertEquals("TIMESTAMP_TZ(9)", cell("DESCRIBE TABLE ali_t", "type"));
    }

    /** Once created, the column KEEPS that flavour — a later mapping change does not reach it. */
    @Test
    public void anExistingColumnDoesNotFollowALaterChange() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_u (c TIMESTAMP)");
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_NTZ'");
        assertEquals("TIMESTAMP_LTZ(9)", resultType("SELECT c FROM ali_u"));
        assertEquals("TIMESTAMP_LTZ(9)", cell("DESCRIBE TABLE ali_u", "type"));
    }

    /** An EXPLICIT spelling is never touched by the mapping, and a precision survives it. */
    @Test
    public void anExplicitSpellingIgnoresTheMapping() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_v (a TIMESTAMP_NTZ, b TIMESTAMP(3),"
            + " c TIMESTAMP_TZ)");
        assertEquals("TIMESTAMP_NTZ(9)", resultType("SELECT a FROM ali_v"));
        assertEquals("TIMESTAMP_LTZ(3)", resultType("SELECT b FROM ali_v"));
        assertEquals("TIMESTAMP_TZ(9)", resultType("SELECT c FROM ali_v"));
    }

    /** And the DDL written back out names the RESOLVED flavour, not the word that was typed. */
    @Test
    public void theWrittenDdlNamesTheResolvedFlavour() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        engine.execute("CREATE OR REPLACE TABLE ali_w (c TIMESTAMP)");
        final String ddl = cell("SELECT GET_DDL('TABLE', 'ali_w') AS g", "g");
        assertTrue(ddl.contains("TIMESTAMP_LTZ(9)"), ddl);
    }
}
