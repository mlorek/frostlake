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

package dev.frostlake.http.rest;

import dev.frostlake.http.rest.resource.AccountResource;
import dev.frostlake.http.rest.resource.AlertResource;
import dev.frostlake.http.rest.resource.ApiIntegrationResource;
import dev.frostlake.http.rest.resource.ArtifactRepositoryResource;
import dev.frostlake.http.rest.resource.CatalogIntegrationResource;
import dev.frostlake.http.rest.resource.ComputePoolResource;
import dev.frostlake.http.rest.resource.CortexEmbedResource;
import dev.frostlake.http.rest.resource.CortexInferenceResource;
import dev.frostlake.http.rest.resource.CortexSearchServiceResource;
import dev.frostlake.http.rest.resource.DatabaseResource;
import dev.frostlake.http.rest.resource.DatabaseRoleResource;
import dev.frostlake.http.rest.resource.DynamicTableResource;
import dev.frostlake.http.rest.resource.EventTableResource;
import dev.frostlake.http.rest.resource.ExternalVolumeResource;
import dev.frostlake.http.rest.resource.FunctionResource;
import dev.frostlake.http.rest.resource.GrantResource;
import dev.frostlake.http.rest.resource.IcebergTableResource;
import dev.frostlake.http.rest.resource.ImageRepositoryResource;
import dev.frostlake.http.rest.resource.ManagedAccountResource;
import dev.frostlake.http.rest.resource.NetworkPolicyResource;
import dev.frostlake.http.rest.resource.NetworkRuleResource;
import dev.frostlake.http.rest.resource.NotebookResource;
import dev.frostlake.http.rest.resource.NotificationIntegrationResource;
import dev.frostlake.http.rest.resource.PasswordPolicyResource;
import dev.frostlake.http.rest.resource.PipeResource;
import dev.frostlake.http.rest.resource.ProcedureResource;
import dev.frostlake.http.rest.resource.RoleResource;
import dev.frostlake.http.rest.resource.SchemaResource;
import dev.frostlake.http.rest.resource.SecretResource;
import dev.frostlake.http.rest.resource.SequenceResource;
import dev.frostlake.http.rest.resource.ServiceResource;
import dev.frostlake.http.rest.resource.SparkConnectResource;
import dev.frostlake.http.rest.resource.StageResource;
import dev.frostlake.http.rest.resource.StreamResource;
import dev.frostlake.http.rest.resource.StreamlitResource;
import dev.frostlake.http.rest.resource.TableResource;
import dev.frostlake.http.rest.resource.TagResource;
import dev.frostlake.http.rest.resource.TaskResource;
import dev.frostlake.http.rest.resource.UserDefinedFunctionResource;
import dev.frostlake.http.rest.resource.UserResource;
import dev.frostlake.http.rest.resource.ViewResource;
import dev.frostlake.http.rest.resource.WarehouseResource;

import java.util.ArrayList;
import java.util.List;

/** The resources the {@code /api/v2} surface serves, one per specification file. */
public final class RestApi {

    /** Static registry only. */
    private RestApi() {
    }

    /** Every resource, in the order they are registered. */
    public static List<RestResource> resources() {
        final List<RestResource> resources = new ArrayList<>();
        resources.add(new RestResultResource());
        resources.add(new WarehouseResource());
        resources.add(new TagResource());
        resources.add(new AccountResource());
        resources.add(new ManagedAccountResource());
        resources.add(new UserResource());
        resources.add(new RoleResource());
        resources.add(new DatabaseRoleResource());
        resources.add(new GrantResource());
        resources.add(new DatabaseResource());
        resources.add(new SchemaResource());
        resources.add(new TableResource());
        resources.add(new ViewResource());
        resources.add(new DynamicTableResource());
        resources.add(new EventTableResource());
        resources.add(new IcebergTableResource());
        resources.add(new SequenceResource());
        resources.add(new StageResource());
        resources.add(new ExternalVolumeResource());
        resources.add(new PipeResource());
        resources.add(new StreamResource());
        resources.add(new TaskResource());
        resources.add(new FunctionResource());
        resources.add(new UserDefinedFunctionResource());
        resources.add(new ProcedureResource());
        resources.add(new ArtifactRepositoryResource());
        resources.add(new ComputePoolResource());
        resources.add(new ImageRepositoryResource());
        resources.add(new ServiceResource());
        resources.add(new NetworkPolicyResource());
        resources.add(new NetworkRuleResource());
        resources.add(new PasswordPolicyResource());
        resources.add(new SecretResource());
        resources.add(new ApiIntegrationResource());
        resources.add(new CatalogIntegrationResource());
        resources.add(new NotificationIntegrationResource());
        resources.add(new AlertResource());
        resources.add(new NotebookResource());
        resources.add(new StreamlitResource());
        resources.add(new CortexInferenceResource());
        resources.add(new CortexEmbedResource());
        resources.add(new CortexSearchServiceResource());
        resources.add(new SparkConnectResource());
        return resources;
    }

    /** A route table holding every resource's endpoints. */
    public static RestRouter router() {
        final RestRouter router = new RestRouter();
        for (final RestResource resource : resources()) {
            resource.register(router);
        }
        return router;
    }
}
