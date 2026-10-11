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

package dev.frostlake.executor.udf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** The handler states of one running statement (see {@link StatementHandlerStates}). */
public final class HandlerStateScope {

    private static final Logger logger = LoggerFactory.getLogger(HandlerStateScope.class);

    private final Map<HandlerStateKey, AutoCloseable> states = new HashMap<>();

    HandlerStateScope() {
    }

    AutoCloseable get(final HandlerStateKey key, final HandlerStateFactory factory) {
        AutoCloseable state = states.get(key);
        if (state == null) {
            state = factory.create();
            states.put(key, state);
        }
        return state;
    }

    /** Closes every state; one that fails to close does not keep the others open. */
    void close() {
        for (final AutoCloseable state : states.values()) {
            try {
                state.close();
            } catch (final Exception failed) {
                logger.debug("a handler state did not close: {}", failed.getMessage());
            }
        }
        states.clear();
    }
}
