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
 * Where live refuses a fault in an ALTER statement it gives up on as a whole (live-verified): after the RENAME of a
 * table, a view, a schema, a database, a warehouse, a user, a tag, a stage or a policy at that RENAME, after a
 * sequence's RENAME TO at the TO, and anywhere in a role's, a stream's or a file format's statement at its kind,
 * then the token after it — where Frostlake refused the token that failed.
 */
public class AlterRenameAnchorTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int position, final String token) {
        return "\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void aTableOrViewRenameIsRefusedAtItsRename() {
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO;"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME"));
        assertEquals(refused(line(25, "RENAME")), refusal("ALTER TABLE IF EXISTS t1 RENAME TO"));
        assertEquals(refused(line(20, "RENAME")), refusal("ALTER TABLE db.s.t1 RENAME TO"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO 1"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME COLUMN a TO"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME CONSTRAINT c TO"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME x"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO t3 x"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO x y z"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER TABLE t1 RENAME TO t3 ('x')"));
        assertEquals(refused(line(14, "RENAME")), refusal("ALTER VIEW v1 RENAME TO"));
        assertEquals(refused(line(14, "RENAME")), refusal("ALTER VIEW v1 RENAME TO 1"));
        assertEquals(refused(line(27, "RENAME")), refusal("ALTER MATERIALIZED VIEW v1 RENAME TO"));
    }

    @Test
    public void everyOtherKindThatRenamesAtItsRename() {
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER SCHEMA s RENAME TO"));
        assertEquals(refused(line(17, "RENAME")), refusal("ALTER DATABASE d RENAME TO x y"));
        assertEquals(refused(line(18, "RENAME")), refusal("ALTER WAREHOUSE w RENAME TO"));
        assertEquals(refused(line(13, "RENAME")), refusal("ALTER USER u RENAME TO"));
        assertEquals(refused(line(13, "RENAME")), refusal("ALTER TAG tg RENAME TO"));
        assertEquals(refused(line(15, "RENAME")), refusal("ALTER STAGE st RENAME TO"));
        assertEquals(refused(line(24, "RENAME")), refusal("ALTER MASKING POLICY mp RENAME TO"));
        assertEquals(refused(line(28, "RENAME")), refusal("ALTER ROW ACCESS POLICY rap RENAME TO"));
    }

    @Test
    public void aSequenceRenameIsRefusedAtItsTo() {
        assertEquals(refused(line(25, "TO")), refusal("ALTER SEQUENCE s1 RENAME TO"));
        assertEquals(refused(line(25, "TO")), refusal("ALTER SEQUENCE s1 RENAME TO;"));
        assertEquals(refused(line(30, "TO")), refusal("ALTER SEQUENCE renamed RENAME TO"));
        assertEquals(refused(line(35, "TO")), refusal("ALTER SEQUENCE IF EXISTS s1 RENAME TO"));
        assertEquals(refused(line(25, "TO")), refusal("ALTER SEQUENCE s1 RENAME TO 1"));
        assertEquals(refused(line(25, "TO"), line(30, "y")), refusal("ALTER SEQUENCE s1 RENAME TO x y"));
        assertEquals(refused(line(25, "TO"), line(30, "(")), refusal("ALTER SEQUENCE s1 RENAME TO x (1)"));
        assertEquals(refused(line(25, "x")), refusal("ALTER SEQUENCE s1 RENAME x"));
        assertEquals(refused(line(24, "<EOF>")), refusal("ALTER SEQUENCE s1 RENAME"));
    }

    @Test
    public void aRoleStreamOrFileFormatFaultNamesItsKindThenTheTokenAfterIt() {
        assertEquals(refused(line(6, "ROLE"), line(11, "r")), refusal("ALTER ROLE r RENAME TO"));
        assertEquals(refused(line(6, "ROLE"), line(11, "r")), refusal("ALTER ROLE r SET COMMENT"));
        assertEquals(refused(line(6, "ROLE"), line(11, "IF")), refusal("ALTER ROLE IF EXISTS r RENAME TO"));
        assertEquals(refused(line(6, "ROLE"), line(11, "r")), refusal("ALTER ROLE r SET COMMENT = 'x' x"));
        assertEquals(refused(line(6, "ROLE"), line(10, "<EOF>")), refusal("ALTER ROLE"));
        assertEquals(refused(line(6, "FILE"), line(11, "FORMAT")), refusal("ALTER FILE FORMAT ff RENAME TO"));
        assertEquals(refused(line(6, "FILE"), line(11, "FORMAT")), refusal("ALTER FILE FORMAT ff SET"));
        assertEquals(refused(line(6, "FILE"), line(11, "FORMAT")), refusal("ALTER FILE FORMAT"));
        assertEquals(refused(line(6, "STREAM"), line(13, "st")), refusal("ALTER STREAM st RENAME TO"));
        assertEquals(refused(line(6, "STREAM"), line(13, "st")), refusal("ALTER STREAM st SET"));
    }

    @Test
    public void kindsThatAlreadyAgree() {
        assertEquals(refused(line(14, "RENAME")), refusal("ALTER TASK tk RENAME TO"));
        assertEquals(refused(line(14, "RENAME")), refusal("ALTER PIPE pp RENAME TO"));
        assertEquals(refused(line(31, "<EOF>")), refusal("ALTER FUNCTION f(INT) RENAME TO"));
        assertEquals(refused(line(18, "<EOF>")), refusal("ALTER TABLE t1 SET"));
        assertEquals(refused(line(24, "<EOF>")), refusal("ALTER TABLE t1 SWAP WITH"));
    }
}
