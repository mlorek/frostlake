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

import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The line a DESCRIBE stacks for its tail after a fault inside the signature it reads after the object's name. The
 * account reads the rest of the statement on past the signature's closing parenthesis and reports that tail's first
 * fault too: {@code DESCRIBE TABLE t1 (a b) x} is 'b', then '&lt;EOF&gt;' — the x read as a property's name — and
 * {@code DESCRIBE TABLE IDENTIFIER(UPPER('t1')) x} the string, then '&lt;EOF&gt;'; a tail that reads whole adds
 * nothing ({@code DESCRIBE TABLE t1 ((a)) TYPE = COLUMNS}). A DROP stacks no such line (all live-verified). A DROP or
 * DESCRIBE of a table or a view that {@link SignatureRecovery} reads whole is refused with every line that reading
 * reports instead.
 */
public final class SignatureTailLine {

    private SignatureTailLine() {
    }

    /**
     * Refuse a DESCRIBE whose every reported fault lies inside its signature with the tail's first fault stacked.
     *
     * @param script   the parsed script
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final FrostlakeParser.SqlScriptContext script, final String sql,
                                     final SyntaxErrorListener listener) {
        requireSignatureReading(script, sql, listener);
        if (script == null || sql == null || !listener.hasErrors() || script.flowChain().size() != 1
                || script.flowChain().get(0).statement().size() != 1) {
            return;
        }
        final FrostlakeParser.DescribeStatementContext describe =
            script.flowChain().get(0).statement().get(0).describeStatement();
        final FrostlakeParser.ObjectSignatureContext signature = describe == null ? null : describe.objectSignature();
        if (signature == null || signature.RPAREN() == null || signature.LPAREN() == null) {
            return;
        }
        final Token open = signature.LPAREN().getSymbol();
        final Token close = signature.RPAREN().getSymbol();
        final List<String> reported = listener.reportedSyntaxLines();
        if (reported.isEmpty()) {
            return;
        }
        final int[] from = LeadingCommentOffset.rebase(open.getLine(), open.getCharPositionInLine());
        final int[] to = LeadingCommentOffset.rebase(close.getLine(), close.getCharPositionInLine());
        for (final String line : reported) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at == null || before(at, from) || before(to, at)) {
                return;
            }
        }
        final StringBuilder blanked = new StringBuilder(sql);
        for (int i = open.getStartIndex(); i <= close.getStopIndex() && i < blanked.length(); i++) {
            if (blanked.charAt(i) != '\n') {
                blanked.setCharAt(i, ' ');
            }
        }
        final List<String> tail = SyntaxErrorListener.linesReportedFor(blanked.toString(), true);
        final String tailLine = tail.isEmpty() ? bareProperty(describe) : tail.get(0);
        final int[] tailAt = tailLine == null ? null : SyntaxErrorListener.sentenceCoordinates(tailLine);
        if (tailAt == null || !before(to, tailAt)) {
            return;
        }
        final List<String> lines = new ArrayList<>(reported);
        lines.add(tailLine);
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * Refuse a DROP or a DESCRIBE of a table or a view with every line the account reports for its signature and the
     * rest of the statement (see {@link SignatureRecovery}) — whether this parse refused the statement or read it
     * whole — unless a fault stands before the signature or the text holds a shape that reading does not model.
     */
    private static void requireSignatureReading(final FrostlakeParser.SqlScriptContext script, final String sql,
                                                final SyntaxErrorListener listener) {
        final SignatureRecovery reading = SignatureRecovery.of(script, sql);
        final List<Token> faults = reading == null ? null : reading.faults();
        if (faults == null || faults.isEmpty() || faultBefore(listener, reading.open())) {
            return;
        }
        final List<String> lines = new ArrayList<>();
        for (final Token token : faults) {
            lines.add(SyntaxErrorListener.sentence(token));
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /** Whether the parse refused something before {@code open}, or refused its text at the lexer. */
    private static boolean faultBefore(final SyntaxErrorListener listener, final Token open) {
        final String first = listener.firstReportedLine();
        if (first == null) {
            return false;
        }
        final int[] at = first.startsWith("syntax error") ? SyntaxErrorListener.sentenceCoordinates(first) : null;
        return at == null || before(at, LeadingCommentOffset.rebase(open.getLine(), open.getCharPositionInLine()));
    }

    /**
     * The line for the first property written without its value — a name the account reads and then wants its '='
     * after — at the token after it, or null when every property has its value.
     */
    private static String bareProperty(final FrostlakeParser.DescribeStatementContext describe) {
        for (final FrostlakeParser.DescribePropertyContext property : describe.describeProperty()) {
            final FrostlakeParser.DescribeParameterContext parameter = property.describeParameter();
            if (parameter == null || parameter.EQ() != null) {
                continue;
            }
            final int index = describe.children.indexOf(property);
            if (index + 1 < describe.getChildCount()) {
                final ParseTree next = describe.getChild(index + 1);
                final Token token = next instanceof ParserRuleContext ? ((ParserRuleContext) next).getStart()
                    : ((TerminalNode) next).getSymbol();
                return SyntaxErrorListener.sentence(token);
            }
            final Token last = describe.getStop();
            final int[] end = LeadingCommentOffset.rebase(last.getLine(),
                last.getCharPositionInLine() + last.getText().length());
            return "syntax error line " + end[0] + " at position " + end[1] + " unexpected '<EOF>'.";
        }
        return null;
    }

    private static boolean before(final int[] a, final int[] b) {
        return a[0] < b[0] || a[0] == b[0] && a[1] < b[1];
    }
}
