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

package dev.frostlake.metastore;

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.security.SessionContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Catalog {

    private final Map<String, Database> databases;
    private final Map<String, Warehouse> warehouses;
    private final Map<String, User> users;
    private final Map<String, Role> roles;
    private String currentDatabase;
    private String currentSchema;
    private String currentWarehouse;
    // Resolves s3:// stage URLs to local paths (stage.s3.localMappings / .localRoot); set by the engine
    // so LIST/GET/PUT/REMOVE on S3 stages and IMPORTS JAR loading share one S3 -> local mapping.
    private S3PathResolver s3PathResolver;
    // Session context used to stamp the creating role as owner on new objects; set by the engine.
    private SessionContext sessionContext;
    // Dropped-object retention for UNDROP (Time Travel restore): "KIND:fqName" → the dropped object snapshot.
    private final Map<String, DroppedObject> droppedObjects = new ConcurrentHashMap<>();

    public Catalog() {
        this.databases = new ConcurrentHashMap<>();
        this.warehouses = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.roles = new ConcurrentHashMap<>();
        createSystemDatabase();
        createDefaultWarehouse();
        createSystemRoles();
    }

    private void createDefaultWarehouse() {
        Warehouse defaultWh = new Warehouse("COMPUTE_WH", WarehouseSize.X_SMALL);
        warehouses.put("COMPUTE_WH", defaultWh);
    }

    private void createSystemDatabase() {
        // Create default SNOWFLAKE database
        // Marked read-only so external callers cannot write to it
        createDatabase("SNOWFLAKE");
        getDatabase("SNOWFLAKE").setReadOnly(true);
        this.currentDatabase = "SNOWFLAKE";
        this.currentSchema = "PUBLIC";
    }

    public void createDatabase(final String name) {
        String upperName = name.toUpperCase();
        if (databases.containsKey(upperName)) {
            throw new RuntimeException("Database already exists: " + name);
        }
        Database db = new Database(upperName);
        db.setOwner(currentRoleForOwner());
        databases.put(upperName, db);
    }

    public void cloneDatabase(final String sourceName, final String targetName) {
        String sourceUpper = sourceName.toUpperCase();
        String targetUpper = targetName.toUpperCase();

        if (databases.containsKey(targetUpper)) {
            throw new RuntimeException("Database already exists: " + targetName);
        }

        Database sourceDb = getDatabase(sourceName);
        Database targetDb = sourceDb.clone(targetName);
        databases.put(targetUpper, targetDb);
    }

    public void dropDatabase(final String name, final boolean cascade) {
        String upperName = name.toUpperCase();
        if (!databases.containsKey(upperName)) {
            throw new RuntimeException("Database does not exist: " + name);
        }
        databases.remove(upperName);
    }

    public Database getDatabase(final String name) {
        Database db = databases.get(name.toUpperCase());
        if (db == null) {
            throw new RuntimeException("Database does not exist: " + name);
        }
        return db;
    }

    public List<Database> getAllDatabases() {
        return new ArrayList<>(databases.values());
    }

    public void useDatabase(final String name) {
        getDatabase(name); // Validates existence
        this.currentDatabase = name.toUpperCase();
    }

    public void useSchema(final String name) {
        if (currentDatabase == null) {
            throw new RuntimeException("No database selected");
        }
        Database db = getDatabase(currentDatabase);
        db.getSchema(name); // Validates existence
        this.currentSchema = name.toUpperCase();
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public String getCurrentSchema() {
        return currentSchema;
    }

    public Table resolveTable(final String qualifiedName) {
        return resolveTable(QualifiedName.parse(qualifiedName));
    }

    public Table resolveTable(final QualifiedName qn) {
        if (qn.size() == 1) {
            // table name only
            if (currentDatabase == null || currentSchema == null) {
                throw new RuntimeException("No database or schema selected");
            }
            return getDatabase(currentDatabase)
                    .getSchema(currentSchema)
                    .getTable(qn.part(0));
        } else if (qn.size() == 2) {
            // schema.table
            if (currentDatabase == null) {
                throw new RuntimeException("No database selected");
            }
            return getDatabase(currentDatabase)
                    .getSchema(qn.part(0))
                    .getTable(qn.part(1));
        } else if (qn.size() == 3) {
            // database.schema.table
            return getDatabase(qn.part(0))
                    .getSchema(qn.part(1))
                    .getTable(qn.part(2));
        } else {
            throw new RuntimeException("Invalid qualified name: " + qn);
        }
    }

    public View resolveView(final String qualifiedName) {
        return resolveView(QualifiedName.parse(qualifiedName));
    }

    public View resolveView(final QualifiedName qn) {
        if (qn.size() == 1) {
            // view name only
            if (currentDatabase == null || currentSchema == null) {
                throw new RuntimeException("No database or schema selected");
            }
            return getDatabase(currentDatabase)
                    .getSchema(currentSchema)
                    .getView(qn.part(0));
        } else if (qn.size() == 2) {
            // schema.view
            if (currentDatabase == null) {
                throw new RuntimeException("No database selected");
            }
            return getDatabase(currentDatabase)
                    .getSchema(qn.part(0))
                    .getView(qn.part(1));
        } else if (qn.size() == 3) {
            // database.schema.view
            return getDatabase(qn.part(0))
                    .getSchema(qn.part(1))
                    .getView(qn.part(2));
        } else {
            throw new RuntimeException("Invalid qualified name: " + qn);
        }
    }

    public Schema resolveSchema(final String qualifiedName) {
        return resolveSchema(QualifiedName.parse(qualifiedName));
    }

    public Schema resolveSchema(final QualifiedName qn) {
        if (qn.size() == 1) {
            if (currentDatabase == null) {
                throw new RuntimeException("No database selected");
            }
            return getDatabase(currentDatabase).getSchema(qn.part(0));
        } else if (qn.size() == 2) {
            return getDatabase(qn.part(0)).getSchema(qn.part(1));
        } else {
            throw new RuntimeException("Invalid qualified schema name: " + qn);
        }
    }

    // Warehouse Management
    public void createWarehouse(final String name, final WarehouseSize size) {
        if (warehouses.containsKey(name.toUpperCase())) {
            throw new RuntimeException("Warehouse already exists: " + name);
        }
        Warehouse warehouse = new Warehouse(name, size);
        warehouse.setOwner(currentRoleForOwner());
        warehouses.put(name.toUpperCase(), warehouse);
    }

    public void dropWarehouse(final String name) {
        String upperName = name.toUpperCase();
        if (!warehouses.containsKey(upperName)) {
            throw new RuntimeException("Warehouse does not exist: " + name);
        }
        if ("COMPUTE_WH".equals(upperName)) {
            throw new RuntimeException("Cannot drop default warehouse");
        }
        warehouses.remove(upperName);
    }

    public Warehouse getWarehouse(final String name) {
        Warehouse wh = warehouses.get(name.toUpperCase());
        if (wh == null) {
            throw new RuntimeException("Warehouse does not exist: " + name);
        }
        return wh;
    }

    public List<Warehouse> getAllWarehouses() {
        return new ArrayList<>(warehouses.values());
    }

    public void useWarehouse(final String name) {
        getWarehouse(name); // Validates existence
        this.currentWarehouse = name.toUpperCase();
    }

    public String getCurrentWarehouse() {
        return currentWarehouse;
    }

    public Warehouse getCurrentWarehouseObject() {
        if (currentWarehouse == null) {
            return null;
        }
        return getWarehouse(currentWarehouse);
    }

    // Stage Management — stages now live in Schema
    private Schema resolveSchemaForObject(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();
        String dbName = currentDatabase;
        if (dbName == null) throw new RuntimeException("No database selected");
        Database db = getDatabase(dbName);
        if (parts.length == 3) return db.getSchema(parts[1]);
        if (parts.length == 2) return db.getSchema(parts[0]);
        String scName = currentSchema;
        if (scName == null) throw new RuntimeException("No schema selected");
        return db.getSchema(scName);
    }

    private String objectName(final String qualifiedName) {
        return QualifiedName.parse(qualifiedName).last();
    }

    /** Set the resolver used to map s3:// stage URLs to local paths (see {@link S3PathResolver}). */
    public void setS3PathResolver(final S3PathResolver s3PathResolver) {
        this.s3PathResolver = s3PathResolver;
    }

    /** Set the session context used to stamp object ownership at CREATE time. */
    public void setSessionContext(final SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /** The role to record as owner on newly-created objects: the session's current role, else SYSADMIN. */
    public String currentRoleForOwner() {
        return sessionContext != null && sessionContext.getCurrentRole() != null
            ? sessionContext.getCurrentRole() : "SYSADMIN";
    }

    /**
     * The owner role of a schema-level object, for ownership-based authorization. Returns null when
     * the type is not one that carries an owner here, or the object cannot be resolved.
     */
    public String getObjectOwnerRole(final String objectType, final String objectName) {
        if (objectType == null || objectName == null) {
            return null;
        }
        try {
            switch (objectType.toUpperCase()) {
                case "TABLE": return resolveTable(objectName).getOwner();
                case "VIEW": return resolveView(objectName).getOwner();
                case "SCHEMA": return resolveSchema(objectName).getOwner();
                case "DATABASE": return getDatabase(objectName).getOwner();
                case "STAGE": return getStage(objectName).getOwner();
                case "WAREHOUSE": return getWarehouse(objectName).getOwner();
                default: {
                    // Schema-level objects: resolve the containing schema, then the object by bare name.
                    final Schema schema = resolveSchemaForObject(objectName);
                    final String bare = objectName(objectName);
                    switch (objectType.toUpperCase()) {
                        case "STREAM": return schema.getStream(bare).getOwner();
                        case "TASK": return schema.getTask(bare).getOwner();
                        case "PIPE": return schema.getPipe(bare).getOwner();
                        case "DYNAMIC TABLE": return schema.getDynamicTable(bare).getOwner();
                        case "MATERIALIZED VIEW": return schema.getMaterializedView(bare).getOwner();
                        case "MASKING POLICY": return schema.getMaskingPolicy(bare).getOwner();
                        case "ROW ACCESS POLICY": return schema.getRowAccessPolicy(bare).getOwner();
                        case "TAG": return schema.getTag(bare).getOwner();
                        default: return null;
                    }
                }
            }
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** The user whose stage {@code @~} resolves to: the session's current user, else PUBLIC. */
    public String currentUserForStage() {
        return sessionContext != null && sessionContext.getCurrentUser() != null
            ? sessionContext.getCurrentUser() : "PUBLIC";
    }

    /** USE ROLE &lt;name&gt;: switch the session's primary role; errors if the role does not exist. */
    public void useRole(final String roleName) {
        final String upper = roleName == null ? null : roleName.toUpperCase();
        if (upper == null || !roles.containsKey(upper)) {
            throw new RuntimeException("Role '" + roleName + "' does not exist or not authorized");
        }
        if (sessionContext != null) {
            sessionContext.setCurrentRole(upper);
        }
    }

    /** USE SECONDARY ROLES {ALL | NONE | &lt;role&gt;}: adjust which non-primary roles' privileges are active. */
    public void useSecondaryRoles(final String spec) {
        if (sessionContext == null) {
            return;
        }
        if ("ALL".equalsIgnoreCase(spec)) {
            for (final String r : roles.keySet()) {
                sessionContext.addActiveRole(r);
            }
        } else if ("NONE".equalsIgnoreCase(spec)) {
            sessionContext.clearActiveRoles();
            if (sessionContext.getCurrentRole() != null) {
                sessionContext.addActiveRole(sessionContext.getCurrentRole());
            }
        } else if (spec != null) {
            sessionContext.addActiveRole(spec);
        }
    }

    /** Record a dropped object's snapshot under "KIND:fqName" so a subsequent UNDROP can restore it. */
    public void recordDropped(final String key, final DroppedObject dropped) {
        droppedObjects.put(key.toUpperCase(), dropped);
    }

    /** Remove and return the most recent dropped-object snapshot for "KIND:fqName", or null if none. */
    public DroppedObject takeDropped(final String key) {
        return droppedObjects.remove(key.toUpperCase());
    }

    /** Re-insert a previously-dropped database object (UNDROP DATABASE). */
    public void restoreDatabase(final Database database) {
        databases.put(database.getName().toUpperCase(), database);
    }

    /** Read the value of a tag set on an object (SYSTEM$GET_TAG); null if the object or tag is absent. */
    public String getObjectTagValue(final String tagName, final String objectName, final String domain) {
        final Taggable target = resolveTaggable(objectName, domain);
        return target == null ? null : target.getTagValue(tagName);
    }

    /** Resolve a tag-bearing object by name and Snowflake object domain (TABLE, COLUMN, VIEW, SCHEMA, DATABASE, WAREHOUSE). */
    private Taggable resolveTaggable(final String objectName, final String domain) {
        final String d = domain == null ? "TABLE" : domain.toUpperCase();
        try {
            switch (d) {
                case "DATABASE": return getDatabase(objectName);
                case "SCHEMA": return resolveSchema(objectName);
                case "TABLE": return resolveTable(objectName);
                case "VIEW": return resolveView(objectName);
                case "WAREHOUSE": return getWarehouse(objectName);
                case "COLUMN": {
                    final int dot = objectName.lastIndexOf('.');
                    if (dot < 0) {
                        return null;
                    }
                    final Table table = resolveTable(objectName.substring(0, dot));
                    return table == null ? null : table.getColumn(objectName.substring(dot + 1));
                }
                default: return null;
            }
        } catch (final RuntimeException e) {
            return null;
        }
    }

    public void createStage(final String name, final StageType type, final String url) {
        Schema schema = resolveSchemaForObject(name);
        String objName = objectName(name);
        if (schema.hasStage(objName)) throw new RuntimeException("Stage already exists: " + name);
        Stage stage = new Stage(objName, type, url, "CSV", false, null, s3PathResolver);
        stage.setOwner(currentRoleForOwner());
        schema.addStage(stage);
    }

    public void createStage(final String name, final StageType type, final String url,
                           final String fileFormat, final boolean encryption, final String comment) {
        Schema schema = resolveSchemaForObject(name);
        String objName = objectName(name);
        if (schema.hasStage(objName)) throw new RuntimeException("Stage already exists: " + name);
        Stage stage = new Stage(objName, type, url, fileFormat, encryption, comment, s3PathResolver);
        stage.setOwner(currentRoleForOwner());
        schema.addStage(stage);
    }

    public void dropStage(final String name) {
        resolveSchemaForObject(name).dropStage(objectName(name));
    }

    public Stage getStage(final String name) {
        return resolveSchemaForObject(name).getStage(objectName(name));
    }

    public void addFileFormat(final String qualifiedName, final FileFormat fileFormat) {
        resolveSchemaForObject(qualifiedName).addFileFormat(fileFormat);
    }

    public FileFormat getFileFormat(final String name) {
        return resolveSchemaForObject(name).getFileFormat(objectName(name));
    }

    public boolean hasFileFormat(final String name) {
        return resolveSchemaForObject(name).hasFileFormat(objectName(name));
    }

    public void dropFileFormat(final String name) {
        resolveSchemaForObject(name).dropFileFormat(objectName(name));
    }

    public Pipe getPipe(final String name) {
        return resolveSchemaForObject(name).getPipe(objectName(name));
    }

    public List<Stage> getAllStages() {
        try {
            if (currentDatabase == null || currentSchema == null) return new ArrayList<>();
            return getDatabase(currentDatabase).getSchema(currentSchema).getStages();
        } catch (final Exception e) { return new ArrayList<>(); }
    }

    // Tag Management — tags now live in Schema
    public void createTag(final String name) {
        Schema schema = resolveSchemaForObject(name);
        String objName = objectName(name);
        if (schema.hasTag(objName)) throw new RuntimeException("Tag already exists: " + name);
        Tag tag = new Tag(objName);
        tag.setOwner(currentRoleForOwner());
        schema.addTag(tag);
    }

    public void createTag(final String name, final List<String> allowedValues,
                         final boolean masking, final String comment) {
        Schema schema = resolveSchemaForObject(name);
        String objName = objectName(name);
        if (schema.hasTag(objName)) throw new RuntimeException("Tag already exists: " + name);
        Tag tag = new Tag(objName, allowedValues, masking, comment);
        tag.setOwner(currentRoleForOwner());
        schema.addTag(tag);
    }

    public void dropTag(final String name) {
        resolveSchemaForObject(name).dropTag(objectName(name));
    }

    public Tag getTag(final String name) {
        return resolveSchemaForObject(name).getTag(objectName(name));
    }

    public boolean hasTag(final String name) {
        try { return resolveSchemaForObject(name).hasTag(objectName(name)); }
        catch (final Exception e) { return false; }
    }

    public List<Tag> getAllTags() {
        try {
            if (currentDatabase == null || currentSchema == null) return new ArrayList<>();
            return getDatabase(currentDatabase).getSchema(currentSchema).getTags();
        } catch (final Exception e) { return new ArrayList<>(); }
    }

    public void renameTag(final String oldName, final String newName) {
        resolveSchemaForObject(oldName).renameTag(objectName(oldName), objectName(newName));
    }

    public void renameMaskingPolicy(final String oldName, final String newName) {
        resolveSchemaForObject(oldName).renameMaskingPolicy(objectName(oldName), objectName(newName));
    }

    public void renameRowAccessPolicy(final String oldName, final String newName) {
        resolveSchemaForObject(oldName).renameRowAccessPolicy(objectName(oldName), objectName(newName));
    }

    // User and Role Management
    private void createSystemRoles() {
        // Create all Snowflake system roles
        // Role hierarchy (top to bottom):
        // ORGADMIN -> ACCOUNTADMIN -> SECURITYADMIN -> USERADMIN
        //                         -> SYSADMIN
        //                         -> PUBLIC (granted to all users)

        // ORGADMIN - Organization administrator (highest level)
        Role orgAdmin = new Role("ORGADMIN");
        orgAdmin.setComment("Organization administrator role - manages organization-level objects");
        roles.put("ORGADMIN", orgAdmin);

        // ACCOUNTADMIN - Account administrator (manages account-level objects)
        Role accountAdmin = new Role("ACCOUNTADMIN");
        accountAdmin.setComment("Account administrator role - manages all account objects and grants");
        roles.put("ACCOUNTADMIN", accountAdmin);

        // SECURITYADMIN - Security administrator (manages users, roles, and security)
        Role securityAdmin = new Role("SECURITYADMIN");
        securityAdmin.setComment("Security administrator role - manages users, roles, and grants");
        roles.put("SECURITYADMIN", securityAdmin);

        // USERADMIN - User administrator (manages users and roles)
        Role userAdmin = new Role("USERADMIN");
        userAdmin.setComment("User administrator role - manages users and roles");
        roles.put("USERADMIN", userAdmin);

        // SYSADMIN - System administrator (manages warehouses, databases, and other objects)
        Role sysAdmin = new Role("SYSADMIN");
        sysAdmin.setComment("System administrator role - manages databases, warehouses, and other objects");
        roles.put("SYSADMIN", sysAdmin);

        // PUBLIC - Default role (granted to all users automatically)
        Role publicRole = new Role("PUBLIC");
        publicRole.setComment("Public role - granted to all users by default");
        roles.put("PUBLIC", publicRole);

        // Set up role hierarchy (roles inherit privileges from granted roles)
        // ACCOUNTADMIN inherits from SECURITYADMIN and SYSADMIN
        accountAdmin.grantRole("SECURITYADMIN");
        accountAdmin.grantRole("SYSADMIN");

        // SECURITYADMIN inherits from USERADMIN
        securityAdmin.grantRole("USERADMIN");

        // ORGADMIN inherits from ACCOUNTADMIN
        orgAdmin.grantRole("ACCOUNTADMIN");
    }

    public void createUser(final String name) {
        if (users.containsKey(name.toUpperCase())) {
            throw new RuntimeException("User already exists: " + name);
        }
        User user = new User(name);
        user.setOwner(currentRoleForOwner());
        // Automatically grant PUBLIC role to all users
        user.grantRole("PUBLIC");
        users.put(name.toUpperCase(), user);
    }

    public void createUser(final String name, final String password, final String defaultRole) {
        if (users.containsKey(name.toUpperCase())) {
            throw new RuntimeException("User already exists: " + name);
        }
        User user = new User(name, password, defaultRole);
        user.setOwner(currentRoleForOwner());
        // Automatically grant PUBLIC role to all users
        user.grantRole("PUBLIC");
        users.put(name.toUpperCase(), user);
    }

    public void dropUser(final String name) {
        String upperName = name.toUpperCase();
        if (!users.containsKey(upperName)) {
            throw new RuntimeException("User does not exist: " + name);
        }
        users.remove(upperName);
    }

    public User getUser(final String name) {
        User user = users.get(name.toUpperCase());
        if (user == null) {
            throw new RuntimeException("User does not exist: " + name);
        }
        return user;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public void createRole(final String name) {
        String upperName = name.toUpperCase();
        if (isSystemRole(upperName)) {
            throw new RuntimeException("Cannot create system role: " + name);
        }
        if (roles.containsKey(upperName)) {
            throw new RuntimeException("Role already exists: " + name);
        }
        Role role = new Role(name);
        role.setOwner(currentRoleForOwner());
        roles.put(upperName, role);
    }

    public void dropRole(final String name) {
        String upperName = name.toUpperCase();
        if (!roles.containsKey(upperName)) {
            throw new RuntimeException("Role does not exist: " + name);
        }
        // Cannot drop system roles
        if (isSystemRole(upperName)) {
            throw new RuntimeException("Cannot drop system role: " + name);
        }
        roles.remove(upperName);
    }

    /**
     * Check if a role is a system role
     */
    public boolean isSystemRole(final String roleName) {
        String upperName = roleName.toUpperCase();
        return upperName.equals("ORGADMIN") ||
               upperName.equals("ACCOUNTADMIN") ||
               upperName.equals("SECURITYADMIN") ||
               upperName.equals("USERADMIN") ||
               upperName.equals("SYSADMIN") ||
               upperName.equals("PUBLIC");
    }

    public Role getRole(final String name) {
        Role role = roles.get(name.toUpperCase());
        if (role == null) {
            throw new RuntimeException("Role does not exist: " + name);
        }
        return role;
    }

    public List<Role> getAllRoles() {
        return new ArrayList<>(roles.values());
    }

    public void grantRoleToUser(final String roleName, final String userName) {
        User user = getUser(userName);
        Role role = getRole(roleName);
        user.grantRole(role.getName(), currentRoleForOwner());
    }

    public void revokeRoleFromUser(final String roleName, final String userName) {
        User user = getUser(userName);
        Role role = getRole(roleName);
        user.revokeRole(role.getName());
    }

    public void grantPrivilegeToRole(final String privilege, final String objectType, final String objectName, final String roleName) {
        Role role = getRole(roleName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        role.grantPrivilege(objectType, objectName, priv, currentRoleForOwner());
    }

    public void revokePrivilegeFromRole(final String privilege, final String objectType, final String objectName, final String roleName) {
        Role role = getRole(roleName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        role.revokePrivilege(objectType, objectName, priv);
    }

    public void grantPrivilegeToUser(final String privilege, final String objectType, final String objectName, final String userName) {
        User user = getUser(userName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        user.grantPrivilege(objectType, objectName, priv, currentRoleForOwner());
    }

    public void revokePrivilegeFromUser(final String privilege, final String objectType, final String objectName, final String userName) {
        User user = getUser(userName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        user.revokePrivilege(objectType, objectName, priv);
    }

    public void grantColumnPrivilegeToRole(final String privilege, final String objectType, final String objectName,
                                           final String columnName, final String roleName) {
        Role role = getRole(roleName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        role.grantColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void revokeColumnPrivilegeFromRole(final String privilege, final String objectType, final String objectName,
                                              final String columnName, final String roleName) {
        Role role = getRole(roleName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        role.revokeColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void grantColumnPrivilegeToUser(final String privilege, final String objectType, final String objectName,
                                           final String columnName, final String userName) {
        User user = getUser(userName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        user.grantColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void revokeColumnPrivilegeFromUser(final String privilege, final String objectType, final String objectName,
                                              final String columnName, final String userName) {
        User user = getUser(userName);
        Privilege priv = Privilege.valueOf(privilege.toUpperCase());
        user.revokeColumnPrivilege(objectType, objectName, columnName, priv);
    }

    public void grantRoleToRole(final String grantedRoleName, final String targetRoleName) {
        Role grantedRole = getRole(grantedRoleName);
        Role targetRole = getRole(targetRoleName);

        // Prevent circular grants
        if (hasCircularRoleGrant(grantedRoleName, targetRoleName)) {
            throw new RuntimeException("Cannot grant role: would create circular role hierarchy");
        }

        targetRole.grantRole(grantedRole.getName(), currentRoleForOwner());
    }

    public void revokeRoleFromRole(final String revokedRoleName, final String targetRoleName) {
        Role revokedRole = getRole(revokedRoleName);
        Role targetRole = getRole(targetRoleName);
        targetRole.revokeRole(revokedRole.getName());
    }

    private boolean hasCircularRoleGrant(final String grantedRoleName, final String targetRoleName) {
        // Check if granting would create a cycle
        Set<String> visited = new HashSet<>();
        return checkCircular(grantedRoleName.toUpperCase(), targetRoleName.toUpperCase(), visited);
    }

    private boolean checkCircular(final String currentRole, final String targetRole, final Set<String> visited) {
        // Check if currentRole transitively contains targetRole in its hierarchy
        if (currentRole.equals(targetRole)) {
            return true;
        }

        // Avoid infinite loops
        if (visited.contains(currentRole)) {
            return false;
        }

        visited.add(currentRole);

        Role currentRoleObj = roles.get(currentRole);
        if (currentRoleObj == null) {
            return false;
        }

        // Check all roles granted to currentRole
        for (final String childRole : currentRoleObj.getGrantedRoles()) {
            if (checkCircular(childRole, targetRole, visited)) {
                return true;
            }
        }

        return false;
    }

    // Rename operations
    public void renameDatabase(final String oldName, final String newName) {
        Database db = getDatabase(oldName);
        if (databases.containsKey(newName.toUpperCase())) {
            throw new RuntimeException("Database already exists: " + newName);
        }
        databases.remove(oldName.toUpperCase());
        // createDatabase stores the name upper-cased; keep rename symmetric so getName()
        // stays consistent with the map key and with every freshly-created database.
        db.rename(newName.toUpperCase());
        databases.put(newName.toUpperCase(), db);
    }

    public void renameTable(final String qualifiedName, final String newName) {
        Table table = resolveTable(qualifiedName);
        Schema schema = resolveSchemaForTable(qualifiedName);
        schema.dropTable(table.getName());
        table.rename(newName);
        schema.addTable(table);
    }

    /**
     * Move a table to a (possibly different) schema/database, optionally renaming it — the qualified-target
     * form of {@code ALTER TABLE ... RENAME TO db.schema.name}. The destination database and schema must
     * already exist (else {@link #getDatabase}/{@code getSchema} throw), and no table of the new name may
     * already exist there. Membership is checked before any mutation, so a rejected move leaves the catalog
     * untouched.
     */
    public void moveTable(final String sourceQualifiedName, final String targetDatabase,
                          final String targetSchema, final String newName) {
        final Table table = resolveTable(sourceQualifiedName);
        final Schema source = resolveSchemaForTable(sourceQualifiedName);
        final Schema target = getDatabase(targetDatabase).getSchema(targetSchema);
        if (target.hasTable(newName)) {
            throw new RuntimeException("Table already exists: "
                + targetDatabase + "." + targetSchema + "." + newName);
        }
        source.dropTable(table.getName());
        table.rename(newName);
        target.addTable(table);
    }

    public void renameView(final String qualifiedName, final String newName) {
        View view = resolveView(qualifiedName);
        Schema schema = resolveSchemaForTable(qualifiedName);
        schema.dropView(view.getName());
        view.rename(newName);
        schema.addView(view);
    }

    public void renameUser(final String oldName, final String newName) {
        User user = getUser(oldName);
        if (users.containsKey(newName.toUpperCase())) {
            throw new RuntimeException("User already exists: " + newName);
        }
        users.remove(oldName.toUpperCase());
        user.rename(newName);
        users.put(newName.toUpperCase(), user);
    }

    public void renameRole(final String oldName, final String newName) {
        Role role = getRole(oldName);
        if (roles.containsKey(newName.toUpperCase())) {
            throw new RuntimeException("Role already exists: " + newName);
        }
        roles.remove(oldName.toUpperCase());
        role.rename(newName);
        roles.put(newName.toUpperCase(), role);
    }

    public void renameWarehouse(final String oldName, final String newName) {
        Warehouse warehouse = getWarehouse(oldName);
        if (warehouses.containsKey(newName.toUpperCase())) {
            throw new RuntimeException("Warehouse already exists: " + newName);
        }
        warehouses.remove(oldName.toUpperCase());
        warehouse.rename(newName);
        warehouses.put(newName.toUpperCase(), warehouse);
    }

    private Schema resolveSchemaForTable(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();
        if (parts.length == 1) {
            return getDatabase(getCurrentDatabase()).getSchema(getCurrentSchema());
        } else if (parts.length == 2) {
            return getDatabase(getCurrentDatabase()).getSchema(parts[0]);
        } else {
            return getDatabase(parts[0]).getSchema(parts[1]);
        }
    }
}
