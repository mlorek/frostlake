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

/**
 * The listing parameters every list endpoint shares, as the SHOW modifiers they translate to:
 * {@code like} → {@code LIKE}, {@code startsWith} → {@code STARTS WITH}, {@code showLimit} and {@code fromName}
 * → {@code LIMIT … FROM}. Where a listing parses a modifier but ignores it, so does the endpoint: the statement
 * decides, as it does in SQL.
 */
public final class RestShow {

    /** The largest {@code showLimit} the specification allows, used when only {@code fromName} is given. */
    public static final long MAX_LIMIT = 10000L;

    /** Static helpers only. */
    private RestShow() {
    }

    /** {@code " LIKE '<pattern>'"} for the {@code like} parameter, or nothing. */
    public static String like(final RestCall call) {
        final String like = call.query("like");
        return like == null ? "" : " LIKE " + RestSql.literal(like);
    }

    /** {@code " LIKE '<name>'"}: the listing narrowed to one name, whose exact match is then made on the rows. */
    public static String likeName(final RestIdentifier name) {
        return " LIKE " + name.literal();
    }

    /**
     * {@code " STARTS WITH '…' LIMIT n FROM '…'"} for the {@code startsWith}, {@code showLimit} and
     * {@code fromName} parameters, each part present only when its parameter is. The limit is passed on as given:
     * the account does not refuse one outside the specification's 1..10000, the statement decides.
     */
    public static String tail(final RestCall call) {
        final StringBuilder out = new StringBuilder();
        final String startsWith = call.query("startsWith");
        if (startsWith != null) {
            out.append(" STARTS WITH ").append(RestSql.literal(startsWith));
        }
        final Long limit = call.integer("showLimit");
        final String fromName = call.query("fromName");
        if (limit != null || fromName != null) {
            out.append(" LIMIT ").append(limit != null ? limit.longValue() : MAX_LIMIT);
            if (fromName != null) {
                out.append(" FROM ").append(RestSql.literal(fromName));
            }
        }
        return out.toString();
    }
}
