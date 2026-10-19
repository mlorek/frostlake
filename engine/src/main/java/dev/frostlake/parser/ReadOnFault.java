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

import org.antlr.v4.runtime.Token;

/** What {@link BlockReadOn} meets first: a refused token, or a line that names none — a text the lexer gave up on. */
final class ReadOnFault {

    private final Token token;
    private final String line;
    private boolean inConstruct;
    private int resumesAt = -1;
    private int statementsRead;

    /** @param token the refused token */
    ReadOnFault(final Token token) {
        this.token = token;
        this.line = SyntaxErrorListener.sentence(token);
    }

    /**
     * @param token the token the line names, or null when it names none
     * @param line  the line as the statement's own parse reports it
     */
    ReadOnFault(final Token token, final String line) {
        this.token = token;
        this.line = line;
    }

    /** The refused token, or null when the line names none. */
    Token token() {
        return token;
    }

    /** The line to report. */
    String line() {
        return line;
    }

    /** Whether a block or a control construct was still open where the fault was met. */
    boolean inConstruct() {
        return inConstruct;
    }

    /**
     * Notes whether a construct was still open where the fault was met, and how many statements had read on by then.
     *
     * @param open       whether a construct was still open
     * @param statements the statements read on before the fault
     * @return this fault
     */
    ReadOnFault within(final boolean open, final int statements) {
        inConstruct = open;
        statementsRead = statements;
        return this;
    }

    /** How many statements read on between the given-up statement and the fault. */
    int statementsRead() {
        return statementsRead;
    }

    /**
     * Notes that the fault is a construct's END followed by another construct's word: that construct is given up
     * too, and the reading resumes after the word.
     *
     * @param index the index of the token the reading resumes at
     * @return this fault
     */
    ReadOnFault resumingAt(final int index) {
        resumesAt = index;
        return this;
    }

    /** The index the reading resumes at after an END's wrong word, or -1 for any other fault. */
    int resumesAt() {
        return resumesAt;
    }
}
