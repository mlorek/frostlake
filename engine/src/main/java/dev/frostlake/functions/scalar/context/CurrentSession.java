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
import dev.frostlake.security.SessionContext;
import dev.frostlake.types.StringType;

import java.util.List;
import java.util.UUID;

public class CurrentSession extends BuiltInFunction {
    private final SessionContext sessionContext;

    public CurrentSession(final SessionContext sessionContext) {
        super("CURRENT_SESSION", StringType.VARCHAR);
        this.sessionContext = sessionContext;
    }

    private static final String FALLBACK_SESSION_ID =
        String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()));

    @Override
    public Object evaluate(final List<Object> args) {
        return sessionContext != null ? sessionContext.getSessionId() : FALLBACK_SESSION_ID;
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
