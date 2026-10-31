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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SHOW CALLER GRANTS lists caller grants in ten columns - created_on, privilege, granted_on, name, is_inherited,
 * inherited_from, inherited_from_database, inherited_from_schema, granted_to, grantee_name - on an object, in the
 * account or to an executable owner, reading a LIMIT; without a target it is refused, "Caller grants are not
 * applicable to 'USER'.", and an object that is not there as the other grant listings refuse one. INHERITED stays
 * a name (all live-verified).
 */
public class ShowCallerGrantsTest extends MeasuredCellsTestSupport {

    @Test
    public void theListingAndItsShape() {
        engine.execute("CREATE OR REPLACE TABLE cg (a INT)");
        assertAnswer("", "SHOW CALLER GRANTS ON ACCOUNT");
        assertAnswer("created_on|TIMESTAMP_LTZ(3)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; privilege|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; granted_on|VARCHAR(16777216)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; name|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|"
                + "null|null|null|null|null ;; is_inherited|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; inherited_from|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "inherited_from_database|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "inherited_from_schema|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "granted_to|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; grantee_name|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE RESULT LAST_QUERY_ID()");
        assertAnswer("", "SHOW CALLER GRANTS ON TABLE cg");
        assertAnswer("0", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("REFUSED SQL compilation error: | Caller grants are not applicable to 'USER'.",
            "SHOW CALLER GRANTS");
        assertAnswer("REFUSED SQL compilation error: | Table 'NOSUCH_CG' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NOSUCH_CG.",
            "SHOW CALLER GRANTS ON TABLE nosuch_cg");
        assertAnswer("REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_CG_SCHEMA' does not exist or not "
                + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.NOSUCH_CG_SCHEMA.",
            "SHOW CALLER GRANTS ON SCHEMA nosuch_cg_schema");
        assertAnswer("", "SHOW CALLER GRANTS ON TABLE cg LIMIT 1");
        assertAnswer("1", "SELECT 1 AS inherited");
        assertAnswer("Table INHERITED successfully created.", "CREATE OR REPLACE TABLE inherited (inherited INT)");
        assertAnswer("", "SELECT inherited FROM inherited");
    }
}
