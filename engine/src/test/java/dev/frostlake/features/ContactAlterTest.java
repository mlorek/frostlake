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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ALTER CONTACT and COMMENT ON CONTACT (live-verified). SET writes COMMENT, URL and EMAIL_DISTRIBUTION_LIST as CREATE
 * does, a comma between two or none, and USERS a list of existing users; UNSET clears them by name, commas between;
 * RENAME TO renames the contact, into another schema too, onto no name a contact holds - its own included. The names
 * and values are judged before the contact is looked for, so IF EXISTS forgives only its absence. A URL is one with a
 * scheme, and a contact is reached by its URL or by its address, not both. Its attachments follow it through a rename
 * and leave it when another of its name replaces it.
 */
public class ContactAlterTest extends MeasuredCellsTestSupport {

    /** Each form of ALTER CONTACT and COMMENT ON CONTACT. */
    @Test
    public void setUnsetRenameAndComment() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT cn808 EMAIL_DISTRIBUTION_LIST = 'n808@example.com'",
                "Contact CN808 successfully created."},
            {"ALTER CONTACT cn808 SET EMAIL_DISTRIBUTION_LIST = 'm808@example.com'",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 SET URL = 'https://example.com/808'",
                "REFUSED Email, url or users/email_list, only one could be set."},
            {"ALTER CONTACT cn808 SET USERS = ('NOSUCH808')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH808' for property 'USER'"},
            {"ALTER CONTACT cn808 SET COMMENT = 'c808'",
                "Statement executed successfully."},
            {"COMMENT ON CONTACT cn808 IS 'd808'",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 UNSET COMMENT",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 RENAME TO cn808b",
                "Statement executed successfully."},
            {"ALTER CONTACT IF EXISTS nosuch808 RENAME TO x808",
                "Statement executed successfully."},
            {"ALTER CONTACT nosuch808 RENAME TO x808",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.NOSUCH808' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH808."},
            {"ALTER CONTACT cn808b RENAME TO test_db.public.cn808c",
                "Statement executed successfully."},
            {"ALTER CONTACT test_db.public.cn808c RENAME TO cn808",
                "Statement executed successfully."},
            {"COMMENT ON CONTACT nosuch808 IS 'x'",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.NOSUCH808' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH808."},
            {"COMMENT IF EXISTS ON CONTACT nosuch808 IS 'x'",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 SET EMAIL_DISTRIBUTION_LIST = 'bad'",
                "REFUSED Invalid email address(es): [bad]."},
            {"ALTER CONTACT cn808 SET EMAIL_DISTRIBUTION_LIST = 'a808@example.com' URL = 'https://example.com/a'",
                "REFUSED Email, url or users/email_list, only one could be set."},
            {"ALTER CONTACT cn808 UNSET URL",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 UNSET EMAIL_DISTRIBUTION_LIST",
                "Statement executed successfully."},
            {"ALTER CONTACT cn808 SET NOPE = 'x'",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'CONTACT'"},
            {"ALTER CONTACT cn808 UNSET NOPE",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'CONTACT'"},
            {"DESCRIBE CONTACT cn808",
                "REFUSED Unsupported feature 'CONTACT'."},
        });
    }

    /** Separators, taken names, a dollar comment and attachments. */
    @Test
    public void separatorsNamesAndAttachments() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT cq809 EMAIL_DISTRIBUTION_LIST = 'q809@example.com'",
                "Contact CQ809 successfully created."},
            {"CREATE OR REPLACE CONTACT cq809b URL = 'https://example.com/q'",
                "Contact CQ809B successfully created."},
            {"ALTER CONTACT cq809 SET COMMENT = 'x', EMAIL_DISTRIBUTION_LIST = 'r809@example.com'",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809|TEST_SCHEMA|x|r809@example.com|null ;; CQ809B|TEST_SCHEMA||null|https://example.com/q"},
            {"ALTER CONTACT cq809 SET COMMENT = 'y' EMAIL_DISTRIBUTION_LIST = 's809@example.com'",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809|TEST_SCHEMA|y|s809@example.com|null ;; CQ809B|TEST_SCHEMA||null|https://example.com/q"},
            {"ALTER CONTACT cq809 UNSET COMMENT, URL",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809|TEST_SCHEMA||s809@example.com|null ;; CQ809B|TEST_SCHEMA||null|https://example.com/q"},
            {"ALTER CONTACT cq809 UNSET COMMENT URL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'URL'."},
            {"ALTER CONTACT cq809 UNSET USERS",
                "Statement executed successfully."},
            {"ALTER CONTACT cq809 SET USERS = ()",
                "Statement executed successfully."},
            {"ALTER CONTACT cq809 RENAME TO cq809b",
                "REFUSED SQL compilation error: | Object 'CQ809B' already exists."},
            {"ALTER CONTACT cq809 RENAME TO cq809",
                "REFUSED SQL compilation error: | Object 'CQ809' already exists."},
            {"ALTER CONTACT cq809 RENAME TO \"cq809\"",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809B|TEST_SCHEMA||null|https://example.com/q ;; cq809|TEST_SCHEMA||s809@example.com|null"},
            {"ALTER CONTACT \"cq809\" RENAME TO cq809",
                "Statement executed successfully."},
            {"ALTER CONTACT IF EXISTS nosuch809 SET NOPE = 'x'",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'CONTACT'"},
            {"ALTER CONTACT IF EXISTS cq809 SET NOPE = 'x'",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'CONTACT'"},
            {"ALTER CONTACT cq809 SET COMMENT = $$dollar$$",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809|TEST_SCHEMA|dollar|s809@example.com|null ;; CQ809B|TEST_SCHEMA||null|https://example.com/q"},
            {"CREATE OR REPLACE CONTACT cq809c EMAIL_DISTRIBUTION_LIST = 'c809@example.com' URL = "
                + "'https://example.com/c'",
                "REFUSED Email, url or users/email_list, only one could be set."},
            {"CREATE OR REPLACE CONTACT cq809c COMMENT = $$d$$",
                "Contact CQ809C successfully created."},
            {"CREATE OR REPLACE TABLE tq809 (a INT)",
                "Table TQ809 successfully created."},
            {"ALTER TABLE tq809 SET CONTACT STEWARD = cq809",
                "Statement executed successfully."},
            {"SELECT purpose, email FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tq809', 'TABLE'))",
                "STEWARD|s809@example.com"},
            {"ALTER CONTACT cq809 RENAME TO cq809r",
                "Statement executed successfully."},
            {"SELECT purpose, email FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tq809', 'TABLE'))",
                "STEWARD|s809@example.com"},
            {"ALTER CONTACT cq809r SET EMAIL_DISTRIBUTION_LIST = 't809@example.com'",
                "Statement executed successfully."},
            {"SELECT purpose, email FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tq809', 'TABLE'))",
                "STEWARD|t809@example.com"},
            {"DROP CONTACT cq809r",
                "CQ809R successfully dropped."},
            {"SELECT purpose, email FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tq809', 'TABLE'))",
                ""},
            {"ALTER CONTACT cq809b SET URL = 'not a url'",
                "REFUSED SQL compilation error: | invalid URL: [not a url]"},
            {"SHOW CONTACTS LIKE 'CQ809%' ->> SELECT \"name\", \"schema_name\", \"comment\", "
                + "\"email_distribution_list\", \"url\" FROM $1 ORDER BY 1",
                "CQ809B|TEST_SCHEMA||null|https://example.com/q ;; CQ809C|TEST_SCHEMA|d|null|null"},
        });
    }

    /** URLs, user lists, value types and attachments across schemas. */
    @Test
    public void urlsUsersAndMovedAttachments() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        assertCells(new String[][] {
            {"CREATE OR REPLACE CONTACT cu810 URL = 'example.com'",
                "REFUSED SQL compilation error: | invalid URL: [example.com]"},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'http://x'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'ftp://x.y/z'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'mailto:a@b.co'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'https://exa mple.com'",
                "REFUSED SQL compilation error: | invalid URL: [https://exa mple.com]"},
            {"CREATE OR REPLACE CONTACT cu810 URL = ''",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'https://example.com:8080/a?b=c#d'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'HTTPS://EXAMPLE.COM'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'http://localhost'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'https://'",
                "REFUSED SQL compilation error: | invalid URL: [https://]"},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'www.example.com/a'",
                "REFUSED SQL compilation error: | invalid URL: [www.example.com/a]"},
            {"CREATE OR REPLACE CONTACT cu810 URL = 'https://x_y.example.com'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 USERS = ()",
                "Contact CU810 successfully created."},
            {"SHOW CONTACTS LIKE 'CU810%' ->> SELECT \"name\", \"comment\", \"email_distribution_list\", \"url\", "
                + "\"entries_in_users\", \"users\" FROM $1 ORDER BY 1",
                "CU810||null|null|0|null"},
            {"CREATE OR REPLACE CONTACT cu810 USERS = ('NOSUCH810')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH810' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT cu810 USERS = ('NOSUCH810', 'NOSUCH810B')",
                "REFUSED SQL compilation error: | invalid value 'NOSUCH810' for property 'USER'"},
            {"CREATE OR REPLACE CONTACT cu810 COMMENT = 'x', URL = 'https://a.co'",
                "Contact CU810 successfully created."},
            {"CREATE OR REPLACE CONTACT cu810 COMMENT = ('a')",
                "REFUSED SQL compilation error: | invalid type of property '[a]' for 'COMMENT'"},
            {"CREATE OR REPLACE CONTACT cu810 USERS = 'x'",
                "REFUSED SQL compilation error: | invalid type of property ''x'' for 'USERS'"},
            {"CREATE OR REPLACE CONTACT cu810 EMAIL_DISTRIBUTION_LIST = 'e810@example.com'",
                "Contact CU810 successfully created."},
            {"ALTER CONTACT cu810 SET COMMENT = ('a')",
                "REFUSED SQL compilation error: | invalid type of property '[a]' for 'COMMENT'"},
            {"ALTER CONTACT cu810 SET USERS = 'x'",
                "REFUSED SQL compilation error: | invalid type of property ''x'' for 'USERS'"},
            {"ALTER CONTACT cu810 SET EMAIL_DISTRIBUTION_LIST = ''",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CU810%' ->> SELECT \"name\", \"comment\", \"email_distribution_list\", \"url\", "
                + "\"entries_in_users\", \"users\" FROM $1 ORDER BY 1",
                "CU810|||null|0|null"},
            {"ALTER CONTACT cu810 SET URL = 'https://u.co'",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CU810%' ->> SELECT \"name\", \"comment\", \"email_distribution_list\", \"url\", "
                + "\"entries_in_users\", \"users\" FROM $1 ORDER BY 1",
                "CU810|||https://u.co|0|null"},
            {"ALTER CONTACT cu810 SET USERS = ()",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CU810%' ->> SELECT \"name\", \"comment\", \"email_distribution_list\", \"url\", "
                + "\"entries_in_users\", \"users\" FROM $1 ORDER BY 1",
                "CU810|||https://u.co|0|null"},
            {"ALTER CONTACT cu810 SET URL = 'not a url'",
                "REFUSED SQL compilation error: | invalid URL: [not a url]"},
            {"ALTER CONTACT cu810 SET URL = 'ftp://x.y'",
                "Statement executed successfully."},
            {"SHOW CONTACTS LIKE 'CU810%' ->> SELECT \"name\", \"comment\", \"email_distribution_list\", \"url\", "
                + "\"entries_in_users\", \"users\" FROM $1 ORDER BY 1",
                "CU810|||ftp://x.y|0|null"},
            {"CREATE OR REPLACE TABLE tu810 (a INT)",
                "Table TU810 successfully created."},
            {"ALTER TABLE tu810 SET CONTACT STEWARD = cu810",
                "Statement executed successfully."},
        });
        engine.execute("CREATE OR REPLACE TASK ku810 WAREHOUSE = " + warehouse + " SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertCells(new String[][] {
            {"ALTER TASK ku810 SET CONTACT SUPPORT = cu810",
                "Statement executed successfully."},
            {"ALTER CONTACT cu810 RENAME TO test_db.public.cu810m",
                "Statement executed successfully."},
            {"SELECT purpose, url FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tu810', 'TABLE'))",
                "STEWARD|ftp://x.y"},
            {"SELECT purpose, url FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.ku810', 'TASK'))",
                "SUPPORT|ftp://x.y"},
            {"SELECT GET_DDL('TABLE', 'tu810')",
                "create or replace TABLE TU810 ( | \tA NUMBER(38,0) | ) WITH CONTACT (STEWARD=TEST_DB.PUBLIC.CU810M) | "
                + ";"},
            {"CREATE OR REPLACE CONTACT test_db.public.cu810m URL = 'https://r.co'",
                "Contact CU810M successfully created."},
            {"SELECT purpose, url FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.tu810', 'TABLE'))",
                ""},
        });
    }
}
