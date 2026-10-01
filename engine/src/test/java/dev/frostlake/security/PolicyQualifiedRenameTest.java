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

package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ALTER … POLICY … RENAME TO} takes a QUALIFIED target, and the qualifier is not decoration:
 * naming another schema MOVES the policy there. Frostlake's rule took a bare name only, so every
 * qualified rename was a syntax error at the word RENAME — and the obvious repair, accepting the name
 * and renaming in place, would have been worse than the error, because a move would have looked like
 * a success while leaving the policy where it was.
 *
 * <p>Every policy kind takes it: masking, row access, projection and aggregation.
 */
public class PolicyQualifiedRenameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE SCHEMA other_s");
        // CREATE SCHEMA makes the new schema current, so the rest of the test says where it means.
        engine.execute("USE SCHEMA test_schema");
    }

    /** The policies one schema holds, as "NAME, SCHEMA". */
    private String policiesIn(final String listing, final String schema) {
        final ResultSet rs = engine.executeQuery(
            "SHOW " + listing + " IN SCHEMA test_db." + schema);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(String.valueOf(rs.getValue("name"))).append(", ")
                .append(String.valueOf(rs.getValue("schema_name")));
        }
        return out.toString();
    }

    /** A qualified target naming the policy's own schema renames it in place. */
    @Test
    public void aQualifiedTargetInTheSameSchemaRenames() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp2 AS (val STRING) RETURNS STRING -> '***'");
        engine.execute("ALTER MASKING POLICY mp2 RENAME TO test_db.test_schema.mp3");
        assertEquals("MP3, TEST_SCHEMA", policiesIn("MASKING POLICIES", "test_schema"));

        engine.execute("ALTER MASKING POLICY mp3 RENAME TO mp4");
        assertEquals("MP4, TEST_SCHEMA", policiesIn("MASKING POLICIES", "test_schema"),
            "the bare form still works");
        engine.execute("DROP MASKING POLICY mp4");
    }

    /** A qualified target naming ANOTHER schema moves the policy into it. */
    @Test
    public void aQualifiedTargetInAnotherSchemaMoves() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mm AS (val STRING) RETURNS STRING -> '***'");
        engine.execute("ALTER MASKING POLICY mm RENAME TO test_db.other_s.mm2");
        assertEquals("", policiesIn("MASKING POLICIES", "test_schema"), "gone from where it was");
        assertEquals("MM2, OTHER_S", policiesIn("MASKING POLICIES", "other_s"), "and in the new one");
        engine.execute("DROP MASKING POLICY test_db.other_s.mm2");
    }

    /** The other policy kinds take a qualified target too. */
    @Test
    public void everyPolicyKindTakesIt() {
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rp1 AS (x INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("ALTER ROW ACCESS POLICY rp1 RENAME TO test_db.test_schema.rp2");
        assertEquals("RP2, TEST_SCHEMA", policiesIn("ROW ACCESS POLICIES", "test_schema"));
        engine.execute("DROP ROW ACCESS POLICY rp2");

        engine.execute("CREATE OR REPLACE PROJECTION POLICY pj1 AS () RETURNS PROJECTION_CONSTRAINT ->"
            + " PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("ALTER PROJECTION POLICY pj1 RENAME TO test_db.test_schema.pj2");
        engine.execute("DROP PROJECTION POLICY pj2");

        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ag1 AS () RETURNS AGGREGATION_CONSTRAINT ->"
            + " AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("ALTER AGGREGATION POLICY ag1 RENAME TO test_db.test_schema.ag2");
        engine.execute("DROP AGGREGATION POLICY ag2");
    }
}
