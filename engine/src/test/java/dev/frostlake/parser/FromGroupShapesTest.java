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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A parenthesized FROM group holds one source and the joins after it — none at all included, so {@code (t)} and
 * {@code ((a JOIN b ON …))} are groups too — and never a comma: a list inside the brackets is refused at the group's
 * first token and then at its closing parenthesis.
 */
public class FromGroupShapesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage().replace('\n', '|');
    }

    @Test
    public void aGroupMayHoldOneSourceAlone() {
        assertEquals("1", answer("SELECT * FROM (t)"));
        assertEquals("1", answer("SELECT * FROM (t a)"));
        assertEquals("1", answer("SELECT * FROM ((t))"));
        assertEquals("1", answer("SELECT COUNT(*) FROM ((t a JOIN t b ON a.a = b.a))"));
        assertEquals("1", answer("SELECT COUNT(*) FROM t x LEFT JOIN (t a) ON x.a = a.a"));
        assertEquals("1", answer("SELECT * FROM ((SELECT 1 AS z) q)"));
    }

    @Test
    public void aCommaInsideAGroupIsRefusedAtItsEnds() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 15 unexpected 't'.|"
            + "syntax error line 1 at position 23 unexpected ')'.", refusal("SELECT * FROM (t a, t b)"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 22 unexpected 't'.|"
            + "syntax error line 1 at position 52 unexpected ')'.",
            refusal("SELECT COUNT(*) FROM (t a JOIN t b ON a.a = b.a, t c)"));
    }
}
