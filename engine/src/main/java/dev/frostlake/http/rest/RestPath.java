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

package dev.frostlake.http.rest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A request path, or a route template, split as the REST API addresses resources: slash-separated segments,
 * the last of which may end in an action — {@code /api/v2/warehouses/{name}:resume} is the segments
 * {@code api, v2, warehouses, {name}} and the action {@code resume}.
 *
 * <p>A request's segments are percent-decoded one by one after the split, so an encoded slash stays inside its
 * segment. The action is split off at the last colon of the last segment that lies outside double quotes, so a
 * quoted name may hold a colon: {@code "a:b":clone} is the name {@code "a:b"} and the action {@code clone}.
 */
public final class RestPath {

    private final List<String> segments;
    private final String action;

    private RestPath(final List<String> segments, final String action) {
        this.segments = Collections.unmodifiableList(segments);
        this.action = action;
    }

    /**
     * Splits a request's raw (still percent-encoded) path.
     *
     * @return the path, or null when a segment's encoding is malformed
     */
    public static RestPath ofRequest(final String rawPath) {
        final List<String> decoded = new ArrayList<>();
        for (final String raw : rawPath.split("/", -1)) {
            if (raw.isEmpty()) {
                continue;
            }
            final String segment = decode(raw);
            if (segment == null) {
                return null;
            }
            decoded.add(segment);
        }
        return split(decoded);
    }

    /** Splits a route template, which is written unencoded. */
    public static RestPath ofTemplate(final String template) {
        final List<String> parts = new ArrayList<>();
        for (final String part : template.split("/", -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return split(parts);
    }

    private static RestPath split(final List<String> parts) {
        if (parts.isEmpty()) {
            return new RestPath(parts, null);
        }
        final String last = parts.get(parts.size() - 1);
        final int colon = actionColon(last);
        if (colon < 0) {
            return new RestPath(parts, null);
        }
        parts.set(parts.size() - 1, last.substring(0, colon));
        return new RestPath(parts, last.substring(colon + 1));
    }

    /** The index of the last colon outside double quotes, or -1. */
    private static int actionColon(final String segment) {
        boolean quoted = false;
        int found = -1;
        for (int i = 0; i < segment.length(); i++) {
            final char c = segment.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ':' && !quoted) {
                found = i;
            }
        }
        return found;
    }

    /** Percent-decodes one path segment; {@code +} stays a plus. Null when an escape is malformed. */
    private static String decode(final String raw) {
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < raw.length()) {
            final char c = raw.charAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length()) {
                    return null;
                }
                final int hi = Character.digit(raw.charAt(i + 1), 16);
                final int lo = Character.digit(raw.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                bytes.write(hi * 16 + lo);
                i += 3;
            } else {
                final byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
                i++;
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    /** The segments, the action removed from the last. */
    public List<String> segments() {
        return segments;
    }

    /** The action, or null when the path names none. */
    public String action() {
        return action;
    }

    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder();
        for (final String segment : segments) {
            out.append('/').append(segment);
        }
        if (action != null) {
            out.append(':').append(action);
        }
        return out.toString();
    }
}
