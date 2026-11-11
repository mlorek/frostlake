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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The syntax-error places of the clauses of a task, a contact and a property's list value that the account reads
 * further than a clause's own words (live-verified): a list inside a property's list reads a string as a property's
 * name and wants its '='; the word after EXECUTE AS USER is the user's name and the bracket after it is refused, alone;
 * a WITH TAG or a WITH CONTACT after another clause is refused at the WITH alone, and a TAG or a CONTACT after a
 * property's value at the word; a contact's list in a SET wants its '=' and a contact, and nothing after it; an
 * EXECUTE TASK's word after a value is the next property's name and wants its '=', and a USING or a RETRY at the end
 * is refused at the end of the input.
 */
public class TaskClauseSyntaxPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void theTaskClausesSyntaxPlaces() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK tk2073 SCHEDULE = '1 MINUTE' QUERY_TAG = (('a')) AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected ')'."},
            {"CREATE OR REPLACE TASK tk2073 SCHEDULE = '1 MINUTE' QUERY_TAG = (('a') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected ')'. | syntax "
                + "error line 1 at position 71 unexpected 'AS'."},
            {"ALTER TASK t SET CONTACT (SUPPORT = c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'."},
            {"ALTER TABLE ct204 SET CONTACT (SUPPORT = c204)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '<EOF>'."},
            {"CREATE OR REPLACE TASK tk2073 EXECUTE AS USER IDENTIFIER(CURRENT_USER()) AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected '('."},
            {"CREATE OR REPLACE TASK tk2073 EXECUTE AS USER CURRENT_USER() AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '('."},
            {"CREATE OR REPLACE TASK tk2073 WITH CONTACT (SUPPORT = c) WITH TAG (tg = 'x') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'WITH'."},
            {"CREATE OR REPLACE TASK tk2073 SCHEDULE = '1 MINUTE' WITH TAG (tg = 'x') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'WITH'."},
            {"CREATE OR REPLACE TASK tk2073 COMMENT = 'x' TAG (tg = 'x') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'TAG'."},
            {"CREATE OR REPLACE TASK tk2073 COMMENT = 'x' WITH TAG (tg = 'x') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'WITH'."},
            {"CREATE OR REPLACE TASK tk2073 COMMENT = 'x' CONTACT (SUPPORT = c) AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'CONTACT'."},
            {"CREATE OR REPLACE TASK tk2073 COMMENT = 'x' WITH CONTACT (SUPPORT = c) AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'WITH'."},
            {"EXECUTE TASK r USING CONFIG = '{}' FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'."},
            {"EXECUTE TASK r USING",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'."},
            {"EXECUTE TASK r USING CONFIG = '{}' RETRY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '<EOF>'."},
            {"EXECUTE TASK r USING CONFIG = '{}',",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '<EOF>'."},
            {"EXECUTE TASK r RETRY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'."},
            {"ALTER TASK tk788 SET FOO (a) = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax "
                + "error line 1 at position 29 unexpected '='."},
            {"ALTER TASK tk788 SET FOO ('a') = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax "
                + "error line 1 at position 31 unexpected '='."},
            {"ALTER TASK tk788 SET FOO (a) = 'x', COMMENT = 'y'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax "
                + "error line 1 at position 29 unexpected '='."},
            {"ALTER TASK t SET CONTACT (SUPPORT = c) SUPPORT = d",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'SUPPORT'."},
            {"ALTER TASK t SET CONTACT ( )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"ALTER TASK t SET CONTACT (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'."},
            {"ALTER TABLE ct204 SET CONTACT (SUPPORT = c204) COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'COMMENT'."},
            {"ALTER TASK tk788 SET QUERY_TAG = (('a' 'b'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ''b''."},
            {"ALTER TASK tk788 SET QUERY_TAG = (('a'), ('b'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax "
                + "error line 1 at position 45 unexpected ')'."},
            {"ALTER SESSION SET QUERY_TAG = (('a'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'."},
            {"ALTER SESSION SET FOO (a) = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax "
                + "error line 1 at position 26 unexpected '='."},
        });
    }

    @Test
    public void theShapesBesideThem() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TASK tk903 EXECUTE AS USER nosuch_u903 AS SELECT 1",
                "REFUSED SQL compilation error: | User 'NOSUCH_U903' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK tk903 EXECUTE AS USER IDENTIFIER('nosuch_u903') AS SELECT 1",
                "REFUSED SQL compilation error: | User 'NOSUCH_U903' does not exist or not authorized."},
            {"CREATE OR REPLACE TASK tk903 EXECUTE AS USER IDENTIFIER AS SELECT 1",
                "REFUSED SQL compilation error: | User 'IDENTIFIER' does not exist or not authorized."},
            {"EXECUTE TASK nosuch903 USING CONFIG = '{}'",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH903' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH903."},
            {"EXECUTE TASK nosuch903 RETRY LAST",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH903' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH903."},
            {"EXECUTE TASK nosuch903 USING CONFIG = '{}' FOO BAR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'BAR'."},
            {"EXECUTE TASK nosuch903 USING CONFIG = '{}' RETRY LAST",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'LAST'."},
            {"EXECUTE TASK nosuch903 USING CONFIG = '{}', FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"EXECUTE TASK nosuch903 USING FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'."},
            {"ALTER TASK nosuch903 SET QUERY_TAG = ((a = 'x'))",
                "REFUSED SQL compilation error: | Task 'TEST_DB.TEST_SCHEMA.NOSUCH903' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TASK "
                + "TEST_DB.TEST_SCHEMA.NOSUCH903."},
            {"ALTER TASK nosuch903 SET QUERY_TAG = (('a', 'b'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax "
                + "error line 1 at position 47 unexpected ')'."},
            {"CREATE OR REPLACE TASK tk903 WITH TAG (tg = 'x') WITH CONTACT (SUPPORT = c) AS SELECT 1",
                "REFUSED SQL compilation error: | Tag 'TG' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG TG."},
            {"CREATE OR REPLACE TASK tk903 SCHEDULE = '1 MINUTE' TAG (tg = 'x') AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'TAG'."},
            {"CREATE OR REPLACE TASK tk903 COMMENT = 'x' TAG AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'TAG'."},
            {"CREATE OR REPLACE TASK tk903 COMMENT = 'x' WITH AS SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'WITH'."},
            {"ALTER TASK nosuch903 SET CONTACT (SUPPORT = c) COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'COMMENT'."},
            {"ALTER TABLE nosuch903 SET CONTACT (SUPPORT = c) = d COMMENT = 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'COMMENT'."},
        });
    }
}
