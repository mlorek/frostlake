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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * CREATE STAGE with no URL makes an internal named stage (Snowflake's default when URL is omitted) — it must
 * not be rejected. A URL still denotes an external stage.
 */
public class InternalStageTest extends BaseDatabaseTest {

    @Test
    public void createInternalStageWithoutUrlSucceeds() {
        engine.execute("CREATE STAGE int_stage");
        // The stage really exists — dropping it succeeds (a no-op create would make this DROP fail).
        engine.execute("DROP STAGE int_stage");
    }

    @Test
    public void externalStageWithUrlStillWorks() {
        engine.execute("CREATE STAGE ext_stage URL = 's3://bucket/path/'");
        engine.execute("DROP STAGE ext_stage");
    }
}
