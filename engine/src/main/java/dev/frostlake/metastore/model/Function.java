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

import dev.frostlake.metastore.SqlObject;
import dev.frostlake.types.DataType;

import java.util.ArrayList;
import java.util.List;

public class Function extends SqlObject {

    private String serviceName;
    private String serviceEndpoint;
    private Long maxBatchRows;
    private final List<Parameter> parameters;
    private final DataType returnType;
    private final List<Parameter> returnColumns; // for RETURNS TABLE(col TYPE, ...)
    private final String body;
    private final boolean isTableFunction;
    private final UdfLanguage language;
    private final String handler;
    private final String runtimeVersion;
    private NullHandling nullHandling = NullHandling.CALLED_ON_NULL_INPUT;
    private Volatility volatility = Volatility.VOLATILE;
    private boolean secure = false;
    private boolean memoizable = false;
    private boolean temporary = false;
    private List<String> imports = new ArrayList<>();

    public Function(final String name, final List<Parameter> parameters,
                   final DataType returnType, final String body, final boolean isTableFunction) {
        this(name, parameters, returnType, null, body, isTableFunction, "SQL", null, null);
    }

    public Function(final String name, final List<Parameter> parameters,
                   final DataType returnType, final String body, final boolean isTableFunction, final String language) {
        this(name, parameters, returnType, null, body, isTableFunction, language, null, null);
    }

    public Function(final String name, final List<Parameter> parameters,
                   final DataType returnType, final String body, final boolean isTableFunction,
                   final String language, final String handler, final String runtimeVersion) {
        this(name, parameters, returnType, null, body, isTableFunction, language, handler, runtimeVersion);
    }

    public Function(final String name, final List<Parameter> parameters,
                   final DataType returnType, final List<Parameter> returnColumns, final String body,
                   final boolean isTableFunction, final String language,
                   final String handler, final String runtimeVersion) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.returnType = returnType;
        this.returnColumns = returnColumns != null ? new ArrayList<>(returnColumns) : new ArrayList<>();
        this.body = body;
        this.isTableFunction = isTableFunction;
        this.language = UdfLanguage.fromString(language);
        this.handler = handler;
        this.runtimeVersion = runtimeVersion;
    }

    public List<Parameter> getParameters() {
        return new ArrayList<>(parameters);
    }

    public List<Parameter> getReturnColumns() {
        return new ArrayList<>(returnColumns);
    }

    public DataType getReturnType() {
        return returnType;
    }

    public String getBody() {
        return body;
    }

    public boolean isTableFunction() {
        return isTableFunction;
    }

    /** The declared language as its canonical uppercase name (e.g. {@code "PYTHON"}); {@code "SQL"} by default. */
    public String getLanguage() {
        return language.name();
    }

    /** The declared language as a typed {@link UdfLanguage} (for runtime dispatch); {@link UdfLanguage#SQL} by default. */
    public UdfLanguage getUdfLanguage() {
        return language;
    }

    public String getHandler() {
        return handler;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public String getNullHandling() { return nullHandling.getSqlText(); }
    public void setNullHandling(final String v) { this.nullHandling = NullHandling.fromString(v); }

    public String getVolatility() { return volatility.name(); }
    public void setVolatility(final String v) { this.volatility = Volatility.fromString(v); }

    public boolean isSecure() { return secure; }
    public void setSecure(final boolean secure) { this.secure = secure; }

    /**
     * MEMOIZABLE, which the account reports in SHOW FUNCTIONS and INFORMATION_SCHEMA.FUNCTIONS. The result
     * is not cached: for a deterministic body a cached and a recomputed result cannot be told apart.
     */
    public boolean isMemoizable() { return memoizable; }
    public void setMemoizable(final boolean memoizable) { this.memoizable = memoizable; }

    /**
     * A TEMPORARY (TEMP / VOLATILE) function lives only as long as the session that created it, the same
     * lifetime Frostlake gives a temporary table. Live also makes it invisible to other sessions and
     * lets it shadow a permanent object of the same name; Frostlake has one namespace per catalog, so
     * it models the lifetime and not the isolation.
     */
    public boolean isTemporary() { return temporary; }

    public void setTemporary(final boolean temporary) { this.temporary = temporary; }

    public List<String> getImports() { return new ArrayList<>(imports); }
    public void setImports(final List<String> imports) { this.imports = imports != null ? new ArrayList<>(imports) : new ArrayList<>(); }

    public void rename(final String newName) {
        throw new UnsupportedOperationException("Functions cannot be renamed");
    }

    @Override
    public String getObjectType() {
        return "FUNCTION";
    }

    /** The service a service function sends its calls to, or null for any other function. */
    public String getServiceName() {
        return serviceName;
    }

    /** The endpoint of that service, or null. */
    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    /** The largest batch a service function sends, or null for the default. */
    public Long getMaxBatchRows() {
        return maxBatchRows;
    }

    /**
     * Makes this a service function.
     *
     * @param service the service's qualified name
     * @param endpoint the endpoint's name
     * @param batchRows the largest batch, or null
     */
    public void setService(final String service, final String endpoint, final Long batchRows) {
        this.serviceName = service;
        this.serviceEndpoint = endpoint;
        this.maxBatchRows = batchRows;
    }

    /** Whether this is a service function. */
    public boolean isServiceFunction() {
        return serviceName != null;
    }
}
