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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Pub/Sub topic's and subscription's name are {@code projects/<project>/topics|subscriptions/<name>}: the two words
 * in any case, a project of 6 to 30 lower-case letters, digits and hyphens, a letter first and no hyphen last, and a
 * name of three characters or more, a letter first and not {@code goog}. Any other text is an invalid value of its
 * parameter, judged ahead of the direction a topic is written with, and an inbound integration lists its subscription
 * and its service account (live-verified; the account refuses integration DDL to the shared test role, so the cells
 * run on a private engine).
 */
public class PubSubNameTest {

    private static final String OUTBOUND = "SQL compilation error:\ninvalid value 'OUTBOUND' for property 'Direction'";

    private static final String[] VALID_TOPICS = {"projects/fl-ident-123/topics/topic1",
        "projects/fl-ident-123/topics/top", "projects/abcdef/topics/topic1",
        "projects/fl-ident-123/topics/Topic_1.x~y+z%25", "PROJECTS/fl-ident-123/TOPICS/topic1",
        "projects/abcdefghijklmnopqrstuvwxyzabcd/topics/topic1"};

    private static final String[] INVALID_TOPICS = {"projects/p/topics/t", "projects/fl-ident-123/topics/t",
        "projects/fl-ident-123/topics/to", "projects/p/topics/topic1", "projects/abcde/topics/topic1",
        "projects/fl_ident/topics/topic1", "projects/FL-IDENT-123/topics/topic1", "projects/12345678/topics/topic1",
        "projects/1abcdef/topics/topic1", "projects/abcdef-/topics/topic1", "projects/fl-ident-123/topics/1topic",
        "projects/fl-ident-123/topics/goog-topic", "projects/fl-ident-123/topics/to pic",
        "projects/fl-ident-123/topic/topic1", "fl-ident-123/topic1", "projects/fl-ident-123/topics/",
        "projects//topics/topic1", "topic1", "", "projects/fl-ident-123/topics/topic1/x",
        "projects/abcdefghijklmnopqrstuvwxyzabcde/topics/topic1", "projects/example.com:abcdef/topics/topic1"};

    private static final String[] INVALID_SUBSCRIPTIONS = {"projects/p/subscriptions/s",
        "projects/fl-ident-123/subscriptions/s", "projects/abcde/subscriptions/sub1",
        "projects/fl-ident-123/subscription/sub1", "sub1", "projects/fl-ident-123/topics/topic1"};

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String topic(final String name) {
        return answer("CREATE OR REPLACE NOTIFICATION INTEGRATION ps_t TYPE = QUEUE DIRECTION = OUTBOUND"
            + " NOTIFICATION_PROVIDER = GCP_PUBSUB ENABLED = TRUE GCP_PUBSUB_TOPIC_NAME = '" + name + "'");
    }

    private String subscription(final String name) {
        return answer("CREATE OR REPLACE NOTIFICATION INTEGRATION ps_s TYPE = QUEUE"
            + " NOTIFICATION_PROVIDER = GCP_PUBSUB ENABLED = TRUE GCP_PUBSUB_SUBSCRIPTION_NAME = '" + name + "'");
    }

    private static String invalid(final String name, final String parameter) {
        return "SQL compilation error:\ninvalid value ['" + name + "'] for parameter '" + parameter + "'";
    }

    @Test
    public void aValidTopicNameGoesOnToItsDirection() {
        for (final String name : VALID_TOPICS) {
            assertEquals(OUTBOUND, topic(name), name);
        }
    }

    @Test
    public void anyOtherTopicNameIsAnInvalidValue() {
        for (final String name : INVALID_TOPICS) {
            assertEquals(invalid(name, "GCP_PUBSUB_TOPIC_NAME"), topic(name), name);
        }
    }

    @Test
    public void aSubscriptionNameTakesTheSameRuleWithItsOwnWord() {
        for (final String name : INVALID_SUBSCRIPTIONS) {
            assertEquals(invalid(name, "GCP_PUBSUB_SUBSCRIPTION_NAME"), subscription(name), name);
        }
        assertEquals("Integration PS_S successfully created.", subscription("projects/fl-ident-123/subscriptions/sub"));
    }

    @Test
    public void anInboundIntegrationListsItsSubscriptionAndServiceAccount() {
        subscription("projects/fl-ident-123/subscriptions/sub1");
        final ResultSet rs = engine.executeQuery("DESC NOTIFICATION INTEGRATION ps_s");
        final List<String> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            rows.add(row.getValue(rs.getColumnIndex("property")) + "="
                + row.getValue(rs.getColumnIndex("property_value")));
        }
        assertEquals("[ENABLED=true, DIRECTION=INBOUND,"
            + " GCP_PUBSUB_SUBSCRIPTION_NAME=projects/fl-ident-123/subscriptions/sub1,"
            + " GCP_PUBSUB_SERVICE_ACCOUNT=frostlake@frostlake.iam.gserviceaccount.com, COMMENT=]", rows.toString());
    }
}
