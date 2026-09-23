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
 * The two conditional DDL status sentences, whose wording depends on what the catalog held BEFORE
 * the statement ran (live-verified across table, view, stage, schema and sequence — the wording is
 * kind-blind):
 *
 * <pre>
 *   CREATE … IF NOT EXISTS, object present   T2 already exists, statement succeeded.
 *   DROP … IF EXISTS, object absent          Drop statement executed successfully
 *                                            (NOSUCHTABLE already dropped).
 * </pre>
 *
 * <p>★ THE NAME IS THE WRITTEN IDENTIFIER CANONICALISED — the DROP form upper-cases even a name
 * that never existed, and a quoted one keeps its case — so it is not a lookup result. The happy
 * paths keep their ordinary sentences, and CREATE OR REPLACE over an existing object never says
 * "already exists".
 */
public class ConditionalDdlStatusTest extends BaseDatabaseTest {

    private String statusOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void createIfNotExistsFindingItsObjectSaysAlreadyExists() {
        statusOf("CREATE OR REPLACE TABLE cds_t2 (a INT)");
        assertEquals("CDS_T2 already exists, statement succeeded.",
            statusOf("CREATE TABLE IF NOT EXISTS cds_t2 (a INT)"));
        statusOf("DROP TABLE IF EXISTS cds_t2");
    }

    @Test
    public void dropIfExistsFindingNothingSaysAlreadyDropped() {
        assertEquals("Drop statement executed successfully (NOSUCHTABLE already dropped).",
            statusOf("DROP TABLE IF EXISTS nosuchtable"));
    }

    @Test
    public void theHappyPathsKeepTheirOrdinarySentences() {
        statusOf("DROP TABLE IF EXISTS cds_t3");
        assertEquals("Table CDS_T3 successfully created.",
            statusOf("CREATE TABLE IF NOT EXISTS cds_t3 (a INT)"));
        assertEquals("CDS_T3 successfully dropped.", statusOf("DROP TABLE IF EXISTS cds_t3"));
    }

    @Test
    public void createOrReplaceOverAnExistingObjectNeverSaysAlreadyExists() {
        statusOf("CREATE OR REPLACE TABLE cds_rep (a INT)");
        assertEquals("Table CDS_REP successfully created.",
            statusOf("CREATE OR REPLACE TABLE cds_rep (a INT)"));
        statusOf("DROP TABLE IF EXISTS cds_rep");
    }

    @Test
    public void theWordingIsKindBlind() {
        statusOf("CREATE OR REPLACE TABLE cds_base (a INT)");
        statusOf("CREATE OR REPLACE VIEW cds_v AS SELECT * FROM cds_base");
        assertEquals("CDS_V already exists, statement succeeded.",
            statusOf("CREATE VIEW IF NOT EXISTS cds_v AS SELECT * FROM cds_base"));
        assertEquals("Drop statement executed successfully (NOSUCHVIEW already dropped).",
            statusOf("DROP VIEW IF EXISTS nosuchview"));
        statusOf("CREATE OR REPLACE STAGE cds_stg");
        assertEquals("CDS_STG already exists, statement succeeded.",
            statusOf("CREATE STAGE IF NOT EXISTS cds_stg"));
        assertEquals("Drop statement executed successfully (NOSUCHSTAGE already dropped).",
            statusOf("DROP STAGE IF EXISTS nosuchstage"));
        assertEquals("TEST_SCHEMA already exists, statement succeeded.",
            statusOf("CREATE SCHEMA IF NOT EXISTS test_schema"));
        assertEquals("Drop statement executed successfully (NOSUCHSCHEMA already dropped).",
            statusOf("DROP SCHEMA IF EXISTS nosuchschema"));
        statusOf("CREATE SEQUENCE IF NOT EXISTS cds_sq");
        assertEquals("CDS_SQ already exists, statement succeeded.",
            statusOf("CREATE SEQUENCE IF NOT EXISTS cds_sq"));
        assertEquals("Drop statement executed successfully (NOSUCHSEQ already dropped).",
            statusOf("DROP SEQUENCE IF EXISTS nosuchseq"));
        statusOf("DROP VIEW IF EXISTS cds_v");
        statusOf("DROP TABLE IF EXISTS cds_base");
        statusOf("DROP STAGE IF EXISTS cds_stg");
        statusOf("DROP SEQUENCE IF EXISTS cds_sq");
    }

    @Test
    public void aQuotedNameKeepsItsCaseInBothSentences() {
        statusOf("CREATE OR REPLACE TABLE \"cds_lc\" (a INT)");
        assertEquals("cds_lc already exists, statement succeeded.",
            statusOf("CREATE TABLE IF NOT EXISTS \"cds_lc\" (a INT)"));
        assertEquals("Drop statement executed successfully (noSuchQuoted already dropped).",
            statusOf("DROP TABLE IF EXISTS \"noSuchQuoted\""));
        statusOf("DROP TABLE IF EXISTS \"cds_lc\"");
    }
}
