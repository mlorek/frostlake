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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The types a scanned result and an anonymous block's result declare. A table created over the RESULT_SCAN of
 * an untyped NULL column keeps the zero width, where one over the query itself widens it. A block's text
 * result declares the unknown 128MB length, whatever the value's own width, and a table created over its
 * scan stores the 16MB default. A RETURN of SQLROWCOUNT declares NUMBER(0,0) once a statement of the block
 * has run, and a text before any has. Every cell is live-verified.
 */
public class ResultScanBlockTypesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rsbt_t (x INT)");
    }

    /** The one result column's declared type, with the parameters both engines report. */
    private static String typeOf(final ResultSet rs) {
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type instanceof NumericType) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        return type instanceof StringType ? "VARCHAR(" + ((StringType) type).getMaxLength() + ")" : String.valueOf(type);
    }

    /** The type and the value a statement's one-row, one-column result declares and holds. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return typeOf(rs) + " " + rs.getRows().get(0).getValue(0);
    }

    /** What a table created over the previous result stores for {@code column}. */
    private String scannedColumn(final String column) {
        engine.execute("CREATE OR REPLACE TABLE rsbt_copy AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return describeCell("rsbt_copy", column, "type");
    }

    @Test
    public void aScannedUntypedNullKeepsTheZeroWidth() {
        engine.executeQuery("SELECT NULL AS n, NULL::VARCHAR AS v, IFF(TRUE, NULL, NULL) AS i");
        engine.execute("CREATE OR REPLACE TABLE rsbt_all AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("VARCHAR(0)", describeCell("rsbt_all", "N", "type"));
        assertEquals("VARCHAR(16777216)", describeCell("rsbt_all", "V", "type"));
        assertEquals("VARCHAR(0)", describeCell("rsbt_all", "I", "type"));
        engine.executeQuery("SELECT NULL AS n");
        engine.execute("CREATE OR REPLACE TABLE rsbt_named AS SELECT n || 'x' AS c, n AS n2"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("VARCHAR(1)", describeCell("rsbt_named", "C", "type"));
        assertEquals("VARCHAR(0)", describeCell("rsbt_named", "N2", "type"));
        engine.execute("CREATE OR REPLACE TABLE rsbt_query AS SELECT NULL AS n");
        assertEquals("VARCHAR(16777216)", describeCell("rsbt_query", "N", "type"));
    }

    @Test
    public void aBlocksTextResultDeclaresTheUnknownLength() {
        assertEquals("VARCHAR(134217728) abc", answer("BEGIN RETURN 'abc'; END;"));
        assertEquals("VARCHAR(16777216)", scannedColumn("anonymous block"));
        assertEquals("VARCHAR(134217728) abc", answer("DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN RETURN s; END;"));
        assertEquals("VARCHAR(134217728) 1", answer("BEGIN FOR i IN 1 TO 1 DO RETURN i; END FOR; END;"));
        assertEquals("VARCHAR(134217728) null", answer("BEGIN LET x := 1; END;"));
        assertEquals("VARCHAR(134217728) null", answer("EXECUTE IMMEDIATE $$ BEGIN RETURN; END; $$"));
        engine.execute("CREATE OR REPLACE PROCEDURE rsbt_p() RETURNS VARCHAR(10) LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; END; $$");
        assertEquals("VARCHAR(134217728) abc", answer("CALL rsbt_p()"));
    }

    @Test
    public void sqlRowCountBeforeAnyStatementIsAText() {
        assertEquals("VARCHAR(134217728) null", answer("BEGIN RETURN SQLROWCOUNT; END;"));
        assertEquals("VARCHAR(16777216)", scannedColumn("anonymous block"));
        assertEquals("VARCHAR(134217728) null",
            answer("BEGIN IF (FALSE) THEN INSERT INTO rsbt_t VALUES (1); END IF; RETURN SQLROWCOUNT; END;"));
        assertEquals("VARCHAR(134217728) null", answer("BEGIN LET y := 1; RETURN SQLROWCOUNT; END;"));
    }

    @Test
    public void sqlRowCountAfterAStatementIsNumberZeroZero() {
        assertEquals("NUMBER(0,0) 1", answer("BEGIN INSERT INTO rsbt_t VALUES (1); RETURN SQLROWCOUNT; END;"));
        assertEquals("NUMBER(0,0) 1", answer("BEGIN BEGIN INSERT INTO rsbt_t VALUES (1); END; RETURN SQLROWCOUNT; END;"));
        assertEquals("NUMBER(0,0) null", answer("BEGIN SELECT 1; RETURN SQLROWCOUNT; END;"));
    }
}
