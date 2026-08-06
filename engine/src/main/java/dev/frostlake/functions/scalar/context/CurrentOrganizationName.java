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
 * CURRENT_ORGANIZATION_NAME() — the organization the account belongs to, configured through
 * {@code organization.name}.
 *
 * <p>Reported UPPER-CASED, measured: an account whose organization-account identifier is
 * {@code TWEPWDT-WJ64893} answers {@code TWEPWDT}, and {@code LOWER(...) = ...} is false there.
 * The docs render the example in lower case, which is the documentation's styling rather than the
 * account's answer — one more reason to measure the cell instead of reading it.
 */
public class CurrentOrganizationName extends BuiltInFunction {

    private final EngineConfig config;

    public CurrentOrganizationName(final EngineConfig config) {
        super("CURRENT_ORGANIZATION_NAME", StringType.VARCHAR);
        this.config = config;
    }

    @Override
    public Object evaluate(final List<Object> args) { return config.getOrganizationName(); }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 0; }
}
