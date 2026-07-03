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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SyntaxErrorListener;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parses expression strings through the real ANTLR grammar and verifies
 * {@link ExpressionAstBuilder} produces the expected AST, rendered canonically by
 * {@link AstPrinterVisitor}. Also confirms that constructs deferred during the migration
 * fail loudly rather than silently dropping semantics.
 */
public class ExpressionAstBuilderTest {

    private static FrostlakeParser.BooleanExprContext parse(final String text) {
        FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(text);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        FrostlakeParser.BooleanExprContext ctx = parser.booleanExpr();
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
        assertEquals("true", ast("true"));
        assertEquals("false", ast("false"));
        assertEquals("null", ast("null"));
    }

    @Test
    public void testStringEscapeIsUnquoted() {
        assertEquals("'it's'", ast("'it''s'"));
    }

    @Test
    public void testColumnReferences() {
        assertEquals("name", ast("name"));
        assertEquals("u.name", ast("u.name"));
    }

    @Test
    public void testArithmeticPrecedence() {
        assertEquals("(1 + (2 * 3))", ast("1 + 2 * 3"));
        assertEquals("((1 + 2) * 3)", ast("(1 + 2) * 3"));
        assertEquals("((10 - 4) - 2)", ast("10 - 4 - 2"));
    }

    @Test
    public void testComparisonsAndLogical() {
        assertEquals("(a > 1)", ast("a > 1"));
        assertEquals("(a >= 1)", ast("a >= 1"));
        assertEquals("((a = 1) AND (b = 2))", ast("a = 1 AND b = 2"));
        assertEquals("((a = 1) OR (b = 2))", ast("a = 1 OR b = 2"));
    }

    @Test
    public void testLogicalPrecedence() {
        // AND binds tighter than OR.
        assertEquals("((a AND b) OR c)", ast("a AND b OR c"));
    }

    @Test
    public void testUnary() {
        assertEquals("(NOT active)", ast("NOT active"));
        assertEquals("(NOT (a = b))", ast("NOT (a = b)"));
        assertEquals("(- 5)", ast("-5"));
        assertEquals("5", ast("+5"));
    }

    @Test
    public void testConcat() {
        assertEquals("('a' || 'b')", ast("'a' || 'b'"));
    }

    // ---- Phase 1: predicates ----

    @Test
    public void testIsNull() {
        assertEquals("(a IS NULL)", ast("a IS NULL"));
        assertEquals("(a IS NOT NULL)", ast("a IS NOT NULL"));
    }

    @Test
    public void testLike() {
        assertEquals("(name LIKE 'a%')", ast("name LIKE 'a%'"));
        assertEquals("(name NOT LIKE 'a%')", ast("name NOT LIKE 'a%'"));
        assertEquals("(name ILIKE 'a%')", ast("name ILIKE 'a%'"));
        assertEquals("(name NOT ILIKE 'a%')", ast("name NOT ILIKE 'a%'"));
        // The ESCAPE char is carried on the LIKE node (honored at evaluation) but not shown in toString.
        assertEquals("(name LIKE 'a%')", ast("name LIKE 'a%' ESCAPE '!'"));
    }

    @Test
    public void testBetween() {
        assertEquals("(x BETWEEN 1 AND 10)", ast("x BETWEEN 1 AND 10"));
        assertEquals("(x NOT BETWEEN 1 AND 10)", ast("x NOT BETWEEN 1 AND 10"));
    }

    @Test
    public void testInList() {
        assertEquals("(x IN (1, 2, 3))", ast("x IN (1, 2, 3)"));
        assertEquals("(x NOT IN (1, 2))", ast("x NOT IN (1, 2)"));
    }

    // ---- Phase 1: CASE / CAST ----

    @Test
    public void testSearchedCase() {
        assertEquals("(CASE WHEN (a > 1) THEN 'big' ELSE 'small' END)",
            ast("CASE WHEN a > 1 THEN 'big' ELSE 'small' END"));
        assertEquals("(CASE WHEN a THEN 1 END)", ast("CASE WHEN a THEN 1 END"));
    }

    @Test
    public void testSimpleCase() {
        // CASE <operand> WHEN v ... rewrites each WHEN into (operand = v).
        assertEquals("(CASE WHEN (x = 1) THEN 'one' WHEN (x = 2) THEN 'two' ELSE 'other' END)",
            ast("CASE x WHEN 1 THEN 'one' WHEN 2 THEN 'two' ELSE 'other' END"));
    }

    @Test
    public void testCast() {
        assertEquals("(x :: INT)", ast("x :: INT"));
        assertEquals("(x :: VARCHAR)", ast("CAST(x AS VARCHAR)"));
        assertEquals("(y :: NUMBER(10,2))", ast("y :: NUMBER(10,2)"));
    }

    // ---- Phase 1: functions, JSON, access, interval, vars ----

    @Test
    public void testFunctionCalls() {
        assertEquals("COALESCE(a, b, 0)", ast("COALESCE(a, b, 0)"));
        assertEquals("ABS(5)", ast("ABS(5)"));
        assertEquals("COUNT(*)", ast("COUNT(*)"));
        assertEquals("COUNT(DISTINCT x)", ast("COUNT(DISTINCT x)"));
    }

    @Test
    public void testJsonLiterals() {
        assertEquals("{'a': 1, 'b': 2}", ast("{'a': 1, 'b': 2}"));
        assertEquals("[1, 2, 3]", ast("[1, 2, 3]"));
    }

    @Test
    public void testAccessPaths() {
        assertEquals("(data:name)", ast("data:name"));
        assertEquals("(data:addr.city)", ast("data:addr.city"));
        assertEquals("(arr[0])", ast("arr[0]"));
    }

    @Test
    public void testInterval() {
        assertEquals("(INTERVAL 5 DAY)", ast("INTERVAL 5 DAY"));
        // String form: INTERVAL '<n> <unit>'.
        assertEquals("(INTERVAL 10 DAYS)", ast("INTERVAL '10 days'"));
        assertEquals("(INTERVAL 3 MONTHS)", ast("INTERVAL '3 months'"));
    }

    @Test
    public void testSessionAndCurrentAndSystem() {
        assertEquals("$myvar", ast("$myvar"));
        assertEquals("CURRENT_TIMESTAMP()", ast("CURRENT_TIMESTAMP"));
        assertEquals("CURRENT_USER()", ast("CURRENT_USER"));
        assertEquals("SYSTEM$STREAM_HAS_DATA('s1')", ast("SYSTEM$STREAM_HAS_DATA('s1')"));
        assertEquals("SYSTEM$TYPEOF(x)", ast("SYSTEM$TYPEOF(x)"));
    }

    // ---- Phase 1: subqueries ----

    @Test
    public void testSubqueriesAndExists() {
        assertEquals("(subquery SELECT max(x) FROM t)", ast("(SELECT max(x) FROM t)"));
        assertEquals("(EXISTS (subquery SELECT 1 FROM t))", ast("EXISTS (SELECT 1 FROM t)"));
        assertEquals("(x IN ((subquery SELECT y FROM t)))", ast("x IN (SELECT y FROM t)"));
        assertEquals("(x > ALL (subquery SELECT y FROM t))", ast("x > ALL (SELECT y FROM t)"));
    }

    // ---- Deferred constructs must fail loudly ----

    @Test
    public void testTupleIn() {
        assertEquals("((a, b) IN (1, 2))", ast("(a, b) IN (1, 2)"));
        assertEquals("((a, b) NOT IN (1, 2, 3, 4))", ast("(a, b) NOT IN (1, 2, 3, 4)"));
        assertEquals("((a, b) IN ((subquery SELECT x, y FROM t)))", ast("(a, b) IN (SELECT x, y FROM t)"));
    }

    // A window function nested in an expression now builds a WindowFunctionExpression node (keyed by its
    // source text) that the window stage resolves per row — rather than failing loudly. The AST printer
    // renders the node as the call's original text.
    @Test
    public void testNestedWindowFunctionBuildsNode() {
        assertEquals("SUM(x) OVER (ORDER BY y)", ast("SUM(x) OVER (ORDER BY y)"));
    }

    // EXECUTE IMMEDIATE now builds an ExecuteImmediateExpression (evaluated as a scalar at run time).
    @Test
    public void testExecuteImmediate() {
        assertEquals("EXECUTE IMMEDIATE 'select 1'", ast("EXECUTE IMMEDIATE 'select 1'"));
        assertEquals("EXECUTE IMMEDIATE :s USING (1, 2)", ast("EXECUTE IMMEDIATE :s USING (1, 2)"));
    }

    // Named function arguments (f(name => value)) now build a FunctionCallExpression that carries the
    // names; the reorder to the target function's parameter order happens at evaluation time.
    @Test
    public void testNamedArguments() {
        assertEquals("FOO(x => 1)", ast("foo(x => 1)"));
        assertEquals("FOO(a, x => 1)", ast("foo(a, x => 1)"));
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
