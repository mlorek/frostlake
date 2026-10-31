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

package dev.frostlake.udf;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The module a call of a Python table function or aggregate imports reads every partition and every group the call
 * reaches, as a scalar function's call serves every row: what the module's code set up is there for the next one,
 * while each partition and each group has an instance of the handler class of its own. Each call in a statement keeps
 * its own module, and none outlives its statement.
 */
public class PythonModuleAcrossPartitionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE pt (g INT, v INT)");
        engine.execute("INSERT INTO pt VALUES (1, 10), (1, 11), (2, 20), (3, 30)");
        engine.execute("""
            CREATE FUNCTION seen(v INT) RETURNS TABLE (n INT) LANGUAGE PYTHON RUNTIME_VERSION = '3.12' HANDLER = 'H'
            AS $$
            rows = []
            class H:
                def process(self, v):
                    rows.append(v)
                    yield (len(rows),)
            $$""");
        engine.execute("""
            CREATE AGGREGATE FUNCTION groups(v INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
            HANDLER = 'A' AS $$
            made = []
            class A:
                def __init__(self):
                    made.append(1)
                    self.n = 0
                @property
                def aggregate_state(self):
                    return self.n
                def accumulate(self, v):
                    self.n += 1
                def merge(self, other):
                    self.n += other
                def finish(self):
                    return len(made) * 100 + self.n
            $$""");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void aTableFunctionCallsModuleReadsEveryPartition() {
        final String partitioned =
            "SELECT t.g, f.n FROM pt t, TABLE(seen(t.v) OVER (PARTITION BY t.g)) f ORDER BY 1, 2";
        assertEquals("1|1 ;; 1|2 ;; 2|3 ;; 3|4", rows(partitioned));
        assertEquals("1|1 ;; 1|2 ;; 2|3 ;; 3|4", rows(partitioned));
        assertEquals("1|1|1 ;; 1|2|2 ;; 2|3|3 ;; 3|4|4", rows("SELECT t.g, f.n, h.n FROM pt t, "
            + "TABLE(seen(t.v) OVER (PARTITION BY t.g)) f, TABLE(seen(t.v) OVER (PARTITION BY t.g)) h "
            + "ORDER BY 1, 2, 3"));
    }

    @Test
    public void anAggregateCallsModuleReadsEveryGroup() {
        assertEquals("1|102 ;; 2|201 ;; 3|301", rows("SELECT g, groups(v) FROM pt GROUP BY g ORDER BY 1"));
        assertEquals("1|102|102 ;; 2|201|201 ;; 3|301|301",
            rows("SELECT g, groups(v), groups(v + 1) FROM pt GROUP BY g ORDER BY 1"));
        assertEquals("104", rows("SELECT groups(v) FROM pt"));
    }
}
