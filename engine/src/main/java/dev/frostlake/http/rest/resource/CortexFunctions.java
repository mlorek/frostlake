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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.storage.ResultSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.Locale;

/**
 * Calls of the {@code SNOWFLAKE.CORTEX} functions the optional {@code frostlake-ai} module registers. The REST
 * surface reaches them through SQL only, so a server without the module answers the statement with an unknown
 * function, which becomes {@code 501} here.
 */
final class CortexFunctions {

    /** Static helpers only. */
    private CortexFunctions() {
    }

    /**
     * Runs a statement calling a Cortex function.
     *
     * @param call the REST call whose session runs it
     * @param sql the statement
     * @param function the function's name as the statement calls it, e.g. {@code SNOWFLAKE.CORTEX.COMPLETE}
     * @return the statement's result
     * @throws RestException {@code 501} when the function is not registered; the statement's own refusal
     *                       otherwise
     */
    static ResultSet query(final RestCall call, final String sql, final String function) {
        try {
            return call.sql().query(sql);
        } catch (final RestException refusal) {
            final String message = refusal.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT)
                    .contains(("unknown user-defined function " + function).toLowerCase(Locale.ROOT))) {
                throw RestException.notImplemented(function + " is not available: add the frostlake-ai module "
                    + "to the server's classpath to serve this endpoint.");
            }
            throw refusal;
        }
    }

    /** The first cell of a one-row result, or null. */
    static Object firstValue(final ResultSet result) {
        if (result.getRows().isEmpty() || result.getColumns().isEmpty()) {
            return null;
        }
        return result.getRows().get(0).getValue(0);
    }

    /**
     * A SHOW cell holding a JSON array of names, such as {@code ["ID","BODY"]}, as a JSON array; null when the
     * cell is empty or not an array.
     */
    static ArrayNode nameArray(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final JsonNode parsed;
        try {
            parsed = RestJson.mapper().readTree(text);
        } catch (final RuntimeException malformed) {
            return null;
        }
        if (parsed == null || !parsed.isArray()) {
            return null;
        }
        final ArrayNode out = RestJson.array();
        for (final JsonNode item : parsed.values()) {
            out.add(item.asString());
        }
        return out;
    }
}
