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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The calls of a SQL UDF's expression body, held at CREATE to what their functions take, as the account compiles
 * the body with the routine's parameters in scope (live-verified): a count no built-in or user-defined function
 * takes, argument types none takes, and named arguments that fit no signature are refused where the call stands
 * in the body, an inner call before the call holding it and the first of two in the text.
 *
 * <pre>
 *   AS 'ABS(1, 2)'        error line 1 at position 1   too many arguments for function [ABS(1, 2)] expected 1, got 2
 *   AS '1 + ABS(1, 2)'    error line 1 at position 5
 *   AS 'LOG(10)'          not enough arguments for function [LOG(10)], expected 2, got 1
 *   AS 'g2(p)'            Invalid argument types for function 'G2': (NUMBER(38,0))     — p declared INT
 *   AS 'g2(z =&gt; 1)'       named arguments [Z] do not match any signature for function G2
 *   AS 'ov(a =&gt; 1, b =&gt; 2)'  More than one function of OV are resolved in prepare state.
 * </pre>
 *
 * <p>The body compiles inside a frame of its own, one character before it, so a place on its first line counts
 * from 1 and a later line's is unmoved. A query body is compiled as a statement where its names resolve, and a
 * block is not compiled until it runs: neither is judged here.
 */
final class SqlUdfBodyArguments {

    /** Where a positioned refusal points. */
    private static final Pattern ERROR_POSITION = Pattern.compile("error line (\\d+) at position (\\d+)");

    /** What the body's value is written after, to compile as a query's only item. */
    private static final String SELECTED = "SELECT ";

    /** The refusals a call's arguments earn. */
    private static final String[] JUDGED = {
        "too many arguments for function", "not enough arguments for function",
        "Invalid argument types for function", "do not match any signature for function",
        "are resolved in prepare state",
    };

    private SqlUdfBodyArguments() {
    }

    /**
     * Refuse an expression body one of whose calls no function takes.
     *
     * @param queryExecutor the executor that compiles the body
     * @param language      the routine's language
     * @param tableFunction whether the routine declares {@code RETURNS TABLE(…)}
     * @param parameters    the routine's parameters, each typed as declared
     * @param written       the body as written
     * @param home          the schema the routine is created in
     */
    static void reject(final QueryExecutor queryExecutor, final String language, final boolean tableFunction,
                       final List<Parameter> parameters, final String written, final Schema home) {
        if (queryExecutor == null || tableFunction || written == null || written.trim().isEmpty() || home == null
                || !"SQL".equals(language)) {
            return;
        }
        final String body = SqlUdfBodyFrame.executableBody(written);
        final String trimmed = body.trim();
        if (queryExecutor.queryStatementOf(trimmed) != null || queryExecutor.proceduralBlockOf(trimmed) != null) {
            return;
        }
        try {
            AntlrExpressionParser.parseTree(trimmed);
        } catch (final RuntimeException notAnExpression) {
            return;
        }
        final ParameterSubstitutedBody typed = ParameterSubstitutedBody.typed(SELECTED + body, parameters);
        try {
            queryExecutor.resolveQueryResultColumnsInScope(home.getDatabaseName(), home.getName(), typed.text());
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage());
            for (final String judged : JUDGED) {
                if (message.contains(judged)) {
                    throw new RuntimeException(inBodyFrame(typed.inWrittenPositions(message)), refused);
                }
            }
        }
    }

    /** A refusal of the value compiled after {@link #SELECTED}, moved into the body's own frame. */
    private static String inBodyFrame(final String message) {
        final Matcher at = ERROR_POSITION.matcher(message);
        if (!at.find() || Integer.parseInt(at.group(1)) != 1) {
            return message;
        }
        return message.substring(0, at.start()) + "error line 1 at position "
            + (Integer.parseInt(at.group(2)) - SELECTED.length() + 1) + message.substring(at.end());
    }
}
