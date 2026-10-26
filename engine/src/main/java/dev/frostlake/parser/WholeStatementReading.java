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

/**
 * A statement of a request of several that the account reads whole where this grammar refused something in it — a
 * stage reference of the account's own spelling, {@code x = @s!=x}: the account runs the statements before it, then
 * refuses it as it compiles, the refusal named by the request's helper as any statement's that fails there
 * (live-verified: {@code DESCRIBE TABLE t1 (a) x = @s!=x; SELECT 1} is the helper's sentence naming the DESCRIBE at
 * position 0, carrying {@code invalid parameter 'x'}).
 */
public final class WholeStatementReading {

    /** How much of the statement the helper's sentence quotes before it cuts it short. */
    private static final int QUOTED_LENGTH = 30;

    private final int startIndex;
    private final int[] place;
    private final String quoted;
    private final String refusal;

    WholeStatementReading(final int startIndex, final int stopIndex, final int line, final int position,
                          final String sql, final String refusal) {
        this.startIndex = startIndex;
        this.place = LeadingCommentOffset.rebase(line, position);
        String written = sql.substring(startIndex, Math.min(stopIndex + 1, sql.length())).trim();
        if (written.endsWith(";")) {
            written = written.substring(0, written.length() - 1).trim();
        }
        this.quoted = written.length() > QUOTED_LENGTH ? written.substring(0, QUOTED_LENGTH) + "..." : written;
        this.refusal = refusal;
    }

    /**
     * The statement as the helper's sentence quotes it.
     *
     * @return its first thirty characters, with {@code ...} after them when it is longer
     */
    public String quoted() {
        return quoted;
    }

    /**
     * Where the statement stands in the request, a comment that opens the request not counted.
     *
     * @return its line and position
     */
    public int[] place() {
        return place.clone();
    }

    /**
     * The statement's own refusal.
     *
     * @return the refusal's message
     */
    public String refusal() {
        return refusal;
    }

    /**
     * Whether statements stand before this one in the request.
     *
     * @param sql the request's text
     * @return whether any does
     */
    public boolean followsStatements(final String sql) {
        return SignatureRecovery.holdsAStatement(sql.substring(0, Math.min(startIndex, sql.length())));
    }

    /**
     * The request with this statement and every one after it blanked, each line and place of the statements before
     * it kept.
     *
     * @param sql the request's text
     * @return the statements before this one
     */
    public String statementsBefore(final String sql) {
        final char[] text = sql.toCharArray();
        for (int i = startIndex; i < text.length; i++) {
            if (text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        return new String(text);
    }
}
