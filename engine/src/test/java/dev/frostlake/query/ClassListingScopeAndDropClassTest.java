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
 * The scopes of SHOW DATASETS, SHOW CLASSES and SHOW of a class, and DROP CLASS. A table, a view and a class hold
 * nothing these statements list — Cannot show objects of type DATASET in TABLE, the noun DATASET, CLASS or
 * INSTANCE, with the scope's name or without it — an instance listing looks a class scope up, and an application
 * is looked up and missed, the datasets' listing naming nothing it misses. A class that does not exist is told
 * before its scope is read, and a class that exists has its scope judged before the page size. SHOW CLASS is
 * refused at the word; DROP CLASS reads a name, a signature and CASCADE or RESTRICT, and misses the class by its
 * full path. A database named by a path asks the key listings for a full search path and names nothing to
 * SHOW DATABASE ROLES (live-verified).
 */
public class ClassListingScopeAndDropClassTest extends MeasuredCellsTestSupport {

    @Test
    public void datasetsInAKindOfObject() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "VIEW'.",
            "SHOW DATASETS IN VIEW t1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "CLASS'.",
            "SHOW DATASETS IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE t1 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS LIKE 'x' IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "SHOW DATASETS IN TABLE t1 x");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "CLASS'.",
            "SHOW DATASETS IN CLASS a.b");
    }

    @Test
    public void classesInAKindOfObject() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "TABLE'.",
            "SHOW CLASSES IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "TABLE'.",
            "SHOW CLASSES IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "VIEW'.",
            "SHOW CLASSES IN VIEW t1");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_ZQ7.",
            "SHOW CLASSES IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_ZQ7.",
            "SHOW CLASSES IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "CLASS'.",
            "SHOW CLASSES IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.CLASS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.CLASS.",
            "SHOW CLASSES IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.APPLICATION' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.APPLICATION.",
            "SHOW CLASSES IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "TABLE'.",
            "SHOW CLASSES IN TABLE t1 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "TABLE'.",
            "SHOW CLASSES LIKE 'x' IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SHOW CLASSES IN TABLE t1 x");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "CLASS'.",
            "SHOW CLASSES IN CLASS a.b");
    }

    @Test
    public void instancesOfAClassInAKindOfObject() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "TABLE'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "TABLE'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "VIEW'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN VIEW t1");
        assertShape(
            "REFUSED SQL compilation error: | Application 'NOSUCH_APP_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_APP_ZQ7.",
            "SHOW SNOWFLAKE.ML.FORECAST IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Application package 'NOSUCH_PKG_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on DATABASE "
                + "NOSUCH_PKG_ZQ7.",
            "SHOW SNOWFLAKE.ML.FORECAST IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLS_ZQ7' does not exist or not "
                + "authorized.",
            "SHOW SNOWFLAKE.ML.FORECAST IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.CLASS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.CLASS.",
            "SHOW SNOWFLAKE.ML.FORECAST IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.APPLICATION' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.APPLICATION.",
            "SHOW SNOWFLAKE.ML.FORECAST IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "TABLE'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN TABLE t1 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "TABLE'.",
            "SHOW SNOWFLAKE.ML.FORECAST LIKE 'x' IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN TABLE t1 x");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.A' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.A.",
            "SHOW SNOWFLAKE.ML.FORECAST IN CLASS a.b");
    }

    @Test
    public void aMissingClassIsToldBeforeItsScope() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN TABLE");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN VIEW t1");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN APPLICATION nosuch_app_zq7");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN APPLICATION PACKAGE nosuch_pkg_zq7");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN CLASS nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN APPLICATION");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN TABLE t1 LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 LIKE 'x' IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "SHOW NOSUCHCLS_ZQ7 IN TABLE t1 x");
        assertShape(
            "REFUSED SQL compilation error: Object type or Class 'NOSUCHCLS_ZQ7' does not exist or not authorized.",
            "SHOW NOSUCHCLS_ZQ7 IN CLASS a.b");
    }

    @Test
    public void showClassAndDropClass() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'CLASS'.",
            "SHOW CLASS");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.NOSUCH_CLS_ZQ7' does not "
                + "exist or not authorized.",
            "DROP CLASS nosuch_cls_zq7");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_CLS_ZQ7 already dropped).",
            "DROP CLASS IF EXISTS nosuch_cls_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP CLASS nosuch_a.nosuch_b.nosuch_c.nosuch_d");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "DROP CLASS nosuch_s_zq7.nosuch_cls_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
            "DROP CLASS");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.\"x\"' does not exist or "
                + "not authorized.",
            "DROP CLASS \"x\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "DROP CLASS x y");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.X' does not exist or not "
                + "authorized.",
            "DROP CLASS x (a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "DROP CLASS IF EXISTS");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.NOSUCH_CLS_ZQ7' does not "
                + "exist or not authorized.",
            "DROP CLASS test_schema.nosuch_cls_zq7");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.NOSUCH_CLS_ZQ7' does not "
                + "exist or not authorized.",
            "DROP CLASS test_db.test_schema.nosuch_cls_zq7");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IN'.",
            "SHOW CLASSES STARTS WITH 'A' IN DATABASE SNOWFLAKE");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IN'.",
            "SHOW DATASETS STARTS WITH 'A' IN DATABASE test_db");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'IN'.",
            "SHOW SNOWFLAKE.ML.FORECAST STARTS WITH 'A' IN DATABASE test_db");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.X' does not exist or not "
                + "authorized.",
            "DROP CLASS x CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.X' does not exist or not "
                + "authorized.",
            "DROP CLASS x RESTRICT");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "DROP CLASS nosuch_db_zq7.s.c");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S_ZQ7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_S_ZQ7.",
            "DROP CLASS IF EXISTS nosuch_s_zq7.c");
        assertAnswer(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "DROP CLASS IF EXISTS a.b.c.d");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.NOSUCH_CLS_ZQ7' does not "
                + "exist or not authorized.",
            "DROP CLASS IDENTIFIER('nosuch_cls_zq7')");
        assertAnswer(
            "REFUSED SQL compilation error: Object type or Class 'TEST_DB.TEST_SCHEMA.X' does not exist or not "
                + "authorized.",
            "DROP CLASS x ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'.",
            "DROP CLASS x (1)");
        assertAnswer(
            "Drop statement executed successfully (X already dropped).",
            "DROP CLASS IF EXISTS x (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'NOT'.",
            "DROP CLASS IF NOT EXISTS x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'CLASS'.",
            "SHOW CLASS x");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'CLASS'.",
            "SHOW CLASS LIKE 'a'");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'CLASS'.",
            "SHOW TERSE CLASS");
        assertShape(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.CLASS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.CLASS.",
            "SHOW CLASS.x");
    }

    @Test
    public void moreScopesAndTheOrderTheyAreJudgedIn() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "VIEW'.",
            "SHOW DATASETS IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "VIEW'.",
            "SHOW CLASSES IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "VIEW'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN VIEW");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'.",
            "SHOW CLASSES IN APPLICATION PACKAGE");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW CLASSES IN APPLICATION a.b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SNOWFLAKE.ML.FORECAST IN APPLICATION a.b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN APPLICATION a.b");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "SHOW DATASETS IN APPLICATION PACKAGE");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW SNOWFLAKE.ML.FORECAST IN CLASS a.b.c.d");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW SNOWFLAKE.ML.FORECAST IN CLASS nosuch_db_zq7.s.c");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE t1 LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type INSTANCE in "
                + "TABLE'.",
            "SHOW SNOWFLAKE.ML.FORECAST IN TABLE t1 LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "CLASS'.",
            "SHOW CLASSES IN CLASS x LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE t1 WITH PRIVILEGES USAGE");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW TERSE DATASETS IN TABLE t1");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE a.b.c.d");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type CLASS in "
                + "VIEW'.",
            "SHOW CLASSES IN VIEW a.b.c.d");
        assertShape(
            "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type DATASET in "
                + "TABLE'.",
            "SHOW DATASETS IN TABLE IDENTIFIER('t1')");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATASETS IN CLASS");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'.",
            "SHOW DATASETS IN APPLICATION PACKAGE x y");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
            "SHOW DATASETS IN CLASS x y");
    }

    @Test
    public void aDatabaseNamedByAPath() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW PRIMARY KEYS IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATABASE ROLES IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW IMPORTED KEYS IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW UNIQUE KEYS IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW EXPORTED KEYS IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
            "SHOW PRIMARY KEYS IN DATABASE a.b.c");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATABASE ROLES IN DATABASE a.b.c");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW DATABASE ROLES IN DATABASE nosuch_db_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Database 'NOSUCH_DB_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB_ZQ7.",
            "SHOW PRIMARY KEYS IN DATABASE nosuch_db_zq7");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW PRIMARY KEYS IN DATABASE \"a\".b");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'LIMIT'.",
            "SHOW PRIMARY KEYS IN DATABASE a.b LIMIT 1");
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        answer(
            "CREATE OR REPLACE VIEW v1 AS SELECT 1 AS a");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for b",
            "SHOW PRIMARY KEYS IN DATABASE a.\"b\"");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA",
            "SHOW PRIMARY KEYS IN DATABASE test_db.test_schema");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
            "SHOW TERSE PRIMARY KEYS IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATABASE ROLES IN a.b");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATABASE ROLES IN DATABASE a.b LIMIT 1");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW DATABASE ROLES LIKE 'x' IN DATABASE a.b");
        assertShape(
            "REFUSED SQL compilation error: | Must specify the full search path starting from database for D",
            "SHOW IMPORTED KEYS IN DATABASE a.b.c.d");
        assertShape(
            "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
            "SHOW TERSE DATABASE ROLES IN DATABASE a.b");
        assertShape(
            "REFUSED page size \"0\" must be greater than 0 in limit clause",
            "SHOW DATABASE ROLES IN DATABASE a.b LIMIT 0");
        assertShape(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "SHOW PRIMARY KEYS IN DATABASE a.");
    }
}
