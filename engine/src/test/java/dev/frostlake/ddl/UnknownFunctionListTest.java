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
public class UnknownFunctionListTest extends BaseDatabaseTest {

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
    public void everyUnknownFunctionIsNamedInOneSentence() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P482_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("CREATE OR REPLACE FUNCTION r1() RETURNS ARRAY AS 'ARRAY_CONSTRUCT(1)'");
            assertRefused("SELECT r1(), r2(), r3()",
                "SQL compilation error:\nUnknown functions R2, R3.");
            assertRefused("SELECT r2(), r2()",
                "SQL compilation error:\nUnknown functions R2, R2.");
            assertRefused("SELECT r2() FROM t WHERE r3() = 1",
                "SQL compilation error:\nUnknown functions R2, R3.");
            assertRefused("SELECT nosuchagg(a) FROM t",
                "SQL compilation error:\nUnknown function NOSUCHAGG.");
            assertRefused("SELECT nosuchagg(a), nosuchscalar(a) FROM t",
                "SQL compilation error:\nUnknown functions NOSUCHAGG, NOSUCHSCALAR.");
            assertRefused("SELECT P482_DB.PUBLIC.rq()",
                "SQL compilation error:\nUnknown user-defined function P482_DB.PUBLIC.RQ.");
            assertRefused("SELECT r3(), r2()",
                "SQL compilation error:\nUnknown functions R3, R2.");
            assertRefused("SELECT r2() + r3() + r2()",
                "SQL compilation error:\nUnknown functions R2, R3, R2.");
            assertRefused("SELECT SUM(r2(a)) FROM t",
                "SQL compilation error:\nUnknown function R2.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P482_DB");
        }
    }
}
