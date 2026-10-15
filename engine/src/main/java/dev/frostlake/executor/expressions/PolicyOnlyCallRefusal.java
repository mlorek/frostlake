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
package dev.frostlake.executor.expressions;

/**
 * The refusal of a call that only a policy's body may make. It is raised while the statement that writes the call
 * compiles, so a statement that only stores the call — a view, a function — is refused as it is created, where a
 * fault of the call's value would be its reader's.
 */
public final class PolicyOnlyCallRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal, as the account words it
     */
    public PolicyOnlyCallRefusal(final String message) {
        super(message);
    }
}
