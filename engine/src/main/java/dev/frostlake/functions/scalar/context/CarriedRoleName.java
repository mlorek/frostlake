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

/**
 * A role name that reached IS_ROLE_IN_SESSION or IS_DATABASE_ROLE_IN_SESSION from a row rather than from the
 * statement's own text: it is matched as it is spelled, where a written one is read as an identifier (see
 * {@link RoleNameText}).
 */
public final class CarriedRoleName {

    private final String text;

    /**
     * @param text the text the row carried, or null
     */
    public CarriedRoleName(final String text) {
        this.text = text;
    }

    /** The text the row carried, or null. */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return String.valueOf(text);
    }
}
