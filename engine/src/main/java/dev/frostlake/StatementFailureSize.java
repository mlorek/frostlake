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
package dev.frostlake;

import dev.frostlake.parser.SqlSyntaxException;

import java.nio.charset.StandardCharsets;

/**
 * The most of a failed statement's message the account sends back (live-verified): a message of more than 16384
 * bytes of UTF-8 is cut to its first 16384 characters, a character of two UTF-16 units counted once, and ended by
 * {@code ... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).} The account cuts a message
 * twice over, so one of fewer characters than that but more bytes keeps them all and ends with that sentence twice.
 * The message a scripting block's handler reads in SQLERRM is cut alike, the failure of a statement it ran or of a
 * procedure it called.
 */
public final class StatementFailureSize {

    /** The most bytes of UTF-8 a message keeps whole, and the most characters a cut one keeps. */
    private static final int MOST = 16384;

    private static final String CUT = "... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).";

    private StatementFailureSize() {
    }

    /**
     * A failure as the account reports it: the failure itself while its message fits, or one of its kind whose
     * message is cut, the failure its cause.
     *
     * @param failure what the statement raised
     * @return the failure to report
     */
    public static RuntimeException kept(final RuntimeException failure) {
        final String message = failure.getMessage();
        final String cut = keptMessage(message);
        if (cut == null || cut.equals(message)) {
            return failure;
        }
        final RuntimeException kept = failure instanceof SqlSyntaxException
            ? new SqlSyntaxException(cut, ((SqlSyntaxException) failure).getSyntaxErrors(),
                ((SqlSyntaxException) failure).getFailedSql())
            : new RuntimeException(cut, failure);
        kept.setStackTrace(failure.getStackTrace());
        return kept;
    }

    /**
     * A failure's message as the account keeps it, the text a block's handler reads in SQLERRM.
     *
     * @param message the message, or null
     * @return the message, cut as the account cuts one
     */
    public static String keptMessage(final String message) {
        return cut(cut(message));
    }

    /** One cut: a message of more than 16384 bytes to its first 16384 characters, a character never split. */
    private static String cut(final String message) {
        if (message == null || message.getBytes(StandardCharsets.UTF_8).length <= MOST) {
            return message;
        }
        final int characters = message.codePointCount(0, message.length());
        final int end = characters <= MOST ? message.length() : message.offsetByCodePoints(0, MOST);
        return message.substring(0, end) + CUT;
    }
}
