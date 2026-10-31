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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * TAG_REFERENCES takes the ACCOUNT domain over the account's locator, as CURRENT_ACCOUNT() answers it, and
 * refuses any other name as an account no session can see; a view's declared text type is VARCHAR(16777216)
 * where a read of it types the text at VARCHAR(134217728) (all live-verified).
 */
public class TagReferencesAccountDomainTest extends MeasuredCellsTestSupport {

    @Test
    public void accountDomainAndViewTypes() {
        engine.execute("CREATE OR REPLACE VIEW vw65 AS SELECT CAST(NULL AS VARCHAR) AS s, 'abc' AS g");
        assertAnswer("S|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; G|VARCHAR(3)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vw65");
        assertShape("S:VARCHAR(134217728), G:VARCHAR(3)", "SELECT * FROM vw65");
        assertAnswer("true",
            "SELECT COUNT(*) >= 0 FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(CURRENT_ACCOUNT(), 'ACCOUNT'))");
        assertAnswer("REFUSED SQL compilation error: | Account 'NOSUCH_ACCT' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_acct', 'ACCOUNT'))");
        assertAnswer("REFUSED SQL compilation error: | Account 'NOSUCH_ACCT' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ACCOUNT <ACCOUNT>.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_acct', 'account'))");
    }
}
