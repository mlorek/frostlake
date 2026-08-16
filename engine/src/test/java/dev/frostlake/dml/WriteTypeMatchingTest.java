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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compile-time type match INSERT applies holds for every write: an UPDATE's SET, both MERGE branches and
 * a column-listed CTAS refuse a value whose type family cannot convert to its column before any row is read,
 * over an empty table too. UPDATE and MERGE say {@code Expression type does not match column data type,
 * expecting NUMBER(38,0) but got BOOLEAN for column N}, reported in the table's column order; a CTAS says
 * {@code incompatible types: [BOOLEAN] and [NUMBER(38,0)]}. An UPDATE with FROM sources types its values
 * with the sources in scope, after resolving their names, and writes a column's DEFAULT. A string parsed
 * into a number stays a row-time fault. Every cell is live-verified.
 */
public class WriteTypeMatchingTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void everyWriteMatchesItsValuesToTheirColumns() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P461_DB");
            engine.execute("CREATE OR REPLACE TABLE ti (v VARCHAR, n NUMBER, b BOOLEAN, d DATE, t TIMESTAMP_NTZ, x VARIANT, bn BINARY)");
            engine.execute("INSERT INTO ti SELECT 'a', 1, TRUE, '2024-01-01'::DATE, '2024-01-01 10:00:00'::TIMESTAMP_NTZ, TO_VARIANT(1), X'AB'");
            engine.execute("INSERT INTO ti SELECT 'b', 7, FALSE, '2024-01-02'::DATE, '2024-01-02 10:00:00'::TIMESTAMP_NTZ, TO_VARIANT(2), X'CD'");
            engine.execute("CREATE OR REPLACE TABLE te (v VARCHAR, n NUMBER, b BOOLEAN, d DATE)");
            assertRefused("UPDATE te SET n = (1 = 1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = TRUE",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = (n > 5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = d",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            assertRefused("UPDATE ti SET d = n",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(38,0) for column D");
            engine.execute("UPDATE ti SET v = n");
            engine.execute("UPDATE ti SET v = d");
            engine.execute("UPDATE ti SET v = b");
            engine.execute("UPDATE ti SET b = 5");
            engine.execute("UPDATE ti SET b = 'true'");
            engine.execute("UPDATE ti SET t = d");
            engine.execute("UPDATE ti SET d = t");
            engine.execute("UPDATE ti SET x = n");
            engine.execute("UPDATE ti SET n = x");
            assertRefused("UPDATE ti SET bn = v",
                "DML operation to table TI failed on column BN with error: The following string is not a legal hex-encoded value: 'true'");
            assertRefused("UPDATE ti SET n = 'abc'",
                "DML operation to table TI failed on column N with error: Numeric value 'abc' is not recognized");
            engine.execute("UPDATE ti SET n = NULL");
            engine.execute("UPDATE ti SET d = '2024-01-01'");
            assertRefused("UPDATE ti SET n = 1, d = n",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(38,0) for column D");
            assertRefused("UPDATE ti SET d = n, n = TRUE",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = CURRENT_DATE()",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            assertRefused("UPDATE ti SET n = s.b FROM (SELECT TRUE AS b) s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET b = d",
                "SQL compilation error:\nExpression type does not match column data type, expecting BOOLEAN but got DATE for column B");
            assertRefused("UPDATE ti SET v = bn",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARCHAR(16777216) but got BINARY(8388608) for column V");
            assertRefused("MERGE INTO ti USING (SELECT 1 AS k) s ON TRUE WHEN MATCHED THEN UPDATE SET n = (1 = 1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("MERGE INTO ti USING (SELECT 1 AS k) s ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (1 = 1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("MERGE INTO te USING (SELECT 1 AS k) s ON TRUE WHEN MATCHED THEN UPDATE SET d = n",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(38,0) for column D");
            assertRefused("MERGE INTO ti USING (SELECT 1 AS k) s ON FALSE WHEN NOT MATCHED THEN INSERT (d) VALUES (5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            engine.execute("MERGE INTO ti USING (SELECT 1 AS k) s ON FALSE WHEN NOT MATCHED THEN INSERT (v) VALUES (5)");
            assertRefused("CREATE OR REPLACE TABLE k1 (n NUMBER) AS SELECT 1 = 1",
                "SQL compilation error:\nincompatible types: [BOOLEAN] and [NUMBER(38,0)]");
            assertRefused("CREATE OR REPLACE TABLE k2 (d DATE) AS SELECT 5",
                "SQL compilation error:\nincompatible types: [NUMBER(1,0)] and [DATE]");
            engine.execute("CREATE OR REPLACE TABLE k3 (v VARCHAR) AS SELECT 5");
            assertRefused("CREATE OR REPLACE TABLE k4 (n NUMBER, d DATE) AS SELECT 1, 2",
                "SQL compilation error:\nincompatible types: [NUMBER(1,0)] and [DATE]");
            engine.execute("CREATE OR REPLACE TABLE k5 (b BOOLEAN) AS SELECT 5");
            assertRefused("CREATE OR REPLACE TABLE k6 (n NUMBER) AS SELECT CURRENT_DATE()",
                "SQL compilation error:\nincompatible types: [DATE] and [NUMBER(38,0)]");
            assertRefused("CREATE OR REPLACE TABLE k7 (n NUMBER) AS SELECT 'abc'",
                "DML operation to table P461_DB.PUBLIC.K7 failed on column N with error: Numeric value 'abc' is not recognized");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P461_DB");
        }
    }

    @Test
    public void anUpdateFromTypesItsValuesWithTheSourcesInScope() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P461B_DB");
            engine.execute("CREATE OR REPLACE TABLE ti (k INT, n NUMBER, b BOOLEAN, d DATE, v VARCHAR)");
            engine.execute("INSERT INTO ti VALUES (1, 1, TRUE, '2024-01-01', 'a')");
            engine.execute("CREATE OR REPLACE TABLE tb (k INT, b BOOLEAN, n NUMBER, d DATE)");
            engine.execute("INSERT INTO tb VALUES (1, TRUE, 5, '2024-02-02')");
            engine.execute("CREATE OR REPLACE TABLE te (k INT, n NUMBER, d DATE)");
            assertRefused("UPDATE ti SET n = s.b FROM (SELECT TRUE AS b) s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = TRUE FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = s.b FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = s.b FROM tb s WHERE FALSE",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            engine.execute("UPDATE ti SET b = s.n FROM tb s");
            assertRefused("UPDATE ti SET d = s.n FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(38,0) for column D");
            assertRefused("UPDATE ti SET n = s.d FROM tb s WHERE ti.k = s.k",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            engine.execute("UPDATE ti SET v = s.d FROM tb s");
            assertRefused("UPDATE ti SET n = s.b, d = s.n FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET d = s.n, n = s.b FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = s.b, d = s.x FROM tb s",
                "SQL compilation error: error line 1 at position 27\ninvalid identifier 'S.X'");
            assertRefused("UPDATE ti SET n = s.b FROM tb s JOIN tb s2 ON s.k = s2.k",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = (1 = 1) FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = s.b FROM (SELECT b FROM tb) s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            engine.execute("UPDATE ti SET n = s.b::NUMBER FROM tb s");
            assertRefused("UPDATE te SET n = s.b FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = s.b FROM te x, tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("UPDATE ti SET n = x.d FROM te x",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            engine.execute("DELETE FROM ti USING tb s WHERE ti.n = s.b");
            assertEquals("",
                rows("SELECT k, n, b, d, v FROM ti"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P461B_DB");
        }
    }

    @Test
    public void anUpdateFromResolvesItsNamesBeforeTypingItsValues() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P461C_DB");
            engine.execute("CREATE OR REPLACE TABLE ti (k INT, n NUMBER DEFAULT 9, b BOOLEAN, d DATE)");
            engine.execute("INSERT INTO ti VALUES (1, 1, TRUE, '2024-01-01')");
            engine.execute("CREATE OR REPLACE TABLE tb (k INT, b BOOLEAN, n NUMBER, d DATE)");
            engine.execute("INSERT INTO tb VALUES (1, TRUE, 5, '2024-02-02')");
            assertRefused("UPDATE ti SET n = k FROM tb s",
                "SQL compilation error:\nambiguous column name 'K'");
            assertRefused("UPDATE ti SET n = k + 1 FROM tb s WHERE ti.k = s.k",
                "SQL compilation error:\nambiguous column name 'K'");
            engine.execute("UPDATE ti SET n = DEFAULT FROM tb s");
            assertRefused("UPDATE ti SET b = b FROM tb s",
                "SQL compilation error:\nambiguous column name 'B'");
            assertRefused("UPDATE ti SET n = nosuch FROM tb s",
                "SQL compilation error: error line 1 at position 18\ninvalid identifier 'NOSUCH'");
            assertRefused("UPDATE ti SET n = s.b, d = nosuch FROM tb s",
                "SQL compilation error: error line 1 at position 27\ninvalid identifier 'NOSUCH'");
            assertRefused("UPDATE ti SET d = s.nosuch, n = s.b FROM tb s",
                "SQL compilation error: error line 1 at position 18\ninvalid identifier 'S.NOSUCH'");
            engine.execute("UPDATE ti SET n = (SELECT MAX(n) FROM tb) FROM tb s");
            assertRefused("UPDATE ti SET n = (SELECT b FROM tb LIMIT 1) FROM tb s",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            engine.execute("UPDATE ti t SET n = s.n FROM tb s WHERE t.k = s.k");
            engine.execute("UPDATE ti SET n = s.n FROM tb s WHERE ti.k = s.k(+)");
            engine.execute("UPDATE ti SET n = t2.n FROM tb s JOIN ti t2 ON s.k = t2.k");
            engine.execute("UPDATE ti SET n = x FROM (SELECT 1 AS x) s");
            assertEquals("1, 1, true, 2024-01-01",
                rows("SELECT k, n, b, d FROM ti"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P461C_DB");
        }
    }

    @Test
    public void anUpdateFromWritesTheDeclaredDefault() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P461D_DB");
            engine.execute("CREATE OR REPLACE TABLE ti (k INT, n NUMBER DEFAULT 9, b BOOLEAN)");
            engine.execute("INSERT INTO ti VALUES (1, 1, TRUE), (2, 2, TRUE)");
            engine.execute("CREATE OR REPLACE TABLE tb (k INT, b BOOLEAN)");
            engine.execute("INSERT INTO tb VALUES (1, FALSE)");
            engine.execute("UPDATE ti SET n = DEFAULT FROM tb s WHERE ti.k = s.k");
            assertEquals("1, 9, true | 2, 2, true",
                rows("SELECT k, n, b FROM ti ORDER BY k"));
            engine.execute("UPDATE ti SET b = s.b, n = DEFAULT FROM tb s WHERE ti.k = s.k + 1");
            assertEquals("1, 9, true | 2, 9, false",
                rows("SELECT k, n, b FROM ti ORDER BY k"));
            engine.execute("UPDATE ti SET b = DEFAULT FROM tb s");
            assertEquals("1, 9, null | 2, 9, null",
                rows("SELECT k, n, b FROM ti ORDER BY k"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P461D_DB");
        }
    }
}
