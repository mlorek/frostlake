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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class UuidString extends BuiltInFunction {
    public UuidString() { super("UUID_STRING", StringType.VARCHAR); }

    // TEMPORARY (test reproducibility): deterministic, still-unique UUIDs instead of UUID.randomUUID().
    // The low 64 bits carry a monotonic counter (guarantees uniqueness); the high 64 bits are a SplitMix64
    // scramble of it so the value still looks like a UUID. Revert this whole block to
    // `return UUID.randomUUID().toString();` to restore real randomness. (The 2-argument NAMED form below
    // is exact RFC 4122 and unaffected by this.)
    private static final AtomicLong COUNTER = new AtomicLong();

    private static long mix(final long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // UUID_STRING(uuid_namespace, name) — Snowflake's NAMED form: an RFC 4122 version-5 UUID,
        // SHA-1 over the namespace UUID's bytes followed by the name's UTF-8 bytes (version and
        // variant bits stamped in). Fully deterministic and bit-exact with Snowflake — the docs
        // example UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d','foo') yields
        // dc0b6f65-fca6-5b4b-9d37-ccc3fde1f3e2. A non-string name (e.g. HASH(...)) is used as its text.
        if (args.size() == 2) {
            if (args.get(0) == null || args.get(1) == null) {
                return null;
            }
            return namedUuid(args.get(0).toString(), args.get(1).toString());
        }
        final long n = COUNTER.incrementAndGet();
        return new UUID(mix(n), n).toString();
    }

    private String namedUuid(final String namespace, final String name) {
        final UUID ns = UUID.fromString(namespace.trim());
        final ByteBuffer nsBytes = ByteBuffer.allocate(16);
        nsBytes.putLong(ns.getMostSignificantBits());
        nsBytes.putLong(ns.getLeastSignificantBits());
        final MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (final NoSuchAlgorithmException impossible) {
            throw new RuntimeException("SHA-1 unavailable", impossible);
        }
        sha1.update(nsBytes.array());
        sha1.update(name.getBytes(StandardCharsets.UTF_8));
        final byte[] digest = sha1.digest();
        digest[6] = (byte) ((digest[6] & 0x0F) | 0x50);   // version 5
        digest[8] = (byte) ((digest[8] & 0x3F) | 0x80);   // RFC 4122 variant
        final ByteBuffer out = ByteBuffer.wrap(digest);
        return new UUID(out.getLong(), out.getLong()).toString();
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 2; }
}
