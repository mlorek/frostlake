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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class FileFormatOptionValueTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aDelimiterIsAtMostTwentyCharacters() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P474_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("CREATE OR REPLACE FILE FORMAT realff TYPE = CSV");
            engine.execute("CREATE OR REPLACE FILE FORMAT fd1 TYPE = CSV FIELD_DELIMITER = 'ab'");
            engine.execute("CREATE OR REPLACE FILE FORMAT fd2 TYPE = CSV FIELD_DELIMITER = 'abcdefghijklmnopqrst'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd3 TYPE = CSV FIELD_DELIMITER = 'abcdefghijklmnopqrstu'",
                "SQL compilation error:\ninvalid value ['abcdefghijklmnopqrstu'] for parameter 'FIELD_DELIMITER'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd4 TYPE = CSV FIELD_DELIMITER = 'abcdefghijklmnopqrstuvwxyzabcdef'",
                "SQL compilation error:\ninvalid value ['abcdefghijklmnopqrstuvwxyzabcdef'] for parameter 'FIELD_DELIMITER'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd5 TYPE = CSV FIELD_DELIMITER = 'abcdefghijklmnopqrstuvwxyzabcdefg'",
                "SQL compilation error:\ninvalid value ['abcdefghijklmnopqrstuvwxyzabcdefg'] for parameter 'FIELD_DELIMITER'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd6 TYPE = CSV RECORD_DELIMITER = 'abcdefghijklmnopqrstuvwxyzabcdefg'",
                "SQL compilation error:\ninvalid value ['abcdefghijklmnopqrstuvwxyzabcdefg'] for parameter 'RECORD_DELIMITER'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd7 TYPE = CSV ESCAPE = 'ab'",
                "SQL compilation error:\ninvalid value ['ab'] for parameter 'ESCAPE'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd8 TYPE = CSV ESCAPE_UNENCLOSED_FIELD = 'ab'",
                "SQL compilation error:\ninvalid value ['ab'] for parameter 'ESCAPE_UNENCLOSED_FIELD'");
            assertRefused("CREATE OR REPLACE FILE FORMAT fd9 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = 'ab'",
                "SQL compilation error:\ninvalid value ['ab'] for parameter 'FIELD_OPTIONALLY_ENCLOSED_BY'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P474_DB");
        }
    }

    @Test
    public void anEscapeIsExactlyOneCharacter() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P474B_DB");
            engine.execute("CREATE OR REPLACE FILE FORMAT g1 TYPE = CSV RECORD_DELIMITER = 'abcdefghijklmnopqrst'");
            assertRefused("CREATE OR REPLACE FILE FORMAT g2 TYPE = CSV RECORD_DELIMITER = 'abcdefghijklmnopqrstu'",
                "SQL compilation error:\ninvalid value ['abcdefghijklmnopqrstu'] for parameter 'RECORD_DELIMITER'");
            engine.execute("CREATE OR REPLACE FILE FORMAT g3 TYPE = CSV ESCAPE = 'a'");
            assertRefused("CREATE OR REPLACE FILE FORMAT g4 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = 'a'",
                "SQL compilation error:\ninvalid value ['a'] for parameter 'FIELD_OPTIONALLY_ENCLOSED_BY'");
            engine.execute("CREATE OR REPLACE FILE FORMAT g5 TYPE = CSV ESCAPE_UNENCLOSED_FIELD = 'a'");
            assertRefused("CREATE OR REPLACE FILE FORMAT g6 TYPE = CSV FIELD_DELIMITER = ''",
                "SQL compilation error:\ninvalid value [''] for parameter 'FIELD_DELIMITER'");
            assertRefused("CREATE OR REPLACE FILE FORMAT g7 TYPE = CSV ESCAPE = ''",
                "SQL compilation error:\ninvalid value [''] for parameter 'ESCAPE'");
            engine.execute("CREATE OR REPLACE FILE FORMAT g8 TYPE = CSV FIELD_DELIMITER = 'NONE'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P474B_DB");
        }
    }

    @Test
    public void anEncloserIsOneOfThreeValues() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P474C_DB");
            engine.execute("CREATE OR REPLACE FILE FORMAT h1 TYPE = CSV ESCAPE = 'NONE'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h2 TYPE = CSV ESCAPE_UNENCLOSED_FIELD = 'NONE'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h3 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = '\"'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h4 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = ''''");
            engine.execute("CREATE OR REPLACE FILE FORMAT h5 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = 'NONE'");
            assertRefused("CREATE OR REPLACE FILE FORMAT h6 TYPE = CSV RECORD_DELIMITER = ''",
                "SQL compilation error:\ninvalid value [''] for parameter 'RECORD_DELIMITER'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h7 TYPE = CSV ESCAPE = '\\\\'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h8 TYPE = CSV FIELD_DELIMITER = '|'");
            engine.execute("CREATE OR REPLACE FILE FORMAT h9 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY = NONE");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P474C_DB");
        }
    }
}
