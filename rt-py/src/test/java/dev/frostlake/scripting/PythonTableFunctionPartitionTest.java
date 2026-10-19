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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Python table function called OVER a partition: one handler reads a partition's rows and its
 * {@code end_partition} answers once for the partition, joined to the partition's own columns with NULL in
 * every other. OVER () and an ORDER BY alone make one partition of the whole input; a partitioning key is a
 * column or a constant - {@code Invalid partitioning expression 'UPPER(EV.G)'; must be a column.} otherwise -
 * and a frame is a syntax error. A SQL table function and a built-in one take an OVER too. Every answer is
 * live-verified.
 */
public class PythonTableFunctionPartitionTest extends MeasuredCellsTestSupport {

    @Test
    public void aPartitionEndsOnce() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10)");
        assertAnswer(
            "Function PY_SUM successfully created.",
            "CREATE OR REPLACE FUNCTION py_sum(v INT) RETURNS TABLE (total INT, cnt INT) LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' HANDLER = 'Agg' AS $$\nclass Agg:\n    def __init__(self):\n        "
                + "self.s = 0\n        self.c = 0\n    def process(self, v):\n        self.s += (v or 0)\n        "
                + "self.c += 1\n    def end_partition(self):\n        yield (self.s, self.c)\n$$");
        assertAnswer(
            "3|2 ;; 10|1",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 1");
        assertAnswer(
            "a|3|2 ;; b|10|1",
            "SELECT ev.g, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 1");
        assertShape(
            "G:VARCHAR(16777216), V:NUMBER(38,0), TOTAL:NUMBER(38,0), CNT:NUMBER(38,0)",
            "SELECT * FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f");
        assertAnswer(
            "a|null|3|2 ;; b|null|10|1",
            "SELECT * FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 3");
        assertAnswer(
            "13|3",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER ()) f");
        assertAnswer(
            "13|3",
            "SELECT SUM(f.total), SUM(f.cnt) FROM ev, TABLE(py_sum(ev.v)) f");
        assertAnswer(
            "3|2 ;; 10|1",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g ORDER BY ev.v)) f ORDER BY "
                + "1");
        assertAnswer(
            "1|1 ;; 2|1 ;; 10|1",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g, ev.v)) f ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'EV.NOPE'",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.nope)) f");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected 'ROWS'.",
            "SELECT f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW)) f");
        assertAnswer(
            "5|1",
            "SELECT f.total, f.cnt FROM TABLE(py_sum(5) OVER (PARTITION BY 1)) f");
        assertAnswer(
            "5|1",
            "SELECT f.total, f.cnt FROM TABLE(py_sum(5)) f");
        assertAnswer(
            "1 ;; 2 ;; 10",
            "SELECT f.value FROM ev, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(ev.v)) OVER (PARTITION BY ev.g)) f");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f WHERE ev.g = 'a'");
        assertAnswer(
            "3|2",
            "SELECT f.total, f.cnt FROM (SELECT * FROM ev WHERE v < 5) e, TABLE(py_sum(e.v) OVER (PARTITION BY "
                + "e.g)) f");
    }

    @Test
    public void thePartitionsOwnColumnsAndWhatAKeyMayBe() {
        answer(
            "CREATE OR REPLACE TABLE ev (g VARCHAR, v INT)");
        answer(
            "INSERT INTO ev VALUES ('a', 1), ('a', 2), ('b', 10), (NULL, 5)");
        assertAnswer(
            "Function PY_SUM successfully created.",
            "CREATE OR REPLACE FUNCTION py_sum(v INT) RETURNS TABLE (total INT, cnt INT) LANGUAGE PYTHON "
                + "RUNTIME_VERSION = '3.11' HANDLER = 'Agg' AS $$\nclass Agg:\n    def __init__(self):\n        "
                + "self.s = 0\n        self.c = 0\n    def process(self, v):\n        self.s += (v or 0)\n        "
                + "self.c += 1\n    def end_partition(self):\n        yield (self.s, self.c)\n$$");
        assertAnswer(
            "a|null|3|2 ;; null|null|5|1 ;; b|null|10|1",
            "SELECT ev.g, ev.v, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 3");
        assertAnswer(
            "REFUSED Invalid partitioning expression 'UPPER(EV.G)'; must be a column.",
            "SELECT ev.g, ev.v, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY UPPER(ev.g))) f "
                + "ORDER BY 3");
        assertAnswer(
            "null|null|18|4",
            "SELECT ev.g, ev.v, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER ()) f");
        assertAnswer(
            "REFUSED Invalid partitioning expression 'EV.V > 1'; must be a column.",
            "SELECT ev.g, ev.v, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g, ev.v > 1)) f "
                + "ORDER BY 3, 1");
        assertAnswer(
            "null|null|18|4",
            "SELECT ev.g, ev.v, f.total, f.cnt FROM ev, TABLE(py_sum(ev.v) OVER (ORDER BY ev.v)) f");
        assertAnswer(
            "null|null|18",
            "SELECT a.g, f.total, g2.total FROM ev a, TABLE(py_sum(a.v) OVER (PARTITION BY a.g)) f, "
                + "TABLE(py_sum(f.total) OVER ()) g2 ORDER BY 1");
        assertAnswer(
            "a|3 ;; null|5 ;; b|10",
            "SELECT ev.g, f.total FROM ev JOIN TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 2");
        answer(
            "SELECT ev.g, f.total FROM ev, LATERAL py_sum(ev.v) OVER (PARTITION BY ev.g) f ORDER BY 2");
        assertAnswer(
            "a|3 ;; null|5 ;; b|10",
            "SELECT ev.g, f.total FROM ev, TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g ORDER BY ev.v DESC NULLS "
                + "LAST)) f ORDER BY 2");
        assertAnswer(
            "Function SQT successfully created.",
            "CREATE OR REPLACE FUNCTION sqt(n NUMBER) RETURNS TABLE (a NUMBER) AS $$ SELECT n * 2 $$");
        assertAnswer(
            "a|2 ;; a|4 ;; null|10 ;; b|20",
            "SELECT ev.g, f.a FROM ev, TABLE(sqt(ev.v) OVER (PARTITION BY ev.g)) f ORDER BY 2");
        assertAnswer(
            "a|a ;; a|a ;; b|b",
            "SELECT ev.g, t.value FROM ev, TABLE(SPLIT_TO_TABLE(ev.g, ',') OVER (PARTITION BY ev.g ORDER BY "
                + "ev.v)) t ORDER BY 1");
    }
}
