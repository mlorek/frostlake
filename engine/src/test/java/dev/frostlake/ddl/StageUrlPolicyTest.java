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
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The DEFAULT stage-URL surface, matching a real account (live-verified, three refusal shapes): a
 * well-formed local URL ({@code file:///dir}) answers the quoted
 * {@code invalid URL prefix found in: '<url>'}, a malformed {@code file://host} spelling and a bare
 * path both answer the unquoted {@code invalid URL: <url>}, while cloud spellings and URL-less
 * internal stages stay legal. The local {@code file://} affordance the rest of the suite relies on
 * is an explicit opt-in ({@code stage.file.urlEnabled}, flipped by BaseDatabaseTest) — this class
 * deliberately builds DEFAULT-config engines instead, so it pins the out-of-the-box surface.
 */
public class StageUrlPolicyTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void createDefaultEngine() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE policy_db");
        engine.execute("USE DATABASE policy_db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
    }

    @AfterEach
    public void shutdown() {
        engine.shutdown();
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertEquals(message, e.getMessage());
    }

    @Test
    public void wellFormedLocalUrlRefusesWithTheQuotedPrefixShape() {
        assertRefused("CREATE STAGE s1 URL='file:///tmp/x'",
            "SQL compilation error:\ninvalid URL prefix found in: 'file:///tmp/x'");
    }

    @Test
    public void malformedLocalUrlAndBarePathRefuseUnquoted() {
        assertRefused("CREATE STAGE s2 URL='file://tmp/x'",
            "SQL compilation error:\ninvalid URL: file://tmp/x");
        assertRefused("CREATE STAGE s3 URL='/tmp/x'",
            "SQL compilation error:\ninvalid URL: /tmp/x");
    }

    @Test
    public void cloudSpellingsAndInternalStagesStayLegal() {
        engine.execute("CREATE STAGE s4 URL='s3://bucket/path/'");
        engine.execute("CREATE STAGE s5");
    }

    @Test
    public void theOptInRestoresTheLocalAffordance() {
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE STAGE s6 URL='file:///tmp/frostlake_policy_optin'");
    }
}
