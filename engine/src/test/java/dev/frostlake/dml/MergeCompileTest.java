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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A MERGE compiles whole before it reads a row, so an empty target or source refuses what a full one would,
 * in live's order: an unreachable WHEN clause, a column SET or INSERTed twice and an INSERT whose values do
 * not fit its columns; every subquery; the SET and INSERT targets; the names of the INSERT values, the SET
 * values, the WHEN MATCHED conditions, the WHEN NOT MATCHED conditions and the ON condition, then every
 * unknown function name in one sentence, then argument types; then an aggregate. An INSERT value and a WHEN
 * NOT MATCHED condition read the source alone. Every cell is live-verified.
 */
public class MergeCompileTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE DATABASE MERGE_COMPILE_DB");
        engine.execute("CREATE OR REPLACE TABLE MERGE_COMPILE_DB.PUBLIC.T (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE MERGE_COMPILE_DB.PUBLIC.FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO MERGE_COMPILE_DB.PUBLIC.FULL_T VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE TABLE MERGE_COMPILE_DB.PUBLIC.EMPTY_S (a INT, b INT)");
    }

    @AfterEach
    public void dropTables() {
        engine.execute("DROP DATABASE IF EXISTS MERGE_COMPILE_DB");
    }

    /** The statement's refusal on one line, or ACCEPTED when it runs. */
    private String answer(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    private static String unpositioned(final String detail) {
        return "SQL compilation error:|" + detail;
    }

    @Test
    public void everyNameIsCompiledOverAnEmptyTarget() {
        assertEquals(at(83, "invalid identifier 'NOSUCHCOL'"),
            answer("MERGE INTO T USING (SELECT 1 AS a) s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = nosuchcol"));
        assertEquals(at(46, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING (SELECT 1 AS a) s ON T.a = s.nosuch WHEN MATCHED THEN UPDATE SET a = 1"));
        assertEquals(at(91, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING (SELECT 1 AS a) s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch)"));
        assertEquals(at(67, "invalid identifier 'NOSUCHCOL'"),
            answer("MERGE INTO T USING (SELECT 1 AS a) s ON T.a = s.a WHEN MATCHED AND nosuchcol = 1 THEN DELETE"));
        assertEquals(at(74, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.nosuch"));
        assertEquals(at(70, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET nosuchtarget = 1"));
        assertEquals(at(71, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (nosuchtarget) VALUES (1)"));
        assertEquals(at(63, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED AND s.nosuch = 1 THEN INSERT (a) VALUES (1)"));
        assertEquals(unpositioned("ambiguous column name 'B'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = b"));
        assertEquals(unpositioned("ambiguous column name 'A'"), answer("MERGE INTO T USING FULL_T s ON a = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(unpositioned("ambiguous column name 'A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = a"));
        assertEquals(at(83, "invalid identifier 'T.A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (T.a)"));
        assertEquals(at(82, "invalid identifier 'NOSUCH'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = (SELECT nosuch FROM FULL_T)"));
        assertEquals(at(27, "invalid identifier 'NOSUCH'"),
            answer("MERGE INTO T USING (SELECT nosuch FROM FULL_T) s ON T.a = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(at(78, "invalid identifier 'T.A'"),
            answer("MERGE INTO T t2 USING FULL_T s ON t2.a = s.a WHEN MATCHED THEN UPDATE SET a = T.a"));
        assertEquals(at(74, "invalid identifier 'FULL_T.A'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = FULL_T.a"));
        assertEquals(at(78, "invalid identifier 'EMPTY_S.A'"),
            answer("MERGE INTO T USING EMPTY_S AS s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = EMPTY_S.a"));
        assertEquals(at(36, "invalid identifier 'EMPTY_S.NOSUCH'"),
            answer("MERGE INTO T USING EMPTY_S ON T.a = EMPTY_S.nosuch WHEN MATCHED THEN DELETE"));
        assertEquals(at(79, "invalid identifier 'TT.NOSUCH'"),
            answer("MERGE INTO T tt USING EMPTY_S s ON tt.a = s.a WHEN MATCHED THEN UPDATE SET a = tt.nosuch"));
        assertEquals(at(71, "invalid identifier 'S.A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET s.a = 1"));
        assertEquals(at(84, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT VALUES (s.a, s.nosuch)"));
        assertEquals(at(108, "invalid identifier 'NOSUCH'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT (a) VALUES (nosuch)"));
        assertEquals(at(32, "invalid identifier 'NOSUCHCOL'"),
            answer("MERGE INTO T USING EMPTY_S s ON nosuchcol = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(hinted(unpositioned("Object 'NOSUCH_TABLE' does not exist or not authorized.")),
            answer("MERGE INTO NOSUCH_TABLE USING EMPTY_S s ON 1 = s.nosuch WHEN MATCHED THEN DELETE"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."),
            answer("MERGE INTO T USING (SELECT 1 AS a) s ON T.a = s.a WHEN MATCHED AND nosuchfn(1) = 1 THEN DELETE"));
        assertEquals(unpositioned("Unknown function NOSUCHFN."),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = nosuchfn(s.a)"));
        assertEquals(unpositioned("invalid type [TO_DATE(S.A)] for parameter 'TO_DATE'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = TO_DATE(s.a)"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("MERGE INTO T USING EMPTY_S s ON TO_DATE(TRUE) IS NULL WHEN MATCHED THEN DELETE"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (TO_DATE(TRUE))"));
        assertEquals(unpositioned("invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND TO_DATE(TRUE) IS NULL THEN DELETE"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.b"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = 'x'"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES ('x')"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (a)"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (b)"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET T.a = 1"));
        assertEquals("ACCEPTED", answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.b + 'x'"));
        assertEquals("ACCEPTED", answer(
            "MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.b WHEN NOT MATCHED THEN INSERT (a) VALUES (s.b)"));
        assertEquals("ACCEPTED", answer(
            "MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = ROW_NUMBER() OVER (ORDER BY s.b)"));
    }

    /** With two faults: structure, then subqueries, then targets, then names by kind in the order of the branches. */
    @Test
    public void theBranchesAreJudgedInLivesOrder() {
        assertEquals(at(80, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING FULL_T s ON T.a = s.nosuch1 WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(125, "invalid identifier 'S.NOSUCH3'"), answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = s.nosuch2 WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch3)"));
        assertEquals(at(82, "invalid identifier 'S.NOSUCH3'"), answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN NOT MATCHED THEN"
            + " INSERT (a) VALUES (s.nosuch3) WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(127, "invalid identifier 'S.NOSUCH3'"), answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = nosuchfn(1) WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch3)"));
        assertEquals(at(84, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING FULL_T s ON nosuchfn(T.a) = s.a WHEN MATCHED THEN UPDATE SET a = s.nosuch"));
        assertEquals(at(75, "invalid identifier 'S.NOSUCH1'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.nosuch1, b = s.nosuch2"));
        assertEquals(at(93, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND s.nosuch1 = 1 THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(121, "invalid identifier 'S.NOSUCH2'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN"
            + " INSERT (a) VALUES (s.a) WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(38, "invalid identifier 'S.NOSUCH'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.nosuch WHEN MATCHED THEN UPDATE SET a = nosuchfn(1)"));
        assertEquals(at(65, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.nosuch1 WHEN MATCHED AND s.nosuch2 = 1 THEN DELETE"));
        assertEquals(at(101, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED AND s.nosuch1 = 1 THEN INSERT (a) VALUES (s.nosuch2)"));
        assertEquals(at(72, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (nosuchtarget) VALUES (s.nosuch2)"));
        assertEquals(at(71, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET nosuchtarget = s.nosuch2"));
        assertEquals(at(69, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.nosuch1 WHEN NOT MATCHED AND s.nosuch2 = 1 THEN INSERT (a) VALUES (1)"));
        assertEquals(at(59, "invalid identifier 'S.NOSUCH1'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND"
            + " s.nosuch1 = 1 THEN DELETE WHEN NOT MATCHED AND s.nosuch2 = 1 THEN INSERT (a) VALUES (1)"));
        assertEquals(at(87, "invalid identifier 'S.NOSUCH1'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND"
            + " s.a = 1 THEN UPDATE SET a = s.nosuch1 WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(95, "invalid identifier 'S.NOSUCH1'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED AND"
            + " s.a = 1 THEN INSERT (a) VALUES (s.nosuch1) WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch2)"));
        assertEquals(at(59, "invalid identifier 'S.NOSUCH1'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND s.nosuch1 = 1 THEN UPDATE SET a = nosuchfn(1)"));
        assertEquals(at(83, "invalid identifier 'NOSUCH1'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = (SELECT nosuch1 FROM FULL_T) WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch2)"));
        assertEquals(at(46, "invalid identifier 'NOSUCH1'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = (SELECT nosuch1 FROM FULL_T) WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(27, "invalid identifier 'NOSUCH1'"),
            answer("MERGE INTO T USING (SELECT nosuch1 FROM FULL_T) s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = nosuch2"));
        assertEquals(at(118, "invalid identifier 'S.NOSUCH2'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND"
            + " s.nosuch1 = 1 THEN DELETE WHEN MATCHED THEN UPDATE SET a = s.nosuch2"));
        assertEquals(at(80, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.b, nosuchtarget = 1"));
        assertEquals(at(75, "invalid identifier 'NOSUCHTARGET'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a, nosuchtarget) VALUES (1, s.nosuch)"));
        assertEquals(at(130, "invalid identifier 'S.NOSUCH'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = TO_DATE(TRUE) WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch)"));
        assertEquals(at(89, "invalid identifier 'S.NOSUCH2'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.nosuch1 WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch2)"));
        assertEquals(at(59, "invalid identifier 'S.NOSUCH1'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND"
            + " s.nosuch1 = 1 THEN UPDATE SET a = 1 WHEN NOT MATCHED AND s.nosuch2 = 1 THEN INSERT (a) VALUES (1)"));
        assertEquals(unpositioned("Unknown functions NOSUCHFN2, NOSUCHFN."), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN"
            + " UPDATE SET a = nosuchfn(1) WHEN NOT MATCHED THEN INSERT (a) VALUES (nosuchfn2(1))"));
        assertEquals(unpositioned("Unknown functions NOSUCHFN2, NOSUCHFN, NOSUCHFN3."), answer("MERGE INTO T USING EMPTY_S s ON nosuchfn3(1) = s.a"
            + " WHEN MATCHED THEN UPDATE SET a = nosuchfn(1) WHEN NOT MATCHED THEN INSERT (a) VALUES (nosuchfn2(1))"));
    }

    @Test
    public void theStatementsShapeIsJudgedFirst() {
        assertEquals(at(84, "Unreachable merge case."),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.b WHEN MATCHED THEN DELETE"));
        assertEquals(at(130, "Unreachable merge case."), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = s.b WHEN NOT MATCHED THEN INSERT (a) VALUES (s.b) WHEN MATCHED THEN DELETE"));
        assertEquals(at(91, "Unreachable merge case."), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN"
            + " INSERT (a) VALUES (1) WHEN NOT MATCHED THEN INSERT (a) VALUES (2)"));
        assertEquals(at(72, "Unreachable merge case."),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN DELETE WHEN MATCHED THEN UPDATE SET a = s.nosuch"));
        assertEquals(at(89, "Unreachable merge case."),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = s.nosuch WHEN MATCHED THEN DELETE"));
        assertEquals(unpositioned("duplicate column name 'A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a, a) VALUES (1, 2)"));
        assertEquals(unpositioned("duplicate column name 'A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = 1, a = 2"));
        assertEquals(unpositioned("duplicate column name 'A'"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = 1, a = s.nosuch"));
        assertEquals(unpositioned("Insert value list does not match column list expecting 1 but got 2"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (1, 2)"));
        assertEquals(unpositioned("Insert value list does not match column list expecting 2 but got 1"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT VALUES (1)"));
        assertEquals(unpositioned("Insert value list does not match column list expecting 1 but got 2"),
            answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch, 2)"));
    }

    @Test
    public void anAggregateIsRefusedWhereTheBranchGroupsNoRows() {
        final String grouped = unpositioned("[PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression");
        assertEquals(grouped, answer("MERGE INTO T USING FULL_T s ON T.a = s.a WHEN MATCHED THEN UPDATE SET a = SUM(s.b)"));
        assertEquals(grouped, answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED AND SUM(s.a) > 0 THEN DELETE"));
        assertEquals(unpositioned("Invalid aggregate function in ON clause [SUM(T.A)]"),
            answer("MERGE INTO T USING EMPTY_S s ON SUM(T.a) = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(at(125, "invalid identifier 'S.NOSUCH'"), answer("MERGE INTO T USING EMPTY_S s ON T.a = s.a WHEN MATCHED THEN UPDATE"
            + " SET a = SUM(s.b) WHEN NOT MATCHED THEN INSERT (a) VALUES (s.nosuch)"));
    }
}
