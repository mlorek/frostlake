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

/**
 * Ends a body that issues a statement past the depth bodies may run to (see {@link CallDepth}). It is no failure
 * of the body's own: no handler of the body catches it and no block of the body words it, and the statement that
 * started the body reports {@link CallDepth#REFUSAL} in its place.
 */
public final class CallDepthExceeded extends BodyAbort {

    private static final long serialVersionUID = 1L;

    private final int level;

    /**
     * @param level the level of the body that issued the statement
     */
    public CallDepthExceeded(final int level) {
        super(CallDepth.REFUSAL);
        this.level = level;
    }

    /** The level of the body that was ended. */
    public int getLevel() {
        return level;
    }
}
