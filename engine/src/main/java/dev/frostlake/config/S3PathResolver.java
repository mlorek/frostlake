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

package dev.frostlake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves Snowflake-style S3 URLs ({@code s3://bucket/key}) to a local filesystem path, so files that
 * live in S3 in production — most usefully the JAR(s) named in a UDF/procedure {@code IMPORTS} clause —
 * can be served from local disk in the local/CI testing the engine targets. Driven by two engine settings:
 *
 * <ul>
 *   <li>{@code stage.s3.localMappings} — explicit prefix overrides, {@code ';'}-separated {@code s3prefix=localdir}
 *       pairs, e.g. {@code "s3://prod-bucket/lib/=/opt/jars; s3://data/=/srv/data"}. The longest matching
 *       prefix wins, and the remainder of the URL is appended to the mapped directory.</li>
 *   <li>{@code stage.s3.localRoot} — the fallback base; an unmapped {@code s3://bucket/key} resolves to
 *       {@code <localRoot>/bucket/key}. Defaults to {@code ~/.frostlake_stages/s3}.</li>
 * </ul>
 */
public class S3PathResolver {

    private static final Logger logger = LoggerFactory.getLogger(S3PathResolver.class);

    private static final String S3_SCHEME = "s3://";

    private final Path localRoot;
    private final Map<String, Path> mappings;   // s3:// prefix -> local dir, in configuration order

    public S3PathResolver(final EngineConfig config) {
        this.localRoot = Paths.get(config.getStageS3LocalRoot());
        this.mappings = parseMappings(config.getStageS3LocalMappings());
    }

    /** True if {@code url} is an {@code s3://…} reference. */
    public static boolean isS3Url(final String url) {
        return url != null && url.startsWith(S3_SCHEME);
    }

    /**
     * Map an {@code s3://bucket/key} URL to its local filesystem path. An explicit {@code stage.s3.localMappings}
     * entry wins (longest matching prefix); with no match the key is placed under the configured local root
     * ({@code s3://bucket/key} → {@code <localRoot>/bucket/key}).
     *
     * @throws IllegalArgumentException if {@code s3Url} is not an {@code s3://} URL
     */
    public Path toLocalPath(final String s3Url) {
        if (!isS3Url(s3Url)) {
            throw new IllegalArgumentException("Not an s3:// URL: " + s3Url);
        }

        String bestPrefix = null;
        Path bestDir = null;
        for (final Map.Entry<String, Path> entry : mappings.entrySet()) {
            final String prefix = entry.getKey();
            if (s3Url.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
                bestDir = entry.getValue();
            }
        }

        if (bestPrefix != null) {
            final String rest = stripLeadingSlash(s3Url.substring(bestPrefix.length()));
            return rest.isEmpty() ? bestDir : bestDir.resolve(rest);
        }

        final String rest = s3Url.substring(S3_SCHEME.length());   // bucket/key
        return rest.isEmpty() ? localRoot : localRoot.resolve(rest);
    }

    private static Map<String, Path> parseMappings(final String raw) {
        final Map<String, Path> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (final String token : raw.split(";")) {
            final String entry = token.trim();
            if (entry.isEmpty()) {
                continue;
            }
            final int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                logger.warn("Ignoring malformed stage.s3.localMappings entry (expected 's3prefix=localdir'): {}", entry);
                continue;
            }
            final String prefix = entry.substring(0, eq).trim();
            final String dir = entry.substring(eq + 1).trim();
            if (!isS3Url(prefix)) {
                logger.warn("Ignoring stage.s3.localMappings entry whose key is not an s3:// prefix: {}", entry);
                continue;
            }
            result.put(prefix, Paths.get(dir));
        }
        return result;
    }

    private static String stripLeadingSlash(final String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
