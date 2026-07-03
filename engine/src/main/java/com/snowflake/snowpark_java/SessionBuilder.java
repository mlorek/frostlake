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

package com.snowflake.snowpark_java;

import java.util.Map;

/**
 * Stub of Snowpark's {@code Session.builder()} fluent builder. The configuration methods are accepted
 * so handler code compiles against the real Snowpark idiom, but {@link #create()} is rejected: Snowflake
 * prohibits creating a new session from within a stored procedure (see the Java stored-procedure
 * limitations documentation). Building the config is allowed; only the terminal create fails — matching
 * Snowflake, where the prohibition is on session creation, not on assembling options.
 */
public class SessionBuilder {

    public SessionBuilder configs(final Map<String, String> options) {
        return this;
    }

    public SessionBuilder config(final String key, final String value) {
        return this;
    }

    /**
     * @throws UnsupportedOperationException always — a new session cannot be created inside a stored procedure.
     */
    public Session create() {
        throw new UnsupportedOperationException(
            "creating a new session is not supported inside a stored procedure");
    }
}
