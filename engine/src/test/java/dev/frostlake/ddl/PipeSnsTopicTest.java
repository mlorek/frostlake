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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pipe's SNS topic feeds automatic ingestion: a pipe that names one without AUTO_INGEST = TRUE is refused. */
public class PipeSnsTopicTest extends BaseDatabaseTest {

    @Test
    public void anSnsTopicWithoutAutoIngestIsRefused() {
        engine.executeQuery("CREATE TABLE pst_t (a VARIANT)");
        engine.executeQuery("CREATE STAGE pst_st");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CREATE PIPE pst_p AUTO_INGEST = FALSE"
                    + " AWS_SNS_TOPIC = 'arn:aws:sns:us-east-1:123456789012:topic' AS COPY INTO pst_t FROM @pst_st");
            }
        });
        assertTrue(refused.getMessage().contains(
            "Pipe Notifications bind failure \"Cannot set AWS SNS topic for pipe without auto_ingest\""),
            refused.getMessage());
        assertEquals(0, engine.executeQuery("SHOW PIPES LIKE 'PST_P' IN SCHEMA test_db.test_schema").getRowCount());
    }
}
