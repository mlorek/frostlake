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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DESCRIBE TABLE, DESCRIBE VIEW and their DESC spellings take an {@code IDENTIFIER('<name>')} or
 * {@code IDENTIFIER($var)} name as well as a written one: a quoted name, a qualified one and a view read
 * through TABLE all resolve, a missing one is refused with the kind DESCRIBE names, and a string that is
 * no identifier reference is refused at the argument. Every cell is live-verified.
 */
public class DescribeIdentifierTest extends BaseDatabaseTest {

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
    public void aDescribedRelationTakesAnIdentifierName() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P467_DB");
            engine.execute("CREATE OR REPLACE TABLE t1 (a INT COMMENT 'x', b VARCHAR(5))");
            engine.execute("CREATE OR REPLACE TABLE \"my table\" (z DATE)");
            engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT a FROM t1");
            engine.execute("SET tn = 't1'");
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER('t1')"));
            assertEquals("Z, DATE, COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESC TABLE IDENTIFIER('\"my table\"')"));
            assertRefused("DESCRIBE TABLE IDENTIFIER('nosuch')",
                "SQL compilation error:\nTable 'NOSUCH' does not exist or not authorized.");
            assertRefused("DESCRIBE TABLE IDENTIFIER('my table')",
                "SQL compilation error: error line 1 at position 26\ninvalid identifier ''my table''");
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE VIEW IDENTIFIER('v1')"));
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER('P467_DB.PUBLIC.T1')"));
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER($tn)"));
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER('v1')"));
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE VIEW IDENTIFIER('t1')"));
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER('PUBLIC.t1')"));
            assertRefused("DESCRIBE TABLE IDENTIFIER(1)",
                "SQL compilation error: error line 1 at position 26\ninvalid identifier '1'");
            assertEquals("A, NUMBER(38,0), COLUMN, Y, null, N, N, null, null, x, null, null, null | B, VARCHAR(5), COLUMN, Y, null, N, N, null, null, null, null, null, null",
                rows("DESCRIBE TABLE IDENTIFIER('t1')"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P467_DB");
        }
    }
}
