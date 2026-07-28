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
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of schema metadata
 */
public class SchemaSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String comment;
    public Instant createdAt;
    public List<TableSnapshot> tables = new ArrayList<>();
    public List<ViewSnapshot> views = new ArrayList<>();
    // Added after the initial release. Old catalog.dat files predate these fields, so deserialization
    // leaves them null (field initializers do not run during deserialization) — restore paths null-guard.
    public List<SequenceSnapshot> sequences = new ArrayList<>();
    // Null on pre-stage snapshots (deserialization bypasses field initializers) — reader null-checks.
    public List<StageSnapshot> stages = new ArrayList<>();
    public List<StreamSnapshot> streams = new ArrayList<>();
    public List<TaskSnapshot> tasks = new ArrayList<>();
    public List<MaskingPolicySnapshot> maskingPolicies = new ArrayList<>();
    public List<FileFormatSnapshot> fileFormats = new ArrayList<>();
    public List<FunctionSnapshot> functions = new ArrayList<>();
    public List<ProcedureSnapshot> procedures = new ArrayList<>();
    public List<PipeSnapshot> pipes = new ArrayList<>();
    public List<DynamicTableSnapshot> dynamicTables = new ArrayList<>();
    public List<RowAccessPolicySnapshot> rowAccessPolicies = new ArrayList<>();
    public List<TagSnapshot> tags = new ArrayList<>();
}
