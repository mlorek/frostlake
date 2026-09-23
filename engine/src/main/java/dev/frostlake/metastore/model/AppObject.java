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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StatementTokens;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A notebook or a Streamlit app: the catalog object that holds an app's settings and names its files. The
 * engine keeps the object, its properties and its version history; it never runs the app.
 *
 * <p>Versions follow the account's model. An object created {@code FROM} a location (or from the default
 * template) starts with one committed version, {@code VERSION$1}. The default version is {@code LAST}, so the
 * last committed version is also the default one. {@code ADD LIVE VERSION FROM LAST} opens a live, editable
 * version on top of the last one; {@code COMMIT} turns the live version into a new last version
 * ({@code VERSION$2}, …) and closes it; {@code ABORT} discards it; {@code ADD VERSION … FROM '<location>'} adds a
 * committed version copied from a stage. A version keeps its alias, its comment and the location its files came
 * from; the live version takes the last version's location, and a commit keeps the live version's alias. A Streamlit
 * created with the legacy {@code ROOT_LOCATION} has no versions.
 */
public class AppObject {

    private static final String URL_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final AppObjectKind kind;
    private String name;
    private String databaseName;
    private String schemaName;
    private final LocalDateTime createdOn;
    private final String urlId;
    private String owner;
    private String comment;
    private String fromLocation;
    private String rootLocation;
    private String mainFile;
    private String queryWarehouse;
    private String warehouse;
    private String runtimeName;
    private String computePool;
    private String title;
    private Long idleAutoShutdownTimeSeconds;
    private List<String> imports = new ArrayList<>();
    private List<String> externalAccessIntegrations = new ArrayList<>();
    private final List<AppObjectVersion> versions = new ArrayList<>();
    private AppObjectVersion liveVersion;
    private LocalDateTime droppedOn;

    /**
     * @param kind notebook or Streamlit
     * @param name the object's resolved name
     */
    public AppObject(final AppObjectKind kind, final String name) {
        this(kind, name, LocalDateTime.ofInstant(StatementClock.instant(), ZoneId.systemDefault()),
            StatementTokens.draw(URL_ID_ALPHABET, 20));
    }

    /**
     * An object created at a given moment under a given URL id, as a restored snapshot brings it back.
     *
     * @param kind notebook or Streamlit
     * @param name the object's resolved name
     * @param createdOn when it was created, on the host's wall clock
     * @param urlId its URL id
     */
    public AppObject(final AppObjectKind kind, final String name, final LocalDateTime createdOn,
                     final String urlId) {
        this.kind = kind;
        this.name = name;
        this.createdOn = createdOn;
        this.urlId = urlId;
    }

    /** Notebook or Streamlit. */
    public AppObjectKind getKind() {
        return kind;
    }

    /** The object's resolved name. */
    public String getName() {
        return name;
    }

    /** Renames the object; the store keying it is updated by its caller. */
    public void setName(final String name) {
        this.name = name;
    }

    /** The database holding the object. */
    public String getDatabaseName() {
        return databaseName;
    }

    /** Places the object in a database. */
    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }

    /** The schema holding the object. */
    public String getSchemaName() {
        return schemaName;
    }

    /** Places the object in a schema. */
    public void setSchemaName(final String schemaName) {
        this.schemaName = schemaName;
    }

    /** When the object was created, on the host's wall clock. */
    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    /** The object's URL id: a lower-case token unique to the object. */
    public String getUrlId() {
        return urlId;
    }

    /** The role owning the object. */
    public String getOwner() {
        return owner;
    }

    /** Sets the owning role. */
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    /** The comment, or null. */
    public String getComment() {
        return comment;
    }

    /** Sets or clears the comment. */
    public void setComment(final String comment) {
        this.comment = comment;
    }

    /** The location the first version was copied from ({@code FROM '<location>'}), or null. */
    public String getFromLocation() {
        return fromLocation;
    }

    /** Sets the source location of the first version. */
    public void setFromLocation(final String fromLocation) {
        this.fromLocation = fromLocation;
    }

    /** The legacy {@code ROOT_LOCATION} of a Streamlit app, or null. */
    public String getRootLocation() {
        return rootLocation;
    }

    /** Sets the legacy root location. */
    public void setRootLocation(final String rootLocation) {
        this.rootLocation = rootLocation;
    }

    /** The entry-point file, or null. */
    public String getMainFile() {
        return mainFile;
    }

    /** Sets the entry-point file. */
    public void setMainFile(final String mainFile) {
        this.mainFile = mainFile;
    }

    /** The warehouse the app's queries run on, or null. */
    public String getQueryWarehouse() {
        return queryWarehouse;
    }

    /** Sets or clears the query warehouse. */
    public void setQueryWarehouse(final String queryWarehouse) {
        this.queryWarehouse = queryWarehouse;
    }

    /** The warehouse a notebook's kernel runs on ({@code WAREHOUSE =}), or null. */
    public String getWarehouse() {
        return warehouse;
    }

    /** Sets the kernel warehouse. */
    public void setWarehouse(final String warehouse) {
        this.warehouse = warehouse;
    }

    /** The runtime name, or null when the default runtime applies. */
    public String getRuntimeName() {
        return runtimeName;
    }

    /** Sets the runtime name. */
    public void setRuntimeName(final String runtimeName) {
        this.runtimeName = runtimeName;
    }

    /** The compute pool of a container runtime, or null. */
    public String getComputePool() {
        return computePool;
    }

    /** Sets the compute pool. */
    public void setComputePool(final String computePool) {
        this.computePool = computePool;
    }

    /** The app's title, or null. */
    public String getTitle() {
        return title;
    }

    /** Sets or clears the title. */
    public void setTitle(final String title) {
        this.title = title;
    }

    /** A notebook's idle shutdown time in seconds, or null when the default applies. */
    public Long getIdleAutoShutdownTimeSeconds() {
        return idleAutoShutdownTimeSeconds;
    }

    /** Sets the idle shutdown time. */
    public void setIdleAutoShutdownTimeSeconds(final Long idleAutoShutdownTimeSeconds) {
        this.idleAutoShutdownTimeSeconds = idleAutoShutdownTimeSeconds;
    }

    /** The stage paths a Streamlit app imports. */
    public List<String> getImports() {
        return imports;
    }

    /** Replaces the imports. */
    public void setImports(final List<String> imports) {
        this.imports = new ArrayList<>(imports);
    }

    /** The external access integrations the app may use. */
    public List<String> getExternalAccessIntegrations() {
        return externalAccessIntegrations;
    }

    /** Replaces the external access integrations. */
    public void setExternalAccessIntegrations(final List<String> integrations) {
        this.externalAccessIntegrations = new ArrayList<>(integrations);
    }

    /** How many committed versions the object has; zero for a legacy {@code ROOT_LOCATION} app. */
    public int getVersionCount() {
        return versions.size();
    }

    /** Adds the first committed version, copied from the {@code FROM} location or from the template. */
    public void addVersion() {
        addVersion(null, null, fromLocation == null ? null : sourceLocation(fromLocation), StatementClock.instant());
    }

    /**
     * Adds a committed version, which becomes the last one.
     *
     * @param alias its alias, or null
     * @param comment its comment, or null
     * @param sourceLocation the location its files came from, as listed, or null
     * @param createdOn when it is added
     */
    public void addVersion(final String alias, final String comment, final String sourceLocation,
                           final Instant createdOn) {
        versions.add(new AppObjectVersion(versions.size() + 1, alias, comment, sourceLocation, createdOn));
    }

    /** The committed versions, first to last. */
    public List<AppObjectVersion> getVersions() {
        return new ArrayList<>(versions);
    }

    /** The last committed version, or null when there is none. */
    public AppObjectVersion lastVersion() {
        return versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    /** The committed version with this alias, or null. */
    public AppObjectVersion versionWithAlias(final String alias) {
        for (final AppObjectVersion version : versions) {
            if (alias.equals(version.getAlias())) {
                return version;
            }
        }
        return null;
    }

    /** Whether a live version is open. */
    public boolean hasLiveVersion() {
        return liveVersion != null;
    }

    /** The live version, or null when none is open. */
    public AppObjectVersion getLiveVersion() {
        return liveVersion;
    }

    /** Opens a live version without an alias or a comment, or discards the live version. */
    public void setLiveVersion(final boolean open) {
        if (open) {
            openLiveVersion(null, null, StatementClock.instant());
        } else {
            liveVersion = null;
        }
    }

    /**
     * Opens a live version on top of the last one, whose location it takes.
     *
     * @param alias its alias, or null
     * @param comment its comment, or null
     * @param createdOn when it is opened
     */
    public void openLiveVersion(final String alias, final String comment, final Instant createdOn) {
        final AppObjectVersion last = lastVersion();
        liveVersion = new AppObjectVersion(0, alias, comment, last == null ? null : last.getSourceLocation(),
            createdOn);
    }

    /**
     * Commits the live version as the next committed version, which keeps its alias and its location and takes the
     * commit's comment, and closes it.
     *
     * @param comment the commit's comment, or null
     * @param createdOn when it is committed
     */
    public void commitLiveVersion(final String comment, final Instant createdOn) {
        addVersion(liveVersion.getAlias(), comment, liveVersion.getSourceLocation(), createdOn);
        liveVersion = null;
    }

    /** Puts back the versions a snapshot held. */
    public void restoreVersions(final List<AppObjectVersion> committed, final AppObjectVersion live) {
        versions.clear();
        versions.addAll(committed);
        liveVersion = live;
    }

    /**
     * A location as a version lists the files' source: the text as written, closed with a slash when it names a
     * path under the stage — {@code @stg} stays, {@code @stg/sub} reads {@code @stg/sub/}.
     */
    public static String sourceLocation(final String written) {
        return written.indexOf('/') >= 0 && !written.endsWith("/") ? written + "/" : written;
    }

    /** When the object was dropped, or null while it is live. */
    public LocalDateTime getDroppedOn() {
        return droppedOn;
    }

    /** Marks the object dropped, or restored with null. */
    public void setDroppedOn(final LocalDateTime droppedOn) {
        this.droppedOn = droppedOn;
    }

    /** The name of committed version {@code n}: {@code VERSION$n}. */
    public static String versionName(final int n) {
        return "VERSION$" + n;
    }

    /** The name of the last committed version, or null when there is none. */
    public String lastVersionName() {
        return versions.isEmpty() ? null : versionName(versions.size());
    }

    /** The name of the default version, which follows the last one, or null when there is none. */
    public String defaultVersionName() {
        return lastVersionName();
    }

    /** The location URI of a version's files: {@code snow://<kind>/<DB>.<SCHEMA>.<NAME>/versions/<version>/}. */
    public String versionLocation(final String version) {
        return "snow://" + kind.uriScheme() + "/" + databaseName + "." + schemaName + "." + name + "/versions/"
            + version.toLowerCase(Locale.ROOT) + "/";
    }
}
