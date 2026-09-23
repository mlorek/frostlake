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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * After any statement but a query, a DML or a scripting statement, one run into the next without its semicolon is
 * refused at the next one's first word ahead of that statement's own later fault — one line, except after a SET,
 * whose value is read on; a word that can also be the first statement's clause is read as that (live-verified).
 */
public class SeparatorBeforeLaterFaultTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void theNextStatementsWordComesFirst() {
        assertEquals(refused(line(1, 45, "REVOKE")),
            refusal("GRANT OWNERSHIP ON TABLE T5 TO ROLE SYSADMIN REVOKE GRANTS"));
        assertEquals(refused(line(1, 44, "REVOKE")),
            refusal("GRANT OWNERSHIP ON TABLE T10 TO ROLE PUBLIC REVOKE CURRENT"));
        assertEquals(refused(line(1, 34, "SELECT")), refusal("GRANT SELECT ON TABLE t TO ROLE r SELECT 1 x y"));
        assertEquals(refused(line(1, 13, "SELECT")), refusal("DROP TABLE t SELECT 1 x y"));
        assertEquals(refused(line(1, 31, "UPDATE")), refusal("ALTER TABLE t ADD COLUMN e INT UPDATE t SET a = 1 x y"));
        assertEquals(refused(line(1, 18, "REVOKE")), refusal("USE SCHEMA PUBLIC REVOKE GRANTS"));
        assertEquals(refused(line(1, 12, "SELECT")), refusal("SHOW TABLES SELECT 1 x y"));
        assertEquals(refused(line(1, 17, "REVOKE")), refusal("TRUNCATE TABLE t REVOKE GRANTS"));
        assertEquals(refused(line(1, 7, "SELECT")), refusal("COMMIT SELECT 1 x y"));
        assertEquals(refused(line(1, 9, "UPDATE")), refusal("CALL p() UPDATE t SET a = 1 x y"));
        assertEquals(refused(line(1, 23, "SELECT")), refusal("CREATE TABLE u (a INT) SELECT 1 x y"));
        assertEquals(refused(line(1, 17, "SELECT")), refusal("DESCRIBE TABLE t SELECT 1 x y"));
        assertEquals(refused(line(1, 37, "SELECT")), refusal("REVOKE SELECT ON TABLE t FROM ROLE r SELECT 1 x y"));
    }

    @Test
    public void onlyASetOrAQueryTailResumes() {
        assertEquals(refused(line(1, 10, "UPDATE"), line(1, 29, "x")), refusal("SET v = 1 UPDATE t SET a = 1 x y"));
        assertEquals(refused(line(1, 26, "SELECT"), line(1, 37, "y")),
            refusal("CREATE VIEW v AS SELECT 1 SELECT 1 x y"));
        assertEquals(refused(line(1, 17, "UPDATE")), refusal("DESCRIBE TABLE t UPDATE t SET a = 1 x y"));
        assertEquals(refused(line(1, 16, "CREATE")), refusal("DESCRIBE VIEW t CREATE TABLE u (a INT) x"));
        assertEquals(refused(line(1, 13, "CREATE")), refusal("DROP TABLE t CREATE TABLE u (a INT) x"));
    }

    @Test
    public void aClauseTheFirstStatementTakesIsReadAsItsOwn() {
        assertEquals(refused(line(1, 43, "'x'")), refusal("CREATE OR REPLACE TABLE tq (a INT) COMMENT 'x'"));
    }
}
