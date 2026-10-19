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

package dev.frostlake.executor;

import java.util.TreeMap;

/**
 * The block's variables as one of its SQL expressions reads them, by name in any letter case: a name the running
 * handlers may not read is refused where the expression looks it up (see {@link HandlerLets}), and a value is handed
 * over as the block's reads convert it (see {@link VariableReadConversion}).
 */
public final class HandlerGuardedVariables extends TreeMap<String, Object> {

    private static final long serialVersionUID = 1L;

    private final transient HandlerLets handlerLets;
    private final transient VariableReadConversion reads;

    /**
     * @param handlerLets what the running handlers may not read
     */
    public HandlerGuardedVariables(final HandlerLets handlerLets) {
        this(handlerLets, null);
    }

    /**
     * @param handlerLets what the running handlers may not read
     * @param reads       converts a value as the expression reads it, or null to hand each value over as held
     */
    public HandlerGuardedVariables(final HandlerLets handlerLets, final VariableReadConversion reads) {
        super(String.CASE_INSENSITIVE_ORDER);
        this.handlerLets = handlerLets;
        this.reads = reads;
    }

    @Override
    public boolean containsKey(final Object key) {
        // Refused whether or not the name holds a value yet: a LET the block never reached is no more the
        // handler's to read than one it ran.
        if (key instanceof String) {
            handlerLets.requireReadable((String) key, 0, 0);
        }
        return super.containsKey(key);
    }

    @Override
    public Object get(final Object key) {
        if (!(key instanceof String)) {
            return super.get(key);
        }
        handlerLets.requireReadable((String) key, 0, 0);
        final Object value = super.get(key);
        return reads == null ? value : reads.read((String) key, value);
    }
}
