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
 * SHOW ORGANIZATION ACCOUNTS is answered by the organization account alone, which this account is not: the listing is
 * refused as an operation on the account's own system, by the organization's MANAGE ACCOUNTS on the current account,
 * with a LIKE as without one (live-verified).
 */
public class OrganizationAccountsRefusalTest extends MeasuredCellsTestSupport {

    /** The listing alone and with a LIKE. */
    @Test
    public void theListingIsRefusedOnAnAccountThatIsNotTheOrganizations() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SHOW ORGANIZATION ACCOUNTS",
                "REFUSED SQL access control error: | Insufficient privileges to operate on  'SYSTEM'. Your primary "
                + "role <ROLE> must have MANAGE ACCOUNTS granted on ACCOUNT <ACCOUNT>."},
            {"SHOW ORGANIZATION ACCOUNTS LIKE 'X%'",
                "REFUSED SQL access control error: | Insufficient privileges to operate on  'SYSTEM'. Your primary "
                + "role <ROLE> must have MANAGE ACCOUNTS granted on ACCOUNT <ACCOUNT>."},
        });
    }
}
