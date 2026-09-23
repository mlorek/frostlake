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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A join's ON condition compiles with its statement, in the scope in force at its join: an invalid identifier, an
 * unknown function, a mistyped argument or a condition that is no predicate is refused over an empty side too, where
 * Frostlake answered no rows, or refused without a position only on a pair it reached. Each refusal takes its rank
 * among the statement's own: the names after the select list's and ahead of every later clause's, the argument types
 * after every clause's names and the ORDER BY ordinal and ahead of every other clause's types, and a condition that is
 * no predicate after every condition's argument types (live-verified).
 */
public class JoinConditionCompileTest extends BaseDatabaseTest {

    private static final String AT = "SQL compilation error: error line 1 at position ";
    private static final String PLUS_NUMBER_BOOLEAN = "\nInvalid argument types for function '+': (NUMBER(1,0), BOOLEAN)";
    private static final String PLUS_VARCHAR_BOOLEAN =
        "\nInvalid argument types for function '+': (VARCHAR(1), BOOLEAN)";
    private static final String NUMBER_PREDICATE_TA =
        "SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [T.A]";
    private static final String BAD_ORDINAL = "SQL compilation error:\n[9] is not a valid order by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (a INT, b INT)");
        engine.execute("CREATE TABLE FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO FULL_T VALUES (1, 2)");
        engine.execute("CREATE TABLE VT (a INT, v VARIANT, s VARCHAR, d DATE)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String invalid(final int position, final String name) {
        return AT + position + "\ninvalid identifier '" + name + "'";
    }

    private int rows(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void aNameIsRefusedWhateverTheSidesHold() {
        assertEquals(invalid(35, "F.NOSUCH2"), refusal("SELECT T.a FROM T JOIN FULL_T f ON f.nosuch2 = T.a"));
        assertEquals(invalid(35, "T.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch2 = f.a"));
        assertEquals(invalid(42, "G.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a"));
        assertEquals(invalid(40, "T.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f LEFT JOIN T ON T.nosuch2 = f.a"));
        assertEquals(invalid(41, "T.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f RIGHT JOIN T ON T.nosuch2 = f.a"));
        assertEquals(invalid(40, "T.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f FULL JOIN T ON T.nosuch2 = f.a"));
        assertEquals(invalid(42, "NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON nosuch = f.a"));
        assertEquals(invalid(42, "X.A"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON x.a = f.a"));
        assertEquals(invalid(49, "D"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND d = 1"));
        assertEquals(invalid(35, "FULL_T.A"), refusal("SELECT f.a FROM FULL_T f JOIN T ON FULL_T.a = T.a"));
        assertEquals(invalid(47, "F.NOSUCH"), refusal("SELECT * FROM FULL_T f JOIN T ON T.a = f.a AND f.nosuch = 1"));
        assertEquals("SQL compilation error:\nambiguous column name 'A'",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON a = f.a"));
    }

    @Test
    public void aNameIsJudgedInTheScopeOfItsOwnJoin() {
        assertEquals(invalid(62, "T.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.a = f.a JOIN T ON T.nosuch = g.a"));
        assertEquals(invalid(42, "T.A"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON T.a = f.a JOIN T ON T.a = g.a"));
        assertEquals(invalid(68, "F.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a JOIN FULL_T g ON g.a = f.nosuch AND T.nosuch = 1"));
        assertEquals(invalid(51, "F.NOSUCH"), refusal("SELECT f.a FROM FULL_T f, T JOIN FULL_T g ON g.a = f.nosuch"));
        assertEquals(invalid(61, "L.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN LATERAL (SELECT f.a AS z) l ON l.nosuch = f.a"));
        assertEquals(invalid(50, "T.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN (FULL_T g JOIN T ON T.nosuch = g.a) ON TRUE"));
        assertEquals(invalid(71, "H.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN (FULL_T g JOIN FULL_T h ON h.a = g.a) ON h.nosuch = f.a"));
        assertEquals(invalid(54, "G.NOSUCH"),
            refusal("SELECT (SELECT COUNT(*) FROM FULL_T h JOIN T ON h.a = g.nosuch) FROM FULL_T g"));
        assertEquals(invalid(70, "S.NOSUCH"),
            refusal("SELECT * FROM FULL_T f JOIN (SELECT a AS x FROM T) s ON s.x = f.a AND s.nosuch = 1"));
        assertEquals(invalid(63, "C.NOSUCH"),
            refusal("WITH c AS (SELECT a FROM T) SELECT f.a FROM FULL_T f JOIN c ON c.nosuch = f.a"));
    }

    @Test
    public void aFunctionAnArgumentOrAPredicateIsRefusedOverAnEmptySide() {
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON nosuchfn(T.a) = f.a"));
        assertEquals(AT + 51 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND 1 + TRUE = 2"));
        assertEquals("SQL compilation error:\ninvalid type [TO_DATE(T.B)] for parameter 'TO_DATE'",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON TO_DATE(T.b) = f.a"));
        assertEquals(AT + "49\ntoo many arguments for function [UPPER(T.A, 1)] expected 1, got 2",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND UPPER(T.a, 1) = 'X'"));
        assertEquals("SQL compilation error: ['NOSUCHUNIT'] is not a valid date/time component for function DATEADD.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND DATEADD(nosuchunit, 1, T.a) IS NULL"));
        assertEquals(AT + "35\nInvalid argument types for function 'IFF': (NUMBER(38,0), BOOLEAN, BOOLEAN)",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON IFF(T.a, TRUE, FALSE)"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'T.A' of type [NUMBER(38,0)] into expected type "
            + "[BOOLEAN]", refusal("SELECT f.a FROM FULL_T f JOIN T ON CASE WHEN T.a THEN TRUE END"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'F.A' of type [NUMBER(38,0)] into expected type "
            + "[DATE]", refusal("SELECT f.a FROM FULL_T f JOIN VT ON VT.d = f.a"));
        assertEquals("SQL compilation error:\nCan not convert parameter 'VT.D' of type [DATE] into expected type "
            + "[NUMBER(38,0)]", refusal("SELECT f.a FROM FULL_T f JOIN VT ON f.a IN (VT.d)"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(38,0)] for predicate [G.A]",
            refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.a"));
        assertEquals("SQL compilation error:\nInvalid data type [VARCHAR(1)] for predicate ['x']",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 'x'"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(1,0)] for predicate [1]",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1"));
        assertEquals("SQL compilation error:\nInvalid data type [VARCHAR(16777216)] for predicate [VT.S]",
            refusal("SELECT f.a FROM FULL_T f JOIN VT ON VT.s"));
    }

    @Test
    public void theConditionsNamesRankAfterTheSelectListsAndAheadOfEveryOtherClauses() {
        assertEquals(invalid(7, "NOSUCH1"), refusal("SELECT nosuch1 FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a"));
        assertEquals(invalid(42, "G.NOSUCH2"),
            refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a WHERE nosuch3 = 1"));
        assertEquals(invalid(59, "F.NOSUCH2"),
            refusal("SELECT (SELECT nosuch FROM FULL_T) FROM T JOIN FULL_T f ON f.nosuch2 = T.a"));
        assertEquals(invalid(52, "G.NOSUCH2"), refusal("SELECT nosuchfn(f.a) FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a"));
        assertEquals(invalid(42, "G.NOSUCH2"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a ORDER BY 9"));
        assertEquals(invalid(42, "G.NOSUCH2"),
            refusal("SELECT f.b FROM FULL_T f JOIN FULL_T g ON g.nosuch2 = f.a GROUP BY f.a"));
        assertEquals(invalid(35, "T.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a WHERE f.a IN (SELECT nosuch6 FROM T)"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a HAVING nosuch9 = 1"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a QUALIFY nosuch8 = 1"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a ORDER BY nosuch5"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a GROUP BY nosuch10"));
        assertEquals(invalid(35, "T.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = f.a WHERE SUM(f.a) > 1"));
        assertEquals(invalid(59, "T.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON nosuchfn(T.a) = f.a AND T.nosuch = 1"));
        assertEquals(invalid(66, "NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND 1 + TRUE = 2 AND nosuch = 1"));
        assertEquals(invalid(61, "T2.NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 JOIN T t2 ON t2.nosuch = 1"));
    }

    @Test
    public void theConditionsFunctionsJoinTheStatementsOneSentence() {
        assertEquals("SQL compilation error:\nUnknown functions FNA, FNB, FNC.",
            refusal("SELECT fna(f.a) FROM FULL_T f JOIN FULL_T g ON fnb(g.a) = f.a WHERE fnc(1) = 1"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 AND nosuchfn(1) = 1"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a JOIN (T t2 JOIN T t3 ON nosuchfn(t3.a) = 1) ON TRUE"));
        assertEquals(invalid(61, "NOSUCH3"), refusal("SELECT f.a FROM FULL_T f JOIN T ON nosuchfn(T.a) = f.a WHERE nosuch3 = 1"));
        assertEquals(BAD_ORDINAL, refusal("SELECT f.a FROM FULL_T f JOIN T ON nosuchfn(1) = 1 ORDER BY 9"));
    }

    @Test
    public void theConditionsTypesRankAfterEveryNameAndTheOrdinal() {
        assertEquals(invalid(75, "NOSUCH3"),
            refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.a = f.a AND 1 + TRUE = 2 WHERE nosuch3 = 1"));
        assertEquals(invalid(52, "NOSUCH3"), refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.a WHERE nosuch3 = 1"));
        assertEquals(invalid(46, "NOSUCH9"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a HAVING nosuch9 = 1"));
        assertEquals(invalid(55, "NOSUCH9"), refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 HAVING nosuch9 = 1"));
        assertEquals(invalid(56, "NOSUCH8"), refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 QUALIFY nosuch8 = 1"));
        assertEquals(invalid(71, "NOSUCH10"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a > 0 GROUP BY nosuch10"));
        assertEquals(invalid(69, "NOSUCH"),
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE EXISTS (SELECT nosuch FROM T)"));
        assertEquals(BAD_ORDINAL, refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 ORDER BY 9"));
        assertEquals(BAD_ORDINAL, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a ORDER BY 9"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 ORDER BY nosuchfn(1)"));
    }

    @Test
    public void theConditionsTypesRankAheadOfEveryOtherClausesTypes() {
        assertEquals(AT + 65 + PLUS_VARCHAR_BOOLEAN,
            refusal("SELECT 1 + TRUE FROM FULL_T f JOIN FULL_T g ON g.a = f.a AND 'a' + TRUE = 2"));
        assertEquals(AT + 44 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON 1 + TRUE = 2 WHERE 'a' + TRUE = 1"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a WHERE 'a' + TRUE = 1"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT 1 + TRUE FROM FULL_T f JOIN T ON T.a"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a WHERE f.a"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a GROUP BY f.a HAVING f.a"));
        assertEquals(NUMBER_PREDICATE_TA, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a WHERE SUM(f.a) > 1"));
        assertEquals(AT + 53 + PLUS_VARCHAR_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND 'a' + TRUE = 1 WHERE f.a"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.b FROM FULL_T f JOIN T ON 1 + TRUE = 2 GROUP BY f.a"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE SUM(f.a) > 1"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE ROW_NUMBER() OVER (ORDER BY 1) = 1"));
        assertEquals(AT + 58 + PLUS_VARCHAR_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON SUM(T.a) > 0 WHERE 'a' + TRUE = 1"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 AND SUM(T.a) > 0"));
        assertEquals(AT + 54 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON SUM(T.a) > 0 AND 1 + TRUE = 2"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 ORDER BY 'a' + TRUE"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 GROUP BY f.a HAVING 'a' + TRUE = 1"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 QUALIFY 'a' + TRUE = 1"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE EXISTS (SELECT 1 FROM T QUALIFY a = 1)"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 WHERE f.a = (SELECT a FROM T GROUP BY b)"));
    }

    @Test
    public void everyConditionsArgumentTypesRankAheadOfAnyConditionThatIsNoPredicate() {
        assertEquals(AT + 54 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a JOIN T t2 ON 1 + TRUE = 2"));
        assertEquals(AT + 63 + PLUS_VARCHAR_BOOLEAN,
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a JOIN T t2 ON t2.a = 'x' + TRUE"));
        assertEquals(AT + 43 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = 1 + TRUE JOIN T t2 ON t2.a"));
        assertEquals(AT + 37 + PLUS_NUMBER_BOOLEAN, refusal("SELECT f.a FROM FULL_T f JOIN T ON 1 + TRUE = 2 JOIN T t2 ON t2.a"));
        assertEquals("SQL compilation error:\nInvalid data type [VARCHAR(1)] for predicate ['x']",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON 'x' JOIN T t2 ON t2.b"));
        assertEquals(AT + "47\ntoo many arguments for function [UPPER(T.A, 1)] expected 1, got 2",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON T.a = 1 AND UPPER(T.a, 1) = 'X' JOIN T t2 ON 1 + TRUE = 2"));
    }

    @Test
    public void anAggregateOrAWindowKeepsItsOwnSentence() {
        assertEquals("SQL compilation error:\nInvalid aggregate function in ON clause [SUM(T.A)]",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON SUM(T.a) > 0"));
        assertEquals("SQL compilation error:\nWindow function [ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)] appears "
            + "outside of SELECT, QUALIFY, and ORDER BY clauses.",
            refusal("SELECT f.a FROM FULL_T f JOIN T ON ROW_NUMBER() OVER (ORDER BY 1) = 1"));
    }

    @Test
    public void aConditionThatCompilesStillJoins() {
        assertEquals(0, rows("SELECT f.a AS z FROM FULL_T f JOIN T ON T.a = z"));
        assertEquals(1, rows("SELECT f.a AS z FROM FULL_T f JOIN FULL_T g ON g.a = z"));
        assertEquals(1, rows("SELECT f.a FROM FULL_T f LEFT JOIN T ON T.a = f.a AND 'x' + 1 = 2"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON NULL"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = 'x'"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND NOT T.b"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a IS NULL = f.a"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON \"T\".a = f.a"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON test_db.test_schema.T.a = f.a"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND T.a IN (1, 'x')"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN VT ON VT.v = f.a"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN VT ON VT.s = f.a"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND f.a::VARIANT"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND T.b"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON (T.a = f.a) OR T.b"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON IFF(T.a = 1, TRUE, FALSE)"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON f.a = ANY (SELECT a FROM T)"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND CURRENT_DATE > '2020-01-01'"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND f.a BETWEEN 'x' AND 'y'"));
        assertEquals(0, rows("SELECT f.a FROM FULL_T f JOIN T ON T.a = f.a AND f.a LIKE 'x%'"));
        final ResultSet correlated = engine.executeQuery(
            "SELECT (SELECT COUNT(*) FROM FULL_T h JOIN T ON h.a = g.a) AS n FROM FULL_T g");
        assertEquals(1, correlated.getRowCount());
        assertEquals(0L, ((Number) correlated.getRows().get(0).getValue(0)).longValue());
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT f.a FROM FULL_T f JOIN FULL_T g ON g.a = 'x'"));
    }
}
