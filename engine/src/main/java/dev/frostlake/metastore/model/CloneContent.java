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


package dev.frostlake.metastore.model;

/**
 * What a database or schema clone copies beyond its own definition, as its CLONE clause asks. A clone copies a named
 * external stage always, a named internal one only under {@code INCLUDE INTERNAL STAGES}, and a temporary stage never
 * (live-verified); {@code IGNORE HYBRID TABLES} leaves the hybrid tables out (documented). {@code IGNORE TABLES WITH
 * INSUFFICIENT DATA RETENTION}, which a clone at a point of Time Travel takes, asks nothing of a clone this engine
 * makes.
 */
public final class CloneContent {

    /** A clone whose clause asks for nothing beyond what every clone copies. */
    public static final CloneContent DEFAULT = new CloneContent(false, false);

    private final boolean internalStages;
    private final boolean hybridTablesIgnored;

    /**
     * The content a clause asks for.
     *
     * @param internalStages      whether the named internal stages are copied too
     * @param hybridTablesIgnored whether the hybrid tables are left out
     */
    public CloneContent(final boolean internalStages, final boolean hybridTablesIgnored) {
        this.internalStages = internalStages;
        this.hybridTablesIgnored = hybridTablesIgnored;
    }

    /**
     * Whether the named internal stages are copied.
     *
     * @return true under INCLUDE INTERNAL STAGES
     */
    public boolean includesInternalStages() {
        return internalStages;
    }

    /**
     * Whether the hybrid tables are left out.
     *
     * @return true under IGNORE HYBRID TABLES
     */
    public boolean ignoresHybridTables() {
        return hybridTablesIgnored;
    }
}
