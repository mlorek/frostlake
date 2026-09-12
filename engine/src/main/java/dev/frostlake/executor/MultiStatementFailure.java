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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * What a request carrying SEVERAL statements answers when one of them fails while running.
 *
 * <p>The account runs such a request through a helper of its own, and a failure inside it comes back
 * naming the statement that failed and where it sits — its own text, its line, and its column within
 * that line — with the statement's own error carried inside. The helper's name and its internal stack
 * are part of the sentence the account produces, so they are part of this one too: a client that
 * matches on the account's wording sees the same text here.
 *
 * <p>Only a failure DURING EXECUTION is spelled this way. A script whose text will not parse is
 * refused as a whole before any of it runs, with an ordinary syntax error that names no statement,
 * and a request carrying a single statement answers with that statement's own error unadorned.
 */
public final class MultiStatementFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The account's helper, named in the sentence a failing statement produces. */
    private static final String HELPER = "SYSTEM$MULTISTMT";

    private MultiStatementFailure(final String message, final RuntimeException cause) {
        super(message, cause);
    }

    /**
     * The failure to throw for a statement that failed inside a script of several statements: the
     * account's sentence when this request carried more than one statement, and {@code failure}
     * itself otherwise.
     *
     * @param tree the parsed script the statement belongs to
     * @param failing the statement that failed
     * @param failure what it failed with
     * @param topLevel whether this is the request's own script rather than a nested one
     * @return the exception to throw
     */
    public static RuntimeException wrapping(final FrostlakeParser.SqlScriptContext tree,
            final FrostlakeParser.StatementContext failing, final RuntimeException failure,
            final boolean topLevel) {
        if (!topLevel || failure instanceof MultiStatementFailure
                || tree == null || tree.flowChain() == null || tree.flowChain().size() < 2
                || failing == null || failing.getStart() == null) {
            return failure;
        }
        return new MultiStatementFailure(sentence(failing, failure), failure);
    }

    /** The account's sentence for {@code failing}, carrying its own error inside. */
    private static String sentence(final FrostlakeParser.StatementContext failing,
            final RuntimeException failure) {
        final Token start = failing.getStart();
        final String inner = failure.getMessage() != null ? failure.getMessage() : failure.toString();
        return "JavaScript execution error: Uncaught Execution of multiple statements failed on statement \""
            + statementText(failing) + "\" (at line " + start.getLine() + ", position "
            + start.getCharPositionInLine() + ").\n"
            + inner
            + " in " + HELPER + " at '    throw `Execution of multiple statements failed on statement"
            + " {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4\n"
            + "stackstrace: \n"
            + HELPER + " line: 10";
    }

    /**
     * The statement as it was written, without the separator that follows it. Read from the source the
     * parser saw rather than rebuilt from tokens, so it keeps its own spacing.
     */
    private static String statementText(final FrostlakeParser.StatementContext failing) {
        final Token start = failing.getStart();
        final Token stop = failing.getStop();
        if (start.getInputStream() == null || stop == null) {
            return failing.getText();
        }
        // The separator closing a statement is not part of the statement the account names, and neither
        // is the space that may sit before it.
        final int end = stop.getType() == FrostlakeParser.SEMI
            ? stop.getStartIndex() - 1 : stop.getStopIndex();
        if (end < start.getStartIndex()) {
            return failing.getText();
        }
        final String text = start.getInputStream().getText(Interval.of(start.getStartIndex(), end));
        int last = text.length();
        while (last > 0 && Character.isWhitespace(text.charAt(last - 1))) {
            last--;
        }
        return text.substring(0, last);
    }
}
