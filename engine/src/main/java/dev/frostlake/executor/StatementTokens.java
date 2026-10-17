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

import dev.frostlake.metastore.StatementDraws;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The tokens a statement hands out to what it creates — a notebook's URL id, a generated job service name, an
 * Iceberg table's location suffix — and the random values it draws, drawn so that the statement, replayed from the
 * write-ahead log, hands out the same ones: each follows from a seed the statement draws at random the first time it
 * needs one, which the log records with the statement's counter draws, and from how many it drew before. Two
 * statements, of one engine or of two, therefore draw unrelated values, and nothing about the statement predicts
 * them. A record from before seeds were logged replays from the statement's start, as it was drawn then. Outside a
 * statement a token is random.
 */
public final class StatementTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** The statement the draw count below belongs to. */
    private static final ThreadLocal<Instant> DRAWN_FOR = new ThreadLocal<Instant>();
    private static final ThreadLocal<int[]> DRAWN = new ThreadLocal<int[]>();
    /** The statement the random stream below belongs to, and the stream. */
    private static final ThreadLocal<Instant> STREAM_FOR = new ThreadLocal<Instant>();
    private static final ThreadLocal<SplittableRandom> STREAM = new ThreadLocal<SplittableRandom>();
    /** The statement the seed below belongs to, and what its tokens and stream are derived from. */
    private static final ThreadLocal<Instant> BASIS_FOR = new ThreadLocal<Instant>();
    private static final ThreadLocal<String> BASIS = new ThreadLocal<String>();
    /** The counter a statement's seed is recorded under among its draws (see {@link StatementDraws}). */
    private static final String SEED_COUNTER = "#statement-seed";

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
        STREAM_FOR.remove();
        STREAM.remove();
        BASIS_FOR.remove();
        BASIS.remove();
    }

    /**
     * What a statement's tokens and random stream are derived from: a seed it draws at random the first time, recorded
     * with its draws for the write-ahead log, which its replay draws again. A replayed record from before seeds were
     * logged derives them from the statement's start, as it did then.
     */
    private static String basis(final Instant statement) {
        if (!statement.equals(BASIS_FOR.get())) {
            final Long replayed = StatementDraws.replayed(SEED_COUNTER);
            final String basis;
            if (replayed != null) {
                basis = "seed:" + Long.toHexString(replayed.longValue());
                StatementDraws.record(SEED_COUNTER, replayed.longValue());
            } else if (StatementDraws.isReplaying()) {
                basis = statement.toString();
            } else {
                final long seed = RANDOM.nextLong();
                basis = "seed:" + Long.toHexString(seed);
                StatementDraws.record(SEED_COUNTER, seed);
            }
            BASIS_FOR.set(statement);
            BASIS.set(basis);
        }
        return BASIS.get();
    }

    /**
     * A random 64-bit value, drawn from a stream the statement's seed starts: what RANDOM(), UNIFORM over it, a
     * SAMPLE without a SEED and UUID_STRING() draw from, so a statement replayed from the write-ahead log draws what
     * it first drew, in the order it drew it. Outside a statement the value is random.
     *
     * @return the value
     */
    public static long nextLong() {
        final Instant statement = StatementClock.observedStatementStart();
        if (statement == null) {
            return RANDOM.nextLong();
        }
        if (!statement.equals(STREAM_FOR.get())) {
            final byte[] seed = sha256().digest((basis(statement) + "#stream").getBytes(StandardCharsets.UTF_8));
            long bits = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                bits = bits << Byte.SIZE | (seed[i] & 0xFFL);
            }
            STREAM_FOR.set(statement);
            STREAM.set(new SplittableRandom(bits));
        }
        return STREAM.get().nextLong();
    }

    /**
     * A version-4 UUID drawn from the statement's stream (see {@link #nextLong}).
     *
     * @return the UUID, spelled as UUID_STRING() answers one
     */
    public static String randomUuid() {
        final long high = nextLong() & 0xFFFFFFFFFFFF0FFFL | 0x0000000000004000L;
        final long low = nextLong() & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L;
        return new UUID(high, low).toString();
    }

    /**
     * A token spelled as a UUID is: 32 hexadecimal digits in groups of 8, 4, 4, 4 and 12. Drawn like any other
     * token, so a statement replayed from the write-ahead log draws the one it first drew.
     *
     * @return the token
     */
    public static String uuid() {
        final String digits = draw("0123456789abcdef", 32);
        return digits.substring(0, 8) + "-" + digits.substring(8, 12) + "-" + digits.substring(12, 16) + "-"
            + digits.substring(16, 20) + "-" + digits.substring(20);
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
            final byte[] bytes = digest.digest((basis(statement) + "#" + drawn[0] + "#" + block)
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
