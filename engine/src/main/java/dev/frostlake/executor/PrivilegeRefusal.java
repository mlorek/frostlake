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
 * The refusal of a statement that needs a privilege the session does not hold on an object another role owns, in
 * the account's own sentence (see {@link SqlAccessControlError#privilegeRequired}). It is a type of its own so
 * that a view's expansion can tell it from every other refusal: raised over a relation the view's query reads,
 * it is reported under the view's name.
 */
public final class PrivilegeRefusal extends SecurityException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal as a client reads it
     */
    public PrivilegeRefusal(final String message) {
        super(message);
    }
}
