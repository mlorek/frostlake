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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * The tokens a statement hands out to what it creates — a notebook's URL id, a generated job service name, an
 * Iceberg table's location suffix — drawn so that the statement, replayed from the write-ahead log, hands out the
 * same ones: each token is derived from the instant the outermost statement started at, which the log records,
 * and from how many tokens the statement drew before it. Every statement's start is its own, whichever session runs
 * it (see {@link StatementClock#observedStatementStart}), so no two statements hand out the same token. Outside a
 * statement a token is random.
 */
public final class StatementTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** The statement the draw count below belongs to. */
    private static final ThreadLocal<Instant> DRAWN_FOR = new ThreadLocal<Instant>();
    private static final ThreadLocal<int[]> DRAWN = new ThreadLocal<int[]>();

    private StatementTokens() {
    }

    /**
     * Starts an outermost statement's draws afresh. A token follows from the statement's start and from how many
     * tokens the statement drew before it, and only the write-ahead log's replay runs a start a second time: on the
     * same thread when an engine recovers inside the process that wrote its log, where the count would otherwise go
     * on from the first run's draws and hand out other tokens.
     */
    public static void restart() {
        DRAWN_FOR.remove();
        DRAWN.remove();
    }

    /**
     * A token of the given length over the given characters.
     *
     * @param alphabet the characters a token is made of
     * @param length how many characters it has
     * @return the token
     */
    public static String draw(final String alphabet, final int length) {
        final StringBuilder out = new StringBuilder(length);
        final Instant statement = StatementClock.observedStatementStart();
        if (statement == null) {
            for (int i = 0; i < length; i++) {
                out.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            return out.toString();
        }
        if (!statement.equals(DRAWN_FOR.get())) {
            DRAWN_FOR.set(statement);
            DRAWN.set(new int[1]);
        }
        final int[] drawn = DRAWN.get();
        drawn[0]++;
        final MessageDigest digest = sha256();
        for (int block = 0; out.length() < length; block++) {
            final byte[] bytes = digest.digest((statement + "#" + drawn[0] + "#" + block)
                .getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < bytes.length && out.length() < length; i++) {
                out.append(alphabet.charAt((bytes[i] & 0xFF) % alphabet.length()));
            }
        }
        return out.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
