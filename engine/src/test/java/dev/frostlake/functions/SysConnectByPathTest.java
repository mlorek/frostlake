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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYS_CONNECT_BY_PATH(expr, separator) answers a CONNECT BY row's path from its root, each value along it after
 * the separator, read at each row of the path; a NULL on the path makes it NULL. Outside a hierarchical query
 * the function is unknown, and it takes exactly two arguments (live-verified).
 */
public class SysConnectByPathTest extends MeasuredCellsTestSupport {

    @Test
    public void pathFromTheRoot() {
        try {
            assertAnswer(
                "Table EMP successfully created.",
                "CREATE OR REPLACE TABLE emp (id INT, mgr INT, nm VARCHAR)");
            assertAnswer(
                "4",
                "INSERT INTO emp VALUES (1, NULL, 'ceo'), (2, 1, 'vp'), (3, 2, 'dir'), (4, 1, 'cfo')");
            assertAnswer(
                "ceo|/ceo ;; cfo|/ceo/cfo ;; vp|/ceo/vp ;; dir|/ceo/vp/dir",
                "SELECT nm, SYS_CONNECT_BY_PATH(nm, '/') AS p FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id "
                    + "= mgr ORDER BY p");
            assertAnswer(
                "ceo| > 1 ;; vp| > 1 > 2 ;; dir| > 1 > 2 > 3 ;; cfo| > 1 > 4",
                "SELECT nm, SYS_CONNECT_BY_PATH(id, ' > ') AS p FROM emp START WITH mgr IS NULL CONNECT BY PRIOR "
                    + "id = mgr ORDER BY p");
            assertAnswer(
                "ceo|ceo ;; cfo|ceocfo ;; vp|ceovp ;; dir|ceovpdir",
                "SELECT nm, SYS_CONNECT_BY_PATH(nm, '') AS p FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id "
                    + "= mgr ORDER BY p");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function SYS_CONNECT_BY_PATH.",
                "SELECT SYS_CONNECT_BY_PATH(nm, '/') FROM emp");
            assertShape(
                "P:VARCHAR(134217728)",
                "SELECT SYS_CONNECT_BY_PATH(nm, '/') AS p FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id = "
                    + "mgr");
            assertAnswer(
                "vp|/VP|1 ;; dir|/VP/DIR|2",
                "SELECT nm, SYS_CONNECT_BY_PATH(UPPER(nm), '/') AS p, LEVEL FROM emp START WITH id = 2 CONNECT BY "
                    + "PRIOR id = mgr ORDER BY p");
            assertAnswer(
                "ceo|/ceo ;; vp|/ceo/vp ;; dir|/ceo/vp/dir",
                "SELECT nm, SYS_CONNECT_BY_PATH(nm, '/') AS p FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id "
                    + "= mgr AND nm <> 'cfo' ORDER BY p");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid number of arguments for "
                    + "[SYS_CONNECT_BY_PATH], expected 2, got 1",
                "SELECT SYS_CONNECT_BY_PATH(nm) FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id = mgr");
            assertAnswer(
                "null ;; null ;; null ;; null",
                "SELECT SYS_CONNECT_BY_PATH(nm, mgr) FROM emp START WITH mgr IS NULL CONNECT BY PRIOR id = mgr");
        } finally {
            answer(
                "DROP TABLE IF EXISTS emp");
        }
    }
}
