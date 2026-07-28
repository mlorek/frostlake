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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CTAS with table options BETWEEN the typed column list and AS — the Snowflake DDL shape
 * {@code CREATE TABLE t (col defs...) CLUSTER BY (c) AS SELECT ...} used by backfill migrations
 * (options were only accepted before the column list or after the whole statement).
 */
public class CtasWithClusterByTest extends BaseDatabaseTest {

    @Test
    public void clusterByBetweenColumnListAndAsSelect() {
        engine.execute("CREATE TABLE src_rows (k NUMBER(38,0), label VARCHAR)");
        engine.execute("INSERT INTO src_rows VALUES (1, 'one'), (2, 'two')");
        engine.execute("""
            CREATE OR REPLACE TABLE ctas_clustered (
                k     NUMBER(38,0)      NOT NULL,
                label VARCHAR(16777216) NULL COLLATE 'en-ci',
                CONSTRAINT pk_ctas_clustered PRIMARY KEY (k)
            )
            CLUSTER BY (k)
            AS
            SELECT k, label FROM src_rows
            """);
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM ctas_clustered");
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void commentAndClusterByBetweenColumnListAndAsSelect() {
        engine.execute("CREATE TABLE src_rows2 (k NUMBER(38,0))");
        engine.execute("INSERT INTO src_rows2 VALUES (7)");
        engine.execute("""
            CREATE OR REPLACE TABLE ctas_commented (k NUMBER(38,0))
            COMMENT = 'built by ctas'
            CLUSTER BY (k)
            AS SELECT k FROM src_rows2
            """);
        final ResultSet rs = engine.executeQuery("SELECT k FROM ctas_commented");
        assertEquals(7, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }
}
