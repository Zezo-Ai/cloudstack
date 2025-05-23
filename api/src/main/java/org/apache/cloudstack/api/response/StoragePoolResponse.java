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
package org.apache.cloudstack.api.response;

import com.cloud.serializer.Param;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolStatus;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithAnnotations;
import org.apache.cloudstack.api.EntityReference;

import java.util.Date;
import java.util.Map;

@EntityReference(value = StoragePool.class)
public class StoragePoolResponse extends BaseResponseWithAnnotations {
    @SerializedName("id")
    @Param(description = "the ID of the storage pool")
    private String id;

    @SerializedName("zoneid")
    @Param(description = "the Zone ID of the storage pool")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the Zone name of the storage pool")
    private String zoneName;

    @SerializedName("podid")
    @Param(description = "the Pod ID of the storage pool")
    private String podId;

    @SerializedName("podname")
    @Param(description = "the Pod name of the storage pool")
    private String podName;

    @SerializedName("name")
    @Param(description = "the name of the storage pool")
    private String name;

    @SerializedName("ipaddress")
    @Param(description = "the IP address of the storage pool")
    private String ipAddress;

    @SerializedName("path")
    @Param(description = "the storage pool path")
    private String path;

    @SerializedName("created")
    @Param(description = "the date and time the storage pool was created")
    private Date created;

    @SerializedName("type")
    @Param(description = "the storage pool type")
    private String type;

    @SerializedName("clusterid")
    @Param(description = "the ID of the cluster for the storage pool")
    private String clusterId;

    @SerializedName("clustername")
    @Param(description = "the name of the cluster for the storage pool")
    private String clusterName;

    @SerializedName("disksizetotal")
    @Param(description = "the total disk size of the storage pool")
    private Long diskSizeTotal;

    @SerializedName("disksizeallocated")
    @Param(description = "the host's currently allocated disk size")
    private Long diskSizeAllocated;

    @SerializedName("disksizeused")
    @Param(description = "the host's currently used disk size")
    private Long diskSizeUsed;

    @SerializedName("capacityiops")
    @Param(description = "IOPS CloudStack can provision from this storage pool")
    private Long capacityIops;

    @SerializedName("allocatediops")
    @Param(description = "total min IOPS currently in use by volumes")
    private Long allocatedIops;

    @SerializedName(ApiConstants.USED_IOPS)
    @Param(description = "total IOPS currently in use", since = "4.20.1")
    private Long usedIops;

    @SerializedName(ApiConstants.STORAGE_CUSTOM_STATS)
    @Param(description = "the storage pool custom stats", since = "4.18.1")
    private Map<String, String> customStats;

    @SerializedName("tags")
    @Param(description = "the tags for the storage pool")
    private String tags;

    @SerializedName(ApiConstants.STORAGE_ACCESS_GROUPS)
    @Param(description = "the storage access groups for the storage pool", since = "4.21.0")
    private String storageAccessGroups;

    @SerializedName(ApiConstants.NFS_MOUNT_OPTIONS)
    @Param(description = "the nfs mount options for the storage pool", since = "4.19.1")
    private String nfsMountOpts;

    @SerializedName(ApiConstants.IS_TAG_A_RULE)
    @Param(description = ApiConstants.PARAMETER_DESCRIPTION_IS_TAG_A_RULE)
    private Boolean isTagARule;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the storage pool")
    private StoragePoolStatus state;

    @SerializedName(ApiConstants.SCOPE)
    @Param(description = "the scope of the storage pool")
    private String scope;

    @SerializedName("overprovisionfactor")
    @Param(description = "the overprovisionfactor for the storage pool", since = "4.4")
    private String overProvisionFactor;

    @SerializedName(ApiConstants.HYPERVISOR)
    @Param(description = "the hypervisor type of the storage pool")
    private String hypervisor;

    @SerializedName("suitableformigration")
    @Param(description = "true if this pool is suitable to migrate a volume," + " false otherwise")
    private Boolean suitableForMigration;

    @SerializedName("provider")
    @Param(description = "Storage provider for this pool")
    private String provider;

    @SerializedName(ApiConstants.STORAGE_CAPABILITIES)
    @Param(description = "the storage pool capabilities")
    private Map<String, String> caps;

    @SerializedName(ApiConstants.MANAGED)
    @Param(description = "whether this pool is managed or not")
    private Boolean managed;

    @SerializedName(ApiConstants.DETAILS)
    @Param(description = "the storage pool details")
    private Map<String, String> details;

    public Map<String, String> getCaps() {
        return caps;
    }

    public void setCaps(Map<String, String> cap) {
        this.caps = cap;
    }

    /**
     * @return the scope
     */
    public String getScope() {
        return scope;
    }

    /**
     * @param scope the scope to set
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getHypervisor() {
        return hypervisor;
    }

    public void setHypervisor(String hypervisor) {
        this.hypervisor = hypervisor;
    }

    @Override
    public String getObjectId() {
        return this.getId();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getPodId() {
        return podId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Long getDiskSizeTotal() {
        return diskSizeTotal;
    }

    public void setDiskSizeTotal(Long diskSizeTotal) {
        this.diskSizeTotal = diskSizeTotal;
    }

    public Long getDiskSizeAllocated() {
        return diskSizeAllocated;
    }

    public void setDiskSizeAllocated(Long diskSizeAllocated) {
        this.diskSizeAllocated = diskSizeAllocated;
    }

    public Long getDiskSizeUsed() {
        return diskSizeUsed;
    }

    public void setDiskSizeUsed(Long diskSizeUsed) {
        this.diskSizeUsed = diskSizeUsed;
    }

    public Long getCapacityIops() {
        return capacityIops;
    }

    public void setCapacityIops(Long capacityIops) {
        this.capacityIops = capacityIops;
    }

    public void setAllocatedIops(Long allocatedIops) {
       this.allocatedIops = allocatedIops;
    }

    public Long getUsedIops() {
        return usedIops;
    }

    public void setUsedIops(Long usedIops) {
        this.usedIops = usedIops;
    }

    public Map<String, String> getCustomStats() {
        return customStats;
    }

    public void setCustomStats(Map<String, String> customStats) {
        this.customStats = customStats;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getStorageAccessGroups() {
        return storageAccessGroups;
    }

    public void setStorageAccessGroups(String storageAccessGroups) {
        this.storageAccessGroups = storageAccessGroups;
    }

    public Boolean getIsTagARule() {
        return isTagARule;
    }

    public void setIsTagARule(Boolean tagARule) {
        isTagARule = tagARule;
    }

    public StoragePoolStatus getState() {
        return state;
    }

    public void setState(StoragePoolStatus state) {
        this.state = state;
    }

    public void setSuitableForMigration(Boolean suitableForMigration) {
        this.suitableForMigration = suitableForMigration;
    }

    public void setOverProvisionFactor(String overProvisionFactor) {
        this.overProvisionFactor = overProvisionFactor;
    }

    public String getOverProvisionFactor() {
        return overProvisionFactor;
    }

    public Boolean getSuitableForMigration() {
        return suitableForMigration;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getNfsMountOpts() {
        return nfsMountOpts;
    }

    public void setNfsMountOpts(String nfsMountOpts) {
        this.nfsMountOpts = nfsMountOpts;
    }

    public Long getAllocatedIops() {
        return allocatedIops;
    }

    public Boolean getTagARule() {
        return isTagARule;
    }

    public void setTagARule(Boolean tagARule) {
        isTagARule = tagARule;
    }

    public Boolean getManaged() {
        return managed;
    }

    public void setManaged(Boolean managed) {
        this.managed = managed;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }
}
