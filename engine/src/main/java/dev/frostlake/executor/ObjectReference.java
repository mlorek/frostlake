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

import java.util.ArrayList;
import java.util.List;

/** The object a SYSTEM$REFERENCE token stands for, by its canonical name, and the privileges it was made with. */
final class ObjectReference {

    private final String[] parts;
    private final List<String> privileges;

    /**
     * @param parts      the object's canonical name, one part per level
     * @param privileges the privileges the reference carries, upper-cased; empty where none was given
     */
    ObjectReference(final String[] parts, final List<String> privileges) {
        this.parts = parts.clone();
        this.privileges = new ArrayList<>(privileges);
    }

    /** @return the object's canonical name parts */
    String[] getParts() {
        return parts.clone();
    }

    /** @return the privileges the reference carries, empty where none was given */
    List<String> getPrivileges() {
        return new ArrayList<>(privileges);
    }
}
