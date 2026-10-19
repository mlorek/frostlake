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

package dev.frostlake.executor.expressions;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An expression is parsed in two stages - the quick SLL prediction first, full LL prediction only for a text the
 * quick one gives up on - and reads exactly as full prediction alone reads it: the same tree for a text both
 * read, the full parse's tree for one only it reads, and a refusal for a text neither reads.
 */
public class TwoStageExpressionParseTest {

    private static final List<String> RULES = Arrays.asList(FrostlakeParser.ruleNames);

    private static final String[] EXPRESSIONS = {
        "1",
        "a + b * 2 - c / 4 % 3",
        "-x || 'it''s' || $$dollar$$",
        "CASE WHEN a > 1 AND b IS NOT NULL THEN 'x' WHEN c IN (1, 2, 3) THEN 'y' ELSE NULL END",
        "CASE x WHEN 1 THEN 'one' ELSE 'other' END",
        "a BETWEEN 1 AND 10 OR NOT (b LIKE 'x%' ESCAPE '!') AND c ILIKE ANY ('a', 'b')",
        "CAST(a AS NUMBER(10,2)) + b::VARCHAR(5)::NUMBER",
        "TRY_CAST(s AS DATE)",
        "v:a.b[0]::VARCHAR",
        "v['k'][1]:x",
        "{'k': k, 'n': [1, 2, {'m': NULL}]}",
        "[1, 'two', TRUE, NULL]",
        "f(a => 1, b => 'x')",
        "f(1, b => 2)",
        "test_db.test_schema.f(x) + s.g()",
        "TRANSFORM(a, x -> x * 2)",
        "REDUCE(a, 0, (acc, v) -> acc + v)",
        "FILTER(a, e INT -> e > k)",
        "COUNT(DISTINCT a) + SUM(b) OVER (PARTITION BY c ORDER BY d ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)",
        "LISTAGG(a, ',') WITHIN GROUP (ORDER BY a DESC)",
        "FIRST_VALUE(a) IGNORE NULLS OVER (ORDER BY b)",
        "EXTRACT(year FROM d) + DATEADD(day, 1, d) - INTERVAL '1 day, 2 hours'",
        "DATE '2024-01-02' < TIMESTAMP '2024-01-02 03:04:05'",
        "x IN (SELECT id FROM t WHERE t.a = x)",
        "EXISTS (SELECT 1 FROM t) AND (SELECT MAX(a) FROM t) > 1",
        "(a, b) IN ((1, 2), (3, 4))",
        "a IS DISTINCT FROM b",
        "a = ANY (SELECT b FROM t)",
        "IFF(a IS NULL, 'n', TO_CHAR(a, '999'))",
        "CURRENT_DATE - 1 > CURRENT_TIMESTAMP",
        "CURRENT_TIMESTAMP((3))",
        "CURRENT_TIMESTAMP((3)) IS NOT NULL",
        ":bound + $session_var + ?",
        "\"Quoted Name\" + \"a\".\"b\".\"c\"",
        "a COLLATE 'en-ci' = b",
        "NOT a AND NOT b OR c",
        "1.5e10 + 0.5 - .25",
        "POSITION('a' IN s) + LENGTH(TRIM(s)) + LENGTH(RTRIM(s, 'x'))",
        "OBJECT_CONSTRUCT(*)",
        "SYSTEM$TYPEOF(NOT TRUE)",
    };

    private static String fullPredictionTree(final String expression) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.BooleanExprContext tree = parser.booleanExpr();
        NamedCallRewrite.apply(tree);
        return tree.toStringTree(RULES);
    }

    @Test
    public void anExpressionReadsAsFullPredictionAloneReadsIt() {
        for (final String expression : EXPRESSIONS) {
            assertEquals(fullPredictionTree(expression),
                AntlrExpressionParser.parseTree(expression).toStringTree(RULES), expression);
        }
    }

    @Test
    public void anExpressionReadsTheSameTheSecondTime() {
        for (final String expression : EXPRESSIONS) {
            final String first = AntlrExpressionParser.parseTree(expression).toStringTree(RULES);
            assertEquals(first, AntlrExpressionParser.parseTree(expression).toStringTree(RULES), expression);
        }
    }

    @Test
    public void aTextThatIsNoExpressionIsRefusedAsBefore() {
        for (final String malformed : new String[] {"1 +", "f(", "CASE WHEN 1 THEN", ")", "a = = b"}) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    AntlrExpressionParser.parseTree(malformed);
                }
            }, malformed);
        }
        final IllegalStateException trailing = assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                AntlrExpressionParser.parseTree("1 2");
            }
        });
        assertEquals("Trailing input after expression: 1 2", trailing.getMessage());
    }
}
