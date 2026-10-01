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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The properties written after a DESCRIBE's object. TYPE takes STAGE or COLUMNS — bare, quoted or as a string,
 * in any case — and the last one written wins; every other name written with a value is refused as an invalid
 * parameter, whatever the kind described (live-verified). The properties are judged in the order written,
 * before the object is looked up: {@code x = 1 TYPE = foo} is refused for its {@code x}, and
 * {@code TYPE = foo x = 1} for its TYPE's value. A property's name is judged before its value, so
 * {@code x = $nosuch} is the invalid parameter, not the missing variable; a TYPE given a session variable reads the
 * variable's value — text naming STAGE or COLUMNS in any case, anything else refused as the variable written:
 * {@code invalid value [$v] for parameter 'TYPE'}, or {@code invalid type [$v]} for a number.
 */
final class DescribeProperties {

    private DescribeProperties() {
    }

    /**
     * Refuse the first property live refuses, and answer whether the TYPE written last asks for the stage
     * properties.
     *
     * @param statement     the DESCRIBE, its properties in the order written
     * @param queryExecutor the executor whose session variables a TYPE may read
     * @return whether the last TYPE is STAGE
     */
    static boolean describesStage(final FrostlakeParser.DescribeStatementContext statement,
                                  final QueryExecutor queryExecutor) {
        final List<FrostlakeParser.DescribePropertyContext> properties = statement.describeProperty();
        int types = 0;
        for (final FrostlakeParser.DescribePropertyContext property : properties) {
            if (isType(property)) {
                types++;
            }
        }
        boolean stage = false;
        int typeIndex = 0;
        for (final FrostlakeParser.DescribePropertyContext property : properties) {
            final FrostlakeParser.DescribeParameterContext parameter = property.describeParameter();
            if (parameter != null && parameter.EQ() == null) {
                throw new RuntimeException(syntaxErrorAfter(statement, property));
            }
            if (!isType(property)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid parameter '" + parameter.getChild(0).getText() + "'"));
            }
            final Object variable = parameter == null ? null : variableValue(parameter, queryExecutor);
            typeIndex++;
            if (typeIndex == types) {
                stage = parameter == null ? isStage(property.describeTypeProperty()) : isStage(parameter, variable);
            }
        }
        return stage;
    }

    /**
     * Whether a property is a TYPE: the ordinary spelling, or the word TYPE given a session variable, a negative
     * number or a parenthesised value, which read as TYPE's own value and are refused as one.
     */
    private static boolean isType(final FrostlakeParser.DescribePropertyContext property) {
        final FrostlakeParser.DescribeParameterContext parameter = property.describeParameter();
        if (parameter == null) {
            return true;
        }
        final FrostlakeParser.DescribeParameterValueContext value = parameter.describeParameterValue();
        return parameter.identifier() != null && parameter.identifier().getStart().getType() == FrostlakeLexer.TYPE
            && value != null && (value.SESSION_VAR_REF() != null || value.MINUS() != null || value.LPAREN() != null);
    }

    /**
     * The value of the session variable a TYPE reads, or null when it reads none; a variable no SET defined is
     * refused at its own position.
     */
    private static Object variableValue(final FrostlakeParser.DescribeParameterContext parameter,
                                        final QueryExecutor queryExecutor) {
        final TerminalNode reference = parameter.describeParameterValue().SESSION_VAR_REF();
        if (reference == null) {
            return null;
        }
        final Token token = reference.getSymbol();
        final String name = token.getText().substring(1).toUpperCase(Locale.ROOT);
        final SecurityManager security = queryExecutor.getSecurityManager();
        final boolean session = security != null && security.getSessionContext() != null;
        final boolean defined = session ? security.getSessionContext().isSessionVariable(name)
            : queryExecutor.getSessionVariables().containsKey(name);
        if (!defined) {
            throw new RuntimeException(SqlCompilationError.at(token.getLine(), token.getCharPositionInLine(),
                "Session variable '$" + name + "' does not exist"));
        }
        return session ? security.getSessionContext().getSessionVariable(name)
            : queryExecutor.getSessionVariables().get(name);
    }

    /**
     * Whether a TYPE written as a session variable, a negative number or a parenthesis names STAGE: the variable's
     * text may, and anything else is refused.
     */
    private static boolean isStage(final FrostlakeParser.DescribeParameterContext parameter, final Object variable) {
        final FrostlakeParser.DescribeParameterValueContext value = parameter.describeParameterValue();
        if (value.LPAREN() != null) {
            throw new RuntimeException("Invalid value specified for property 'TYPE'");
        }
        if (value.SESSION_VAR_REF() != null && variable instanceof String) {
            if ("STAGE".equalsIgnoreCase((String) variable)) {
                return true;
            }
            if ("COLUMNS".equalsIgnoreCase((String) variable)) {
                return false;
            }
        }
        final String kind = value.SESSION_VAR_REF() != null && variable instanceof Number ? "type" : "value";
        throw new RuntimeException(SqlCompilationError.of(
            "invalid " + kind + " [" + value.getText() + "] for parameter 'TYPE'"));
    }

    /**
     * A name written without its value: live reads it as a property still waiting for its '=', so the token
     * after it is the syntax error — the next property, the semicolon or the end of the input.
     */
    private static String syntaxErrorAfter(final FrostlakeParser.DescribeStatementContext statement,
                                           final FrostlakeParser.DescribePropertyContext property) {
        final int index = statement.children.indexOf(property);
        final int[] shown;
        final String text;
        if (index + 1 < statement.getChildCount()) {
            final Token next = statement.getChild(index + 1) instanceof ParserRuleContext
                ? ((ParserRuleContext) statement.getChild(index + 1)).getStart()
                : ((TerminalNode) statement.getChild(index + 1)).getSymbol();
            shown = LeadingCommentOffset.rebase(next.getLine(), next.getCharPositionInLine());
            text = next.getText();
        } else {
            final Token last = statement.getStop();
            shown = LeadingCommentOffset.rebase(last.getLine(), last.getCharPositionInLine() + last.getText().length());
            text = "<EOF>";
        }
        return SqlCompilationError.of("syntax error line " + shown[0] + " at position " + shown[1]
            + " unexpected '" + text + "'.");
    }

    /** Whether one TYPE property names STAGE, or COLUMNS; any other value is refused as written. */
    private static boolean isStage(final FrostlakeParser.DescribeTypePropertyContext type) {
        final String value;
        if (type.STRING_LITERAL() != null) {
            value = SqlStringLiterals.decode(type.STRING_LITERAL().getText());
        } else if (type.identifier() != null) {
            value = ParseTreeText.getIdentifier(type.identifier());
        } else {
            value = type.getStop().getText();
        }
        if ("STAGE".equalsIgnoreCase(value)) {
            return true;
        }
        if ("COLUMNS".equalsIgnoreCase(value)) {
            return false;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "invalid value [" + type.getStop().getText() + "] for parameter 'TYPE'"));
    }

    /**
     * A TYPE written after a DESCRIBE that names no kind is its syntax error, at the TYPE: live reads the
     * statement as ending with the name.
     *
     * @param type the TYPE property written, or null
     */
    static void refuseAfterBareName(final FrostlakeParser.DescribeTypePropertyContext type) {
        if (type == null) {
            return;
        }
        final Token word = type.getStart();
        final int[] shown = LeadingCommentOffset.rebase(word.getLine(), word.getCharPositionInLine());
        throw new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
            + shown[1] + " unexpected '" + word.getText() + "'."));
    }
}
