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

package dev.frostlake.transaction;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transaction may be NAMED, and the name is what {@code SHOW TRANSACTIONS} reports in place of the
 * UUID an unnamed one carries — live-verified for every opening spelling ({@code BEGIN NAME},
 * {@code BEGIN WORK NAME}, {@code BEGIN TRANSACTION NAME}, {@code START TRANSACTION NAME}).
 *
 * <p>The name is an IDENTIFIER, with an identifier's rules: unquoted folds upper, quoted keeps its
 * case, and a string literal is refused outright. Two measured oddities are pinned here rather than
 * tidied away — a DOTTED name is accepted and only its FIRST part survives, and {@code NAME} itself
 * is a legal name — and only the OPENING statement takes one: {@code COMMIT NAME x} is a syntax error.
 */
public class TransactionNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE tx_rows (n INT)");
    }

    @Override
    protected void teardownTest() {
        // Every test here leaves a transaction open; the next one must not inherit it.
        engine.execute("ROLLBACK");
    }

    /** The name cell of the session's one open transaction. */
    private String openTransactionName() {
        engine.execute("INSERT INTO tx_rows VALUES (1)");
        final ResultSet transactions = engine.executeQuery("SHOW TRANSACTIONS");
        assertEquals(1, transactions.getRows().size(), "expected exactly one open transaction");
        return cell(transactions, transactions.getRows().get(0), "name");
    }

    private void assertRefused(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(ex.getMessage()).contains("syntax error"),
            "expected a syntax error, got: " + ex.getMessage());
    }

    @Test
    public void anUnnamedTransactionIsNamedByAUuid() {
        engine.execute("BEGIN");
        assertTrue(openTransactionName()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "an unnamed transaction carries a generated UUID");
    }

    @Test
    public void beginNameNamesTheTransaction() {
        engine.execute("BEGIN NAME my_txn");
        assertEquals("MY_TXN", openTransactionName());
    }

    @Test
    public void beginWorkTakesANameToo() {
        engine.execute("BEGIN WORK NAME w1");
        assertEquals("W1", openTransactionName());
    }

    @Test
    public void beginTransactionTakesANameToo() {
        engine.execute("BEGIN TRANSACTION NAME txn_two");
        assertEquals("TXN_TWO", openTransactionName());
    }

    @Test
    public void startTransactionTakesANameToo() {
        engine.execute("START TRANSACTION NAME txn_three");
        assertEquals("TXN_THREE", openTransactionName());
    }

    @Test
    public void aQuotedNameKeepsItsCase() {
        engine.execute("BEGIN NAME \"MixedCase\"");
        assertEquals("MixedCase", openTransactionName());
    }

    /** Measured, not tidied: live accepts a dotted name and keeps only the first part. */
    @Test
    public void aDottedNameKeepsOnlyItsFirstPart() {
        engine.execute("BEGIN NAME a.b");
        assertEquals("A", openTransactionName());
    }

    @Test
    public void nameIsItselfALegalName() {
        engine.execute("BEGIN NAME name");
        assertEquals("NAME", openTransactionName());
    }

    @Test
    public void aStringLiteralIsNotAName() {
        assertRefused("BEGIN NAME 'a string'");
        engine.execute("BEGIN");   // leave the state this class's teardown expects
    }

    @Test
    public void onlyTheOpeningStatementTakesAName() {
        engine.execute("BEGIN NAME z");
        assertRefused("COMMIT NAME z");
    }

    @Test
    public void nameRemainsUsableAsAColumnName() {
        engine.execute("BEGIN");
        engine.execute("CREATE TABLE kw_column (name INT)");
        engine.execute("INSERT INTO kw_column VALUES (7)");
        assertEquals("7", engine.executeQuery("SELECT name FROM kw_column WHERE name = 7")
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void nameRemainsUsableAsATableName() {
        engine.execute("BEGIN");
        engine.execute("CREATE TABLE name (n INT)");
        engine.execute("INSERT INTO name VALUES (3)");
        assertEquals("3", engine.executeQuery("SELECT n FROM name").getRows().get(0).getValue(0).toString());
    }
}
