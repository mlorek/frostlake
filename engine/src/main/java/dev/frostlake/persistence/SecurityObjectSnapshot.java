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
package dev.frostlake.persistence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;

/** A network rule, network policy, password policy or secret, as a snapshot holds it. */
public class SecurityObjectSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The object's kind, as the enum constant's name. */
    public String kind;
    /** The object's canonical name. */
    public String name;
    /** The owning role. */
    public String owner;
    /** The comment, or null. */
    public String comment;
    /** When the object was created, in epoch milliseconds. */
    public long createdMillis;
    /** The properties that are set: strings, longs, booleans and lists of strings. */
    public LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    /** The tags set on the object. */
    public HashMap<String, String> tags = new HashMap<>();
}
