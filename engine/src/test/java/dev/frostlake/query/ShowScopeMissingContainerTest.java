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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A SHOW whose scope names a schema or a database that does not exist, kind by kind and in every scope form:
 * IN SCHEMA of a bare and of a qualified name, IN DATABASE, the bare IN, with TERSE and with LIKE. The table
 * kinds, views, objects, stages, file formats, network rules, notebooks, Streamlit apps and models answer the
 * sentence that names nothing, and so does SCHEMAS IN DATABASE; every other kind names the missing schema or
 * database, and the column and key listings ask for a full path. A scope that exists lists nothing. Every
 * answer is live-verified.
 */
public class ShowScopeMissingContainerTest extends MeasuredCellsTestSupport {

    @Test
    public void aMissingScopeIsAnsweredByItsKind() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW VIEWS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW PIPES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PIPES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PIPES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW STREAMS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW STREAMS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW STREAMS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW TASKS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TASKS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TASKS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW SEQUENCES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SEQUENCES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SEQUENCES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW TAGS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TAGS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TAGS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW MASKING POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW MASKING POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW MASKING POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW ROW ACCESS POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ROW ACCESS POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ROW ACCESS POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW CORTEX SEARCH SERVICES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW CORTEX SEARCH SERVICES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW CORTEX SEARCH SERVICES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW PROCEDURES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PROCEDURES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PROCEDURES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW FUNCTIONS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW FUNCTIONS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW FUNCTIONS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW USER FUNCTIONS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW USER FUNCTIONS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW USER FUNCTIONS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_S",
            "SHOW COLUMNS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW COLUMNS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW COLUMNS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW SECRETS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SECRETS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SECRETS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW PASSWORD POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PASSWORD POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PASSWORD POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW ALERTS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ALERTS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ALERTS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EVENT TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EVENT TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EVENT TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW ICEBERG TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW ICEBERG TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW ICEBERG TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW MATERIALIZED VIEWS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW MATERIALIZED VIEWS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW MATERIALIZED VIEWS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW OBJECTS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW OBJECTS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW OBJECTS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW IMAGE REPOSITORIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW IMAGE REPOSITORIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW IMAGE REPOSITORIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW SERVICES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SERVICES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SERVICES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STREAMLITS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STREAMLITS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STREAMLITS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW PROJECTION POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PROJECTION POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PROJECTION POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW AGGREGATION POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW AGGREGATION POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW AGGREGATION POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW JOIN POLICIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW JOIN POLICIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW JOIN POLICIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW HYBRID TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW HYBRID TABLES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW HYBRID TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_S",
            "SHOW PRIMARY KEYS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PRIMARY KEYS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW PRIMARY KEYS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_S",
            "SHOW UNIQUE KEYS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW UNIQUE KEYS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW UNIQUE KEYS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH_S",
            "SHOW IMPORTED KEYS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW IMPORTED KEYS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW IMPORTED KEYS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW DATABASE ROLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SCHEMAS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW CONTACTS IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW CONTACTS IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW CONTACTS IN DATABASE nosuch_db_g7");
    }

    @Test
    public void theBareScopeTerseAndLikeFormsFollowTheKind() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW ARTIFACT REPOSITORIES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ARTIFACT REPOSITORIES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ARTIFACT REPOSITORIES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW JOB SERVICES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW JOB SERVICES IN SCHEMA nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW JOB SERVICES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STREAMLITS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STREAMLITS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW ALERTS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ALERTS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW FILE FORMATS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DYNAMIC TABLES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW IMAGE REPOSITORIES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW IMAGE REPOSITORIES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW SERVICES IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW SERVICES IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW STREAMS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW STREAMS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "SHOW TASKS IN nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TASKS IN nosuch_db_g7.nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TERSE TASKS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW TERSE STREAMS IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TERSE EXTERNAL TABLES IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TERSE DYNAMIC TABLES IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NETWORK RULES LIKE 'x%' IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW MODELS LIKE 'x' IN SCHEMA nosuch_s");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_G7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_G7.",
            "SHOW ALERTS LIKE 'x' IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW STAGES LIKE 'x' IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW EXTERNAL TABLES LIKE 'x' IN DATABASE nosuch_db_g7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW NOTEBOOKS LIKE 'x' IN SCHEMA nosuch_s");
        assertAnswer(
            "",
            "SHOW NETWORK RULES IN SCHEMA test_schema");
        assertAnswer(
            "",
            "SHOW ALERTS IN SCHEMA test_schema");
        assertAnswer(
            "",
            "SHOW ALERTS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW ALERTS IN SCHEMA test_db.test_schema");
        assertAnswer(
            "",
            "SHOW EXTERNAL TABLES IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW EXTERNAL TABLES IN SCHEMA test_schema");
        assertAnswer(
            "",
            "SHOW MODELS IN SCHEMA test_schema");
        assertAnswer(
            "",
            "SHOW MODELS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW NOTEBOOKS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW STREAMLITS IN SCHEMA test_db.test_schema");
        assertAnswer(
            "",
            "SHOW IMAGE REPOSITORIES IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW SERVICES IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW STAGES IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW FILE FORMATS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW DYNAMIC TABLES IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW STREAMS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW TASKS IN DATABASE test_db");
        assertAnswer(
            "",
            "SHOW NETWORK RULES IN DATABASE test_db");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type MODEL in "
                + "TABLE'.",
            "SHOW MODELS IN TABLE t");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type EXTERNAL "
                + "TABLE in TABLE'.",
            "SHOW EXTERNAL TABLES IN TABLE t");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type ALERT in "
                + "TABLE'.",
            "SHOW ALERTS IN TABLE t");
    }
}
