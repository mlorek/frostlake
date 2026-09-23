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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A subquery compiles whole, in turn, once the enclosing query's names and ORDER BY position are settled: its
 * argument and predicate types, its own ORDER BY and GROUP BY positions, its argument counts, its constant arguments
 * and its set operator's column count are refused ahead of the enclosing query's own types, wherever each is written.
 * What a subquery refuses about placement — an aggregate in its WHERE, its grouped select list, a QUALIFY without a
 * window — waits behind the enclosing query's types (all live-verified).
 */
public class SubqueryTypesBeforeOuterTypesTest extends BaseDatabaseTest {

    private static final String VARCHAR_PLUS = "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)";
    private static final String NUMBER_PLUS = "Invalid argument types for function '+': (NUMBER(1,0), BOOLEAN)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2)");
        engine.execute("CREATE TABLE rt (n INT, g VARCHAR(5), d DATE)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    private static String plain(final String sentence) {
        return "SQL compilation error:\n" + sentence;
    }

    @Test
    public void aSubquerysArgumentTypeComesBeforeTheEnclosingQuerysTypes() {
        assertEquals(at(57, VARCHAR_PLUS), refusal("SELECT a FROM T WHERE 'a' + TRUE = 1 AND a = (SELECT 'x' + TRUE)"));
        assertEquals(at(38, VARCHAR_PLUS), refusal("SELECT a FROM T WHERE a = (SELECT 'x' + TRUE) AND 'a' + TRUE = 1"));
        assertEquals(at(29, VARCHAR_PLUS), refusal("SELECT 1 + TRUE, (SELECT 'x' + TRUE) FROM T"));
        assertEquals(at(47, VARCHAR_PLUS),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON (SELECT 'x' + TRUE) = 1 AND 1 + TRUE = 2"));
        assertEquals(at(72, VARCHAR_PLUS),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a = (SELECT 'x' + TRUE)"));
        assertEquals(at(63, VARCHAR_PLUS), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a WHERE f.a = (SELECT 'x' + TRUE)"));
        assertEquals(at(58, VARCHAR_PLUS), refusal("SELECT a FROM T WHERE 'x' + TRUE = 1 ORDER BY (SELECT 'y' + TRUE)"));
        assertEquals(at(48, VARCHAR_PLUS), refusal("SELECT UPPER(1, 2) FROM T WHERE a = (SELECT 'x' + TRUE)"));
        assertEquals(at(69, VARCHAR_PLUS),
            refusal("SELECT a FROM T GROUP BY a HAVING 'x' + TRUE = 1 AND a = (SELECT 'y' + TRUE)"));
    }

    @Test
    public void everyTypeRefusalOfASubqueryRanksAhead() {
        assertEquals(plain("Invalid data type [NUMBER(38,0)] for predicate [T.A]"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a IN (SELECT a FROM T WHERE a)"));
        assertEquals(plain("[9] is not a valid order by expression"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a = (SELECT a FROM T ORDER BY 9)"));
        assertEquals(at(28, "too many arguments for function [LOWER(1, 2)] expected 1, got 2"),
            refusal("SELECT UPPER(1, 2), (SELECT LOWER(1, 2)) FROM RT"));
        assertEquals(at(15, "too many arguments for function [LOWER(1, 2)] expected 1, got 2"),
            refusal("SELECT (SELECT LOWER(1, 2)), UPPER(1, 2) FROM RT"));
        assertEquals(plain("argument 1 to function RANDOM needs to be constant, found 'T.A'"),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT RANDOM(a) FROM T)"));
        assertEquals(plain("[9] is not a valid group by expression"),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM T GROUP BY 9)"));
        assertEquals(at(53, "not enough arguments for function [LENGTH()], expected 1, got 0"),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT LENGTH())"));
        assertEquals(plain("invalid number of result columns for set operator input branches, expected 1, got 2 in branch 2"),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM T UNION SELECT a, b FROM T)"));
        assertEquals(at(53, "Invalid argument types for function 'IFF': (NUMBER(38,0), NUMBER(1,0), NUMBER(1,0))"),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT IFF(a, 1, 2) FROM T)"));
    }

    @Test
    public void aSubquerysPlacementRefusalWaitsBehindTheEnclosingTypes() {
        assertEquals(at(37, NUMBER_PLUS),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE EXISTS (SELECT 1 FROM T QUALIFY a = 1)"));
        assertEquals(at(37, NUMBER_PLUS),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a = (SELECT SUM(1) FROM T WHERE SUM(a) > 1)"));
        assertEquals(at(37, NUMBER_PLUS),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a = (SELECT a FROM T GROUP BY b)"));
        assertEquals(at(26, VARCHAR_PLUS),
            refusal("SELECT a FROM T WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM T WHERE SUM(a) > 1)"));
        assertEquals(at(69, VARCHAR_PLUS),
            refusal("SELECT a FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1) AND 'a' + TRUE = 1"));
    }

    @Test
    public void namesAndTheOrderByPositionStillComeFirst() {
        assertEquals(plain("[9] is not a valid order by expression"),
            refusal("SELECT a FROM T WHERE a = (SELECT 'x' + TRUE) ORDER BY 9"));
        assertEquals(at(53, "invalid identifier 'NOSUCH'"),
            refusal("SELECT a FROM T WHERE a = (SELECT 'x' + TRUE) HAVING nosuch = 1"));
        assertEquals(at(55, "invalid identifier 'NOSUCH'"),
            refusal("SELECT a FROM T WHERE a = (SELECT 'x' + TRUE) ORDER BY nosuch"));
        assertEquals(plain("Unknown function NOSUCHFN."), refusal("SELECT nosuchfn(a) FROM T WHERE a = (SELECT 'x' + TRUE)"));
    }

    @Test
    public void eachSubqueryCompilesWholeBeforeTheNext() {
        assertEquals(at(38, VARCHAR_PLUS),
            refusal("SELECT a FROM T WHERE a = (SELECT 'x' + TRUE) AND a = (SELECT nosuch FROM T)"));
        assertEquals(at(22, VARCHAR_PLUS), refusal("SELECT a, (SELECT 'x' + TRUE) FROM T WHERE a = (SELECT nosuch FROM T)"));
        assertEquals(at(68, VARCHAR_PLUS),
            refusal("SELECT a FROM T WHERE a = (SELECT 1 + TRUE FROM T WHERE (SELECT 'x' + TRUE) = 1)"));
        assertEquals(at(81, VARCHAR_PLUS),
            refusal("SELECT a FROM T WHERE a = (SELECT a FROM T WHERE SUM(a) > 1) AND a = (SELECT 'x' + TRUE)"));
    }
}
