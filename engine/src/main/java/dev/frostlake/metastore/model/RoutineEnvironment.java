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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a handler routine is given to run with beyond its body: the packages it names, the artifact repository
 * they come from, where a Java handler's compiled classes are kept, and the external access integrations and
 * secrets it may use. The engine keeps each as written and tells it back; none changes what a call does.
 */
public final class RoutineEnvironment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The repository a Python routine's packages come from when it names none. */
    public static final String DEFAULT_ARTIFACT_REPOSITORY = "snowflake.snowpark.pypi_shared_repository";

    private List<String> packages = new ArrayList<>();
    private String artifactRepository;
    private String targetPath;
    private List<String> externalAccessIntegrations = new ArrayList<>();
    private Map<String, String> secrets = new LinkedHashMap<>();
    private boolean secretsDeclared;

    /** The packages the PACKAGES clause names, in the order written. */
    public List<String> getPackages() {
        return new ArrayList<>(packages);
    }

    /** Records the packages the PACKAGES clause names. */
    public void setPackages(final List<String> packages) {
        this.packages = packages == null ? new ArrayList<String>() : new ArrayList<>(packages);
    }

    /** The artifact repository as written, or null when the routine names none. */
    public String getArtifactRepository() {
        return artifactRepository;
    }

    /** Records the artifact repository as written. */
    public void setArtifactRepository(final String artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    /**
     * The repository a description tells: the one written, or the shared one a routine that names packages and
     * no repository takes them from; null when it names neither.
     */
    public String describedArtifactRepository() {
        if (artifactRepository != null) {
            return artifactRepository;
        }
        return packages.isEmpty() ? null : DEFAULT_ARTIFACT_REPOSITORY;
    }

    /** Where the compiled classes are kept — {@code @DB.SCHEMA.STAGE/path/x.jar} — or null. */
    public String getTargetPath() {
        return targetPath;
    }

    /** Records where the compiled classes are kept. */
    public void setTargetPath(final String targetPath) {
        this.targetPath = targetPath;
    }

    /** The external access integrations named, canonical, in the order written. */
    public List<String> getExternalAccessIntegrations() {
        return new ArrayList<>(externalAccessIntegrations);
    }

    /** Records the external access integrations named. */
    public void setExternalAccessIntegrations(final List<String> integrations) {
        this.externalAccessIntegrations = integrations == null ? new ArrayList<String>() : new ArrayList<>(integrations);
    }

    /** The secrets named: the handler's variable for each, and the secret's full name, in the order written. */
    public Map<String, String> getSecrets() {
        return new LinkedHashMap<>(secrets);
    }

    /** Records the secrets named, by the handler's variable for each; null for a routine that declares none. */
    public void setSecrets(final Map<String, String> secrets) {
        this.secrets = secrets == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(secrets);
        this.secretsDeclared = secrets != null;
    }

    /** Whether the routine names at least one secret. */
    public boolean hasSecrets() {
        return !secrets.isEmpty();
    }

    /**
     * The secrets as a listing tells them: nothing for a routine that never declared any, else one JSON object,
     * its variables in order, each secret named from the routine's own schema - bare within it, with its schema
     * within the routine's database, in full beyond it (live-verified).
     *
     * @param database the routine's database
     * @param schema   the routine's schema
     * @return the cell's text
     */
    public String secretsText(final String database, final String schema) {
        if (!secretsDeclared) {
            return "";
        }
        final Map<String, String> sorted = new TreeMap<>(secrets);
        final StringBuilder text = new StringBuilder("{");
        for (final Map.Entry<String, String> secret : sorted.entrySet()) {
            text.append(text.length() > 1 ? "," : "").append(jsonText(secret.getKey())).append(':')
                .append(jsonText(relative(secret.getValue(), database, schema)));
        }
        return text.append('}').toString();
    }

    private static String relative(final String name, final String database, final String schema) {
        final String inSchema = database + "." + schema + ".";
        if (name.startsWith(inSchema)) {
            return name.substring(inSchema.length());
        }
        return name.startsWith(database + ".") ? name.substring(database.length() + 1) : name;
    }

    private static String jsonText(final String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** A copy a cloned routine keeps. */
    public RoutineEnvironment copy() {
        final RoutineEnvironment copy = new RoutineEnvironment();
        copy.setPackages(packages);
        copy.setArtifactRepository(artifactRepository);
        copy.setTargetPath(targetPath);
        copy.setExternalAccessIntegrations(externalAccessIntegrations);
        copy.setSecrets(secretsDeclared ? secrets : null);
        return copy;
    }

    /**
     * A list as a description tells it: {@code ['a','b']}, and {@code []} for none.
     *
     * @param items the items
     * @return the list's text
     */
    public static String listed(final List<String> items) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; items != null && i < items.size(); i++) {
            text.append(i == 0 ? "" : ",").append('\'').append(items.get(i)).append('\'');
        }
        return text.append(']').toString();
    }
}
