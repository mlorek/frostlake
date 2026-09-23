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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two things the grammar admitted that the account refuses: a CROSS JOIN carrying an ON, and a null
 * treatment written INSIDE the parentheses of a window function that does not take it there.
 *
 * <p>FIRST_VALUE and LAST_VALUE are the two that DO take it inside; every one of them takes it after
 * the closing parenthesis.
 */
public class CrossJoinAndNullTreatmentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t1 (id INT, \"default\" INT)");
        engine.execute("INSERT INTO t1 VALUES (5, 1), (7, 2)");
        engine.execute("CREATE OR REPLACE TABLE g (k INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50)");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The message of the refusal a statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", "|");
    }

    /** A CROSS JOIN takes no ON: the account refuses it at the ON. */
    @Test
    public void aCrossJoinTakesNoOn() {
        assertTrue(refusal("SELECT t1.id, g.v FROM t1 CROSS JOIN g ON t1.id = g.k")
            .contains("syntax error line 1 at position 39 unexpected 'ON'."));
    }

    /** The plain CROSS JOIN is unaffected. */
    @Test
    public void thePlainCrossJoinStillRuns() {
        assertEquals("2", answer("SELECT COUNT(*) FROM t1 CROSS JOIN g"));
    }

    /** A null treatment inside the parentheses belongs to FIRST_VALUE and LAST_VALUE alone. */
    @Test
    public void onlyTheValueFunctionsTakeItInside() {
        assertEquals("50", answer("SELECT FIRST_VALUE(v IGNORE NULLS) OVER (ORDER BY k) FROM g"));
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT LAG(v IGNORE NULLS) OVER (ORDER BY k) FROM g");
            }
        });
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT NTH_VALUE(v, 1 IGNORE NULLS) OVER (ORDER BY k) FROM g");
            }
        });
    }

    /** After the closing parenthesis, every one of them takes it. */
    @Test
    public void allOfThemTakeItOutside() {
        assertEquals("null", answer("SELECT LAG(v) IGNORE NULLS OVER (ORDER BY k) FROM g"));
        assertEquals("50", answer("SELECT FIRST_VALUE(v) IGNORE NULLS OVER (ORDER BY k) FROM g"));
    }

    /** An EXCLUDE list may write DEFAULT, which then resolves as a column name like any other. */
    @Test
    public void anExcludeListMayWriteDefault() {
        assertTrue(refusal("SELECT * EXCLUDE (default) FROM t1 ORDER BY 1")
            .contains("column 'DEFAULT' does not exist"),
            "it parses, and is refused for naming a column that is not there");
        assertEquals("5", answer("SELECT * EXCLUDE (\"default\") FROM t1 ORDER BY 1"),
            "the quoted lower-case column is a different name");
    }
}
