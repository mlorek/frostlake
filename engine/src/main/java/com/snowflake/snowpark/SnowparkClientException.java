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


package com.snowflake.snowpark;

/**
 * The exception the Snowpark client raises for what it will not do with a value, under the name a handler sees
 * it by: a cell read through an accessor of another kind is {@code Error Code: 0400, Error message: Cannot cast
 * java.lang.String(a) to Int.}
 */
public class SnowparkClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SnowparkClientException(final String message) {
        super(message);
    }
}
