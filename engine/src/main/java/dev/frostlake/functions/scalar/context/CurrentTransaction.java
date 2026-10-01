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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * CURRENT_TRANSACTION() — the open transaction's public id as decimal text, or NULL when none is open.
 *
 * <p>Live-verified: inside an open transaction the call answers a string of digits, and outside one it
 * answers NULL, so {@code CURRENT_TRANSACTION() IS NOT NULL} is the plain test for "am I in a
 * transaction". Frostlake used to answer NULL always, which made that test read FALSE inside one.
 */
public class CurrentTransaction extends BuiltInFunction {

    private TransactionManager transactionManager;

    public CurrentTransaction() { super("CURRENT_TRANSACTION", StringType.VARCHAR); }

    /**
     * Wire the manager holding the session's transaction; the engine does this once both exist.
     *
     * @param transactionManager the engine's transaction manager
     */
    public void setTransactionManager(final TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (transactionManager == null) {
            return null;
        }
        final Transaction open = transactionManager.getCurrentTransaction();
        // The transaction's PUBLIC id — the number SHOW TRANSACTIONS and SHOW LOCKS list for it, so a
        // listing can be filtered by it — never the engine's internal counter.
        return open == null ? null : String.valueOf(open.getPublicId());
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
