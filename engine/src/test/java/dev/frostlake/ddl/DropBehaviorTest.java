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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every DROP takes a trailing CASCADE or RESTRICT — a table, a view, a sequence, a function, a user and the rest,
 * with IF EXISTS too — while writing both is a syntax error at the second, and a missing object is still refused
 * by name. Every cell is live-verified.
 */
public class DropBehaviorTest extends BaseDatabaseTest {

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void everyDropTakesADropBehavior() {
        engine.execute("CREATE TABLE c1 (a INT)");
        engine.execute("CREATE TABLE c2 (a INT)");
        engine.execute("CREATE VIEW cv AS SELECT a FROM c1");
        engine.execute("CREATE TABLE c3 (a INT)");
        engine.execute("CREATE VIEW cv2 AS SELECT 1 AS x");
        engine.execute("CREATE SEQUENCE sq1");
        engine.execute("DROP VIEW cv CASCADE");
        engine.execute("DROP TABLE c1 CASCADE");
        engine.execute("DROP TABLE c2 RESTRICT");
        engine.execute("DROP TABLE IF EXISTS nothing_here CASCADE");
        engine.execute("DROP VIEW cv2 RESTRICT");
        engine.execute("DROP TABLE IF EXISTS c3 RESTRICT");
        engine.execute("DROP SEQUENCE sq1 CASCADE");
        engine.execute("DROP TABLE IF EXISTS x2 cascade");
        final String[] ifExists = {
            "DROP MATERIALIZED VIEW IF EXISTS mv1 CASCADE", "DROP STREAM IF EXISTS st1 CASCADE",
            "DROP FUNCTION IF EXISTS f1() CASCADE", "DROP TASK IF EXISTS tk1 CASCADE", "DROP STAGE IF EXISTS stg1 CASCADE",
            "DROP FILE FORMAT IF EXISTS ff1 CASCADE", "DROP DYNAMIC TABLE IF EXISTS dt1 CASCADE", "DROP TAG IF EXISTS tg1 CASCADE",
            "DROP PROCEDURE IF EXISTS p1() CASCADE", "DROP PIPE IF EXISTS pp1 CASCADE",
            "DROP WAREHOUSE IF EXISTS drop_behavior_wh CASCADE", "DROP ROLE IF EXISTS drop_behavior_role CASCADE",
            "DROP USER IF EXISTS drop_behavior_user CASCADE", "DROP MASKING POLICY IF EXISTS mp1 CASCADE",
            "DROP ROW ACCESS POLICY IF EXISTS rap1 CASCADE", "DROP CORTEX SEARCH SERVICE IF EXISTS css1 CASCADE",
            "DROP CONTACT IF EXISTS ct1 CASCADE", "DROP SCHEMA IF EXISTS nosch CASCADE",
            "DROP DATABASE IF EXISTS drop_behavior_nodb RESTRICT",
        };
        for (final String drop : ifExists) {
            engine.execute(drop);
        }
        assertEquals("0", answer("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'TEST_SCHEMA'"));
    }

    @Test
    public void bothBehaviorsOrAMissingObjectAreRefused() {
        engine.execute("CREATE TABLE c4 (a INT)");
        assertEquals("SQL compilation error:|syntax error line 1 at position 22 unexpected 'RESTRICT'.",
            answer("DROP TABLE c4 CASCADE RESTRICT"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 32 unexpected 'CASCADE'.",
            answer("DROP VIEW IF EXISTS x3 RESTRICT CASCADE"));
        assertEquals(hinted("SQL compilation error:|Table 'TEST_DB.TEST_SCHEMA.NOTHING2' does not exist or not authorized."),
            answer("DROP TABLE nothing2 CASCADE"));
    }
}
