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


package dev.frostlake.functions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UUID_STRING() and RANDOM() draw from a stream a random seed starts, never from the statement's start alone: no value
 * is the one the statement's CURRENT_TIMESTAMP would derive, and two engines started together draw different values.
 */
public class StatementRandomSeedTest {

    /** The UUID the statement's start alone would have derived, at a given nanosecond. */
    private static String derivedFromStart(final long epochNanos) throws NoSuchAlgorithmException {
        final Instant start = Instant.ofEpochSecond(Math.floorDiv(epochNanos, 1_000_000_000L),
            Math.floorMod(epochNanos, 1_000_000_000L));
        final byte[] seed = MessageDigest.getInstance("SHA-256")
            .digest((start + "#stream").getBytes(StandardCharsets.UTF_8));
        long bits = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            bits = bits << Byte.SIZE | (seed[i] & 0xFFL);
        }
        final SplittableRandom stream = new SplittableRandom(bits);
        final long high = stream.nextLong() & 0xFFFFFFFFFFFF0FFFL | 0x0000000000004000L;
        final long low = stream.nextLong() & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L;
        return new UUID(high, low).toString();
    }

    @Test
    public void aUuidIsNotTheOneTheStatementsStartWouldDerive() throws NoSuchAlgorithmException {
        final DatabaseEngine engine = new DatabaseEngine();
        try {
            for (int run = 0; run < 20; run++) {
                final List<Object> cells = engine.executeQuery(
                    "SELECT UUID_STRING(), DATE_PART(EPOCH_NANOSECOND, CURRENT_TIMESTAMP(9))").getRows().get(0)
                    .getValues();
                final String drawn = String.valueOf(cells.get(0));
                final long nanos = ((Number) cells.get(1)).longValue();
                for (int place = 0; place < 1000; place++) {
                    assertTrue(!drawn.equals(derivedFromStart(nanos + place)), drawn + " derived at " + place);
                }
                assertEquals('4', drawn.charAt(14));
            }
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void twoEnginesDrawDifferentValues() {
        final DatabaseEngine first = new DatabaseEngine();
        final DatabaseEngine second = new DatabaseEngine();
        try {
            final Set<String> seen = new HashSet<String>();
            for (int i = 0; i < 200; i++) {
                for (final DatabaseEngine engine : new DatabaseEngine[] {first, second}) {
                    final Row row = engine.executeQuery("SELECT UUID_STRING(), RANDOM()").getRows().get(0);
                    assertTrue(seen.add(String.valueOf(row.getValue(0))), "repeated " + row.getValue(0));
                    assertTrue(seen.add(String.valueOf(row.getValue(1))), "repeated " + row.getValue(1));
                }
            }
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }
}
