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
import dev.frostlake.metastore.StatementAnswers;
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * LAST_TRANSACTION() — the public id of the last transaction the CALLING session committed or rolled back, as
 * decimal text, or NULL when that session has ended none. It is the same number CURRENT_TRANSACTION() showed
 * inside that transaction.
 */
public class LastTransaction extends BuiltInFunction {
    private final SessionContext sessionContext;

    public LastTransaction(final SessionContext sessionContext) {
        super("LAST_TRANSACTION", StringType.VARCHAR);
        this.sessionContext = sessionContext;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A replayed statement answers the transaction it first saw end (see StatementAnswers).
        return StatementAnswers.answered(getName(),
            sessionContext != null ? sessionContext.getLastTransactionId() : null);
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
