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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.VectorValue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Cortex inference and embed endpoints ({@code cortex-inference.yaml}, {@code cortex-embed.yaml}). The
 * functions they call come from the optional {@code frostlake-ai} module, which the engine tests do not load; the
 * class registers stand-ins for {@code COMPLETE} and {@code EMBED_TEXT_768} and leaves {@code EMBED_TEXT_1024}
 * unregistered, to see both the answer and the {@code 501} of a server without the module.
 */
public class RestCortexInferenceTest extends BaseRestTest {

    @BeforeAll
    public static void registerStandIns() {
        final FunctionRegistry registry = server.getEngine().getEngine().getFunctionRegistry();
        registry.register(new BuiltInFunction("SNOWFLAKE.CORTEX.COMPLETE", StringType.VARCHAR) {
            @Override
            public Object evaluate(final List<Object> args) {
                if (args.size() == 2) {
                    return "echo: " + args.get(1);
                }
                return "{\"choices\":[{\"messages\":\"conversation of " + args.get(1).toString().length()
                    + " chars\"}]}";
            }

            @Override
            public int getMinArgCount() {
                return 2;
            }

            @Override
            public int getMaxArgCount() {
                return 3;
            }
        });
        registry.register(new BuiltInFunction("SNOWFLAKE.CORTEX.EMBED_TEXT_768",
                new VectorType(VectorElementType.FLOAT, 2)) {
            @Override
            public Object evaluate(final List<Object> args) {
                return VectorValue.of(VectorElementType.FLOAT, new double[] {0.5, args.get(1).toString().length()});
            }

            @Override
            public int getMinArgCount() {
                return 2;
            }

            @Override
            public int getMaxArgCount() {
                return 2;
            }
        });
    }

    @Test
    public void aCompletionStreamsAsOneDataEvent() throws Exception {
        final HttpResponse<String> response = post("/api/v2/cortex/inference:complete",
            "{\"model\":\"m\",\"messages\":[{\"content\":\"hi there\"}]}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("text/event-stream", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().startsWith("data: ") && response.body().endsWith("\n\n"), response.body());
        final JsonNode event = MAPPER.readTree(response.body().substring("data: ".length()).trim());
        final JsonNode delta = event.path("choices").get(0).path("delta");
        assertEquals("text", delta.path("type").asString());
        assertEquals("echo: hi there", delta.path("content").asString());
    }

    @Test
    public void aConversationUsesTheHistoryForm() throws Exception {
        final HttpResponse<String> response = post("/api/v2/cortex/inference:complete",
            "{\"model\":\"m\",\"temperature\":0.2,\"messages\":[{\"role\":\"system\",\"content\":\"be brief\"},"
            + "{\"role\":\"user\",\"content_list\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("conversation of "), response.body());
    }

    @Test
    public void streamFalseAnswersOneJsonObject() throws Exception {
        final JsonNode answer = ok(post("/api/v2/cortex/inference:complete",
            "{\"model\":\"m\",\"stream\":false,\"messages\":[{\"content\":\"x\"}]}"));
        assertEquals("echo: x", answer.path("choices").get(0).path("message").path("content").asString());
    }

    @Test
    public void malformedCompletionRequestsAre400() throws Exception {
        error(400, post("/api/v2/cortex/inference:complete", "{\"messages\":[{\"content\":\"x\"}]}"));
        error(400, post("/api/v2/cortex/inference:complete", "{\"model\":\"m\",\"messages\":[]}"));
        error(501, post("/api/v2/cortex/inference:complete",
            "{\"model\":\"m\",\"messages\":[{\"content_list\":[{\"type\":\"image\"}]}]}"));
    }

    @Test
    public void modelsAreNotListed() throws Exception {
        assertEquals(400, get("/api/v2/cortex/models").statusCode());
    }

    @Test
    public void anEmbeddingPerText() throws Exception {
        final JsonNode answer = ok(post("/api/v2/cortex/inference:embed",
            "{\"model\":\"e5-base-v2\",\"text\":[\"ab\",\"abcd\"]}"));
        assertEquals("list", answer.path("object").asString());
        assertEquals("e5-base-v2", answer.path("model").asString());
        assertEquals(2, answer.path("data").size());
        final JsonNode second = answer.path("data").get(1);
        assertEquals("embedding", second.path("object").asString());
        assertEquals(1, second.path("index").asInt());
        assertEquals(4.0, second.path("embedding").get(0).get(1).asDouble());
        assertTrue(answer.path("usage").isObject());
    }

    @Test
    public void anUnregisteredEmbeddingFunctionIs501AndAnUnknownModel400() throws Exception {
        assertTrue(error(501, post("/api/v2/cortex/inference:embed",
            "{\"model\":\"nv-embed-qa-4\",\"text\":[\"a\"]}")).contains("EMBED_TEXT_1024"));
        error(400, post("/api/v2/cortex/inference:embed", "{\"model\":\"no-such-model\",\"text\":[\"a\"]}"));
        error(400, post("/api/v2/cortex/inference:embed", "{\"model\":\"e5-base-v2\",\"text\":[]}"));
    }
}
