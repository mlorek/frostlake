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
 * SHOW REGIONS IN DATA EXCHANGE refuses the exchange it names as one that does not exist or is not authorized, the
 * name as written, quoted or not, and without a name as the feature it does not support, naming the form to write;
 * SHOW ORGANIZATION ACCOUNTS reads a LIKE and refuses a STARTS WITH after it (all live-verified).
 */
public class ShowDataExchangeScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void theDataExchangeAndOrganizationScopes() {
        assertAnswer("REFUSED SQL compilation error: | Data exchange 'NOSUCH_X9' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on DATA EXCHANGE NOSUCH_X9.",
            "SHOW REGIONS IN DATA EXCHANGE nosuch_x9");
        assertAnswer("REFUSED SQL compilation error: | Data exchange '\"Mixed_x9\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on DATA EXCHANGE \"Mixed_x9\".",
            "SHOW REGIONS IN DATA EXCHANGE \"Mixed_x9\"");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'STARTS'.",
            "SHOW ORGANIZATION ACCOUNTS LIKE 'a' STARTS WITH 'b'");
        assertAnswer("REFUSED Unsupported feature 'SHOW REGIONS IN DATA EXCHANGE'; use 'SHOW REGIONS IN DATA EXCHANGE "
                + "<exchange_name>' instead.",
            "SHOW REGIONS IN DATA EXCHANGE");
    }
}
