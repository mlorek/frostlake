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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The notification integration endpoints ({@code notification-integration.yaml}). */
public class RestNotificationIntegrationTest extends BaseRestTest {

    @Test
    public void eachHookTypeRoundTrips() throws Exception {
        ok(post("/api/v2/notification-integrations", "{\"name\":\"rn_mail\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"EMAIL\",\"default_subject\":\"s\"}}"));
        ok(post("/api/v2/notification-integrations", "{\"name\":\"rn_hook\",\"enabled\":false,"
            + "\"notification_hook\":{\"type\":\"WEBHOOK\",\"webhook_url\":\"https://h/x\","
            + "\"webhook_body_template\":\"{\\\"m\\\":\\\"SNOWFLAKE_WEBHOOK_MESSAGE\\\"}\",\"webhook_headers\":{\"Content-Type\":\"application/json\"}}}"));
        ok(post("/api/v2/notification-integrations", "{\"name\":\"rn_sns\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"QUEUE_AWS_SNS_OUTBOUND\",\"aws_sns_topic_arn\":\"arn:t\","
            + "\"aws_sns_role_arn\":\"arn:r\"}}"));
        ok(post("/api/v2/notification-integrations", "{\"name\":\"rn_gcp\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"QUEUE_GCP_PUBSUB_INBOUND\","
            + "\"gcp_pubsub_subscription_name\":\"projects/p/subscriptions/s\"}}"));

        final JsonNode mail = ok(get("/api/v2/notification-integrations/rn_mail"));
        assertEquals("EMAIL", mail.path("notification_hook").path("type").asString());
        assertEquals("s", mail.path("notification_hook").path("default_subject").asString());
        final JsonNode hook = ok(get("/api/v2/notification-integrations/rn_hook")).path("notification_hook");
        assertEquals("WEBHOOK", hook.path("type").asString());
        assertEquals("https://h/x", hook.path("webhook_url").asString());
        assertEquals("application/json", hook.path("webhook_headers").path("Content-Type").asString());
        final JsonNode sns = ok(get("/api/v2/notification-integrations/rn_sns")).path("notification_hook");
        assertEquals("QUEUE_AWS_SNS_OUTBOUND", sns.path("type").asString());
        assertEquals("arn:t", sns.path("aws_sns_topic_arn").asString());
        assertTrue(sns.path("sf_aws_iam_user_arn").isNull(), "no identity is invented: " + sns);
        assertEquals("QUEUE_GCP_PUBSUB_INBOUND", ok(get("/api/v2/notification-integrations/rn_gcp"))
            .path("notification_hook").path("type").asString());

        assertEquals("RN_GCP,RN_HOOK,RN_MAIL,RN_SNS", names(ok(get("/api/v2/notification-integrations?like=RN_%25"))));
        ok(delete("/api/v2/notification-integrations/rn_gcp"));
        error(404, delete("/api/v2/notification-integrations/rn_gcp"));
        ok(delete("/api/v2/notification-integrations/rn_gcp?ifExists=true"));
    }

    @Test
    public void refusals() throws Exception {
        error(400, post("/api/v2/notification-integrations", "{\"name\":\"rn_bad\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"WEBHOOK\"}}"));
        error(400, post("/api/v2/notification-integrations", "{\"name\":\"rn_bad\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"PIGEON\"}}"));
        ok(post("/api/v2/notification-integrations", "{\"name\":\"other_twice\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"EMAIL\"}}"));
        error(409, post("/api/v2/notification-integrations", "{\"name\":\"other_twice\",\"enabled\":true,"
            + "\"notification_hook\":{\"type\":\"EMAIL\"}}"));
        ok(post("/api/v2/notification-integrations?createMode=orReplace", "{\"name\":\"other_twice\",\"enabled\":false,"
            + "\"notification_hook\":{\"type\":\"EMAIL\"}}"));
        assertEquals(false, ok(get("/api/v2/notification-integrations/other_twice")).path("enabled").asBoolean());
        error(404, get("/api/v2/notification-integrations/rn_absent"));
    }
}
