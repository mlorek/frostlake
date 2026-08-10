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
 * CHECK constraints. Unlike the primary, unique and foreign keys Snowflake records without enforcing,
 * a check IS enforced — INSERT, UPDATE and MERGE all refuse a row it makes FALSE (live-verified) —
 * and it is readable in two places: DESCRIBE's {@code check} cell and GET_DDL's constraint line.
 *
 * <p>The measured edges: three-valued logic decides it, so a NULL lets the row through; the DESCRIBE
 * cell belongs to the ONE column an expression names (a check over two columns appears in neither,
 * and where two checks name the same column the later wins); GET_DDL always renders a table-level
 * line, even for a check written on the column; and adding one to an existing table demands ENABLE
 * NOVALIDATE, because live will not validate the rows already there.
 */
public class CheckConstraintTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ck_t (n NUMBER, m NUMBER, CONSTRAINT ck_pos CHECK (n > 0))");
    }

    private String checkCell(final String table, final String column) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE " + table);
        return cell(described, soleRowWhere(described, "name", column), "check");
    }

    private String tableDdl(final String table) {
        return engine.executeQuery("SELECT GET_DDL('TABLE', '" + table + "')")
            .getRows().get(0).getValue(0).toString();
    }

    private long rowCount(final String table) {
        return Long.parseLong(engine.executeQuery("SELECT COUNT(*) FROM " + table)
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void describeShowsTheExpressionOnTheColumnItNames() {
        assertEquals("n > 0", checkCell("ck_t", "N"));
        assertEquals(null, checkCell("ck_t", "M"));
    }

    /** Written on the column, reported the same way. */
    @Test
    public void aColumnLevelCheckReachesTheSameCell() {
        engine.execute("CREATE TABLE ck_col (n NUMBER CHECK (n > 0), m NUMBER)");
        assertEquals("n > 0", checkCell("ck_col", "N"));
        assertEquals(null, checkCell("ck_col", "M"));
    }

    /** A check over two columns hangs on neither. */
    @Test
    public void aCheckSpanningColumnsAppearsInNoCell() {
        engine.execute("CREATE TABLE ck_two (a NUMBER, b NUMBER, CONSTRAINT c_ab CHECK (a < b))");
        assertEquals(null, checkCell("ck_two", "A"));
        assertEquals(null, checkCell("ck_two", "B"));
    }

    @Test
    public void theLaterCheckWinsTheCell() {
        engine.execute("CREATE TABLE ck_dup (a NUMBER CHECK (a > 0), CONSTRAINT c2 CHECK (a < 10))");
        assertEquals("a < 10", checkCell("ck_dup", "A"));
    }

    @Test
    public void getDdlRendersEveryCheckAsATableLevelLine() {
        engine.execute("CREATE TABLE ck_ddl (a NUMBER CHECK (a > 0), CONSTRAINT c2 CHECK (a < 10))");
        final String ddl = tableDdl("ck_ddl");
        assertTrue(ddl.contains("check (a > 0)"), ddl);
        assertTrue(ddl.contains("constraint C2 check (a < 10)"), ddl);
    }

    @Test
    public void aRowTheCheckRefusesIsNotWritten() {
        engine.execute("INSERT INTO ck_t VALUES (5, 1)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ck_t VALUES (-5, 1)");
            }
        });
        assertTrue(ex.getMessage().endsWith("failed because CHECK constraint CK_POS,"
            + " which requires that n > 0, was violated"), ex.getMessage());
        assertEquals(1L, rowCount("ck_t"));
    }

    /** Three-valued logic: only FALSE violates, so a NULL goes in. */
    @Test
    public void aNullLetsTheRowThrough() {
        engine.execute("INSERT INTO ck_t VALUES (NULL, 1)");
        assertEquals(1L, rowCount("ck_t"));
    }

    @Test
    public void updateIsCheckedToo() {
        engine.execute("INSERT INTO ck_t VALUES (5, 1)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("UPDATE ck_t SET n = -1 WHERE n = 5");
            }
        });
        assertEquals(1L, rowCount("ck_t"));
        assertEquals("5", engine.executeQuery("SELECT n FROM ck_t").getRows().get(0).getValue(0).toString());
    }

    @Test
    public void oneBadRowRefusesTheWholeInsert() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ck_t VALUES (1, 1), (-2, 2)");
            }
        });
        assertEquals(0L, rowCount("ck_t"));
    }

    @Test
    public void mergeIsCheckedToo() {
        engine.execute("CREATE TABLE ck_src (n NUMBER, m NUMBER)");
        engine.execute("INSERT INTO ck_src VALUES (-4, 1)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("MERGE INTO ck_t t USING ck_src s ON t.n = s.n"
                    + " WHEN NOT MATCHED THEN INSERT (n, m) VALUES (s.n, s.m)");
            }
        });
        assertEquals(0L, rowCount("ck_t"));
    }

    /** An unnamed check is reported under the generated name, in double quotes. */
    @Test
    public void anUnnamedCheckIsReportedUnderItsGeneratedName() {
        engine.execute("CREATE TABLE ck_anon (n NUMBER CHECK (n > 0))");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ck_anon VALUES (-1)");
            }
        });
        assertTrue(ex.getMessage().contains("CHECK constraint \"SYS_CONSTRAINT_"), ex.getMessage());
        assertTrue(ex.getMessage().endsWith("which requires that n > 0, was violated"), ex.getMessage());
    }

    @Test
    public void addingOneLaterNeedsEnableNovalidate() {
        engine.execute("ALTER TABLE ck_t ADD CONSTRAINT ck_m CHECK (m > 0) ENABLE NOVALIDATE");
        assertEquals("m > 0", checkCell("ck_t", "M"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ck_t VALUES (1, -1)");
            }
        });
    }

    @Test
    public void addingOneWithoutThatSuffixIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ck_t ADD CONSTRAINT ck_m CHECK (m > 0)");
            }
        });
        assertEquals("ALTER TABLE ADD CHECK is not supported with ENABLE VALIDATE"
            + " (you may specify ENABLE NOVALIDATE instead).", ex.getMessage());
    }

    /** NOT ENFORCED is refused on ALTER with the same sentence, and accepted at CREATE. */
    @Test
    public void notEnforcedIsRefusedOnAlterAndAcceptedOnCreate() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ck_t ADD CONSTRAINT ck_m CHECK (m > 0) NOT ENFORCED");
            }
        });
        assertEquals("ALTER TABLE ADD CHECK is not supported with ENABLE VALIDATE"
            + " (you may specify ENABLE NOVALIDATE instead).", ex.getMessage());
        engine.execute("CREATE TABLE ck_ne (n NUMBER, CONSTRAINT c CHECK (n > 0) NOT ENFORCED)");
        engine.execute("CREATE TABLE ck_en (n NUMBER, CONSTRAINT c CHECK (n > 0) ENABLE NOVALIDATE)");
    }

    /** Rows already there are left alone — that is what NOVALIDATE says. */
    @Test
    public void existingRowsAreNotValidated() {
        engine.execute("CREATE TABLE ck_old (n NUMBER)");
        engine.execute("INSERT INTO ck_old VALUES (-1)");
        engine.execute("ALTER TABLE ck_old ADD CONSTRAINT c_pos CHECK (n > 0) ENABLE NOVALIDATE");
        assertEquals(1L, rowCount("ck_old"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO ck_old VALUES (-2)");
            }
        });
    }

    @Test
    public void dropConstraintRemovesTheCheck() {
        engine.execute("ALTER TABLE ck_t DROP CONSTRAINT ck_pos");
        assertEquals(null, checkCell("ck_t", "N"));
        engine.execute("INSERT INTO ck_t VALUES (-5, 1)");
        assertEquals(1L, rowCount("ck_t"));
    }

    @Test
    public void droppingAConstraintNobodyHasIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ck_t DROP CONSTRAINT no_such");
            }
        });
        assertEquals("SQL compilation error:\nconstraint 'NO_SUCH' does not exist", ex.getMessage());
    }

    /** CHECK is reserved: it cannot name a table or a column. */
    @Test
    public void checkIsNotAvailableAsAName() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE kw_t (check NUMBER)");
            }
        });
    }

    /** NOVALIDATE, unlike CHECK, is an ordinary word. */
    @Test
    public void novalidateRemainsUsableAsAName() {
        engine.execute("CREATE TABLE kw_t2 (novalidate NUMBER, validate NUMBER)");
        engine.execute("INSERT INTO kw_t2 VALUES (1, 2)");
        assertEquals("1", engine.executeQuery("SELECT novalidate FROM kw_t2")
            .getRows().get(0).getValue(0).toString());
    }
}
