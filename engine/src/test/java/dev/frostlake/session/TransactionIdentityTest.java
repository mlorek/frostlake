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

package dev.frostlake.session;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transaction has ONE id: the 19-digit number CURRENT_TRANSACTION() answers inside it, SHOW TRANSACTIONS
 * and SHOW LOCKS list for it, and LAST_TRANSACTION() answers after it ends. SYSTEM$ABORT_TRANSACTION takes
 * that id and aborts the transaction it names.
 */
public class TransactionIdentityTest extends BaseDatabaseTest {

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** Inside a transaction the id is a 19-digit number, and SHOW TRANSACTIONS lists the same one. */
    @Test
    public void theListedIdIsCurrentTransaction() {
        engine.execute("BEGIN");
        assertTrue(one("SELECT CURRENT_TRANSACTION()").matches("\\d{19}"));
        engine.execute("SHOW TRANSACTIONS");
        assertEquals("1", one("SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
            + "WHERE \"id\" = CURRENT_TRANSACTION()"));
        engine.execute("COMMIT");
    }

    /** After a COMMIT or a ROLLBACK, LAST_TRANSACTION() is the id CURRENT_TRANSACTION() showed inside it. */
    @Test
    public void lastTransactionIsTheIdTheTransactionHad() {
        engine.execute("BEGIN");
        engine.execute("SET inside_id = CURRENT_TRANSACTION()");
        engine.execute("COMMIT");
        assertEquals("true", one("SELECT LAST_TRANSACTION() = $inside_id"));
        engine.execute("BEGIN");
        engine.execute("SET rolled_id = CURRENT_TRANSACTION()");
        engine.execute("ROLLBACK");
        assertEquals("true", one("SELECT LAST_TRANSACTION() = $rolled_id"));
    }

    /** Outside a transaction there is none. */
    @Test
    public void outsideATransactionThereIsNone() {
        assertEquals("null", one("SELECT CURRENT_TRANSACTION()"));
    }

    /** Both answer a widthless VARCHAR. */
    @Test
    public void bothAreWidthlessText() {
        assertEquals("VARCHAR[LOB]", one("SELECT SYSTEM$TYPEOF(CURRENT_TRANSACTION())"));
        assertEquals("VARCHAR[LOB]", one("SELECT SYSTEM$TYPEOF(LAST_TRANSACTION())"));
    }

    /** An id that names no running transaction is answered, not refused. */
    @Test
    public void abortingAnUnknownIdSaysSo() {
        assertEquals("Could not abort txn: 1234567890123456789",
            one("SELECT SYSTEM$ABORT_TRANSACTION(1234567890123456789)"));
    }
}
