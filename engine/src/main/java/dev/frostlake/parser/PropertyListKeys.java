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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Token;

import java.util.Locale;

/**
 * The properties whose value is a list of properties of its own: an authentication policy's MFA_POLICY, PAT_POLICY,
 * WORKLOAD_IDENTITY_POLICY and CLIENT_POLICY. Every other property reads a single value, so a list of properties
 * after it stays the syntax error it is.
 */
public final class PropertyListKeys {

    private static final String[] KEYS = {"MFA_POLICY", "PAT_POLICY", "WORKLOAD_IDENTITY_POLICY", "CLIENT_POLICY"};

    private PropertyListKeys() {
    }

    /**
     * Whether a property's name opens a list of properties.
     *
     * @param name the token that names the property
     * @return whether it does
     */
    public static boolean opens(final Token name) {
        return name != null && isListKey(name.getText());
    }

    /**
     * Whether a property's name, as written, is one whose value is a list of properties.
     *
     * @param name the name
     * @return whether it is
     */
    public static boolean isListKey(final String name) {
        if (name == null) {
            return false;
        }
        final String upper = name.toUpperCase(Locale.ROOT);
        for (final String key : KEYS) {
            if (key.equals(upper)) {
                return true;
            }
        }
        return false;
    }
}
