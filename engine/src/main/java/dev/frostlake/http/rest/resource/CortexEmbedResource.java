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
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.values.VectorValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;

/**
 * Cortex embeddings ({@code cortex-embed.yaml}): {@code POST /api/v2/cortex/inference:embed} runs
 * {@code SNOWFLAKE.CORTEX.EMBED_TEXT_768} or {@code EMBED_TEXT_1024}, whichever takes the requested model, once
 * per text. Both are functions of the optional {@code frostlake-ai} module, so the endpoint answers {@code 501}
 * on a server without it. The answer's {@code usage} is empty: no token count is kept.
 */
public final class CortexEmbedResource implements RestResource {

    private static final List<String> MODELS_768 = Arrays.asList(
        "snowflake-arctic-embed-m-v1.5", "snowflake-arctic-embed-m", "e5-base-v2");
    private static final List<String> MODELS_1024 = Arrays.asList(
        "snowflake-arctic-embed-l-v2.0", "snowflake-arctic-embed-l-v2.0-8k", "nv-embed-qa-4",
        "multilingual-e5-large", "voyage-multilingual-2");

    @Override
    public void register(final RestRouter router) {
        router.add("POST", "/api/v2/cortex/inference:embed", "embed", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        final JsonNode body = call.body();
        final String model = RestJson.text(body, "model");
        if (model == null) {
            throw RestException.missingProperty("model");
        }
        final String function;
        if (MODELS_768.contains(model)) {
            function = "SNOWFLAKE.CORTEX.EMBED_TEXT_768";
        } else if (MODELS_1024.contains(model)) {
            function = "SNOWFLAKE.CORTEX.EMBED_TEXT_1024";
        } else {
            throw RestException.badRequest("Unknown embedding model '" + model + "'.");
        }
        final List<String> texts = RestJson.strings(body, "text");
        if (texts == null) {
            throw RestException.missingProperty("text");
        }
        if (texts.isEmpty()) {
            throw RestException.badRequest("Property 'text' must hold at least one text.");
        }
        final StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(function).append('(').append(RestSql.literal(model)).append(", ")
                .append(RestSql.literal(texts.get(i))).append(')');
        }
        final ResultSet result = CortexFunctions.query(call, sql.toString(), function);
        final ArrayNode data = RestJson.array();
        for (int i = 0; i < texts.size(); i++) {
            final Object value = result.getRows().isEmpty() ? null : result.getRows().get(0).getValue(i);
            final ObjectNode item = RestJson.object();
            item.put("object", "embedding");
            item.set("embedding", RestJson.array().add(vector(value)));
            item.put("index", i);
            data.add(item);
        }
        final ObjectNode out = RestJson.object();
        out.put("object", "list");
        out.set("data", data);
        out.put("model", model);
        out.set("usage", RestJson.object());
        return RestResponse.json(200, out);
    }

    /** A VECTOR value as a JSON array of numbers. */
    private static ArrayNode vector(final Object value) {
        final ArrayNode out = RestJson.array();
        if (value instanceof VectorValue) {
            for (final double element : ((VectorValue) value).elements()) {
                out.add(element);
            }
            return out;
        }
        if (value != null) {
            final JsonNode parsed = RestJson.mapper().readTree(value.toString());
            for (final JsonNode element : parsed.values()) {
                out.add(element.asDouble());
            }
        }
        return out;
    }
}
