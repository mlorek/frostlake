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
 * Whether the session reading a definition may act as an object's owner, and whether it sees the object at all —
 * the seam GET_DDL, SHOW and DESCRIBE ask the session through. Only the owner of a SECURE function, procedure or
 * view is shown how it is defined, and an object the session does not see is answered as one that does not exist.
 */
public interface DefinitionOwnership {

    /**
     * @param owner the object's owner role
     * @return whether the session holds that role, by itself or through a role it inherits
     */
    boolean actsAsOwner(String owner);

    /**
     * Whether the session sees an object: it owns it or holds a privilege on it, or holds MANAGE GRANTS.
     *
     * @param objectType the object's kind as privileges record it
     * @param objectName the object's name in full
     * @param owner      the object's owner role, or null when none is recorded
     * @return whether the session sees the object
     */
    boolean sees(String objectType, String objectName, String owner);
}
