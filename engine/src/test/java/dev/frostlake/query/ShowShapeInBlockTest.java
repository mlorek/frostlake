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
 * What a SHOW statement's shape alone rules out is refused while the block that holds it compiles: before
 * any statement of the block runs, on a branch that is never taken, past every handler, with its place in
 * the block's own lines, and at CREATE for a procedure's body. A syntax fault of any SHOW comes first, then
 * the first other refusal as written, a DESCRIBE TAG among them; the declared types come before both and an
 * unnamed bind and the names after. What is looked up - a schema, a class, a model, an account's identifier
 * - is refused when the statement runs, as an uncaught exception, after the statements before it took
 * effect. A refused modifier keyword stacks the block's line. Every answer is live-verified.
 */
public class ShowShapeInBlockTest extends MeasuredCellsTestSupport {

    @Test
    public void theBlockIsRefusedBeforeItRuns() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 39 unexpected '('.",
                "BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (1); SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
                "SHOW TABLES IN USER LIMIT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 66 unexpected '('.",
                "BEGIN INSERT INTO ts VALUES (2); SHOW TABLES IN DATASET IDENTIFIER('y'); END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'.",
                "SHOW TABLES IN DATASET IDENTIFIER('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN INSERT INTO ts VALUES (3); SHOW TABLES IN ACCOUNT x; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW TABLES IN ACCOUNT x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (4); SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES LIMIT 0");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (5); SHOW TABLES IN SCHEMA nosuch_s; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'nosuch'.",
                "BEGIN INSERT INTO ts VALUES (6); SHOW TABLES WITH PRIVILEGES nosuch; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'nosuch'.",
                "SHOW TABLES WITH PRIVILEGES nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in TABLE'.",
                "BEGIN INSERT INTO ts VALUES (7); SHOW TABLES IN TABLE t1; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in TABLE'.",
                "SHOW TABLES IN TABLE t1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (8); SHOW TABLES IN DATASET LIMIT; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
                "SHOW TABLES IN DATASET LIMIT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 74 unexpected '('.",
                "BEGIN INSERT INTO ts VALUES (9); SHOW FUNCTIONS IN MATERIALIZED IDENTIFIER('y'); END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'IDENTIFIER'.",
                "SHOW FUNCTIONS IN MATERIALIZED IDENTIFIER('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (10); SHOW TABLES STARTS WITH; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
                "SHOW TABLES STARTS WITH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (11); SHOW TABLES LIMIT; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
                "SHOW TABLES LIMIT");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "BEGIN INSERT INTO ts VALUES (12); SHOW FLATTEN; END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'FLATTEN' does not exist or not authorized.",
                "SHOW FLATTEN");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (13); SHOW TABLES IN a.b.c; END;");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "BEGIN INSERT INTO ts VALUES (14); SHOW TABLES IN DATABASE a.b; END;");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN DATABASE a.b");
            assertAnswer(
                "REFUSED syntax error line 1 at position 49 unexpected 'nosuch'.",
                "BEGIN INSERT INTO ts VALUES (15); SHOW TABLES IN nosuch x; END;");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'.",
                "SHOW TABLES IN nosuch x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: Object type or Class 'NOSUCH' does not exist or not authorized.",
                "BEGIN INSERT INTO ts VALUES (16); SHOW FUNCTIONS IN nosuch x; END;");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH' does not exist or not authorized.",
                "SHOW FUNCTIONS IN nosuch x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (17); SHOW TABLES IN APPLICATION x; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN APPLICATION x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (18); SHOW TABLES IN CLASS x; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN CLASS x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'LIKE'.",
                "BEGIN INSERT INTO ts VALUES (19); SHOW TABLES LIKE 'x' LIKE 'y'; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'LIKE'.",
                "SHOW TABLES LIKE 'x' LIKE 'y'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 69 unexpected 'WITH'.",
                "BEGIN INSERT INTO ts VALUES (20); SHOW TABLES STARTS WITH 'a' STARTS WITH 'b'; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'STARTS'.",
                "SHOW TABLES STARTS WITH 'a' STARTS WITH 'b'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 60 unexpected '2'.",
                "BEGIN INSERT INTO ts VALUES (21); SHOW TABLES LIMIT 1 LIMIT 2; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'.",
                "SHOW TABLES LIMIT 1 LIMIT 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'x'. | syntax "
                    + "error line 1 at position 67 unexpected 'END'.",
                "BEGIN INSERT INTO ts VALUES (22); SHOW TERSE FUNCTIONS IN MODEL x; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
                "SHOW TERSE FUNCTIONS IN MODEL x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: account identifier 'A.B.C' has too many qualifiers",
                "BEGIN INSERT INTO ts VALUES (23); SHOW ROLES IN a.b.c; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW ROLES IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'x'. | syntax "
                    + "error line 1 at position 57 unexpected 'END'.",
                "BEGIN INSERT INTO ts VALUES (24); SHOW TABLES IN USER x; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
                "SHOW TABLES IN USER x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 84 unexpected 'END'.",
                "BEGIN INSERT INTO ts VALUES (25); SHOW SNOWFLAKE.ML.FORECAST WITH PRIVILEGES USAGE; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'PRIVILEGES'.",
                "SHOW SNOWFLAKE.ML.FORECAST WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW DATASETS ... WITH PRIVILEGES <>'.",
                "BEGIN INSERT INTO ts VALUES (26); SHOW DATASETS WITH PRIVILEGES USAGE; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW DATASETS ... WITH PRIVILEGES <>'.",
                "SHOW DATASETS WITH PRIVILEGES USAGE");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void everyShapeRefusalIsTheBlocksWithPlacesStackedLinesAndHandlers() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (101); SHOW TABLES STARTS; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
                "SHOW TABLES STARTS");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (102); SHOW TABLES WITH; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
                "SHOW TABLES WITH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'x'.",
                "BEGIN INSERT INTO ts VALUES (103); SHOW TABLES WITH x; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW TABLES WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 59 unexpected '1'.",
                "BEGIN INSERT INTO ts VALUES (104); SHOW PRIMARY KEYS LIMIT 1; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'LIMIT'.",
                "SHOW PRIMARY KEYS LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 53 unexpected 'WITH'.",
                "BEGIN INSERT INTO ts VALUES (105); SHOW LOCKS STARTS WITH 'A'; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 23 unexpected ''A''.",
                "SHOW LOCKS STARTS WITH 'A'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 69 unexpected 'END'.",
                "BEGIN INSERT INTO ts VALUES (106); SHOW TASKS WITH PRIVILEGES USAGE; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'PRIVILEGES'.",
                "SHOW TASKS WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'ROOT'. | syntax "
                    + "error line 1 at position 52 unexpected 'ONLY'.",
                "BEGIN INSERT INTO ts VALUES (107); SHOW TABLES ROOT ONLY; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'ROOT'.",
                "SHOW TABLES ROOT ONLY");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (108); SHOW TABLES WITH PRIVILEGES; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
                "SHOW TABLES WITH PRIVILEGES");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'STARTS'.",
                "BEGIN INSERT INTO ts VALUES (109); SHOW TABLES WITH PRIVILEGES USAGE STARTS WITH 'a'; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 46 unexpected ''a''.",
                "SHOW TABLES WITH PRIVILEGES USAGE STARTS WITH 'a'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 62 unexpected 'WITH'.",
                "BEGIN INSERT INTO ts VALUES (110); SHOW TABLES LIMIT 1 STARTS WITH 'a'; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'STARTS'.",
                "SHOW TABLES LIMIT 1 STARTS WITH 'a'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (111); SHOW TABLES IN APPLICATION PACKAGE; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '<EOF>'.",
                "SHOW TABLES IN APPLICATION PACKAGE");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
                "SHOW TABLES IN COMPUTE POOL");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "BEGIN INSERT INTO ts VALUES (113); SHOW TABLES WITH PRIVILEGES USAGE; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "SHOW TABLES WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type VIEW in "
                    + "INSTANCE'.",
                "BEGIN INSERT INTO ts VALUES (114); SHOW VIEWS IN nosuch x; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type VIEW in "
                    + "INSTANCE'.",
                "SHOW VIEWS IN nosuch x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (115); SHOW FUNCTIONS IN MODEL x; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW FUNCTIONS IN MODEL x");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "BEGIN INSERT INTO ts VALUES (116); SHOW COLUMNS IN SCHEMA a.b.c; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW COLUMNS IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for Y",
                "BEGIN INSERT INTO ts VALUES (117); SHOW TABLES IN ORGANIZATION y; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for Y",
                "SHOW TABLES IN ORGANIZATION y");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in PIPE'.",
                "BEGIN INSERT INTO ts VALUES (118); SHOW TABLES IN PIPE p; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in PIPE'.",
                "SHOW TABLES IN PIPE p");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                    + "DATABASE in DATABASE'.",
                "BEGIN INSERT INTO ts VALUES (119); SHOW DATABASES IN DATABASE x; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type "
                    + "DATABASE in DATABASE'.",
                "SHOW DATABASES IN DATABASE x");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type Table "
                    + "in Compute pool'.",
                "BEGIN INSERT INTO ts VALUES (120); SHOW TABLES IN COMPUTE POOL p; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type Table "
                    + "in Compute pool'.",
                "SHOW TABLES IN COMPUTE POOL p");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: account identifier 'A.B.C' has too many qualifiers",
                "BEGIN INSERT INTO ts VALUES (121); SHOW USERS IN a.b.c; END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: account identifier 'A.B.C' has too many qualifiers",
                "SHOW USERS IN a.b.c");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'INT'.",
                "BEGIN INSERT INTO ts VALUES (122); SHOW GRANTS ON FUNCTION f(x INT); END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'INT'.",
                "SHOW GRANTS ON FUNCTION f(x INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (123); SHOW GRANTS IN; END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
                "SHOW GRANTS IN");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (124); SHOW TABLES IN SCHEMA a.b.c; END;");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA a.b.c");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Application 'A' does not exist or not authorized. Your primary role <ROLE> must have "
                    + "USAGE or any other privilege granted on DATABASE A.",
                "BEGIN INSERT INTO ts VALUES (125); SHOW COLUMNS IN APPLICATION a; END;");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Application 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW COLUMNS IN APPLICATION a");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Account 'X' does not exist or not authorized. Your primary role <ROLE> must have at "
                    + "least one privilege granted on ACCOUNT <ACCOUNT>.",
                "BEGIN INSERT INTO ts VALUES (126); SHOW COMPUTE POOLS IN ACCOUNT x; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Account 'X' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
                "SHOW COMPUTE POOLS IN ACCOUNT x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ';'.",
                "BEGIN INSERT INTO ts VALUES (127); SHOW TABLES LIMIT 1 FROM; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
                "SHOW TABLES LIMIT 1 FROM");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (128); SHOW TABLES LIKE 'a' LIMIT 0; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES LIKE 'a' LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 71 unexpected 'WITH'.",
                "BEGIN INSERT INTO ts VALUES (129); SHOW VERSIONS IN NOTEBOOK nb STARTS WITH 'x'; END;");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'STARTS'.",
                "SHOW VERSIONS IN NOTEBOOK nb STARTS WITH 'x'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (130); SHOW TABLES IN DATASET; END;");
            assertAnswer(
                "6",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN DATASET");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (131); SHOW TABLES IN USER; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN USER");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (132); SHOW CLASSES LIMIT 0; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW CLASSES LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN INSERT INTO ts VALUES (133); SHOW DATASETS IN ACCOUNT x; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW DATASETS IN ACCOUNT x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (134); SHOW SNOWFLAKE.ML.FORECAST LIMIT 0; END;");
            assertAnswer(
                "7",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW SNOWFLAKE.ML.FORECAST LIMIT 0");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: Object type or Class 'NOSUCH_CLASS' does not exist or not authorized.",
                "BEGIN INSERT INTO ts VALUES (135); SHOW nosuch_class; END;");
            assertAnswer(
                "8",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'NOSUCH_CLASS' does not exist or not "
                    + "authorized.",
                "SHOW nosuch_class");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (136); SHOW DATASETS IN SCHEMA nosuch_s; END;");
            assertAnswer(
                "9",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATASETS IN SCHEMA nosuch_s");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Role 'NOSUCH_ROLE_X' does not exist or not authorized. Your primary role <ROLE> must "
                    + "have at least one privilege granted on ROLE NOSUCH_ROLE_X.",
                "BEGIN INSERT INTO ts VALUES (137); SHOW GRANTS TO ROLE nosuch_role_x; END;");
            assertAnswer(
                "10",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'NOSUCH_ROLE_X' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on ROLE NOSUCH_ROLE_X.",
                "SHOW GRANTS TO ROLE nosuch_role_x");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary role <ROLE> must "
                    + "have at least one privilege granted on TABLE NOSUCH_T.",
                "BEGIN INSERT INTO ts VALUES (138); SHOW PARAMETERS IN TABLE nosuch_t; END;");
            assertAnswer(
                "11",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
                "SHOW PARAMETERS IN TABLE nosuch_t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 56 unexpected '0'.",
                "BEGIN INSERT INTO ts VALUES (139); SHOW VARIABLES LIMIT 0; END;");
            assertAnswer(
                "11",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'LIMIT'.",
                "SHOW VARIABLES LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (140); SHOW WAREHOUSES LIMIT 0; END;");
            assertAnswer(
                "11",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW WAREHOUSES LIMIT 0");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary role <ROLE> must "
                    + "have at least one privilege granted on TABLE NOSUCH_T.",
                "BEGIN INSERT INTO ts VALUES (141); DESCRIBE TABLE nosuch_t; END;");
            assertAnswer(
                "12",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'NOSUCH_T' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
                "DESCRIBE TABLE nosuch_t");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN INSERT INTO ts VALUES (142); SHOW DATABASES IN ACCOUNT x; END;");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW DATABASES IN ACCOUNT x");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW ROLES IN a.b.c LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW FUNCTIONS IN MODEL x LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW USERS IN a.b.c LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES IN a.b.c LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES IN SCHEMA nosuch_s LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW FUNCTIONS IN nosuch x LIMIT 0");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "SHOW TABLES IN a.b.c WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'A' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have USAGE or any other privilege granted on DATABASE A.",
                "SHOW ROLES IN a.b.c LIMIT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 3 at position 27 unexpected ';'.",
                "BEGIN\n  INSERT INTO ts VALUES (1);\n  SHOW TABLES IN USER LIMIT;\nEND;");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 2 at position 25 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 2 at position 35 unexpected '('.",
                "BEGIN\n  SHOW TABLES IN DATASET IDENTIFIER('y');\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN\n  SHOW TABLES\n    IN ACCOUNT x;\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 3 at position 12 unexpected 'LIMIT'. | syntax "
                    + "error line 3 at position 18 unexpected '2'.",
                "BEGIN\n  SHOW TABLES\n    LIMIT 1 LIMIT 2;\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 2 at position 27 unexpected ';'.",
                "-- lead\nBEGIN\n  SHOW TABLES IN USER LIMIT;\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 39 unexpected '('.",
                "BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 41 unexpected 'WITH'.",
                "BEGIN SHOW TABLES STARTS WITH 'a' STARTS WITH 'b'; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 32 unexpected '2'.",
                "BEGIN SHOW TABLES LIMIT 1 LIMIT 2; RETURN 1; END;");
            assertAnswer(
                "REFUSED syntax error line 1 at position 21 unexpected 'nosuch'.",
                "BEGIN SHOW TABLES IN nosuch x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'nosuch'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES nosuch; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 56 unexpected 'RETURN'.",
                "BEGIN SHOW SNOWFLAKE.ML.FORECAST WITH PRIVILEGES USAGE; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 66 unexpected 'END'.",
                "BEGIN SHOW SNOWFLAKE.ML.FORECAST WITH PRIVILEGES USAGE; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 39 unexpected '('.",
                "BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 41 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET IDENTIFIER; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 33 unexpected 'END'.",
                "BEGIN SHOW TABLES LIMIT 1 LIMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW TABLES STARTS WITH 'a' STARTS; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 49 unexpected 'WITH'.",
                "BEGIN SHOW TABLES STARTS WITH 'a' LIMIT 1 STARTS WITH 'b'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ';'.",
                "BEGIN SHOW TABLES STARTS; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ';'.",
                "BEGIN SHOW TABLES WITH; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "BEGIN SHOW TABLES WITH x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LIMIT'. | syntax "
                    + "error line 1 at position 30 unexpected '1'.",
                "BEGIN SHOW PRIMARY KEYS LIMIT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 24 unexpected 'WITH'.",
                "BEGIN SHOW LOCKS STARTS WITH 'A'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 40 unexpected 'END'.",
                "BEGIN SHOW TASKS WITH PRIVILEGES USAGE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'ROOT'. | syntax "
                    + "error line 1 at position 23 unexpected 'ONLY'.",
                "BEGIN SHOW TABLES ROOT ONLY; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'STARTS'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES USAGE STARTS WITH 'a'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'STARTS'. | "
                    + "syntax error line 1 at position 33 unexpected 'WITH'.",
                "BEGIN SHOW TABLES LIMIT 1 STARTS WITH 'a'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'.",
                "BEGIN SHOW TABLES IN APPLICATION PACKAGE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'.",
                "BEGIN SHOW TABLES LIMIT 1 FROM; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
                "BEGIN SHOW GRANTS IN; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
                "BEGIN SHOW GRANTS IN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ';'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN USER LIMIT; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN ACCOUNT x; END IF; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN IF (FALSE) THEN SHOW TABLES LIMIT 0; END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 69 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET IDENTIFIER('y'); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN SHOW TABLES IN ACCOUNT x; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "caught",
                "BEGIN SHOW TABLES IN SCHEMA nosuch_s; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES LIMIT 0; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in TABLE'.",
                "BEGIN SHOW TABLES IN TABLE t1; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "caught",
                "BEGIN SHOW TABLES IN a.b.c; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "caught",
                "BEGIN SHOW ROLES IN a.b.c; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "caught",
                "BEGIN SHOW FUNCTIONS IN MODEL x; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type VIEW in "
                    + "INSTANCE'.",
                "BEGIN SHOW VIEWS IN nosuch x; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES USAGE; EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "EXECUTE IMMEDIATE 'BEGIN INSERT INTO ts VALUES (50); SHOW TABLES IN ACCOUNT x; END;'");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "CREATE OR REPLACE PROCEDURE p_show() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO ts "
                    + "VALUES (60); SHOW TABLES IN ACCOUNT x; RETURN 1; END;'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function P_SHOW.",
                "CALL p_show()");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected ';'.",
                "CREATE OR REPLACE PROCEDURE p_show2() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO ts "
                    + "VALUES (61); SHOW TABLES IN USER LIMIT; RETURN 1; END;'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function P_SHOW2.",
                "CALL p_show2()");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "CREATE OR REPLACE PROCEDURE p_show3() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO ts "
                    + "VALUES (62); SHOW TABLES LIMIT 0; RETURN 1; END;'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function P_SHOW3.",
                "CALL p_show3()");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN LET r RESULTSET := (SHOW TABLES IN ACCOUNT x); RETURN 1; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN LET r RESULTSET := (SHOW TABLES LIMIT 0); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ')'.",
                "BEGIN LET r RESULTSET := (SHOW TABLES IN USER LIMIT); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "BEGIN LET r RESULTSET := (SHOW TABLES IN SCHEMA nosuch_s); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "BEGIN INSERT INTO ts VALUES (70); BEGIN SHOW TABLES IN ACCOUNT x; END; END;");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ';'.",
                "BEGIN SHOW TABLES IN ACCOUNT x; SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; SHOW TABLES IN ACCOUNT x; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES LIMIT 0; SELECT nosuch_col FROM t1; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SELECT nosuch_col FROM t1; SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN RETURN nosuch_var; SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES LIMIT 0; RETURN nosuch_var; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; RETURN nosuch_var; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected ';'.",
                "BEGIN RETURN nosuch_var; SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN LET a INT := 1; LET a INT := 2; SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | Invalid number precision: 99. Must "
                    + "be between 0 and 38.",
                "DECLARE a NUMBER(99); BEGIN SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES LIMIT 0; DESCRIBE TAG x; END;");
            assertAnswer(
                "REFUSED Unsupported feature 'TAG'.",
                "BEGIN DESCRIBE TAG x; SHOW TABLES LIMIT 0; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theOrderAmongTheBlocksChecks() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE ts (a INT)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 |  Variable with name 'A' declared "
                    + "twice.",
                "DECLARE a INT; a INT; BEGIN SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SELECT ?; SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES LIMIT 0; SELECT ?; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; SELECT ?; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "DECLARE v VARCHAR DEFAULT 'x'; BEGIN INSERT INTO ts VALUES (1); SHOW TABLES IN DATABASE "
                    + "IDENTIFIER(:v) LIMIT 0; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 68 | Session variable '$NOSUCH_VAR' does "
                    + "not exist",
                "BEGIN INSERT INTO ts VALUES (3); SHOW TABLES IN DATABASE IDENTIFIER($nosuch_var) LIMIT 0; END;");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : page size \"0\" "
                    + "must be greater than 0 in limit clause",
                "BEGIN INSERT INTO ts VALUES (4); EXECUTE IMMEDIATE 'SHOW TABLES LIMIT 0'; END;");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : SQL compilation "
                    + "error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
                "BEGIN INSERT INTO ts VALUES (5); EXECUTE IMMEDIATE 'SHOW TABLES IN USER LIMIT'; END;");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM ts");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in TABLE'.",
                "CREATE OR REPLACE PROCEDURE p_s1() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN IF (FALSE) THEN SHOW "
                    + "TABLES IN TABLE t1; END IF; RETURN 1; END;'");
            assertAnswer(
                "Function P_S2 successfully created.",
                "CREATE OR REPLACE PROCEDURE p_s2() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TABLES IN SCHEMA "
                    + "nosuch_s; RETURN 1; END;'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Object does not exist, or operation cannot be performed.",
                "CALL p_s2()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 4 at position 12 unexpected 'LIMIT'. | syntax "
                    + "error line 4 at position 18 unexpected '2'.",
                "CREATE OR REPLACE PROCEDURE p_s3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  SHOW TABLES\n    "
                    + "LIMIT 1 LIMIT 2;\nEND;\n$$");
            assertAnswer(
                "REFUSED Unsupported feature 'TAG'.",
                "CREATE OR REPLACE PROCEDURE p_s4() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN DESCRIBE TAG x; RETURN "
                    + "1; END;'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'PRIVILEGES'. | "
                    + "syntax error line 1 at position 40 unexpected 'RETURN'.",
                "CREATE OR REPLACE PROCEDURE p_s5() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN SHOW TASKS WITH "
                    + "PRIVILEGES USAGE; RETURN 1; END;'");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW TABLES IN ACCOUNT x LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in TABLE'.",
                "SHOW TABLES IN TABLE t1 LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type VIEW in "
                    + "INSTANCE'.",
                "SHOW VIEWS IN nosuch x LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for C",
                "SHOW COLUMNS IN SCHEMA a.b.c LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for B",
                "SHOW TABLES IN DATABASE a.b LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW DATASETS IN ACCOUNT x LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for X",
                "SHOW TABLES IN ACCOUNT x WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW TABLES LIMIT 0 WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'LIMIT'.",
                "SHOW GRANTS IN LIMIT 0");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "SHOW GRANTS LIMIT 0");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
                "SHOW GRANTS LIKE 'a' IN");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
                "BEGIN SHOW GRANTS IN; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'.",
                "BEGIN SHOW TABLES IN APPLICATION PACKAGE; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported statement type 'Cannot show objects of type TABLE "
                    + "in FAILOVER GROUP'.",
                "BEGIN SHOW TABLES IN FAILOVER GROUP; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'LIMIT'.",
                "BEGIN LET r RESULTSET := (SHOW TABLES LIMIT 1 LIMIT 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'IDENTIFIER'.",
                "BEGIN LET r RESULTSET := (SHOW TABLES IN DATASET IDENTIFIER('y')); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'PRIVILEGES'.",
                "BEGIN LET r RESULTSET := (SHOW TASKS WITH PRIVILEGES USAGE); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'.",
                "BEGIN LET r RESULTSET := (SHOW TABLES STARTS); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ';'.",
                "BEGIN SHOW TABLES IN ACCOUNT x; DESCRIBE TAG x; SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ';'.",
                "BEGIN DESCRIBE TAG x; SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN SHOW TABLES IN SCHEMA nosuch_s; SHOW TABLES LIMIT 0; END;");
            assertAnswer(
                "REFUSED page size \"0\" must be greater than 0 in limit clause",
                "BEGIN BEGIN SHOW TABLES LIMIT 0; EXCEPTION WHEN OTHER THEN RETURN 1; END; EXCEPTION WHEN OTHER "
                    + "THEN RETURN 2; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
