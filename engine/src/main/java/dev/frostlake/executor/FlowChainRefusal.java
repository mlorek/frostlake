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
 * A refusal of a {@code ->>} chain as a whole, raised as the chain compiles rather than as one of its stages fails, so
 * it is no uncaught STATEMENT_ERROR (see {@link FlowChainFailure}): a {@code $0}, which references no stage at all, and
 * a task statement whose body the chain goes on from.
 */
public final class FlowChainRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal
     */
    public FlowChainRefusal(final String message) {
        super(message);
    }
}
