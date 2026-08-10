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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FILE column type — the TYPE SURFACE only (the {@code TO_FILE} / {@code FL_*} function family is a
 * separate concern and is deliberately not implemented here).
 *
 * <p>Every expectation below was measured against live Snowflake:
 * {@code CREATE TABLE ft (f FILE)} is accepted and {@code DESCRIBE TABLE} / {@code SHOW COLUMNS} /
 * {@code GET_DDL} / {@code INFORMATION_SCHEMA.COLUMNS.DATA_TYPE} all report {@code FILE}, while
 * {@code NULL::FILE} is REJECTED because Snowflake routes a cast to FILE through {@code TO_FILE}.
 */
public class FileTypeTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeTest.class);

    /** Live: {@code DESCRIBE TABLE ft} on a {@code (f FILE)} table gives {@code F | FILE | COLUMN}. */
    @Test
    public void describeTableReportsFileType() {
        engine.execute("CREATE TABLE ft_desc (id INTEGER, f FILE)");

        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE ft_desc");
        assertEquals(2, rs.getRows().size());
        final int typeCol = columnIndex(rs, "type");
        assertEquals("F", String.valueOf(rs.getRows().get(1).getValue(columnIndex(rs, "name"))).toUpperCase());
        assertEquals("FILE", String.valueOf(rs.getRows().get(1).getValue(typeCol)).toUpperCase());
    }

    /**
     * Live: {@code SELECT data_type FROM information_schema.columns WHERE table_name='FT'}
     * returns {@code FILE} — FILE is NOT folded onto a family name the way INTEGER folds onto NUMBER.
     */
    @Test
    public void informationSchemaReportsFileDataType() {
        engine.execute("CREATE TABLE ft_isc (id INTEGER, f FILE)");

        final ResultSet file = engine.executeQuery(
            "SELECT data_type FROM information_schema.columns WHERE table_name = 'FT_ISC' AND column_name = 'F'");
        assertEquals(1, file.getRows().size());
        assertEquals("FILE", String.valueOf(file.getRows().get(0).getValue(0)).toUpperCase());

        // Control: the sibling INTEGER column DOES fold onto its family name, so FILE passing through
        // is the type's own reporting and not a missing mapping.
        final ResultSet integer = engine.executeQuery(
            "SELECT data_type FROM information_schema.columns WHERE table_name = 'FT_ISC' AND column_name = 'ID'");
        assertEquals(1, integer.getRows().size());
        assertEquals("NUMBER", String.valueOf(integer.getRows().get(0).getValue(0)).toUpperCase());
    }

    /** FILE describes itself in SHOW COLUMNS' descriptor as an OBJECT-valued type. */
    @Test
    public void showColumnsReportsFileDataType() {
        engine.execute("CREATE TABLE ft_show (f FILE)");

        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE ft_show");
        assertEquals(1, rs.getRows().size());
        final String dataType = String.valueOf(rs.getRows().get(0).getValue(columnIndex(rs, "data_type")));
        assertEquals("{\"type\":\"FILE\",\"outputType\":\"OBJECT\",\"nullable\":true}", dataType);
    }

    /** Live: {@code GET_DDL('TABLE','ft')} renders the column as {@code F FILE} (no parameters). */
    @Test
    public void getDdlRendersFileColumn() {
        engine.execute("CREATE TABLE ft_ddl (id INTEGER, f FILE)");

        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('TABLE', 'ft_ddl')");
        final String ddl = String.valueOf(rs.getRows().get(0).getValue(0));
        assertTrue(ddl.contains("F FILE"), "GET_DDL must render the FILE column, got: " + ddl);
    }

    /** Live: {@code ALTER TABLE ft ADD COLUMN g FILE} is accepted and DESCRIBE shows FILE. */
    @Test
    public void alterTableAddsFileColumn() {
        engine.execute("CREATE TABLE ft_alter (id INTEGER)");
        engine.execute("ALTER TABLE ft_alter ADD COLUMN g FILE");

        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE ft_alter");
        assertEquals(2, rs.getRows().size());
        assertEquals("FILE",
            String.valueOf(rs.getRows().get(1).getValue(columnIndex(rs, "type"))).toUpperCase());
    }

    /** Live: a FILE column accepts NULL ({@code INSERT INTO ft VALUES (4, NULL)}) and reads back NULL. */
    @Test
    public void nullRoundTripsThroughFileColumn() {
        engine.execute("CREATE TABLE ft_null (id INTEGER, f FILE)");
        engine.execute("INSERT INTO ft_null VALUES (1, NULL)");

        final ResultSet rs = engine.executeQuery("SELECT id, f FROM ft_null");
        assertEquals(1, rs.getRows().size());
        assertNull(rs.getRows().get(0).getValue(1));

        final ResultSet nulls = engine.executeQuery("SELECT id FROM ft_null WHERE f IS NULL");
        assertEquals(1, nulls.getRows().size());
    }

    /**
     * A write of a stage path that names no existing file is rejected on BOTH backends. Live
     * Snowflake resolves the written value against the stage, so
     * {@code INSERT INTO ft SELECT '@st/x.txt'} for a missing file fails "DML operation to table W
     * failed on column F with error: Remote file '@sse/nope.txt' was not found. …" — the write path
     * goes through {@code TO_FILE}, so it validates exactly as {@code TO_FILE} does.
     *
     * <p>Writes that SUCCEED (a resolvable stage path, and a valid metadata object) are covered by
     * {@code dev.frostlake.functions.file.FileColumnWriteTest}, which needs a real staged file.
     */
    @Test
    public void writeOfAMissingStageFileIsRejected() {
        engine.execute("CREATE STAGE ft_stage");
        engine.execute("CREATE TABLE ft_write (id INTEGER, f FILE)");

        final RuntimeException insert = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ft_write SELECT 1, '@ft_stage/no_such_file.txt'");
            }
        });
        assertTrue(insert.getMessage().contains("was not found"),
            "unexpected message: " + insert.getMessage());
        logger.info("INSERT of a missing staged file rejected: {}", insert.getMessage());

        engine.execute("INSERT INTO ft_write VALUES (2, NULL)");
        final RuntimeException update = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("UPDATE ft_write SET f = '@ft_stage/no_such_file.txt' WHERE id = 2");
            }
        });
        assertTrue(update.getMessage().contains("was not found"),
            "unexpected message: " + update.getMessage());
        logger.info("UPDATE to a missing staged file rejected: {}", update.getMessage());
    }

    /**
     * Live: FILE is NOT a cast target. {@code NULL::FILE} and {@code CAST(NULL AS FILE)} both
     * fail with "invalid type [CAST(NULL AS FILE)] for parameter 'TO_FILE'" — the `::` form reports
     * itself as CAST — and this is a COMPILE-time error, raised without evaluating a row.
     */
    @Test
    public void castToFileIsRejected() {
        final RuntimeException shorthand = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT NULL::FILE");
            }
        });
        assertTrue(shorthand.getMessage().contains("invalid type [CAST(NULL AS FILE)] for parameter 'TO_FILE'"),
            "unexpected message: " + shorthand.getMessage());

        final RuntimeException worded = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(NULL AS FILE)");
            }
        });
        assertTrue(worded.getMessage().contains("invalid type [CAST(NULL AS FILE)] for parameter 'TO_FILE'"),
            "unexpected message: " + worded.getMessage());
    }

    /**
     * Live: the cast target is rejected whatever the operand — {@code '@st/x.txt'::FILE} fails
     * "invalid type [CAST('@st/x.txt' AS FILE)] for parameter 'TO_FILE'", the operand rendered verbatim.
     */
    @Test
    public void castOfALiteralToFileNamesTheOperand() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '@st/x.txt'::FILE");
            }
        });
        assertTrue(error.getMessage().contains("invalid type [CAST('@st/x.txt' AS FILE)] for parameter 'TO_FILE'"),
            "unexpected message: " + error.getMessage());
    }

    /**
     * Live: TRY_CAST is rejected too, and renders WITHOUT the target type —
     * {@code TRY_CAST(NULL AS FILE)} fails "invalid type [TRY_CAST(NULL)] for parameter 'TO_FILE'".
     */
    @Test
    public void tryCastToFileIsRejected() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT TRY_CAST(NULL AS FILE)");
            }
        });
        assertTrue(error.getMessage().contains("invalid type [TRY_CAST(NULL)] for parameter 'TO_FILE'"),
            "unexpected message: " + error.getMessage());
    }

    /**
     * Live: FILE is not a legal element of a structured type — {@code ARRAY(FILE)},
     * {@code OBJECT(x FILE)} and {@code MAP(VARCHAR, FILE)} all fail "Unsupported data type 'FILE'."
     * even though a top-level {@code f FILE} column is accepted.
     */
    @Test
    public void fileIsNotAStructuredElementType() {
        assertUnsupportedNestedFile("CREATE TABLE ft_arr (f ARRAY(FILE))");
        assertUnsupportedNestedFile("CREATE TABLE ft_obj (f OBJECT(x FILE))");
        assertUnsupportedNestedFile("CREATE TABLE ft_map (f MAP(VARCHAR, FILE))");
    }

    /** Live: both {@code FILE(10)} and {@code FILE()} are syntax errors ("unexpected '('"). */
    @Test
    public void fileTakesNoTypeParameters() {
        final RuntimeException sized = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE ft_sized (f FILE(10))");
            }
        });
        assertTrue(sized.getMessage().contains("syntax error"), "unexpected message: " + sized.getMessage());

        final RuntimeException empty = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE ft_empty (f FILE())");
            }
        });
        assertTrue(empty.getMessage().contains("syntax error"), "unexpected message: " + empty.getMessage());
    }

    /**
     * Live: FILE is NOT reserved — a column, a select alias, a table and a scripting variable
     * may all be named {@code file}. Adding the type spelling must not take that away.
     */
    @Test
    public void fileRemainsAPlainIdentifier() {
        engine.execute("CREATE TABLE ft_ident (file INTEGER)");
        engine.execute("INSERT INTO ft_ident VALUES (7)");
        assertEquals("7", String.valueOf(engine.executeQuery("SELECT file FROM ft_ident")
            .getRows().get(0).getValue(0)));

        assertEquals("1", String.valueOf(engine.executeQuery("SELECT 1 AS file")
            .getRows().get(0).getValue(0)));

        engine.execute("CREATE TABLE file (x INTEGER)");
        engine.execute("INSERT INTO file VALUES (3)");
        assertEquals("3", String.valueOf(engine.executeQuery("SELECT x FROM file")
            .getRows().get(0).getValue(0)));

        assertEquals("1", String.valueOf(engine.executeQuery("""
            DECLARE file INTEGER := 1;
            BEGIN
              RETURN file;
            END;""").getRows().get(0).getValue(0)));
    }

    /**
     * Live: FILE is accepted as a UDF/procedure parameter type and as a RETURNS type —
     * {@code DESC FUNCTION uf1(FILE)} reports {@code signature | (X FILE)}.
     */
    @Test
    public void fileIsARoutineParameterAndReturnType() {
        engine.execute("CREATE FUNCTION ft_fn_arg(x FILE) RETURNS VARCHAR AS $$ 'a' $$");
        engine.execute("CREATE FUNCTION ft_fn_ret(x INTEGER) RETURNS FILE AS $$ NULL $$");
        engine.execute("""
            CREATE PROCEDURE ft_proc_arg(x FILE)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$
            BEGIN
              RETURN 'a';
            END;
            $$""");
        engine.execute("""
            CREATE PROCEDURE ft_proc_ret(x INTEGER)
            RETURNS FILE
            LANGUAGE SQL
            AS $$
            BEGIN
              RETURN NULL;
            END;
            $$""");
    }

    /**
     * Live: a scripting variable may be declared FILE, in both the DECLARE section
     * ({@code DECLARE v FILE;}) and the LET form ({@code LET v FILE := NULL}).
     */
    @Test
    public void fileIsAScriptingVariableType() {
        assertEquals("1", String.valueOf(engine.executeQuery("""
            DECLARE v FILE;
            BEGIN
              v := NULL;
              RETURN 1;
            END;""").getRows().get(0).getValue(0)));

        assertEquals("1", String.valueOf(engine.executeQuery("""
            BEGIN
              LET v FILE := NULL;
              RETURN 1;
            END;""").getRows().get(0).getValue(0)));
    }

    /** Live: CTAS from a FILE column keeps the column's declared type FILE. */
    @Test
    public void ctasPreservesTheFileColumnType() {
        engine.execute("CREATE TABLE ft_src (f FILE)");
        engine.execute("CREATE TABLE ft_copy AS SELECT f FROM ft_src");

        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE ft_copy");
        assertEquals(1, rs.getRows().size());
        assertEquals("FILE",
            String.valueOf(rs.getRows().get(0).getValue(columnIndex(rs, "type"))).toUpperCase());
    }

    private void assertUnsupportedNestedFile(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(error.getMessage().contains("Unsupported data type 'FILE'"),
            "unexpected message for [" + sql + "]: " + error.getMessage());
    }

    /** Locate a result column by name, case-insensitively — the two backends differ only in casing. */
    private int columnIndex(final ResultSet rs, final String name) {
        final List<ResultSetColumn> columns = rs.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalStateException("No column named " + name + " in " + columns);
    }
}
