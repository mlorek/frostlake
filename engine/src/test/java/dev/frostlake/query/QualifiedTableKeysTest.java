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
 * SHOW PRIMARY KEYS, SHOW UNIQUE KEYS and SHOW IMPORTED KEYS over a table named with its database and
 * schema list it under those, whatever schema the session is in. Every answer is live-verified.
 */
public class QualifiedTableKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableNamedInFullIsListedUnderItsOwnSchema() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE flq_k1");
            engine.execute("CREATE OR REPLACE SCHEMA flq_k1.s1");
            engine.execute("CREATE OR REPLACE TABLE flq_k1.s1.keyed (a INT PRIMARY KEY, b INT UNIQUE)");
            engine.execute("CREATE OR REPLACE TABLE flq_k1.s1.referring (a INT REFERENCES flq_k1.s1.keyed (a))");
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("SHOW PRIMARY KEYS IN TABLE flq_k1.s1.keyed");
            assertAnswer(
                "FLQ_K1|S1|KEYED|A",
                "SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute("SHOW UNIQUE KEYS IN TABLE flq_k1.s1.keyed");
            assertAnswer(
                "FLQ_K1|S1|KEYED|B",
                "SELECT \"database_name\", \"schema_name\", \"table_name\", \"column_name\" FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute("SHOW IMPORTED KEYS IN TABLE flq_k1.s1.referring");
            assertAnswer(
                "FLQ_K1|S1|KEYED|FLQ_K1|S1|REFERRING",
                "SELECT \"pk_database_name\", \"pk_schema_name\", \"pk_table_name\", \"fk_database_name\", "
                + "\"fk_schema_name\", \"fk_table_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            engine.execute("SHOW PRIMARY KEYS IN SCHEMA flq_k1.s1");
            assertAnswer(
                "FLQ_K1|S1|KEYED",
                "SELECT \"database_name\", \"schema_name\", \"table_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS flq_k1");
        }
    }
}
