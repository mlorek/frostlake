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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * ALTER of a masking or row access policy — a new body, a comment set or unset, a tag list — and the refusals of its
 * property list, in live's words and order (all live-verified): a repeated name, then an unknown name, then a value
 * no comment takes, all before the policy itself is looked up.
 */
public class AlterPolicyPropertiesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE MASKING POLICY mp AS (val STRING) RETURNS STRING -> val");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (x INT) RETURNS BOOLEAN -> TRUE");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String maskingComment() {
        final ResultSet rs = engine.executeQuery("SHOW MASKING POLICIES LIKE 'MP'");
        return cell(rs, soleRowWhere(rs, "name", "MP"), "comment");
    }

    private String body(final String kind, final String name) {
        final ResultSet rs = engine.executeQuery("DESCRIBE " + kind + " " + name);
        return cell(rs, rs.getRows().get(0), "body");
    }

    @Test
    public void aNewBodyIsWhatDescribeShows() {
        engine.execute("ALTER MASKING POLICY mp SET BODY -> UPPER(val)");
        assertEquals("UPPER(val)", body("MASKING POLICY", "mp"));
        engine.execute("ALTER MASKING POLICY mp SET BODY -> '***';");
        assertEquals("'***'", body("MASKING POLICY", "mp"));
        engine.execute("ALTER MASKING POLICY mp SET BODY -> $$UPPER(val)$$");
        assertEquals("$$UPPER(val)$$", body("MASKING POLICY", "mp"));
        engine.execute("ALTER ROW ACCESS POLICY rap SET BODY -> x > 1");
        assertEquals("x > 1", body("ROW ACCESS POLICY", "rap"));
    }

    /**
     * The new body is the one a query applies. Frostlake applies no policy to ACCOUNTADMIN or SYSADMIN, so the reads
     * run under PUBLIC, which the live harness cannot switch to.
     */
    @Test
    public void theNewBodyIsTheOneAQueryApplies() {
        Assumptions.assumeFalse(isLiveSnowflake(), "switches the engine session's role");
        engine.execute("CREATE TABLE secrets (s VARCHAR, n INT)");
        engine.execute("INSERT INTO secrets VALUES ('abc', 1), ('def', 2)");
        engine.execute("ALTER TABLE secrets MODIFY COLUMN s SET MASKING POLICY mp");
        engine.execute("ALTER TABLE secrets ADD ROW ACCESS POLICY rap ON (n)");
        engine.getSecurityManager().getSessionContext().setCurrentRole("PUBLIC");
        assertEquals(2, engine.executeQuery("SELECT s FROM secrets").getRows().size());
        engine.execute("ALTER MASKING POLICY mp SET BODY -> UPPER(val)");
        engine.execute("ALTER ROW ACCESS POLICY rap SET BODY -> x > 1");
        final ResultSet rs = engine.executeQuery("SELECT s FROM secrets");
        assertEquals(1, rs.getRows().size());
        assertEquals("DEF", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void theCommentIsSetClearedAndSpelledAsLiveSpellsIt() {
        assertEquals("", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET COMMENT = 'it''s'");
        assertEquals("it's", maskingComment());
        engine.execute("ALTER MASKING POLICY mp UNSET COMMENT");
        assertEquals("", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET COMMENT = abc");
        assertEquals("ABC", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET COMMENT = \"Abc\"");
        assertEquals("Abc", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET COMMENT = NULL");
        assertEquals("", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET comment = $$d$$");
        assertEquals("d", maskingComment());
        engine.execute("ALTER MASKING POLICY mp SET COMMENT = a.b");
        assertEquals("a.b", maskingComment());
        engine.execute("ALTER ROW ACCESS POLICY rap SET COMMENT = 'r'");
        final ResultSet rs = engine.executeQuery("SHOW ROW ACCESS POLICIES LIKE 'RAP'");
        assertEquals("r", cell(rs, soleRowWhere(rs, "name", "RAP"), "comment"));
    }

    @Test
    public void tagsAreSetAndUnsetOnAPolicy() {
        engine.execute("CREATE TAG tg");
        engine.execute("CREATE TAG tg2");
        engine.execute("ALTER MASKING POLICY mp SET TAG tg = 'v', tg2 = 'w'");
        engine.execute("ALTER MASKING POLICY mp UNSET TAG tg, tg2");
        engine.execute("ALTER ROW ACCESS POLICY rap SET TAG tg = 'v'");
        assertEquals(hinted("SQL compilation error:\nTag 'COMMENT' does not exist or not authorized."),
            refusal("ALTER MASKING POLICY mp SET TAG tg = 'v', COMMENT = 'c'"));
        assertEquals(hinted("SQL compilation error:\nTag 'NOSUCH' does not exist or not authorized."),
            refusal("ALTER ROW ACCESS POLICY rap SET TAG nosuch = 'v'"));
        assertEquals(hinted("SQL compilation error:\nTag 'X' does not exist or not authorized."),
            refusal("ALTER MASKING POLICY mp UNSET TAG tg, x"));
    }

    @Test
    public void aRepeatOutranksAnUnknownNameWhichOutranksABadValue() {
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("ALTER MASKING POLICY mp SET foo = 1, COMMENT = 'a', COMMENT = 'b'"));
        assertEquals("SQL compilation error:\nduplicate property 'foo';",
            refusal("ALTER MASKING POLICY mp SET foo = 1, foo = 2"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("ALTER MASKING POLICY mp SET COMMENT = 'a' COMMENT = 'b'"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("ALTER MASKING POLICY mp UNSET foo, COMMENT, COMMENT"));
        assertEquals("SQL compilation error:\ninvalid property 'foo' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET foo = 1, FOO = 2"));
        assertEquals("SQL compilation error:\ninvalid property 'foo' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET COMMENT = 1, foo = 2"));
        assertEquals("SQL compilation error:\ninvalid value [1] for parameter 'COMMENT'",
            refusal("ALTER MASKING POLICY mp SET COMMENT = 1"));
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'COMMENT'",
            refusal("ALTER MASKING POLICY mp SET COMMENT = -1"));
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'COMMENT'",
            refusal("ALTER MASKING POLICY mp SET COMMENT = TRUE"));
        assertEquals("SQL compilation error:\ninvalid value [1] for parameter 'COMMENT'",
            refusal("ALTER MASKING POLICY mp SET COMMENT = (1)"));
    }

    @Test
    public void unknownNamesAreEchoedAsWrittenInTheOrderLiveReportsThem() {
        assertEquals("SQL compilation error:\ninvalid property 'Foo' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET Foo = 1"));
        assertEquals("SQL compilation error:\ninvalid property '\"COMMENT\"' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET \"COMMENT\" = 'q'"));
        assertEquals("SQL compilation error:\ninvalid property 'BODY' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET BODY = val"));
        assertEquals("SQL compilation error:\ninvalid property 'TAG' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp UNSET TAG"));
        assertEquals("SQL compilation error:\ninvalid property 'bar' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp SET foo = 1, bar = 2"));
        assertEquals("SQL compilation error:\ninvalid property 'bar' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp UNSET foo, bar, baz"));
        assertEquals("SQL compilation error:\ninvalid property 'zeta' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY mp UNSET zeta, alpha"));
        assertEquals("SQL compilation error:\ninvalid property 'foo' for 'ROW_ACCESS_POLICY'",
            refusal("ALTER ROW ACCESS POLICY rap SET foo = 'x'"));
        assertEquals("SQL compilation error:\ninvalid property 'x' for 'MASKING_POLICY'",
            refusal("ALTER MASKING POLICY nosuch UNSET x"));
    }

    @Test
    public void aMissingPolicyIsRefusedUnlessIfExistsForgivesIt() {
        assertEquals(hinted("SQL compilation error:\nMasking policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
            + "authorized."), refusal("ALTER MASKING POLICY nosuch SET BODY -> val"));
        assertEquals(hinted("SQL compilation error:\nMasking policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
            + "authorized."), refusal("ALTER MASKING POLICY nosuch SET TAG nosuchtag = 'v'"));
        assertEquals(hinted("SQL compilation error:\nRow access policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
            + "not authorized."), refusal("ALTER ROW ACCESS POLICY nosuch RENAME TO y"));
        engine.execute("ALTER MASKING POLICY IF EXISTS nosuch SET BODY -> val");
        engine.execute("ALTER ROW ACCESS POLICY IF EXISTS nosuch SET COMMENT = 'c'");
    }
}
