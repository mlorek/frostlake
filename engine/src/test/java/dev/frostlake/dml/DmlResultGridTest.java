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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The result grid a DML statement answers, read through RESULT_SCAN of its own query id. A MERGE answers one
 * count column per KIND of action its WHEN clauses write; an UPDATE … FROM counts the target rows several
 * source rows joined; and every DML count is a NUMBER(19,0).
 */
public class DmlResultGridTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE it (n INT, m INT)");
        engine.execute("INSERT INTO it VALUES (1, 1), (2, 2)");
        engine.execute("CREATE OR REPLACE TABLE ut (id INT, v VARCHAR)");
        engine.execute("INSERT INTO ut VALUES (1, 'a'), (2, 'b'), (200, 'c')");
    }

    /** NAME:NUMBER(p,s) of each column, then the row, of a DML statement's result. */
    private String grid(final String dml) {
        final ResultSet rs = engine.executeQuery(dml);
        final StringBuilder text = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            final NumericType type = (NumericType) column.getDataType();
            text.append(column.getName()).append(':').append(type.getPrecision()).append(',')
                .append(type.getScale()).append(' ');
        }
        final ResultSet scan = engine.executeQuery("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        scan.next();
        for (int c = 0; c < scan.getColumnCount(); c++) {
            text.append(c == 0 ? "= " : "|").append(scan.getValue(c));
        }
        return text.toString().trim();
    }

    /** A MERGE answers only the kinds of action it has clauses for. */
    @Test
    public void mergeAnswersTheKindsItWrites() {
        assertEquals("number of rows updated:19,0 = 1", grid(
            "MERGE INTO it USING (SELECT 1 AS n) s ON it.n = s.n WHEN MATCHED THEN UPDATE SET m = 2"));
        assertEquals("number of rows inserted:19,0 = 1", grid(
            "MERGE INTO it USING (SELECT 5 AS n) s ON it.n = s.n WHEN NOT MATCHED THEN INSERT (n, m) VALUES (s.n, 0)"));
        assertEquals("number of rows deleted:19,0 = 1", grid(
            "MERGE INTO it USING (SELECT 5 AS n) s ON it.n = s.n WHEN MATCHED THEN DELETE"));
    }

    /** The columns follow the clauses, not what happened: nothing matched still answers the UPDATE's 0. */
    @Test
    public void theColumnsFollowTheClauses() {
        assertEquals("number of rows updated:19,0 = 0", grid(
            "MERGE INTO it USING (SELECT 99 AS n) s ON it.n = s.n WHEN MATCHED THEN UPDATE SET m = 9"));
        assertEquals("number of rows updated:19,0 number of rows deleted:19,0 = 1|1", grid(
            "MERGE INTO it USING (SELECT 1 AS n UNION ALL SELECT 2) s ON it.n = s.n "
            + "WHEN MATCHED AND it.n = 2 THEN DELETE WHEN MATCHED THEN UPDATE SET m = 4"));
    }

    /** A target row several source rows join is counted once as multi-joined. */
    @Test
    public void aMultiJoinedTargetRowIsCountedOnce() {
        assertEquals("number of rows updated:19,0 number of multi-joined rows updated:19,0 = 1|1", grid(
            "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y' "
            + "UNION ALL SELECT 1, 'z') s WHERE ut.id = s.id"));
        assertEquals("number of rows updated:19,0 number of multi-joined rows updated:19,0 = 2|2", grid(
            "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v UNION ALL SELECT 1, 'y' "
            + "UNION ALL SELECT 2, 'p' UNION ALL SELECT 2, 'q') s WHERE ut.id = s.id"));
        assertEquals("number of rows updated:19,0 number of multi-joined rows updated:19,0 = 1|0", grid(
            "UPDATE ut SET v = s.v FROM (SELECT 1 AS id, 'x' AS v) s WHERE ut.id = s.id"));
    }

    /** A multi-table INSERT names each target it wrote. */
    @Test
    public void aMultiTableInsertNamesItsTargets() {
        engine.execute("CREATE OR REPLACE TABLE it2 (n INT)");
        assertEquals("number of rows inserted into IT:19,0 number of rows inserted into IT2:19,0 = 1|1", grid(
            "INSERT ALL INTO it (n) VALUES (b) INTO it2 (n) VALUES (b) SELECT 9 AS b"));
    }
}
