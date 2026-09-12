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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REDUCE declares its accumulator's type, folded from the initial value and the lambda's body: VARIANT
 * arithmetic is FLOAT, an exact number widens to 38 digits keeping its scale, a text body beside a numeric
 * start reads as NUMBER(18,5), a text keeps its width, and a BOOLEAN body over a number is refused. A table
 * created over it declares the same column, and a numeric accumulator is held at that type through every
 * step. Every cell is live-verified.
 */
public class ReduceAccumulatorTypeTest extends BaseDatabaseTest {

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

    /** REDUCE's declared type across initial values, bodies, typed arrays and typed parameters. */
    @Test
    public void reduceDeclaresItsAccumulator() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P455_DB");
            assertEquals("FLOAT[DOUBLE]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x))"));
            assertEquals("VARCHAR(134217728)[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT('a', 'b'), '', (acc, x) -> acc || x))"));
            assertEquals("NUMBER(38,0)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc, x) -> acc + x))"));
            assertEquals("NUMBER(38,0)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc INT, x INT) -> acc + x))"));
            assertEquals("VARIANT[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), NULL, (acc, x) -> x))"));
            assertEquals("NUMBER(18,5)[SB8]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc || x))"));
            assertRefused("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc > x))",
                "incompatible types: [BOOLEAN] and [NUMBER(1,0)]");
            assertEquals("NUMBER(38,1)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0.5, (acc, x) -> acc + 1))"));
            assertEquals("NUMBER(38,0)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc + 1))"));
            assertEquals("VARIANT[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> x))"));
            assertEquals("VARCHAR(1)[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), '', (acc, x) -> acc))"));
            assertEquals("FLOAT[DOUBLE]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0::FLOAT, (acc, x) -> acc + x::FLOAT))"));
            assertEquals("OBJECT[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), OBJECT_CONSTRUCT(), (acc, x) -> OBJECT_INSERT(acc, x::VARCHAR, x)))"));
            assertEquals("ARRAY[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), [], (acc, x) -> ARRAY_APPEND(acc, x)))"));
            assertEquals("DOUBLE",
                rows("SELECT TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x))"));
            engine.execute("CREATE OR REPLACE TABLE P455_DB.PUBLIC.R AS SELECT REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x) AS v");
            engine.execute("DESC TABLE P455_DB.PUBLIC.R");
            engine.execute("CREATE OR REPLACE TABLE P455_DB.PUBLIC.R2 AS SELECT REDUCE(ARRAY_CONSTRUCT('a', 'b'), '', (acc, x) -> acc || x) AS v");
            engine.execute("DESC TABLE P455_DB.PUBLIC.R2");
            engine.execute("CREATE OR REPLACE TABLE P455_DB.PUBLIC.R3 AS SELECT REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc, x) -> acc + x) AS v");
            engine.execute("DESC TABLE P455_DB.PUBLIC.R3");
            assertEquals("NUMBER(38,2)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE([1, 2]::ARRAY(NUMBER(10,2)), 0, (acc, x) -> acc + x))"));
            assertEquals("NUMBER(38,0)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc * 2))"));
            assertEquals("FLOAT[DOUBLE]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(NULL, 0, (acc, x) -> acc + x))"));
            assertEquals("VARCHAR(2)[LOB]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 'ab', (acc, x) -> acc))"));
            assertEquals("BOOLEAN[SB1]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), TRUE, (acc, x) -> acc AND x::BOOLEAN))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P455_DB");
        }
    }

    /** A table created over REDUCE declares the accumulator, and a BOOLEAN body over a number is refused. */
    @Test
    public void aTableOverReduceDeclaresTheSameColumn() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P455B_DB");
            engine.execute("CREATE OR REPLACE TABLE P455B_DB.PUBLIC.R AS SELECT REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x) AS v");
            engine.execute("DESC TABLE P455B_DB.PUBLIC.R");
            assertEquals("FLOAT",
                rows("SELECT \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("CREATE OR REPLACE TABLE P455B_DB.PUBLIC.R2 AS SELECT REDUCE(ARRAY_CONSTRUCT('a', 'b'), '', (acc, x) -> acc || x) AS v");
            engine.execute("DESC TABLE P455B_DB.PUBLIC.R2");
            assertEquals("VARCHAR(16777216)",
                rows("SELECT \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            engine.execute("CREATE OR REPLACE TABLE P455B_DB.PUBLIC.R3 AS SELECT REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc, x) -> acc + x) AS v");
            engine.execute("DESC TABLE P455B_DB.PUBLIC.R3");
            assertEquals("NUMBER(38,0)",
                rows("SELECT \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
            assertEquals("6",
                rows("SELECT v FROM P455B_DB.PUBLIC.R3"));
            assertRefused("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc > x)",
                "incompatible types: [BOOLEAN] and [NUMBER(1,0)]");
            assertEquals("0.00000",
                rows("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc || x) AS v"));
            assertEquals("NUMBER(18,5)[SB8]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc || x))"));
            assertEquals("ab",
                rows("SELECT REDUCE(ARRAY_CONSTRUCT('a', 'b'), '', (acc, x) -> acc || x) AS v"));
            assertEquals("NUMBER(38,0)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc + x::INT))"));
            assertEquals("NUMBER(38,3)[SB16]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc + x::NUMBER(10,3)))"));
            assertEquals("FLOAT[DOUBLE]",
                rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 1.5::FLOAT, (acc, x) -> acc || x))"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P455B_DB");
        }
    }

    /** Concatenation beside a VARIANT, and a numeric accumulator read back at its declared type after every step. */
    @Test
    public void aNumericAccumulatorIsHeldAtItsTypeThroughEveryStep() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P455C_DB");
            engine.execute("SELECT SYSTEM$TYPEOF(0 || PARSE_JSON('1'))");
            engine.execute("SELECT SYSTEM$TYPEOF(1.5::FLOAT || PARSE_JSON('1'))");
            engine.execute("SELECT SYSTEM$TYPEOF('' || PARSE_JSON('1'))");
            engine.execute("SELECT SYSTEM$TYPEOF(0 || 'a')");
            engine.execute("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x)::VARCHAR");
            engine.execute("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 0, (acc, x) -> acc || x)::VARCHAR");
            engine.execute("SELECT TO_VARCHAR(REDUCE(ARRAY_CONSTRUCT(1, 2), 0.5, (acc, x) -> acc + 1))");
            engine.execute("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 0.5, (acc, x) -> acc + 1)");
            engine.execute("SELECT REDUCE([1, 2, 3]::ARRAY(INT), 0, (acc, x) -> acc + x)");
            engine.execute("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 1.5::FLOAT, (acc, x) -> acc || x)::VARCHAR");
            engine.execute("SELECT REDUCE(ARRAY_CONSTRUCT('a', 'b'), 'x', (acc, x) -> acc || x)");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P455C_DB");
        }
    }
}
