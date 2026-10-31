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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Python table function returns or yields is shaped into rows the same way on either backend, CPython's way: a
 * bytes or a bytearray is one value, as a str is, so a BINARY row or cell holds it whole; only a tuple or a list is a
 * row's cells, so a range yielded as a row is one cell its column refuses. A None item skips: {@code return None},
 * {@code [None]} or {@code []} writes no row for its input row, and the rows yielded or listed after a None are
 * ignored, in {@code end_partition} too (Snowflake's UDTF documentation).
 */
public class PythonTableFunctionRowsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE DATABASE d");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE src (x INT)");
        engine.execute("INSERT INTO src VALUES (1), (2), (3)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private void function(final String name, final String args, final String columns, final String body) {
        engine.execute("CREATE FUNCTION " + name + "(" + args + ") RETURNS TABLE(" + columns + ") LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' HANDLER = 'H' AS $$\nclass H:\n" + body + "$$");
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
    public void bytesAreOneValue() {
        function("t3", "", "s BINARY", "    def process(self):\n        return b'ab'\n");
        function("t4", "", "s BINARY", "    def process(self):\n        yield b'ab'\n        yield (b'cd',)\n"
            + "        yield bytearray(b'ef')\n");
        assertEquals("6162", rows("SELECT HEX_ENCODE(s) FROM TABLE(t3())"));
        assertEquals("6162 ;; 6364 ;; 6566", rows("SELECT HEX_ENCODE(s) FROM TABLE(t4())"));
    }

    @Test
    public void onlyTuplesAndListsAreCells() {
        function("t8", "", "n INT", "    def process(self):\n        yield 1\n        yield (2, 3)\n"
            + "        yield range(3)\n");
        function("t9", "", "n INT", "    def process(self):\n        yield 1\n        yield [4, 5]\n");
        assertEquals("1 ;; 4", rows("SELECT * FROM TABLE(t9())"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(t8())");
            }
        });
        assertTrue(refused.getMessage().startsWith("Python Interpreter Error:"), refused.getMessage());
        assertTrue(refused.getMessage().contains("TypeError: int() argument must be a string, a bytes-like object"),
            refused.getMessage());
        assertTrue(refused.getMessage().endsWith("in function T8 with handler H"), refused.getMessage());
    }

    @Test
    public void aNoneItemSkips() {
        function("s1", "x INT", "a INT", "    def process(self, x):\n        if x % 2 == 0:\n"
            + "            return [None]\n        return [(x,)]\n");
        function("s2", "x INT", "a INT", "    def process(self, x):\n        yield (x,)\n        yield None\n"
            + "        yield (x + 10,)\n");
        function("s3", "x INT", "a INT", "    def process(self, x):\n        if x == 1:\n            return []\n"
            + "        if x == 2:\n            return None\n        return [(x,), None, (x + 10,)]\n");
        assertEquals("1", rows("SELECT * FROM TABLE(s1(1)) UNION ALL SELECT * FROM TABLE(s1(2))"));
        assertEquals("1", rows("SELECT * FROM TABLE(s2(1))"));
        assertEquals("3", rows("SELECT * FROM TABLE(s3(1)) UNION ALL SELECT * FROM TABLE(s3(2)) "
            + "UNION ALL SELECT * FROM TABLE(s3(3))"));
    }

    @Test
    public void aNoneItemEndsThePartitionsRows() {
        function("s4", "x INT", "a INT", "    def __init__(self):\n        self.n = 0\n    def process(self, x):\n"
            + "        self.n += 1\n        return None\n    def end_partition(self):\n        yield (self.n,)\n"
            + "        yield None\n        yield (99,)\n");
        assertEquals("3", rows("SELECT t.a FROM src, TABLE(s4(x) OVER (PARTITION BY 1)) t ORDER BY 1"));
    }

    @Test
    public void aNoneCellIsANull() {
        function("s5", "x INT", "a INT, b VARCHAR", "    def process(self, x):\n        yield (None, 'n')\n"
            + "        yield (x, None)\n");
        assertEquals("null|n ;; 7|null", rows("SELECT * FROM TABLE(s5(7))"));
    }
}
