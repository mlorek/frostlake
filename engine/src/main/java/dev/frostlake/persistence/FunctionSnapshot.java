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

import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.types.DataType;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serializable snapshot of a user-defined FUNCTION (any language, scalar or table). */
public class FunctionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public List<ParameterSnapshot> parameters = new ArrayList<>();
    public String returnType;
    /** Whether the RETURNS type was written with its width; null on older snapshots. */
    public Boolean returnSized;
    /** Whether the RETURNS type was written NOT NULL; absent in a snapshot written before it was recorded. */
    public Boolean returnNotNull;
    /** The return type itself (see {@link ParameterSnapshot#type}); null on older snapshots. */
    public DataType returnDataType;
    public List<ParameterSnapshot> returnColumns = new ArrayList<>();
    public String body;
    public boolean tableFunction;
    public String language;
    public String handler;
    public String runtimeVersion;
    public String nullHandling;
    public String volatility;
    public boolean secure;
    public boolean memoizable;
    public List<String> imports = new ArrayList<>();
    /** Packages, artifact repository, target path, integrations and secrets; null on older snapshots. */
    public RoutineEnvironment environment;
    public String comment;
    public String owner;

    // A service function's SERVICE, ENDPOINT and MAX_BATCH_ROWS; null for every other function and on old snapshots.
    public String serviceName;
    public String serviceEndpoint;
    public Long maxBatchRows;

    // An external function's API_INTEGRATION and URL; null for every other function and on old snapshots.
    /** Whether the function is a user-defined aggregate; false in a snapshot taken before aggregates were kept. */
    public boolean aggregate;
    public String apiIntegration;
    public String externalUrl;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
    // When the object was created. Null on snapshots written before it was saved, which restore it as new.
    public Instant createdAt;
    /** INFORMATION_SCHEMA's LAST_DDL. Null on snapshots written before it was saved, which report the CREATE. */
    public Instant lastDdlAt;
}
