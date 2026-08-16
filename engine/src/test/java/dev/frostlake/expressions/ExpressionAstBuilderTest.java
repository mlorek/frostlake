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

package dev.frostlake.expressions;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SyntaxErrorListener;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parses expression strings through the real ANTLR grammar and verifies
 * {@link ExpressionAstBuilder} produces the expected AST, rendered canonically by
 * {@link AstPrinterVisitor}. Also confirms that constructs deferred during the migration
 * fail loudly rather than silently dropping semantics.
 */
public class ExpressionAstBuilderTest {

    private static FrostlakeParser.BooleanExprContext parse(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        final SyntaxErrorListener errorListener = new SyntaxErrorListener(text);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        final FrostlakeParser.BooleanExprContext ctx = parser.booleanExpr();
        errorListener.throwIfErrors();
        return ctx;
    }

    private static String ast(final String text) {
        return AstPrinterVisitor.print(new ExpressionAstBuilder().build(parse(text)));
    }


    // ---- Phase 0 slice ----

    @Test
    public void testLiterals() {
        assertEquals("42", ast("42"));
        assertEquals("'hello'", ast("'hello'"));
        // Booleans print UPPER — the refusal-bracket spelling a real account uses ([IFF(TRUE)]).
        assertEquals("TRUE", ast("true"));
        assertEquals("FALSE", ast("false"));
        assertEquals("null", ast("null"));
    }

    @Test
    public void testStringEscapeIsUnquoted() {
        assertEquals("'it's'", ast("'it''s'"));
    }

    @Test
    public void testColumnReferences() {
        assertEquals("NAME", ast("name"));
        assertEquals("U.NAME", ast("u.name"));
    }

    @Test
    public void testArithmeticPrecedence() {
        assertEquals("(1 + (2 * 3))", ast("1 + 2 * 3"));
        assertEquals("((1 + 2) * 3)", ast("(1 + 2) * 3"));
        assertEquals("((10 - 4) - 2)", ast("10 - 4 - 2"));
    }

    @Test
    public void testComparisonsAndLogical() {
        assertEquals("(A > 1)", ast("a > 1"));
        assertEquals("(A >= 1)", ast("a >= 1"));
        assertEquals("((A = 1) AND (B = 2))", ast("a = 1 AND b = 2"));
        assertEquals("((A = 1) OR (B = 2))", ast("a = 1 OR b = 2"));
    }

    @Test
    public void testLogicalPrecedence() {
        // AND binds tighter than OR.
        assertEquals("((A AND B) OR C)", ast("a AND b OR c"));
    }

    @Test
    public void testUnary() {
        assertEquals("(NOT ACTIVE)", ast("NOT active"));
        assertEquals("(NOT (A = B))", ast("NOT (a = b)"));
        assertEquals("(- 5)", ast("-5"));
        // Unary plus is a node of its own — it converts a text or a VARIANT operand and refuses the
        // families that have no numeric reading under its own name — so it prints, like the minus.
        assertEquals("(+ 5)", ast("+5"));
    }

    @Test
    public void testConcat() {
        assertEquals("('a' || 'b')", ast("'a' || 'b'"));
    }

    // ---- Phase 1: predicates ----

    @Test
    public void testIsNull() {
        assertEquals("(A IS NULL)", ast("a IS NULL"));
        assertEquals("(A IS NOT NULL)", ast("a IS NOT NULL"));
    }

    @Test
    public void testLike() {
        assertEquals("(NAME LIKE 'a%')", ast("name LIKE 'a%'"));
        assertEquals("(NAME NOT LIKE 'a%')", ast("name NOT LIKE 'a%'"));
        assertEquals("(NAME ILIKE 'a%')", ast("name ILIKE 'a%'"));
        assertEquals("(NAME NOT ILIKE 'a%')", ast("name NOT ILIKE 'a%'"));
        // The ESCAPE char is carried on the LIKE node (honored at evaluation) but not shown in toString.
        assertEquals("(NAME LIKE 'a%')", ast("name LIKE 'a%' ESCAPE '!'"));
    }

    @Test
    public void testBetween() {
        assertEquals("(X BETWEEN 1 AND 10)", ast("x BETWEEN 1 AND 10"));
        assertEquals("(X NOT BETWEEN 1 AND 10)", ast("x NOT BETWEEN 1 AND 10"));
    }

    @Test
    public void testInList() {
        assertEquals("(X IN (1, 2, 3))", ast("x IN (1, 2, 3)"));
        assertEquals("(X NOT IN (1, 2))", ast("x NOT IN (1, 2)"));
    }

    // ---- Phase 1: CASE / CAST ----

    @Test
    public void testSearchedCase() {
        assertEquals("(CASE WHEN (A > 1) THEN 'big' ELSE 'small' END)",
            ast("CASE WHEN a > 1 THEN 'big' ELSE 'small' END"));
        assertEquals("(CASE WHEN A THEN 1 END)", ast("CASE WHEN a THEN 1 END"));
    }

    @Test
    public void testSimpleCase() {
        // CASE <operand> WHEN v ... rewrites each WHEN into (operand = v).
        assertEquals("(CASE WHEN (X = 1) THEN 'one' WHEN (X = 2) THEN 'two' ELSE 'other' END)",
            ast("CASE x WHEN 1 THEN 'one' WHEN 2 THEN 'two' ELSE 'other' END"));
    }

    @Test
    public void testCast() {
        assertEquals("(X :: INT)", ast("x :: INT"));
        assertEquals("(X :: VARCHAR)", ast("CAST(x AS VARCHAR)"));
        assertEquals("(Y :: NUMBER(10,2))", ast("y :: NUMBER(10,2)"));
    }

    // ---- Phase 1: functions, JSON, access, interval, vars ----

    @Test
    public void testFunctionCalls() {
        assertEquals("COALESCE(A, B, 0)", ast("COALESCE(a, b, 0)"));
        assertEquals("ABS(5)", ast("ABS(5)"));
        assertEquals("COUNT(*)", ast("COUNT(*)"));
        assertEquals("COUNT(DISTINCT X)", ast("COUNT(DISTINCT x)"));
    }

    @Test
    public void testJsonLiterals() {
        assertEquals("{'a': 1, 'b': 2}", ast("{'a': 1, 'b': 2}"));
        assertEquals("[1, 2, 3]", ast("[1, 2, 3]"));
    }

    @Test
    public void testAccessPaths() {
        assertEquals("(DATA:name)", ast("data:name"));
        assertEquals("(DATA:addr.city)", ast("data:addr.city"));
        assertEquals("(ARR[0])", ast("arr[0]"));
    }

    @Test
    public void testInterval() {
        // Snowflake forms only (live-verified): quoted string (plural units inside, multi-part,
        // bare number = seconds) and quoted number + SINGULAR unit suffix. Unquoted amounts and
        // plural suffixes are not interval syntax.
        // The printed form is CANONICAL in the UNIT — 'days' and DAY are one unit, not two — but it
        // keeps WHICH SPELLING carried it, because that decides whether a DATE stays a DATE, and this
        // print is used as an expression key.
        assertEquals("(INTERVAL 5 DAY keyword)", ast("INTERVAL '5' DAY"));
        assertEquals("(INTERVAL 10 DAY in-string)", ast("INTERVAL '10 days'"));
        assertEquals("(INTERVAL 3 MONTH in-string)", ast("INTERVAL '3 months'"));
        // A bare number is the STRING form with its unit defaulted, not the keyword form.
        assertEquals("(INTERVAL 10 SECOND in-string)", ast("INTERVAL '10'"));
    }

    @Test
    public void testSessionAndCurrentAndSystem() {
        assertEquals("$myvar", ast("$myvar"));
        assertEquals("CURRENT_TIMESTAMP()", ast("CURRENT_TIMESTAMP"));
        assertEquals("CURRENT_USER()", ast("CURRENT_USER"));
        assertEquals("SYSTEM$STREAM_HAS_DATA('s1')", ast("SYSTEM$STREAM_HAS_DATA('s1')"));
        assertEquals("SYSTEM$TYPEOF(X)", ast("SYSTEM$TYPEOF(x)"));
    }

    // ---- Phase 1: subqueries ----

    @Test
    public void testSubqueriesAndExists() {
        assertEquals("(subquery SELECT max(x) FROM t)", ast("(SELECT max(x) FROM t)"));
        assertEquals("(EXISTS (subquery SELECT 1 FROM t))", ast("EXISTS (SELECT 1 FROM t)"));
        assertEquals("(X IN ((subquery SELECT y FROM t)))", ast("x IN (SELECT y FROM t)"));
        assertEquals("(X > ALL (subquery SELECT y FROM t))", ast("x > ALL (SELECT y FROM t)"));
    }

    // ---- Deferred constructs must fail loudly ----

    @Test
    public void testTupleIn() {
        // The flat spellings still PARSE (so validation can refuse them with Snowflake's ROW-typed
        // message); the tuple-row list is the semantically valid list form.
        assertEquals("((A, B) IN (1, 2))", ast("(a, b) IN (1, 2)"));
        assertEquals("((A, B) NOT IN (1, 2, 3, 4))", ast("(a, b) NOT IN (1, 2, 3, 4)"));
        assertEquals("((A, B) IN ((1, 2), (3, 4)))", ast("(a, b) IN ((1, 2), (3, 4))"));
        assertEquals("((A, B) NOT IN ((1, null)))", ast("(a, b) NOT IN ((1, NULL))"));
        assertEquals("((A, B) IN ((subquery SELECT x, y FROM t)))", ast("(a, b) IN (SELECT x, y FROM t)"));
    }

    // A window function nested in an expression now builds a WindowFunctionExpression node (keyed by its
    // source text) that the window stage resolves per row — rather than failing loudly. The AST printer
    // renders the node as the call's original text.
    @Test
    public void testNestedWindowFunctionBuildsNode() {
        assertEquals("SUM(x) OVER (ORDER BY y)", ast("SUM(x) OVER (ORDER BY y)"));
    }

    // EXECUTE IMMEDIATE is a statement, not an expression (it previously built an
    // ExecuteImmediateExpression). Asserted through ExpressionEvaluator.parse rather than the ast()
    // helper above, because the two stop at different places: `execute` is a live-legal column name,
    // so the grammar reads it as one and the helper — which does not check for trailing input —
    // returns that name happily. The production entry point refuses the text as a whole, which is
    // the property worth guarding.
    @Test
    public void testExecuteImmediateIsNotAnExpression() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ExpressionEvaluator.parse("EXECUTE IMMEDIATE 'select 1'");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ExpressionEvaluator.parse("EXECUTE IMMEDIATE :s USING (1, 2)");
            }
        });
        // And the leading word alone is just a name now.
        assertEquals("EXECUTE", ast("EXECUTE"));
    }

    // Named function arguments (f(name => value)) now build a FunctionCallExpression that carries the
    // names; the reorder to the target function's parameter order happens at evaluation time.
    @Test
    public void testNamedArguments() {
        assertEquals("FOO(x => 1)", ast("foo(x => 1)"));
        assertEquals("FOO(A, x => 1)", ast("foo(a, x => 1)"));
        assertEquals("FOO(a => 1, b => 2)", ast("foo(a => 1, b => 2)"));
    }

    // :name bind variables build a BindVariableExpression (resolved from the procedural scope at
    // evaluation time) — no longer a deferred construct.
    @Test
    public void testBindVariable() {
        assertEquals(":myparam", ast(":myparam"));
        assertEquals("(:x + 1)", ast(":x + 1"));
    }
}
