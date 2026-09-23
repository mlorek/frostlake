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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

/**
 * Cortex inference ({@code cortex-inference.yaml}): {@code POST /api/v2/cortex/inference:complete} runs
 * {@code SNOWFLAKE.CORTEX.COMPLETE} — a function of the optional {@code frostlake-ai} module, so it answers
 * {@code 501} on a server without it — and {@code GET /api/v2/cortex/models} answers {@code 501}, since no SQL
 * function lists the models a server can reach.
 *
 * <p>A single user message with no sampling options is the string form, {@code COMPLETE(model, prompt)};
 * anything else is the conversation form, {@code COMPLETE(model, [{role, content}, …], {options})}, whose
 * answer's {@code choices[0].messages} is the completion. The answer is not streamed as it is generated: the
 * whole completion is sent as one {@code text/event-stream} data event, {@code {"choices": [{"delta":
 * {"type": "text", "content": …}}]}}, and the stream then ends. A request with {@code "stream": false} is
 * answered with one JSON object instead, {@code {"choices": [{"message": {"content": …}}]}}.
 */
public final class CortexInferenceResource implements RestResource {

    private static final String COMPLETE = "SNOWFLAKE.CORTEX.COMPLETE";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", "/api/v2/cortex/models", "getModels", this);
        router.add("POST", "/api/v2/cortex/inference:complete", "cortexLLMInferenceComplete", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if ("getModels".equals(call.operation())) {
            if (!call.hasBody()) {
                throw RestException.unreadable("GET /api/v2/cortex/models reads a GetModelsRequest body.");
            }
            throw RestException.notImplemented("Listing Cortex models is not supported: the models are the ones "
                + "the frostlake-ai module's model server holds, which no SQL function lists.");
        }
        return complete(call);
    }

    private static RestResponse complete(final RestCall call) {
        final JsonNode body = call.body();
        final String model = RestJson.text(body, "model");
        if (model == null) {
            throw RestException.missingProperty("model");
        }
        if (!RestJson.present(body, "messages")) {
            throw RestException.missingProperty("messages");
        }
        final JsonNode messages = body.get("messages");
        if (!messages.isArray()) {
            throw RestException.unreadable("Property 'messages' must be an array.");
        }
        if (messages.isEmpty()) {
            throw RestException.badRequest("Property 'messages' must hold at least one message.");
        }
        final ArrayNode history = RestJson.array();
        for (final JsonNode message : messages.values()) {
            if (!message.isObject()) {
                throw RestException.unreadable("Each message must be an object.");
            }
            final ObjectNode turn = RestJson.object();
            final String role = RestJson.text(message, "role");
            turn.put("role", role == null ? "user" : role);
            turn.put("content", text(message));
            history.add(turn);
        }
        final ObjectNode options = RestJson.object();
        copyNumber(body, "temperature", options);
        copyNumber(body, "top_p", options);
        copyNumber(body, "max_tokens", options);
        final boolean single = history.size() == 1 && "user".equals(history.get(0).path("role").asString())
            && options.isEmpty();
        final String sql;
        if (single) {
            sql = "SELECT " + COMPLETE + "(" + RestSql.literal(model) + ", "
                + RestSql.literal(history.get(0).path("content").asString()) + ")";
        } else {
            sql = "SELECT " + COMPLETE + "(" + RestSql.literal(model) + ", PARSE_JSON("
                + RestSql.literal(history.toString()) + "), PARSE_JSON(" + RestSql.literal(options.toString())
                + "))";
        }
        final Object answer = CortexFunctions.firstValue(CortexFunctions.query(call, sql, COMPLETE));
        final String completion = completion(answer);
        final Boolean stream = RestJson.bool(body, "stream");
        if (stream != null && !stream.booleanValue()) {
            final ObjectNode message = RestJson.object();
            message.put("content", completion);
            final ObjectNode choice = RestJson.object();
            choice.set("message", message);
            final ObjectNode out = RestJson.object();
            out.set("choices", RestJson.array().add(choice));
            return RestResponse.json(200, out);
        }
        final ObjectNode delta = RestJson.object();
        delta.put("type", "text");
        delta.put("content", completion);
        final ObjectNode choice = RestJson.object();
        choice.set("delta", delta);
        final ObjectNode event = RestJson.object();
        event.set("choices", RestJson.array().add(choice));
        final String events = "data: " + event + "\n\n";
        return RestResponse.raw(200, "text/event-stream", events.getBytes(StandardCharsets.UTF_8));
    }

    /** A message's text: its {@code content}, or the text items of its {@code content_list} joined. */
    private static String text(final JsonNode message) {
        final String content = RestJson.text(message, "content");
        if (content != null) {
            return content;
        }
        final JsonNode list = message.get("content_list");
        final StringBuilder joined = new StringBuilder();
        if (list != null && list.isArray()) {
            for (final JsonNode item : list.values()) {
                if (!"text".equals(item.path("type").asString(""))) {
                    throw RestException.notImplemented("Only text content is supported in Cortex completion "
                        + "messages.");
                }
                joined.append(item.path("text").asString(""));
            }
            return joined.toString();
        }
        throw RestException.badRequest("Each message needs a 'content' or a 'content_list'.");
    }

    private static void copyNumber(final JsonNode body, final String property, final ObjectNode options) {
        if (!RestJson.present(body, property)) {
            return;
        }
        final JsonNode value = body.get(property);
        if (!value.isNumber()) {
            throw RestException.unreadable("Property '" + property + "' must be a number.");
        }
        options.set(property, value);
    }

    /**
     * The completion text of COMPLETE's answer: the string form answers the text itself, the conversation form
     * a JSON object whose {@code choices[0].messages} holds it.
     */
    private static String completion(final Object answer) {
        if (answer == null) {
            return "";
        }
        final String text = answer.toString();
        final String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            try {
                final JsonNode parsed = RestJson.mapper().readTree(trimmed);
                final JsonNode messages = parsed.path("choices").path(0).path("messages");
                if (messages.isString()) {
                    return messages.asString();
                }
            } catch (final RuntimeException notJson) {
                return text;
            }
        }
        return text;
    }
}
