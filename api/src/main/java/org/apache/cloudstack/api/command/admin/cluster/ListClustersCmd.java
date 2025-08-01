// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.api.command.admin.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.cloudstack.extension.Extension;
import org.apache.cloudstack.extension.ExtensionHelper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.cloud.cpu.CPU;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.org.Cluster;
import com.cloud.utils.Pair;

@APICommand(name = "listClusters", description = "Lists clusters.", responseObject = ClusterResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListClustersCmd extends BaseListCmd {

    @Inject
    ExtensionHelper extensionHelper;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ClusterResponse.class, description = "lists clusters by the cluster ID")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "lists clusters by the cluster name")
    private String clusterName;

    @Parameter(name = ApiConstants.POD_ID, type = CommandType.UUID, entityType = PodResponse.class, description = "lists clusters by Pod ID")
    private Long podId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "lists clusters by Zone ID")
    private Long zoneId;

    @Parameter(name = ApiConstants.HYPERVISOR, type = CommandType.STRING, description = "lists clusters by hypervisor type")
    private String hypervisorType;

    @Parameter(name = ApiConstants.CLUSTER_TYPE, type = CommandType.STRING, description = "lists clusters by cluster type")
    private String clusterType;

    @Parameter(name = ApiConstants.ALLOCATION_STATE, type = CommandType.STRING, description = "lists clusters by allocation state")
    private String allocationState;

    @Parameter(name = ApiConstants.MANAGED_STATE, type = CommandType.STRING, description = "whether this cluster is managed by cloudstack")
    private String managedState;

    @Parameter(name = ApiConstants.SHOW_CAPACITIES, type = CommandType.BOOLEAN, description = "flag to display the capacity of the clusters")
    private Boolean showCapacities;

    @Parameter(name = ApiConstants.ARCH, type = CommandType.STRING,
            description = "CPU arch of the clusters",
            since = "4.20.1")
    private String arch;

    @Parameter(name = ApiConstants.STORAGE_ACCESS_GROUP, type = CommandType.STRING,
            description = "the name of the storage access group",
            since = "4.21.0")
    private String storageAccessGroup;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public String getClusterName() {
        return clusterName;
    }

    public Long getPodId() {
        return podId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public String getHypervisorType() {
        return hypervisorType;
    }

    public String getClusterType() {
        return clusterType;
    }

    public String getAllocationState() {
        return allocationState;
    }

    public String getManagedstate() {
        return managedState;
    }

    public void setManagedstate(String managedstate) {
        this.managedState = managedstate;
    }

    public Boolean getShowCapacities() {
        return showCapacities;
    }

    public CPU.CPUArch getArch() {
        return StringUtils.isBlank(arch) ? null : CPU.CPUArch.fromType(arch);
    }

    public String getStorageAccessGroup() {
        return storageAccessGroup;
    }

    public ListClustersCmd() {

    }

    public ListClustersCmd(String storageAccessGroup) {
        this.storageAccessGroup = storageAccessGroup;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    protected void updateClustersExtensions(final List<ClusterResponse> clusterResponses) {
        if (CollectionUtils.isEmpty(clusterResponses)) {
            return;
        }
        Map<Long, Extension> idExtensionMap = new HashMap<>();
        for  (ClusterResponse response : clusterResponses) {
            if (!Hypervisor.HypervisorType.External.getHypervisorDisplayName().equals(response.getHypervisorType())) {
                continue;
            }
            Long extensionId = extensionHelper.getExtensionIdForCluster(response.getInternalId());
            if (extensionId == null) {
                continue;
            }
            Extension extension = idExtensionMap.computeIfAbsent(extensionId, id -> extensionHelper.getExtension(id));
            if (extension == null) {
                continue;
            }
            response.setExtensionId(extension.getUuid());
            response.setExtensionName(extension.getName());
        }
    }

    protected Pair<List<ClusterResponse>, Integer> getClusterResponses() {
        Pair<List<? extends Cluster>, Integer> result = _mgr.searchForClusters(this);
        List<ClusterResponse> clusterResponses = new ArrayList<>();
        for (Cluster cluster : result.first()) {
            ClusterResponse clusterResponse = _responseGenerator.createClusterResponse(cluster, showCapacities);
            clusterResponse.setObjectName("cluster");
            clusterResponses.add(clusterResponse);
        }
        updateClustersExtensions(clusterResponses);
        return new Pair<>(clusterResponses, result.second());
    }

    @Override
    public void execute() {
        Pair<List<ClusterResponse>, Integer> clusterResponses = getClusterResponses();
        ListResponse<ClusterResponse> response = new ListResponse<>();
        response.setResponses(clusterResponses.first(), clusterResponses.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }
}
