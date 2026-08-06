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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * CURRENT_ACCOUNT_NAME() — the account's NAME, configured through {@code account.name}.
 *
 * <p>Not the same identifier as {@link CurrentAccount}, which answers the LOCATOR. Measured together
 * on one account: {@code CURRENT_ACCOUNT()} is {@code PG65914} while {@code CURRENT_ACCOUNT_NAME()} is
 * {@code WJ64893}, and SHOW ACCOUNTS reports them as separate {@code account_locator} and
 * {@code account_name} columns. Treating the two as one value is the easy mistake here.
 */
public class CurrentAccountName extends BuiltInFunction {

    private final EngineConfig config;

    public CurrentAccountName(final EngineConfig config) {
        super("CURRENT_ACCOUNT_NAME", StringType.VARCHAR);
        this.config = config;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return config.getAccountName();
    }

    @Override
    public int getMinArgCount() {
        return 0;
    }

    @Override
    public int getMaxArgCount() {
        return 0;
    }
}
