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

package dev.frostlake.security;

/**
 * What a session runs with for a parameter it has not set itself: the value its user carries, else the account's.
 * A session's own ALTER SESSION SET always comes first.
 */
public interface InheritedParameters {

    /**
     * The value a user's sessions inherit for a parameter.
     *
     * @param user the session's user
     * @param name the parameter's name, upper case
     * @return the value as a session holds one — a Long, a Boolean or a String — or null when neither the user nor
     *         the account sets the parameter
     */
    Object value(String user, String name);
}
