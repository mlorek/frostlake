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

import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.ArtifactRepository;
import dev.frostlake.metastore.model.ContainerObjects;
import dev.frostlake.metastore.model.ContainerService;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ImageRepository;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityObjectStore;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.Warehouse;

import java.util.List;

/**
 * What becomes of the objects a role owns when the role's name stops naming it. A dropped role hands every object
 * it owned to the role that dropped it — its tables, views, sequences, stages, routines and schemas read
 * {@code ACCOUNTADMIN} as their owner straight after an ACCOUNTADMIN's {@code DROP ROLE} (live-verified), and every
 * other kind it owns goes the same way, its database roles, secrets, network rules and policies, integrations, external
 * volumes, notebooks, Streamlit apps, Cortex Search services, image and artifact repositories and services (documented:
 * "Ownership of any objects owned by the dropped role is transferred to the role that executes the DROP ROLE
 * command") — and a renamed role goes on owning them under its new name. Without that an owner's name would name no
 * role, and nobody, or anybody, could act as the owner.
 */
public final class RoleOwnership {

    private RoleOwnership() {
    }

    /**
     * Give every object one role owns to another.
     *
     * @param catalog the catalog
     * @param from    the role that owned them
     * @param to      the role that owns them from now on
     */
    public static void handOver(final Catalog catalog, final String from, final String to) {
        for (final Database database : catalog.getAllDatabases()) {
            reown(database, from, to);
            for (final DatabaseRole role : database.getDatabaseRoles()) {
                if (owns(from, role.getOwner())) {
                    role.setOwner(to);
                }
            }
            for (final Schema schema : database.getAllSchemas()) {
                reown(schema, from, to);
                reownAll(schema.getTables(), from, to);
                reownAll(schema.getShadowedTables(), from, to);
                reownAll(schema.getViews(), from, to);
                reownAll(schema.getMaterializedViews(), from, to);
                reownAll(schema.getDynamicTables(), from, to);
                reownAll(schema.getContacts(), from, to);
                reownAll(schema.getJoinPolicies(), from, to);
                reownAll(schema.getAggregationPolicies(), from, to);
                reownAll(schema.getProjectionPolicies(), from, to);
                reownAll(schema.getMaskingPolicies(), from, to);
                reownAll(schema.getRowAccessPolicies(), from, to);
                reownAll(schema.allProcedures(), from, to);
                reownAll(schema.allFunctions(), from, to);
                reownAll(schema.getAlerts(), from, to);
                reownAll(schema.getPipes(), from, to);
                reownAll(schema.getStages(), from, to);
                reownAll(schema.getTags(), from, to);
                for (final Stream stream : schema.getStreams()) {
                    if (owns(from, stream.getOwner())) {
                        stream.setOwner(to);
                    }
                }
                for (final Task task : schema.getTasks()) {
                    if (owns(from, task.getOwner())) {
                        task.setOwner(to);
                    }
                }
                for (final Sequence sequence : schema.getSequences()) {
                    if (owns(from, sequence.getOwner())) {
                        sequence.setOwner(to);
                    }
                }
                for (final FileFormat format : schema.getFileFormats()) {
                    if (owns(from, format.getOwner())) {
                        format.setOwner(to);
                    }
                }
                for (final CortexSearchService service : schema.getCortexSearchServices()) {
                    if (owns(from, service.getOwner())) {
                        service.setOwner(to);
                    }
                }
                for (final AppObjectKind kind : AppObjectKind.values()) {
                    for (final AppObject object : schema.getAppObjects().all(kind)) {
                        if (owns(from, object.getOwner())) {
                            object.setOwner(to);
                        }
                    }
                }
                reownContainerObjects(schema.getContainerObjects(), from, to);
                reownSecurityObjects(schema.getSecurityObjects(), from, to);
            }
        }
        reownSecurityObjects(catalog.getSecurityObjects(), from, to);
        reownAll(catalog.getIntegrations().all(), from, to);
        reownAll(catalog.getExternalVolumes().all(), from, to);
        reownAll(catalog.getComputePools(), from, to);
        for (final Warehouse warehouse : catalog.getAllWarehouses()) {
            if (owns(from, warehouse.getOwner())) {
                warehouse.setOwner(to);
            }
        }
        for (final Role role : catalog.getAllRoles()) {
            if (owns(from, role.getOwner())) {
                role.setOwner(to);
            }
        }
        for (final User user : catalog.getAllUsers()) {
            if (owns(from, user.getOwner())) {
                user.setOwner(to);
            }
        }
    }

    private static void reownContainerObjects(final ContainerObjects objects, final String from, final String to) {
        for (final ImageRepository repository : objects.getImageRepositories()) {
            if (owns(from, repository.getOwner())) {
                repository.setOwner(to);
            }
        }
        for (final ArtifactRepository repository : objects.getArtifactRepositories()) {
            if (owns(from, repository.getOwner())) {
                repository.setOwner(to);
            }
        }
        for (final ContainerService service : objects.getServices()) {
            if (owns(from, service.getOwner())) {
                service.setOwner(to);
            }
        }
    }

    private static void reownSecurityObjects(final SecurityObjectStore objects, final String from, final String to) {
        for (final SecurityObjectKind kind : SecurityObjectKind.values()) {
            reownAll(objects.list(kind), from, to);
        }
    }

    private static void reownAll(final List<? extends SqlObject> objects, final String from, final String to) {
        for (final SqlObject object : objects) {
            reown(object, from, to);
        }
    }

    private static void reown(final SqlObject object, final String from, final String to) {
        if (owns(from, object.getOwner())) {
            object.setOwner(to);
        }
    }

    private static boolean owns(final String role, final String owner) {
        return owner != null && owner.equalsIgnoreCase(role);
    }
}
