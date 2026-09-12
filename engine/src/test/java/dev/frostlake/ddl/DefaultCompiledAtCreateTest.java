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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class DefaultCompiledAtCreateTest extends BaseDatabaseTest {

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
    public void aDefaultIsCompiledWhereItStands() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P478_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("CREATE OR REPLACE SEQUENCE s1");
            assertRefused("CREATE OR REPLACE TABLE e1 (c TIMESTAMP_NTZ(3) DEFAULT TRY_CAST(SYSDATE() AS TIMESTAMP_NTZ(3)))",
                "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types TIMESTAMP_NTZ(9) and TIMESTAMP_NTZ(3)");
            assertRefused("SELECT TRY_CAST(SYSDATE() AS TIMESTAMP_NTZ(3))",
                "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types TIMESTAMP_NTZ(9) and TIMESTAMP_NTZ(3)");
            assertRefused("CREATE OR REPLACE TABLE e3 (c INT DEFAULT nosuchfunc(1))",
                "SQL compilation error:\nUnknown function NOSUCHFUNC.");
            assertRefused("CREATE OR REPLACE TABLE e4 (c INT DEFAULT ABS(1, 2))",
                "SQL compilation error: error line 1 at position 42\ntoo many arguments for function [ABS(1, 2)] expected 1, got 2");
            assertRefused("CREATE OR REPLACE TABLE e5 (c INT DEFAULT a)",
                "SQL compilation error: error line 1 at position 42\ninvalid identifier 'A'");
            assertRefused("CREATE OR REPLACE TABLE e6 (c INT DEFAULT (SELECT 1))",
                "SQL compilation error: error line 1 at position 43\nsub-queries are not supported as part of the specification of a default value clause.");
            assertRefused("CREATE OR REPLACE TABLE e7 (c INT DEFAULT MAX(1))",
                "SQL compilation error: error line 1 at position 42\naggregate functions are not allowed as part of the specification of a default value clause.");
            engine.execute("CREATE OR REPLACE TABLE e8 (c INT DEFAULT s1.NEXTVAL)");
            engine.execute("CREATE OR REPLACE TABLE e9 (c INT DEFAULT 1/0)");
            assertRefused("CREATE OR REPLACE TABLE e10 (c VARCHAR DEFAULT TRY_CAST(1 AS VARCHAR))",
                "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types NUMBER(1,0) and VARCHAR(134217728)");
            engine.execute("CREATE OR REPLACE TABLE e11 (c INT DEFAULT CURRENT_DATE)");
            engine.execute("CREATE OR REPLACE TABLE e12 (c INT DEFAULT TO_NUMBER('x'))");
            assertRefused("CREATE OR REPLACE TABLE e13 (c VARCHAR DEFAULT UPPER('a', 'b'))",
                "SQL compilation error: error line 1 at position 47\ntoo many arguments for function [UPPER('a', 'b')] expected 1, got 2");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P478_DB");
        }
    }

    @Test
    public void aDefaultsCallIsCountedWhereItStands() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P478C_DB");
            assertRefused("CREATE OR REPLACE TABLE f1 (c INT DEFAULT ABS())",
                "SQL compilation error: error line 1 at position 42\nnot enough arguments for function [ABS()], expected 1, got 0");
            assertRefused("SELECT ABS()",
                "SQL compilation error: error line 1 at position 7\nnot enough arguments for function [ABS()], expected 1, got 0");
            engine.execute("CREATE OR REPLACE TABLE f3 (c VARCHAR DEFAULT CONCAT('a'))");
            assertRefused("CREATE OR REPLACE TABLE f4 (c INT DEFAULT GREATEST())",
                "SQL compilation error: error line 1 at position 42\nnot enough arguments for function [GREATEST()], expected 1, got 0");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P478C_DB");
        }
    }
}
