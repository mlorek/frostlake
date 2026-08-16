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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 128MB UNKNOWN-LENGTH VARCHAR, and the clamp that turns it into a column. Two constants, and the
 * boundary between them is what this test pins:
 *
 * <pre>
 *   SYSTEM$TYPEOF(UPPER(NULL))                          VARCHAR(134217728)[LOB]   the expression
 *   CREATE TABLE t AS SELECT UPPER(NULL) AS c           VARCHAR(16777216)         the column
 * </pre>
 *
 * <p>★ THE 128MB IS WHAT AN EXPRESSION'S STRING TYPE WIDENS TO when nothing bounds it — an UNTYPED
 * argument, a NULL concat operand, or NULLIFZERO handing a VARCHAR through. It is NOT a marker for
 * "the argument had no type": {@code NULLIFZERO(s)} over an ordinary VARCHAR column of digits is
 * 134217728 live, while a bare {@code SELECT s} from the same column stays 16777216.
 *
 * <p>★ THE CLAMP IS THE COLUMN-DECLARING PATHS' — a CTAS and a CREATE VIEW both settle the unknown
 * length to the 16MB storage default. The ZERO width goes the same way UP: a VIEW over
 * {@code SELECT NULL} declares VARCHAR(16777216), though the bare query's result column is VARCHAR(0).
 *
 * <p>★ EXPRESSIONS WITH A COMPUTABLE WIDTH DO NOT MOVE: {@code UPPER(s)} keeps its tripled width and
 * {@code s || 'x'} its sum, both uncapped at 16MB.
 */
public class UnknownLengthVarcharTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE ul (s VARCHAR, n NUMBER(3,0))");
        engine.execute("INSERT INTO ul VALUES ('7', 1)");
    }

    /** The first value of the first row. */
    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One created relation's first column type. */
    private String declared(final String createSql, final String describeSql) {
        engine.execute(createSql);
        final ResultSet rs = engine.executeQuery(describeSql);
        rs.next();
        return String.valueOf(rs.getValue(1));
    }

    @Test
    void anExpressionOverAnUntypedArgumentIsTheUnknownLength() {
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(UPPER(NULL)) FROM ul"));
        assertEquals("VARCHAR(134217728)[LOB]",
            value("SELECT SYSTEM$TYPEOF(SUBSTR(NULL, 1, 2)) FROM ul"));
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(NULL || 'x') FROM ul"));
    }

    @Test
    void nullifzeroWidensAVarcharItMerelyHandsThrough() {
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(NULLIFZERO(s)) FROM ul"));
        // The bare read of the same column keeps the storage default.
        assertEquals("VARCHAR(16777216)[LOB]", value("SELECT SYSTEM$TYPEOF(s) FROM ul"));
    }

    @Test
    void computableWidthsDoNotMove() {
        assertEquals("VARCHAR(50331648)[LOB]", value("SELECT SYSTEM$TYPEOF(UPPER(s)) FROM ul"));
        assertEquals("VARCHAR(16777217)[LOB]", value("SELECT SYSTEM$TYPEOF(s || 'x') FROM ul"));
        assertEquals("VARCHAR(2)[LOB]", value("SELECT SYSTEM$TYPEOF('ab') FROM ul"));
    }

    @Test
    void theColumnDeclaringPathsClampToTheStorageDefault() {
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE TABLE ulc AS SELECT UPPER(NULL) AS c FROM ul",
                "DESCRIBE TABLE ulc"));
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE TABLE ulc AS SELECT NULLIFZERO(s) AS c FROM ul",
                "DESCRIBE TABLE ulc"));
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE TABLE ulc AS SELECT NULL || 'x' AS c FROM ul",
                "DESCRIBE TABLE ulc"));
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE VIEW ulv AS SELECT UPPER(NULL) AS c FROM ul",
                "DESCRIBE VIEW ulv"));
    }

    @Test
    void aViewOverSelectNullWidensTheZeroWidthToTheSameDefault() {
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE VIEW ulv AS SELECT NULL AS c FROM ul", "DESCRIBE VIEW ulv"));
        assertEquals("VARCHAR(16777216)",
            declared("CREATE OR REPLACE TABLE ulc AS SELECT NULL AS c FROM ul", "DESCRIBE TABLE ulc"));
    }
}
