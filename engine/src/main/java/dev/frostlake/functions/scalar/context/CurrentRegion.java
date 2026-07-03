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

public class CurrentRegion extends BuiltInFunction {
    private final EngineConfig config;

    public CurrentRegion(final EngineConfig config) {
        super("CURRENT_REGION", StringType.VARCHAR);
        this.config = config;
    }

    // Convenience constructor for backward compat
    public CurrentRegion() {
        this(new EngineConfig());
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return config != null ? config.getRegion() : "PUBLIC.AWS_US_EAST_1";
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 0; }
}
