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

package dev.frostlake.http;

/**
 * Told about each session the idle sweep ends, so the engine can do for an EXPIRED session exactly what it
 * does for a released one: roll its open transaction back and drop the state it kept for it.
 */
public interface ExpiredSessionListener {

    /**
     * One session has just been removed from the manager for sitting idle too long.
     *
     * @param session the session, already out of the manager's map
     */
    void expired(SessionContext session);
}
