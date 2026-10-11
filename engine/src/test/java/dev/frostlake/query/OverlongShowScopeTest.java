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
 * A SHOW scope whose name has more parts than the container it names. A schema is a database and a schema at
 * most: IN a.b.c and IN SCHEMA a.b.c are answered by the sentence that names nothing by every schema-level
 * listing, once a LIMIT 0 and a WITH PRIVILEGES are judged; the column and key listings read a table's path
 * after a bare IN, and after IN SCHEMA ask for a full search path. A database is one part: IN DATABASE a.b asks
 * for a full search path, naming the last part, before a LIMIT 0 is judged. The listings of the account's own
 * things refuse three parts as an account identifier with too many qualifiers. Every answer is live-verified.
 */
public class OverlongShowScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aSchemaIsTwoPartsAtMost() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW ROLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW VIEWS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW COLUMNS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW USER FUNCTIONS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STAGES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TASKS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PIPES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TAGS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FILE FORMATS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW MASKING POLICIES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DYNAMIC TABLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW ALERTS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SECRETS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW OBJECTS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SCHEMAS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW EXTERNAL TABLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW MATERIALIZED VIEWS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW NETWORK RULES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C.D' has too many qualifiers",
                "SHOW ROLES IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier '\"a\".\"b\".\"c\"' has too many qualifiers",
                "SHOW ROLES IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW ROLES IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type ROLE in "
                    + "SCHEMA'.",
                "SHOW ROLES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW COLUMNS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Database '\"a\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE \"a\".",
                "SHOW COLUMNS IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW COLUMNS IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW COLUMNS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STAGES IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STAGES IN \"a\".\"b\".\"c\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STAGES IN IDENTIFIER('a.b.c')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STAGES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN test_schema.t1.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN test_schema.t1.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN test_schema.t1.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN test_db.test_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN test_db.test_schema.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PROCEDURES IN SNOWFLAKE.ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN SNOWFLAKE.CORE.BUDGET");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SEQUENCES IN SNOWFLAKE.ML.FORECAST");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW ROLES IN a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW ROLES IN a");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW ROLES IN ACCOUNT a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW FUNCTIONS IN a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. "
                    + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_S.",
                "SHOW FUNCTIONS IN test_db.nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW SEQUENCES IN a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW FUNCTIONS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW SEQUENCES IN DATABASE a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN IDENTIFIER('a.b.c.d')");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW FUNCTIONS IN a.b.c LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS LIKE 'x' IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TERSE FUNCTIONS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW BUILTIN FUNCTIONS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW USER PROCEDURES IN a.b.c");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aDatabaseIsOnePart() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW STREAMS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TASKS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW PIPES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TAGS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW STAGES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW VIEWS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW PROCEDURES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW COLUMNS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW SCHEMAS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW OBJECTS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW ALERTS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW SECRETS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW MASKING POLICIES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW DYNAMIC TABLES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW FILE FORMATS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW NETWORK RULES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW EXTERNAL TABLES IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW USER FUNCTIONS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW PRIMARY KEYS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW PRIMARY KEYS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW PRIMARY KEYS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW UNIQUE KEYS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW UNIQUE KEYS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW UNIQUE KEYS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW IMPORTED KEYS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW IMPORTED KEYS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW IMPORTED KEYS IN a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW ROLES IN ACCOUNT a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW ROLES IN ACCOUNT a");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW ROLES IN DATABASE a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW USERS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW WAREHOUSES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW DATABASES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN DATABASE a.b LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Database '\"a.b\"' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"a.b\".",
                "SHOW FUNCTIONS IN DATABASE \"a.b\"");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW TABLES IN DATABASE a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN ACCOUNT a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW FUNCTIONS IN ACCOUNT a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN DATABASE IDENTIFIER('a.b')");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW FUNCTIONS ... WITH PRIVILEGES <>'.",
                "SHOW FUNCTIONS IN a.b.c WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "SHOW TABLES IN a.b.c WITH PRIVILEGES SELECT");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW SEQUENCES ... WITH PRIVILEGES <>'.",
                "SHOW SEQUENCES IN a.b.c WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW COLUMNS IN SCHEMA a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for D",
                "SHOW COLUMNS IN SCHEMA a.b.c.d");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW COLUMNS IN DATABASE a.b");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TERSE TABLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES LIKE 'x' IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN test_db.test_schema.t1");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW STREAMS IN test_db.test_schema.t1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
