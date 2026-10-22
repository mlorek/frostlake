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
 * A column's type word that is a keyword of this grammar but a plain name on the account, OTHER, is read as any
 * unknown type word is: a width fault of an earlier column is reported first, and the first unknown type of the
 * statement is the one refused (all live-verified).
 */
public class KeywordTypeWordTest extends MeasuredCellsTestSupport {

    @Test
    public void keywordTypeWords() {
        engine.execute("CREATE OR REPLACE TABLE nf_p (x INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 45 | Invalid character length: 0. Must "
                + "be between 1 and 134,217,728.",
            "CREATE TABLE nf_q52 (n nosuchtype, v VARCHAR(0), m other)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "ALTER TABLE nf_p ADD COLUMN y nosuch, z other");
        assertAnswer("REFUSED SQL Compilation error: | Unsupported data type 'nosuch' for iceberg tables.",
            "ALTER ICEBERG TABLE nf_ice ADD COLUMN y nosuch, z other");
    }
}
