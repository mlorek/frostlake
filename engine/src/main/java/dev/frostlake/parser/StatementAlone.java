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
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * One statement of a text read as a script of its own, at its own place: everything else is blanked, line breaks
 * kept, so what it answers is placed in the text.
 */
final class StatementAlone {

    private StatementAlone() {
    }

    /**
     * The first fault of the statement from {@code from} to {@code to}, read outside every block: the parse's own,
     * or the first word of a statement only a block may hold, whichever comes first.
     *
     * @param tokens the text's default-channel tokens
     * @param from   the index of the statement's first token
     * @param to     the index of the statement's last token, its semicolon when it has one
     * @return the fault, or null when the statement reads on
     */
    static ReadOnFault firstFault(final List<Token> tokens, final int from, final int to) {
        final Token first = tokens.get(from);
        final Token last = tokens.get(to);
        final CharStream source = first.getInputStream();
        if (source == null || source.size() == 0) {
            return null;
        }
        final char[] text = source.getText(Interval.of(0, source.size() - 1)).toCharArray();
        final int stop = last.getType() == Token.EOF ? text.length : last.getStopIndex();
        for (int i = 0; i < text.length; i++) {
            if ((i < first.getStartIndex() || i > stop) && text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        final String alone = new String(text);
        final SyntaxErrorListener listener = new SyntaxErrorListener(alone, true);
        listener.statementParse();
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(alone));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(stream);
        // Where this parse reports a fault is read: it must not depend on what was parsed before.
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
        final String parsed = listener.firstReportedLine();
        final int[] parsedAt = parsed == null ? null : SyntaxErrorListener.sentenceCoordinates(parsed);
        final Token scripting = ScriptingStatementPlacement.firstScriptingWord(script, stream, listener.hasErrors());
        final int[] placedAt = scripting == null ? null
            : LeadingCommentOffset.rebase(scripting.getLine(), scripting.getCharPositionInLine());
        final boolean ownFault = placedAt == null || parsedAt != null
            && (parsedAt[0] < placedAt[0] || parsedAt[0] == placedAt[0] && parsedAt[1] < placedAt[1]);
        if (parsedAt == null && placedAt == null) {
            return parsed == null ? null : new ReadOnFault(null, parsed);
        }
        if (!ownFault) {
            final int placed = indexOf(tokens, from, to, placedAt);
            return new ReadOnFault(placed < 0 ? null : tokens.get(placed), SyntaxErrorListener.sentence(scripting));
        }
        final int named = parsed.startsWith("syntax error ") ? indexOf(tokens, from, to, parsedAt) : -1;
        return new ReadOnFault(named < 0 ? null : tokens.get(named), parsed);
    }

    /** The index of the token of the statement placed at a line's coordinates, or -1. */
    private static int indexOf(final List<Token> tokens, final int from, final int to, final int[] at) {
        for (int i = from; i <= to; i++) {
            final Token token = tokens.get(i);
            final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (shown[0] == at[0] && shown[1] == at[1]) {
                return i;
            }
        }
        return -1;
    }
}
