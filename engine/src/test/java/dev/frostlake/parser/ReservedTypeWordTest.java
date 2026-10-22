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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A column's type word that is a keyword of this grammar: OTHER, a plain name on the account, is read as an
 * unknown type, where LANGUAGE and SESSION are refused where they stand (all live-verified).
 */
public class ReservedTypeWordTest extends MeasuredCellsTestSupport {

    @Test
    public void keywordTypeWords() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE nf_p (x INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'language'.",
            "CREATE TABLE nf_q58 (m language)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'session'.",
            "CREATE TABLE nf_q59 (m session)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'OTHER'.",
            "CREATE TABLE nf_q53 (m other)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "ALTER TABLE nf_p ADD COLUMN y nosuch, z other");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'language'.",
            "CREATE TABLE nf_q60 (n nosuchtype, m language)");
    }
}
