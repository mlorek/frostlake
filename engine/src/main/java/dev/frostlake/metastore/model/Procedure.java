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

public class Procedure extends SqlObject {

    private final List<Parameter> parameters;
    private final DataType returnType;
    private final String body;
    private final UdfLanguage language;
    private final String handler;
    private final String runtimeVersion;
    private final List<String> packages;
    private ExecuteAs executeAs = ExecuteAs.OWNER;
    private List<String> imports = new ArrayList<>();
    // Declared RETURNS TABLE(col TYPE, ...) columns — name the result of a CALL / TABLE(proc()) source.
    private List<Parameter> returnColumns = new ArrayList<>();
    private boolean temporary = false;

    /**
     * A TEMPORARY (TEMP / VOLATILE) procedure lives only as long as the session that created it, the same
     * lifetime Frostlake gives a temporary table. Live also makes it invisible to other sessions and
     * lets it shadow a permanent object of the same name; Frostlake has one namespace per catalog, so
     * it models the lifetime and not the isolation.
     */
    public boolean isTemporary() { return temporary; }

    public void setTemporary(final boolean temporary) { this.temporary = temporary; }

    public Procedure(final String name, final List<Parameter> parameters,
                    final DataType returnType, final String body) {
        this(name, parameters, returnType, body, "SQL", null, null, null);
    }

    public Procedure(final String name, final List<Parameter> parameters,
                    final DataType returnType, final String body, final String language) {
        this(name, parameters, returnType, body, language, null, null, null);
    }

    public Procedure(final String name, final List<Parameter> parameters,
                    final DataType returnType, final String body, final String language,
                    final String handler, final String runtimeVersion, final List<String> packages) {
        super(name);
        this.parameters = new ArrayList<>(parameters);
        this.returnType = returnType;
        this.body = body;
        this.language = UdfLanguage.fromString(language);
        this.handler = handler;
        this.runtimeVersion = runtimeVersion;
        this.packages = packages != null ? new ArrayList<>(packages) : new ArrayList<>();
    }

    public List<Parameter> getReturnColumns() {
        return new ArrayList<>(returnColumns);
    }

    public void setReturnColumns(final List<Parameter> returnColumns) {
        this.returnColumns = returnColumns != null ? new ArrayList<>(returnColumns) : new ArrayList<>();
    }

    public List<Parameter> getParameters() {
        return new ArrayList<>(parameters);
    }

    public DataType getReturnType() {
        return returnType;
    }

    public String getBody() {
        return body;
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

    public List<String> getPackages() {
        return new ArrayList<>(packages);
    }

    public List<String> getImports() { return new ArrayList<>(imports); }
    public void setImports(final List<String> imports) { this.imports = imports != null ? new ArrayList<>(imports) : new ArrayList<>(); }

    public String getExecuteAs() {
        return executeAs.name();
    }

    public void setExecuteAs(final String executeAs) {
        this.executeAs = ExecuteAs.fromString(executeAs);
    }

    @Override
    public void rename(final String newName) {
        throw new UnsupportedOperationException("Procedures cannot be renamed");
    }

    @Override
    public String getObjectType() {
        return "PROCEDURE";
    }
}
