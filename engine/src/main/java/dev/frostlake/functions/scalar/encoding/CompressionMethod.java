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

package dev.frostlake.functions.scalar.encoding;

import java.util.Locale;

/**
 * The {@code method} argument of {@code COMPRESS} / {@code DECOMPRESS_STRING} /
 * {@code DECOMPRESS_BINARY}: an algorithm name with an optional compression level in parentheses,
 * e.g. {@code 'zlib'}, {@code 'ZLIB(1)'}, {@code ' zstd '}.
 *
 * <p>The accepted shape was live-verified against Snowflake:
 * <ul>
 *   <li>the name is case-insensitive and surrounding whitespace is ignored ({@code '  SNAPPY  '},
 *       {@code 'snappy\t'} both work) — whitespace <em>inside</em> the name is not
 *       ({@code 'sn appy'} is rejected);</li>
 *   <li>the level is an optional {@code (n)} suffix, whitespace-tolerant inside the parentheses
 *       ({@code 'zlib( 1 )'}), where {@code n} is a non-negative decimal integer that fits in a
 *       signed 32-bit int — {@code 'zlib(2147483647)'} is accepted, {@code 'zlib(2147483648)'} is
 *       not; leading zeros are fine ({@code 'zlib(01)'} is level 1);</li>
 *   <li>level {@code 0} means "the method's default", identical to omitting the level;</li>
 *   <li>a level above the algorithm's maximum clamps rather than erroring ({@code 'zlib(10)'}
 *       behaves as {@code 'zlib(9)'});</li>
 *   <li>anything else — a negative level, a non-integer level, empty or unbalanced parentheses, an
 *       unknown name — is reported as an unknown <em>method</em>, quoting the whole original string
 *       lowercased but NOT trimmed: {@code COMPRESS(x, '  DEFLATE  ')} fails with
 *       {@code Unknown compression method '  deflate  '} (SQLSTATE 42P19, error 100194).</li>
 * </ul>
 */
public final class CompressionMethod {

    /** Snowflake's "use the algorithm's default level" sentinel; {@code method(0)} means the same. */
    public static final int DEFAULT_LEVEL = 0;

    private final CompressionAlgorithm algorithm;
    private final int level;
    private final String raw;

    private CompressionMethod(final CompressionAlgorithm algorithm, final int level, final String raw) {
        this.algorithm = algorithm;
        this.level = level;
        this.raw = raw;
    }

    /**
     * Parses the {@code method} argument, or throws Snowflake's unknown-method error.
     *
     * @param raw the method text exactly as the caller wrote it — it is echoed verbatim in a
     *            decompression failure and lowercased in the unknown-method error
     */
    public static CompressionMethod parse(final String raw) {
        final String lower = raw.toLowerCase(Locale.ROOT);
        final String trimmed = lower.trim();
        final String name;
        final int level;
        if (trimmed.endsWith(")")) {
            final int open = trimmed.indexOf('(');
            if (open < 0) {
                throw unknownMethod(lower);
            }
            name = trimmed.substring(0, open).trim();
            level = parseLevel(trimmed.substring(open + 1, trimmed.length() - 1).trim(), lower);
        } else if (trimmed.indexOf('(') >= 0) {
            // An unbalanced '(' — 'zlib(1' — is an unknown method, not a truncated level.
            throw unknownMethod(lower);
        } else {
            name = trimmed;
            level = DEFAULT_LEVEL;
        }
        final CompressionAlgorithm algorithm = algorithmFor(name);
        if (algorithm == null) {
            throw unknownMethod(lower);
        }
        return new CompressionMethod(algorithm, level, raw);
    }

    private static CompressionAlgorithm algorithmFor(final String name) {
        if (name.equals("snappy")) {
            return CompressionAlgorithm.SNAPPY;
        }
        if (name.equals("zlib")) {
            return CompressionAlgorithm.ZLIB;
        }
        if (name.equals("zstd")) {
            return CompressionAlgorithm.ZSTD;
        }
        if (name.equals("bz2")) {
            return CompressionAlgorithm.BZ2;
        }
        return null;
    }

    private static int parseLevel(final String digits, final String lowerMethod) {
        if (digits.isEmpty()) {
            throw unknownMethod(lowerMethod);
        }
        for (int i = 0; i < digits.length(); i++) {
            // Digits only: this is what rejects '-1', '+1', '1.5', 'x' and the trailing junk of
            // 'zlib(1)(2)' (whose parenthesised text parses as "1)(2").
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
                throw unknownMethod(lowerMethod);
            }
        }
        try {
            return Integer.parseInt(digits);
        } catch (final NumberFormatException e) {
            // Beyond a signed 32-bit int Snowflake stops treating it as a level at all.
            throw unknownMethod(lowerMethod);
        }
    }

    private static RuntimeException unknownMethod(final String lowerMethod) {
        return new RuntimeException("Unknown compression method '" + lowerMethod + "'");
    }

    public CompressionAlgorithm algorithm() {
        return algorithm;
    }

    /** The requested level, or {@link #DEFAULT_LEVEL} when none was given. */
    public int level() {
        return level;
    }

    /** The method text exactly as written, for the decompression-failure message. */
    public String raw() {
        return raw;
    }
}
