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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A constraint can be renamed and its enforcement properties changed, through either the ALTER or the
 * MODIFY spelling — all live-verified, including two properties in one statement.
 *
 * <p>Two measured details are load-bearing. {@code VALIDATE} and {@code NOVALIDATE} are NOT
 * constraint properties: the documentation lists them, live refuses both as syntax errors, so they
 * stay refused here — admitting them would be leniency. And the two "not there" refusals do not
 * share a sentence: a rename answers {@code constraint 'X' does not exist} while altering answers the
 * generic {@code Object 'X' does not exist or not authorized.}
 *
 * <p>RELY is asserted through SHOW UNIQUE KEYS' {@code rely} cell — the flag rides on the
 * constraint's columns, which is where that cell reads from — rather than by the statement merely
 * being accepted. ENFORCED has no read surface on either engine, so it is only pinned as accepted.
 */
public class AlterConstraintClausesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT, m INT,"
            + " CONSTRAINT u1 UNIQUE (n), CONSTRAINT u2 UNIQUE (m))");
    }

    /** The {@code rely} cell SHOW UNIQUE KEYS reports for one named constraint. */
    private String relyOf(final String constraintName) {
        final ResultSet keys = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE t");
        return cell(keys, soleRowWhere(keys, "constraint_name", constraintName), "rely");
    }

    private RuntimeException refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    // ── RENAME CONSTRAINT ─────────────────────────────────────────────────────────────────────────

    @Test
    public void renameConstraintMovesTheName() {
        engine.execute("ALTER TABLE t RENAME CONSTRAINT u1 TO u1x");
        // The new name is the one that answers now; the old one is gone.
        engine.execute("ALTER TABLE t RENAME CONSTRAINT u1x TO u1");
        assertEquals("SQL compilation error:\nconstraint 'U1X' does not exist",
            refusalOf("ALTER TABLE t RENAME CONSTRAINT u1x TO other").getMessage());
    }

    @Test
    public void renamingAnUnknownConstraintIsRefused() {
        assertEquals("SQL compilation error:\nconstraint 'NOSUCH' does not exist",
            refusalOf("ALTER TABLE t RENAME CONSTRAINT nosuch TO other").getMessage());
    }

    // ── enforcement properties ────────────────────────────────────────────────────────────────────

    @Test
    public void relyIsRecordedAndReadsBack() {
        engine.execute("ALTER TABLE t ALTER CONSTRAINT u1 RELY");
        assertEquals("true", relyOf("U1"));
        engine.execute("ALTER TABLE t ALTER CONSTRAINT u1 NORELY");
        assertEquals("false", relyOf("U1"));
    }

    @Test
    public void modifyIsTheSameStatement() {
        engine.execute("ALTER TABLE t MODIFY CONSTRAINT u2 RELY");
        assertEquals("true", relyOf("U2"));
    }

    @Test
    public void enforcementFlagsAreAccepted() {
        engine.execute("ALTER TABLE t ALTER CONSTRAINT u2 ENFORCED");
        engine.execute("ALTER TABLE t MODIFY CONSTRAINT u2 NOT ENFORCED");
    }

    @Test
    public void severalPropertiesInOneStatement() {
        engine.execute("ALTER TABLE t ALTER CONSTRAINT u2 NOT ENFORCED NORELY");
        assertEquals("false", relyOf("U2"));
    }

    /** The documentation lists these; live refuses them, so they must stay refused. */
    @Test
    public void validateIsNotAConstraintProperty() {
        assertTrue(refusalOf("ALTER TABLE t ALTER CONSTRAINT u2 VALIDATE")
            .getMessage().contains("syntax error"));
        assertTrue(refusalOf("ALTER TABLE t ALTER CONSTRAINT u2 NOVALIDATE")
            .getMessage().contains("syntax error"));
    }

    @Test
    public void alteringAnUnknownConstraintUsesTheObjectSentence() {
        assertEquals("SQL compilation error:\nObject 'NOSUCH' does not exist or not authorized.",
            refusalOf("ALTER TABLE t ALTER CONSTRAINT nosuch RELY").getMessage());
    }

    // ── the keywords stay ordinary names ──────────────────────────────────────────────────────────

    @Test
    public void norelyAndEnforcedRemainUsableAsNames() {
        engine.execute("CREATE TABLE kw (norely INT, enforced INT)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
        assertEquals("1", engine.executeQuery("SELECT norely FROM kw WHERE enforced = 2")
            .getRows().get(0).getValue(0).toString());
    }
}
