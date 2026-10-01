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
import java.time.Instant;

/** A future grant a role holds on a database or schema, as a snapshot holds it. */
public class FutureGrantSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The kind of object the grant covers, e.g. TABLE. */
    public String objectKind;

    /** DATABASE or SCHEMA. */
    public String scopeKind;

    /** The qualified name of the database or schema. */
    public String scopeName;

    /** The privilege granted on every future object. */
    public String privilege;

    /** Whether the grant carries WITH GRANT OPTION. */
    public boolean grantOption;

    /** When the grant was made. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public Instant createdOn;
}
