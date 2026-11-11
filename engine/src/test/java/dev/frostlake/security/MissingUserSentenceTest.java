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
 * A user that does not exist is refused with one sentence by DESCRIBE, SHOW PARAMETERS IN USER, SHOW GRANTS TO USER
 * and SHOW DELEGATED AUTHORIZATIONS under a role that holds MANAGE GRANTS, and a faulty signature after a role's or a
 * database's name in SHOW GRANTS is named as live names it (live-verified).
 */
public class MissingUserSentenceTest extends MeasuredCellsTestSupport {

    @Test
    public void oneSentence() {
        assertCells(new String[][] {
            {"DESCRIBE USER nosuch_user_w9",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW PARAMETERS IN USER nosuch_user_w9",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW GRANTS TO USER nosuch_user_w9",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW DELEGATED AUTHORIZATIONS BY USER nosuch_user_w9",
                "REFUSED SQL compilation error: | User 'NOSUCH_USER_W9' does not exist or not authorized."},
            {"SHOW GRANTS TO ROLE nosuch_role_w9(a b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'b'."},
            {"SHOW GRANTS ON DATABASE nosuch_db_w9(a b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'b'."},
        });
    }
}
