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

import dev.frostlake.LiveSnowflake;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A GRANT of privileges on a table named by the word IDENTIFIER alone, with no whole reference after it: the account
 * reads the word as the table's own name and the parentheses after it as a list of types. A plain word there is
 * refused where it stands; a word with parameters is a type's, and each text among its parameters is refused where it
 * stands. Every answer is live-verified; the class runs on the engine only, since the account under test takes no
 * GRANT statement.
 */
public class GrantIdentifierTypeListTest extends MeasuredCellsTestSupport {

    @Test
    public void theParenthesesAfterATableCalledIdentifierListTypes() {
        assumeFalse(LiveSnowflake.enabled());
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 't'.",
            "GRANT SELECT ON TABLE IDENTIFIER(t) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ''t''.",
            "GRANT SELECT ON TABLE IDENTIFIER(UPPER('t')) TO ROLE nosuch_role_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''a''. | syntax error "
                + "line 1 at position 45 unexpected ''b''.",
            "GRANT SELECT ON TABLE IDENTIFIER(CONCAT('a', 'b')) TO ROLE nosuch_role_w7");
    }
}
