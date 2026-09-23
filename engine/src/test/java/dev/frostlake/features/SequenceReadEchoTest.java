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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A NEXTVAL or CURRVAL read that names no sequence echoes its qualifier part by part, each spelled as written
 * and quoted only where the name needs quotes, then the pseudo-column in upper case. Every cell is
 * live-verified.
 */
public class SequenceReadEchoTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    private static String invalid(final int position, final String echo) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier '" + echo + "'";
    }

    @Test
    public void aQuotedNameKeepsItsQuotesWhereItNeedsThem() {
        assertEquals(invalid(7, "\"nosuch\".NEXTVAL"), refusal("SELECT \"nosuch\".nextval"));
        assertEquals(invalid(7, "\"Mixed\".CURRVAL"), refusal("SELECT \"Mixed\".currval"));
        assertEquals(invalid(7, "\"a b\".NEXTVAL"), refusal("SELECT \"a b\".nextval"));
        assertEquals(invalid(7, "NOSUCH.NEXTVAL"), refusal("SELECT \"NOSUCH\".nextval"));
        assertEquals(invalid(7, "NOSUCH.NEXTVAL"), refusal("SELECT nosuch.nextval"));
    }

    @Test
    public void everyPartOfAQualifiedNameIsSpelledOnItsOwn() {
        assertEquals(invalid(7, "TEST_DB.\"Sch\".\"lower\".NEXTVAL"),
            refusal("SELECT test_db.\"Sch\".\"lower\".nextval"));
        assertEquals(invalid(7, "TEST_DB.PUBLIC.\"other\".NEXTVAL"),
            refusal("SELECT test_db.PUBLIC.\"other\".NEXTVAL"));
        assertEquals(invalid(7, "PUBLIC.\"lower2\".NEXTVAL"), refusal("SELECT PUBLIC.\"lower2\".nextval"));
    }

    @Test
    public void theEchoKeepsTheReferencesPlace() {
        engine.execute("CREATE TABLE t (a INT, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE SEQUENCE \"lower\"");
        assertEquals(invalid(10, "\"zz\".NEXTVAL"), refusal("SELECT a, \"zz\".nextval FROM t"));
        assertEquals(invalid(7, "\"lower\".CURRVAL"), refusal("SELECT \"lower\".currval"));
    }
}
