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

package dev.frostlake.executor.udf;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Whether a statement creates a TEMPORARY object, and which kind Snowflake calls it — the question an
 * owner's rights stored procedure has to answer before running a handler's statement.
 *
 * <p>Answered from the PARSE TREE, by asking the {@code CREATE} statement which of its optional tokens
 * are present. The previous implementation split the SQL on whitespace and looked at the first few
 * words, which is the thing this project does not do — and it was not merely stylistic: because the
 * text check ran BEFORE the parser, it "recognised" three spellings the grammar could not actually
 * parse, so {@code CREATE VOLATILE TABLE} inside a procedure produced a confident refusal for a
 * statement the engine would have rejected as a syntax error anywhere else.
 *
 * <p>The kind names are Snowflake's own, as they appear in its refusal — {@code TABLE}, {@code STAGE},
 * {@code FILE_FORMAT} — with the underscore in FILE_FORMAT and no space.
 *
 * <p>Only the kinds Frostlake's grammar can express are answered here — which, since the grammar
 * learned the temporary spellings of VIEW and FUNCTION, is every kind Snowflake refuses under owner's
 * rights.
 */
public final class TemporaryObjectStatements {

    private TemporaryObjectStatements() {
    }

    /**
     * The Snowflake kind name when {@code sqlText} creates a temporary object, otherwise null.
     *
     * <p>Unparseable text answers null: it is not this class's job to report a syntax error, and the
     * engine will raise a better one when it runs the statement.
     */
    public static String temporaryObjectKind(final String sqlText) {
        final FrostlakeParser.CreateStatementContext create = parseCreate(sqlText);
        if (create == null) {
            return null;
        }
        if (!isTemporary(create)) {
            return null;
        }
        if (create.TABLE() != null) {
            return "TABLE";
        }
        if (create.STAGE() != null) {
            return "STAGE";
        }
        if (create.FILE() != null && create.FORMAT() != null) {
            return "FILE_FORMAT";
        }
        if (create.VIEW() != null) {
            return "VIEW";
        }
        if (create.FUNCTION() != null) {
            return "FUNCTION";
        }
        return null;
    }

    /**
     * Whether a parsed {@code CREATE} carries one of the temporary keywords.
     *
     * <p>TRANSIENT is deliberately absent: measured live, an owner's rights procedure creates a
     * transient table happily, and a transient object outlives its session. Only TEMPORARY, TEMP and
     * VOLATILE are the temporary spellings — VOLATILE included, live-verified for views, functions and
     * procedures alike, where a leading VOLATILE is the temporary keyword and not the volatility
     * attribute a function may carry after RETURNS.
     */
    public static boolean isTemporary(final FrostlakeParser.CreateStatementContext create) {
        return create.TEMPORARY() != null || create.TEMP() != null || create.VOLATILE() != null;
    }

    /** The statement's {@code CREATE} node, or null when the text is not a single parseable CREATE. */
    private static FrostlakeParser.CreateStatementContext parseCreate(final String sqlText) {
        if (sqlText == null || sqlText.isEmpty()) {
            return null;
        }
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sqlText));
            lexer.removeErrorListeners();
            final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
            if (script == null || script.flowChain().isEmpty()) {
                return null;
            }
            for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
                for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                    final FrostlakeParser.CreateStatementContext create = createOf(statement);
                    if (create != null) {
                        return create;
                    }
                }
            }
            return null;
        } catch (final RuntimeException notParseable) {
            return null;
        }
    }

    private static FrostlakeParser.CreateStatementContext createOf(
            final FrostlakeParser.StatementContext statement) {
        if (statement == null || statement.ddlStatement() == null) {
            return null;
        }
        return statement.ddlStatement().createStatement();
    }
}
