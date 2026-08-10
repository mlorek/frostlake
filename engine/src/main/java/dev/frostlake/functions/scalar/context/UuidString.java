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

/**
 * UUID_STRING() — a random version-4 UUID, and UUID_STRING(namespace, name) — the RFC 4122 version-5
 * name-based form, which is bit-exact with Snowflake.
 *
 * <p>The no-argument form is genuinely random, as Snowflake's is. It once packed a monotonic counter
 * into the low 64 bits with a SplitMix64 scramble above it, for test reproducibility; that was removed
 * because nothing depended on it — the tests assert shape and that two calls DIFFER, which random
 * values satisfy more convincingly than a counter, and the vendor suite never calls it. The counter
 * also restarted at zero in a fresh JVM, so it never gave the WAL-replay exactness it looked like it
 * gave.
 */
public class UuidString extends BuiltInFunction {
    public UuidString() { super("UUID_STRING", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // UUID_STRING(uuid_namespace, name) — Snowflake's NAMED form: an RFC 4122 version-5 UUID,
        // SHA-1 over the namespace UUID's bytes followed by the name's UTF-8 bytes (version and
        // variant bits stamped in). Fully deterministic and bit-exact with Snowflake — the docs
        // example UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d','foo') yields
        // dc0b6f65-fca6-5b4b-9d37-ccc3fde1f3e2. A non-string name (e.g. HASH(...)) is used as its text.
        if (args.size() == 2) {
            // Neither argument is null-propagating, and the two behave differently — live-measured:
            // a NULL (or malformed) NAMESPACE is an error, while a NULL NAME hashes as the empty
            // string and still yields a UUID.
            if (args.get(0) == null) {
                throw new RuntimeException(BADLY_FORMED);
            }
            return namedUuid(args.get(0).toString(),
                args.get(1) == null ? "" : args.get(1).toString());
        }
        return UUID.randomUUID().toString();
    }

    /** Live's wording when the namespace is not a UUID, NULL or malformed alike. */
    private static final String BADLY_FORMED = "Badly formed UUID on line 0";

    private String namedUuid(final String namespace, final String name) {
        final UUID ns;
        try {
            ns = UUID.fromString(namespace.trim());
        } catch (final IllegalArgumentException notAUuid) {
            throw new RuntimeException(BADLY_FORMED);
        }
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

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 2; }
}
