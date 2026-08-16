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

package dev.frostlake.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The driver-side multi-statement gate, matching the real driver (live-verified): one
 * {@code execute()} refuses {@code ;}-separated packs unless MULTI_STATEMENT_COUNT says otherwise —
 * {@code Actual statement count N did not match the desired statement count D.} (state 0A000,
 * code 8), matched EXACTLY in both directions, with 0 meaning any count. The count is read from a
 * per-statement parameter first, then the connection's default, which starts at 1 (or the
 * MULTI_STATEMENT_COUNT connection property) and follows {@code ALTER SESSION SET
 * MULTI_STATEMENT_COUNT} / {@code UNSET} statements executed through the connection.
 *
 * <p>Counting is scanner-based like the drivers' placeholder handling — string literals (both
 * {@code ''} and {@code \'} escapes), quoted identifiers, {@code --}/{@code //}/{@code /* *&#47;}
 * comments and {@code $$…$$} bodies are opaque; a trailing {@code ;} does not add a statement; a
 * scripting region — an unquoted block or DECLARE-headed body — counts as one statement, as the
 * account counts it (see {@link #countStatements}).
 */
final class JdbcMultiStatement {

    private JdbcMultiStatement() {
    }

    /**
     * Top-level statement count of {@code sql} under the scanner rules above. Scripting regions
     * count as ONE statement, the way the account counts them (live-verified: an unquoted
     * {@code CREATE PROCEDURE … AS BEGIN …; END;} and a bare anonymous {@code BEGIN …; END;} both
     * pass under the default single-statement gate): a block-opening {@code BEGIN} (one not
     * immediately followed by {@code ;}, {@code TRANSACTION}, {@code WORK} or {@code NAME} — those
     * are transaction starts) or a top-level {@code DECLARE} opens a region; nested blocks pair
     * their own BEGIN/END; {@code END IF}/{@code WHILE}/{@code FOR}/{@code LOOP}/{@code REPEAT}
     * close constructs the counter never opened and are skipped; a CASE inside a region pairs with
     * its bare {@code END} or {@code END CASE}.
     */
    static int countStatements(final String sql) {
        int count = 0;
        boolean sawContent = false;
        int depth = 0;
        boolean headerBeginPending = false;
        int i = 0;
        final int n = sql.length();
        while (i < n) {
            final char c = sql.charAt(i);
            if (c == '\'') {
                i = skipString(sql, i);
                sawContent = true;
            } else if (c == '"') {
                i = skipQuoted(sql, i);
                sawContent = true;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = skipLine(sql, i);
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                final int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '/') {
                i = skipLine(sql, i);
            } else if (c == '$' && i + 1 < n && sql.charAt(i + 1) == '$') {
                final int end = sql.indexOf("$$", i + 2);
                i = end < 0 ? n : end + 2;
                sawContent = true;
            } else if (c == ';') {
                if (depth == 0 && sawContent) {
                    count++;
                    sawContent = false;
                }
                i++;
            } else if (Character.isLetter(c) || c == '_') {
                final int start = i;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                final String word = sql.substring(start, i).toUpperCase(Locale.ROOT);
                sawContent = true;
                if ("BEGIN".equals(word) && !isTransactionBegin(sql, i)) {
                    if (headerBeginPending) {
                        headerBeginPending = false;
                    } else {
                        depth++;
                    }
                } else if ("DECLARE".equals(word) && depth == 0) {
                    depth++;
                    headerBeginPending = true;
                } else if ("CASE".equals(word) && depth > 0) {
                    depth++;
                } else if ("END".equals(word) && depth > 0) {
                    final String follower = nextWord(sql, i);
                    if ("IF".equals(follower) || "WHILE".equals(follower) || "FOR".equals(follower)
                            || "LOOP".equals(follower) || "REPEAT".equals(follower)) {
                        i = skipNextWord(sql, i);
                    } else {
                        if ("CASE".equals(follower)) {
                            i = skipNextWord(sql, i);
                        }
                        depth--;
                    }
                }
            } else {
                if (!Character.isWhitespace(c)) {
                    sawContent = true;
                }
                i++;
            }
        }
        if (sawContent) {
            count++;
        }
        return count;
    }

    /** Whether the BEGIN ending at {@code after} starts a transaction rather than a block. */
    private static boolean isTransactionBegin(final String sql, final int after) {
        int j = after;
        final int n = sql.length();
        while (j < n && Character.isWhitespace(sql.charAt(j))) {
            j++;
        }
        if (j >= n || sql.charAt(j) == ';') {
            return true;
        }
        final String word = nextWord(sql, after);
        return "TRANSACTION".equals(word) || "WORK".equals(word) || "NAME".equals(word);
    }

    /** The upper-cased word after position {@code from}, or empty when none follows. */
    private static String nextWord(final String sql, final int from) {
        int j = from;
        final int n = sql.length();
        while (j < n && Character.isWhitespace(sql.charAt(j))) {
            j++;
        }
        final int start = j;
        while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
            j++;
        }
        return sql.substring(start, j).toUpperCase(Locale.ROOT);
    }

    /** The index just past the word {@link #nextWord} would return. */
    private static int skipNextWord(final String sql, final int from) {
        int j = from;
        final int n = sql.length();
        while (j < n && Character.isWhitespace(sql.charAt(j))) {
            j++;
        }
        while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
            j++;
        }
        return j;
    }

    /**
     * The value an exact {@code ALTER SESSION SET MULTI_STATEMENT_COUNT = n} statement assigns, 1
     * for the {@code UNSET} form (the parameter's default), or null when {@code sql} is not that
     * statement — so a connection can follow the session parameter without a server round trip.
     */
    static Integer sessionCountAssignment(final String sql) {
        final List<String> words = words(sql);
        if (words.size() == 4 && "ALTER".equals(words.get(0)) && "SESSION".equals(words.get(1))
                && "UNSET".equals(words.get(2)) && "MULTI_STATEMENT_COUNT".equals(words.get(3))) {
            return 1;
        }
        if (words.size() == 6 && "ALTER".equals(words.get(0)) && "SESSION".equals(words.get(1))
                && "SET".equals(words.get(2)) && "MULTI_STATEMENT_COUNT".equals(words.get(3))
                && "=".equals(words.get(4))) {
            try {
                return Integer.valueOf(words.get(5));
            } catch (final NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }

    /** The live refusal, byte-for-byte. */
    static SQLException countMismatch(final int actual, final int desired) {
        return new SQLException("Actual statement count " + actual
            + " did not match the desired statement count " + desired + ".", "0A000", 8);
    }

    /** Upper-cased word/symbol tokens of {@code sql}, comments dropped, for the detector above. */
    private static List<String> words(final String sql) {
        final List<String> words = new ArrayList<String>();
        int i = 0;
        final int n = sql.length();
        while (i < n) {
            final char c = sql.charAt(i);
            if (Character.isWhitespace(c) || c == ';') {
                i++;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i = skipLine(sql, i);
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                final int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '=') {
                words.add("=");
                i++;
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                final int start = i;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
                words.add(sql.substring(start, i).toUpperCase(Locale.ROOT));
            } else {
                words.add(String.valueOf(c));
                i++;
            }
            if (words.size() > 7) {
                return words; // longer than any form the detector accepts
            }
        }
        return words;
    }

    private static int skipString(final String s, final int start) {
        int j = start + 1;
        final int n = s.length();
        while (j < n) {
            final char c = s.charAt(j);
            if (c == '\\') {
                j += 2;
            } else if (c == '\'') {
                if (j + 1 < n && s.charAt(j + 1) == '\'') {
                    j += 2;
                } else {
                    return j + 1;
                }
            } else {
                j++;
            }
        }
        return j;
    }

    private static int skipQuoted(final String s, final int start) {
        int j = start + 1;
        final int n = s.length();
        while (j < n) {
            if (s.charAt(j) == '"') {
                if (j + 1 < n && s.charAt(j + 1) == '"') {
                    j += 2;
                    continue;
                }
                return j + 1;
            }
            j++;
        }
        return j;
    }

    private static int skipLine(final String s, final int start) {
        final int j = s.indexOf('\n', start);
        return j < 0 ? s.length() : j + 1;
    }
}
