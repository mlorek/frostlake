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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.ObjectType;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * PARSE_URL(url [, permissive]) — parses a URL into a VARIANT OBJECT with Snowflake's keys, in ALPHABETICAL
 * order (live-verified): {@code fragment, host, parameters, path, port, query, scheme}. {@code port} is a
 * string; {@code parameters} is an object of the decoded query key/value pairs; missing components are JSON
 * null. The leading slash is stripped from {@code path} and any userinfo is folded into {@code host} (e.g.
 * {@code user:pass@host}), matching Snowflake. A parse failure (including a missing scheme) raises; when
 * {@code permissive} is 1 it instead returns an object with only an {@code error} key. NULL url yields NULL.
 */
public class ParseUrl extends TextArgumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParseUrl() { super("PARSE_URL", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final boolean permissive = args.size() > 1 && args.get(1) != null
            && ((Number) args.get(1)).intValue() != 0;
        try {
            final URI uri = new URI(args.get(0).toString());
            if (uri.getScheme() == null) {
                return onError("scheme not specified", permissive);
            }
            // Keys are emitted in ALPHABETICAL order — fragment, host, parameters, path, port, query,
            // scheme — matching the object live Snowflake returns.
            final ObjectNode obj = MAPPER.createObjectNode();
            if (uri.getFragment() == null) {
                obj.putNull("fragment");
            } else {
                obj.put("fragment", uri.getFragment());
            }

            final String userInfo = uri.getUserInfo();
            final String rawHost = uri.getHost();
            if (rawHost != null) {
                obj.put("host", (userInfo != null ? userInfo + "@" : "") + rawHost);
            } else if (uri.getAuthority() != null) {
                obj.put("host", uri.getAuthority());
            } else {
                obj.putNull("host");
            }

            final String query = uri.getRawQuery();
            final ObjectNode parameters = MAPPER.createObjectNode();
            if (query != null && !query.isEmpty()) {
                for (final String pair : query.split("&")) {
                    if (pair.isEmpty()) continue;
                    final int eq = pair.indexOf('=');
                    if (eq >= 0) {
                        parameters.put(pair.substring(0, eq), pair.substring(eq + 1));
                    } else {
                        parameters.putNull(pair);
                    }
                }
            }
            obj.set("parameters", parameters);

            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path == null || path.isEmpty()) {
                obj.putNull("path");
            } else {
                obj.put("path", path);
            }

            if (uri.getPort() >= 0) {
                obj.put("port", String.valueOf(uri.getPort()));
            } else {
                obj.putNull("port");
            }

            if (query == null || query.isEmpty()) {
                obj.putNull("query");
            } else {
                obj.put("query", query);
            }

            obj.put("scheme", uri.getScheme());
            return obj.toString();
        } catch (final URISyntaxException e) {
            return onError(e.getReason() != null ? e.getReason() : "invalid URL", permissive);
        }
    }

    private Object onError(final String message, final boolean permissive) {
        if (permissive) {
            final ObjectNode err = MAPPER.createObjectNode();
            err.put("error", message);
            return err.toString();
        }
        throw new RuntimeException("Error parsing URL: " + message);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
