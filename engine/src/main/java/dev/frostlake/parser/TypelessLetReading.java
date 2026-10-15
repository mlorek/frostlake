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

package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A LET that names its variable and nothing else, {@code LET q;}. The account reads the statement and refuses it
 * while the block compiles — {@code variable 'Q' must have either a type or an initializer to infer a type from},
 * at the LET — where this grammar reads no such statement and would refuse the semicolon. So a text whose LET
 * statements of that shape are its only faults is refused in the account's words: for the first of them, or for
 * a DECLARE section's name alone that is written before it. A text with another fault keeps its syntax error,
 * which the account reports first too. In a routine's unquoted body the place is the body's own, as the body's
 * other refusals are (all live-verified).
 *
 * <p>Only a LET that opens a statement is read so — one after a semicolon or after the word that opens a block's
 * or a branch's statements: a LET run into the statement before it is that statement's word.
 */
public final class TypelessLetReading {

    /** The statement that does nothing, which a refused LET gives way to while the rest of the text is read. */
    private static final String NOTHING = "NULL";

    private TypelessLetReading() {
    }

    /**
     * Refuse the text for its first variable declared by a name alone when the parse met a fault and the LETs of
     * that shape are all of it.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener) {
        final String refusal = listener.hasErrors() ? refusalOf(sql) : null;
        if (refusal != null) {
            throw new RuntimeException(refusal);
        }
    }

    /**
     * @param sql a text that does not parse
     * @return the refusal of its first variable declared by a name alone, or null when its LETs of a name alone are
     *         not all that keeps it from parsing
     */
    public static String refusalOf(final String sql) {
        if (sql == null) {
            return null;
        }
        final List<Token> spoken = spokenTokens(sql);
        final char[] text = sql.toCharArray();
        Token first = null;
        Token named = null;
        for (int i = 0; i + 2 < spoken.size(); i++) {
            if (spoken.get(i).getType() != FrostlakeLexer.LET || spoken.get(i + 2).getType() != FrostlakeLexer.SEMI
                    || !SyntaxErrorListener.isNameLike(spoken.get(i + 1))
                    || i > 0 && !opensStatements(spoken.get(i - 1).getType())) {
                continue;
            }
            if (first == null) {
                first = spoken.get(i);
                named = spoken.get(i + 1);
            }
            // The statement gives way to one that does nothing, standing where it stood.
            final int from = spoken.get(i).getStartIndex();
            final int to = spoken.get(i + 1).getStopIndex();
            if (to >= text.length || to - from + 1 < NOTHING.length()) {
                return null;
            }
            for (int c = from; c <= to; c++) {
                if (text[c] != '\n') {
                    text[c] = c - from < NOTHING.length() ? NOTHING.charAt(c - from) : ' ';
                }
            }
        }
        if (first == null) {
            return null;
        }
        final String repaired = new String(text);
        if (!SyntaxErrorListener.linesReportedAfresh(repaired).isEmpty()) {
            return null;
        }
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(new FrostlakeLexer(
            SqlCharStream.of(repaired))));
        parser.removeErrorListeners();
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        // A DECLARE section's name alone that is written first is the one refused, at the name.
        final FrostlakeParser.UntypedDeclarationItemContext declared = firstNameAlone(script, first.getStartIndex());
        final Token at = declared == null ? first : declared.identifier().getStart();
        final String written = declared == null ? named.getText() : declared.identifier().getText();
        final Token origin = unquotedBodyAround(script, at.getStartIndex());
        final int line = origin == null ? at.getLine() : at.getLine() - origin.getLine() + 1;
        final int position = origin == null || at.getLine() != origin.getLine() ? at.getCharPositionInLine()
            : at.getCharPositionInLine() - origin.getCharPositionInLine();
        final String name = written.length() > 1 && written.charAt(0) == '"'
            ? written.substring(1, written.length() - 1) : written.toUpperCase(Locale.ROOT);
        final String detail = " variable '" + name + "' must have either a type or an initializer to infer a type from";
        // A body's place is its own, whatever stands before the statement that holds the body.
        return origin == null ? SqlCompilationError.at(line, position, detail)
            : SqlCompilationError.inline("error line " + line + " at position " + position + "\n" + detail);
    }

    /** The first DECLARE item of a name alone that starts before {@code limit}, in the order written, or null. */
    private static FrostlakeParser.UntypedDeclarationItemContext firstNameAlone(final ParseTree node,
                                                                              final int limit) {
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.UntypedDeclarationItemContext item =
                (FrostlakeParser.UntypedDeclarationItemContext) node;
            return item.booleanExpr() == null && item.getStart().getStartIndex() < limit ? item : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.UntypedDeclarationItemContext found = firstNameAlone(node.getChild(i), limit);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The first token of the routine's unquoted body that holds the character at {@code index}, or null. */
    private static Token unquotedBodyAround(final ParseTree node, final int index) {
        if (node instanceof FrostlakeParser.BodyDefinitionContext) {
            final ParserRuleContext body = (ParserRuleContext) node;
            return ((FrostlakeParser.BodyDefinitionContext) node).beginEndBlock() != null
                && body.getStart().getStartIndex() <= index && body.getStop() != null
                && index <= body.getStop().getStopIndex() ? body.getStart() : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = unquotedBodyAround(node.getChild(i), index);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Token> spokenTokens(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : tokens.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                spoken.add(token);
            }
        }
        return spoken;
    }

    private static boolean opensStatements(final int type) {
        return type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.THEN
            || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO || type == FrostlakeLexer.LOOP
            || type == FrostlakeLexer.REPEAT;
    }
}
