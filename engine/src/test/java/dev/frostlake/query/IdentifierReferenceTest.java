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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IDENTIFIER() reads its string as an identifier reference. Once trimmed, each dotted part must be a closed
 * quoted identifier or an unquoted one (a letter or an underscore, then letters, digits, underscores and
 * dollars); any other string is refused as an invalid identifier before a lookup is tried, echoing the
 * argument as written at its own position. The rule is the same for a FROM, a DDL target and USE. Every cell
 * is live-verified.
 */
public class IdentifierReferenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE \"my table\" (a INT)");
        engine.execute("CREATE TABLE t1 (a INT)");
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aStringThatIsNoIdentifierIsRefusedAtTheArgument() {
        assertRefused("SELECT * FROM IDENTIFIER('my table')", "error line 1 at position 25\ninvalid identifier ''my table''");
        assertRefused("SELECT * FROM IDENTIFIER('1abc')", "error line 1 at position 25\ninvalid identifier ''1abc''");
        assertRefused("SELECT * FROM IDENTIFIER('a-b')", "error line 1 at position 25\ninvalid identifier ''a-b''");
        assertRefused("SELECT * FROM IDENTIFIER('')", "error line 1 at position 25\ninvalid identifier ''''");
        assertRefused("SELECT * FROM IDENTIFIER('é')", "error line 1 at position 25\ninvalid identifier ''é''");
        assertRefused("SELECT * FROM IDENTIFIER('\"unclosed')", "error line 1 at position 25\ninvalid identifier ''\"unclosed''");
        assertRefused("SELECT * FROM IDENTIFIER('a b.c')", "error line 1 at position 25\ninvalid identifier ''a b.c''");
        assertRefused("SELECT * FROM IDENTIFIER('test_db.bad part.t1')",
            "error line 1 at position 25\ninvalid identifier ''test_db.bad part.t1''");
        assertRefused("SELECT a FROM   IDENTIFIER('my table')", "error line 1 at position 27\ninvalid identifier ''my table''");
    }

    @Test
    public void everyNameSiteAndAVariableReadTheSameRule() {
        assertRefused("CREATE TABLE IDENTIFIER('new table') (a INT)", "error line 1 at position 24\ninvalid identifier ''new table''");
        assertRefused("USE SCHEMA IDENTIFIER('bad schema')", "error line 1 at position 22\ninvalid identifier ''bad schema''");
        engine.execute("SET v = 'my table'");
        assertRefused("SELECT * FROM IDENTIFIER($v)", "error line 1 at position 25\ninvalid identifier '$v'");
    }

    @Test
    public void anIdentifierStringResolvesAsWritten() {
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER('\"my table\"')").getRows().size());
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER('t1')").getRows().size());
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER(' t1')").getRows().size());
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER('t1 ')").getRows().size());
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER('test_schema.t1')").getRows().size());
        assertEquals(0, engine.executeQuery("SELECT * FROM IDENTIFIER('test_schema.\"my table\"')").getRows().size());
        assertRefused("SELECT * FROM IDENTIFIER('T1$x')", "Object 'T1$X' does not exist or not authorized.");
        assertRefused("SELECT * FROM IDENTIFIER('_t')", "Object '_T' does not exist or not authorized.");
    }
}
