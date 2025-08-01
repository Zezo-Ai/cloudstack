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

import java.util.Date;
import java.util.Map;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithAnnotations;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.offering.ServiceOffering;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = ServiceOffering.class)
public class ServiceOfferingResponse extends BaseResponseWithAnnotations {
    @SerializedName("id")
    @Param(description = "the id of the service offering")
    private String id;

    @SerializedName("name")
    @Param(description = "the name of the service offering")
    private String name;

    @SerializedName("state")
    @Param(description = "state of the service offering")
    private String state;

    @SerializedName("displaytext")
    @Param(description = "an alternate display text of the service offering.")
    private String displayText;

    @SerializedName("cpunumber")
    @Param(description = "the number of CPU")
    private Integer cpuNumber;

    @SerializedName("cpuspeed")
    @Param(description = "the clock rate CPU speed in Mhz")
    private Integer cpuSpeed;

    @SerializedName("memory")
    @Param(description = "the memory in MB")
    private Integer memory;

    @SerializedName("created")
    @Param(description = "the date this service offering was created")
    private Date created;

    @SerializedName("storagetype")
    @Param(description = "the storage type for this service offering")
    private String storageType;

    @SerializedName("provisioningtype") @Param(description="provisioning type used to create volumes. Valid values are thin, sparse, fat.", since = "4.4.0")
    private String provisioningType;

    @SerializedName("offerha")
    @Param(description = "the ha support in the service offering")
    private Boolean offerHa;

    @SerializedName("limitcpuuse")
    @Param(description = "restrict the CPU usage to committed service offering")
    private Boolean limitCpuUse;

    @SerializedName("isvolatile")
    @Param(description = "true if the vm needs to be volatile, i.e., on every reboot of vm from API root disk is discarded and creates a new root disk")
    private Boolean isVolatile;

    @SerializedName(ApiConstants.STORAGE_TAGS)
    @Param(description = "the tags for the service offering")
    private String tags;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the domain ID(s) this disk offering belongs to. Ignore this information as it is not currently applicable.")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the domain name(s) this disk offering belongs to. Ignore this information as it is not currently applicable.")
    private String domain;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the zone ID(s) this disk offering belongs to. Ignore this information as it is not currently applicable.", since = "4.13.0")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE)
    @Param(description = "the zone name(s) this disk offering belongs to. Ignore this information as it is not currently applicable.", since = "4.13.0")
    private String zone;

    @SerializedName(ApiConstants.HOST_TAGS)
    @Param(description = "the host tag for the service offering")
    private String hostTag;

    @SerializedName(ApiConstants.IS_SYSTEM_OFFERING)
    @Param(description = "is this a system vm offering")
    private Boolean isSystem;

    @SerializedName(ApiConstants.IS_DEFAULT_USE)
    @Param(description = "is this a  default system vm offering")
    private Boolean defaultUse;

    @SerializedName(ApiConstants.SYSTEM_VM_TYPE)
    @Param(description = "is this a the systemvm type for system vm offering")
    private String vmType;

    @SerializedName(ApiConstants.NETWORKRATE)
    @Param(description = "data transfer rate in megabits per second allowed.")
    private Integer networkRate;

    @SerializedName("iscustomizediops")
    @Param(description = "true if disk offering uses custom iops, false otherwise", since = "4.4")
    private Boolean customizedIops;

    @SerializedName(ApiConstants.MIN_IOPS)
    @Param(description = "the min iops of the disk offering", since = "4.4")
    private Long minIops;

    @SerializedName(ApiConstants.MAX_IOPS)
    @Param(description = "the max iops of the disk offering", since = "4.4")
    private Long maxIops;

    @SerializedName(ApiConstants.HYPERVISOR_SNAPSHOT_RESERVE)
    @Param(description = "Hypervisor snapshot reserve space as a percent of a volume (for managed storage using Xen or VMware)", since = "4.4")
    private Integer hypervisorSnapshotReserve;

    @SerializedName("diskBytesReadRate")
    @Param(description = "bytes read rate of the service offering")
    private Long bytesReadRate;

    @SerializedName("diskBytesReadRateMax")
    @Param(description = "burst bytes read rate of the disk offering")
    private Long bytesReadRateMax;

    @SerializedName("diskBytesReadRateMaxLength")
    @Param(description = "length (in seconds) of the burst")
    private Long bytesReadRateMaxLength;

    @SerializedName("diskBytesWriteRate")
    @Param(description = "bytes write rate of the service offering")
    private Long bytesWriteRate;

    @SerializedName("diskBytesWriteRateMax")
    @Param(description = "burst bytes write rate of the disk offering")
    private Long bytesWriteRateMax;

    @SerializedName("diskBytesWriteRateMaxLength")
    @Param(description = "length (in seconds) of the burst")
    private Long bytesWriteRateMaxLength;

    @SerializedName("diskIopsReadRate")
    @Param(description = "io requests read rate of the service offering")
    private Long iopsReadRate;

    @SerializedName("diskIopsReadRateMax")
    @Param(description = "burst io requests read rate of the disk offering")
    private Long iopsReadRateMax;

    @SerializedName("diskIopsReadRateMaxLength")
    @Param(description = "length (in second) of the burst")
    private Long iopsReadRateMaxLength;

    @SerializedName("diskIopsWriteRate")
    @Param(description = "io requests write rate of the service offering")
    private Long iopsWriteRate;

    @SerializedName("diskIopsWriteRateMax")
    @Param(description = "burst io requests write rate of the disk offering")
    private Long iopsWriteRateMax;

    @SerializedName("diskIopsWriteRateMaxLength")
    @Param(description = "length (in seconds) of the burst")
    private Long iopsWriteRateMaxLength;

    @SerializedName(ApiConstants.DEPLOYMENT_PLANNER)
    @Param(description = "deployment strategy used to deploy VM.")
    private String deploymentPlanner;

    @SerializedName(ApiConstants.SERVICE_OFFERING_DETAILS)
    @Param(description = "additional key/value details tied with this service offering", since = "4.2.0")
    private Map<String, String> details;

    @SerializedName("iscustomized")
    @Param(description = "is true if the offering is customized", since = "4.3.0")
    private Boolean isCustomized;

    @SerializedName("cacheMode")
    @Param(description = "the cache mode to use for this disk offering. none, writeback or writethrough", since = "4.14")
    private String cacheMode;

    @SerializedName("vspherestoragepolicy")
    @Param(description = "the vsphere storage policy tagged to the service offering in case of VMware", since = "4.15")
    private String vsphereStoragePolicy;

    @SerializedName(ApiConstants.ROOT_DISK_SIZE)
    @Param(description = "Root disk size in GB", since = "4.15")
    private Long rootDiskSize;

    @SerializedName(ApiConstants.DYNAMIC_SCALING_ENABLED)
    @Param(description = "true if virtual machine needs to be dynamically scalable of cpu or memory", since = "4.16")
    private Boolean dynamicScalingEnabled;

    @SerializedName(ApiConstants.DISK_OFFERING_STRICTNESS)
    @Param(description = "True/False to indicate the strictness of the disk offering association with the compute offering. " +
            "When set to true, override of disk offering is not allowed when VM is deployed and " +
            "change disk offering is not allowed for the ROOT disk after the VM is deployed", since = "4.17")
    private Boolean diskOfferingStrictness;

    @SerializedName(ApiConstants.DISK_OFFERING_ID)
    @Param(description = "the ID of the disk offering to which service offering is linked", since = "4.17")
    private String diskOfferingId;

    @SerializedName("diskofferingname")
    @Param(description = "name of the disk offering", since = "4.17")
    private String diskOfferingName;

    @SerializedName("diskofferingdisplaytext")
    @Param(description = "the display text of the disk offering", since = "4.17")
    private String diskOfferingDisplayText;

    @SerializedName(ApiConstants.ENCRYPT_ROOT)
    @Param(description = "true if virtual machine root disk will be encrypted on storage", since = "4.18")
    private Boolean encryptRoot;

    @SerializedName(ApiConstants.GPU_CARD_ID)
    @Param(description = "the ID of the gpu card to which service offering is linked", since = "4.21")
    private String gpuCardId;

    @SerializedName(ApiConstants.GPU_CARD_NAME)
    @Param(description = "the name of the gpu card to which service offering is linked", since = "4.21")
    private String gpuCardName;

    @SerializedName(ApiConstants.VGPU_PROFILE_ID)
    @Param(description = "the ID of the vgpu profile to which service offering is linked", since = "4.21")
    private String vgpuProfileId;

    @SerializedName(ApiConstants.VGPU_PROFILE_NAME)
    @Param(description = "the name of the vgpu profile to which service offering is linked", since = "4.21")
    private String vgpuProfileName;

    @SerializedName(ApiConstants.VIDEORAM)
    @Param(description = "the video RAM size in MB")
    private Long videoRam;

    @SerializedName(ApiConstants.MAXHEADS)
    @Param(description = "the maximum number of display heads")
    private Long maxHeads;

    @SerializedName(ApiConstants.MAXRESOLUTIONX)
    @Param(description = "the maximum X resolution")
    private Long maxResolutionX;

    @SerializedName(ApiConstants.MAXRESOLUTIONY)
    @Param(description = "the maximum Y resolution")
    private Long maxResolutionY;

    @SerializedName(ApiConstants.GPU_COUNT)
    @Param(description = "the count of GPUs to attach ", since = "4.21")
    private Integer gpuCount;

    @SerializedName(ApiConstants.GPU_DISPLAY)
    @Param(description = "whether GPU device is used for display or not ", since = "4.21")
    private Boolean gpuDisplay;

    @SerializedName(ApiConstants.PURGE_RESOURCES)
    @Param(description = "Whether to cleanup VM and its associated resource upon expunge", since = "4.20")
    private Boolean purgeResources;

    @SerializedName(ApiConstants.INSTANCE_LEASE_DURATION)
    @Param(description = "Instance lease duration (in days) for service offering", since = "4.21.0")
    private Integer leaseDuration;

    @SerializedName(ApiConstants.INSTANCE_LEASE_EXPIRY_ACTION)
    @Param(description = "Action to be taken once lease is over", since = "4.21.0")
    private String leaseExpiryAction;

    public ServiceOfferingResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystemOffering(Boolean isSystem) {
        this.isSystem = isSystem;
    }

    public Boolean isDefaultUse() {
        return defaultUse;
    }

    public void setDefaultUse(Boolean defaultUse) {
        this.defaultUse = defaultUse;
    }

    public String getSystemVmType() {
        return vmType;
    }

    public void setSystemVmType(String vmtype) {
        vmType = vmtype;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public int getCpuNumber() {
        return cpuNumber;
    }

    public void setCpuNumber(Integer cpuNumber) {
        this.cpuNumber = cpuNumber;
    }

    public int getCpuSpeed() {
        return cpuSpeed;
    }

    public void setCpuSpeed(Integer cpuSpeed) {
        this.cpuSpeed = cpuSpeed;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getProvisioningType(){
        return provisioningType;
    }

    public void setProvisioningType(String provisioningType){
        this.provisioningType = provisioningType;
    }

    public Boolean getOfferHa() {
        return offerHa;
    }

    public void setOfferHa(Boolean offerHa) {
        this.offerHa = offerHa;
    }

    public Boolean getLimitCpuUse() {
        return limitCpuUse;
    }

    public void setLimitCpuUse(Boolean limitCpuUse) {
        this.limitCpuUse = limitCpuUse;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getHostTag() {
        return hostTag;
    }

    public void setHostTag(String hostTag) {
        this.hostTag = hostTag;
    }

    public void setNetworkRate(Integer networkRate) {
        this.networkRate = networkRate;
    }

    public String getDeploymentPlanner() {
        return deploymentPlanner;
    }

    public void setDeploymentPlanner(String deploymentPlanner) {
        this.deploymentPlanner = deploymentPlanner;
    }

    public boolean getVolatileVm() {
        return isVolatile;
    }

    public void setVolatileVm(boolean isVolatile) {
        this.isVolatile = isVolatile;
    }

    public Boolean isCustomizedIops() {
        return customizedIops;
    }

    public void setCustomizedIops(Boolean customizedIops) {
        this.customizedIops = customizedIops;
    }

    public Long getMinIops() {
        return minIops;
    }

    public void setMinIops(Long minIops) {
        this.minIops = minIops;
    }

    public Long getMaxIops() {
        return maxIops;
    }

    public void setMaxIops(Long maxIops) {
        this.maxIops = maxIops;
    }

    public Integer getHypervisorSnapshotReserve() {
        return hypervisorSnapshotReserve;
    }

    public void setHypervisorSnapshotReserve(Integer hypervisorSnapshotReserve) {
        this.hypervisorSnapshotReserve = hypervisorSnapshotReserve;
    }

    public void setBytesReadRate(Long bytesReadRate) {
        this.bytesReadRate = bytesReadRate;
    }

    public void setBytesReadRateMax(Long bytesReadRateMax) { this.bytesReadRateMax = bytesReadRateMax; }

    public void setBytesReadRateMaxLength(Long bytesReadRateMaxLength) { this.bytesReadRateMaxLength = bytesReadRateMaxLength; }

    public void setBytesWriteRate(Long bytesWriteRate) {
        this.bytesWriteRate = bytesWriteRate;
    }

    public void setBytesWriteRateMax(Long bytesWriteRateMax) { this.bytesWriteRateMax = bytesWriteRateMax; }

    public void setBytesWriteRateMaxLength(Long bytesWriteRateMaxLength) { this.bytesWriteRateMaxLength = bytesWriteRateMaxLength; }

    public void setIopsReadRate(Long iopsReadRate) {
        this.iopsReadRate = iopsReadRate;
    }

    public void setIopsReadRateMax(Long iopsReadRateMax) { this.iopsReadRateMax = iopsReadRateMax; }

    public void setIopsReadRateMaxLength(Long iopsReadRateMaxLength) { this.iopsReadRateMaxLength = iopsReadRateMaxLength; }

    public void setIopsWriteRate(Long iopsWriteRate) {
        this.iopsWriteRate = iopsWriteRate;
    }

    public void setIopsWriteRateMax(Long iopsWriteRateMax) { this.iopsWriteRateMax = iopsWriteRateMax; }

    public void setIopsWriteRateMaxLength(Long iopsWriteRateMaxLength) { this.iopsWriteRateMaxLength = iopsWriteRateMaxLength; }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public void setIscutomized(boolean iscutomized) {
        this.isCustomized = iscutomized;
    }

    public void setCacheMode(String cacheMode) {
        this.cacheMode = cacheMode;
    }

    public Integer getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Integer leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public String getLeaseExpiryAction() {
        return leaseExpiryAction;
    }

    public void setLeaseExpiryAction(String leaseExpiryAction) {
        this.leaseExpiryAction = leaseExpiryAction;
    }

    public String getVsphereStoragePolicy() {
        return vsphereStoragePolicy;
    }

    public void setVsphereStoragePolicy(String vsphereStoragePolicy) {
        this.vsphereStoragePolicy = vsphereStoragePolicy;
    }

    public void setRootDiskSize(Long rootDiskSize) {
        this.rootDiskSize = rootDiskSize;
    }

    public Boolean getDynamicScalingEnabled() {
        return dynamicScalingEnabled;
    }

    public void setDynamicScalingEnabled(Boolean dynamicScalingEnabled) {
        this.dynamicScalingEnabled = dynamicScalingEnabled;
    }


    public Boolean getDiskOfferingStrictness() {
        return diskOfferingStrictness;
    }

    public void setDiskOfferingStrictness(Boolean diskOfferingStrictness) {
        this.diskOfferingStrictness = diskOfferingStrictness;
    }

    public void setDiskOfferingId(String diskOfferingId) {
        this.diskOfferingId = diskOfferingId;
    }

    public void setDiskOfferingName(String diskOfferingName) {
        this.diskOfferingName = diskOfferingName;
    }

    public void setDiskOfferingDisplayText(String diskOfferingDisplayText) {
        this.diskOfferingDisplayText = diskOfferingDisplayText;
    }

    public String getDiskOfferingId() {
        return diskOfferingId;
    }

    public String getDiskOfferingName() {
        return diskOfferingName;
    }

    public String getDiskOfferingDisplayText() {
        return diskOfferingDisplayText;
    }

    public void setEncryptRoot(Boolean encrypt) { this.encryptRoot = encrypt; }

    public String getVgpuProfileName() {
        return vgpuProfileName;
    }

    public void setVgpuProfileName(String vgpuProfileName) {
        this.vgpuProfileName = vgpuProfileName;
    }

    public Long getVideoRam() {
        return videoRam;
    }

    public void setVideoRam(Long videoRam) {
        this.videoRam = videoRam;
    }

    public Long getMaxHeads() {
        return maxHeads;
    }

    public void setMaxHeads(Long maxHeads) {
        this.maxHeads = maxHeads;
    }

    public Long getMaxResolutionX() {
        return maxResolutionX;
    }

    public void setMaxResolutionX(Long maxResolutionX) {
        this.maxResolutionX = maxResolutionX;
    }

    public Long getMaxResolutionY() {
        return maxResolutionY;
    }

    public void setMaxResolutionY(Long maxResolutionY) {
        this.maxResolutionY = maxResolutionY;
    }

    public String getVgpuProfileId() {
        return vgpuProfileId;
    }

    public void setVgpuProfileId(String vgpuProfileId) {
        this.vgpuProfileId = vgpuProfileId;
    }

    public String getGpuCardName() {
        return gpuCardName;
    }

    public void setGpuCardName(String gpuCardName) {
        this.gpuCardName = gpuCardName;
    }

    public String getGpuCardId() {
        return gpuCardId;
    }

    public void setGpuCardId(String gpuCardId) {
        this.gpuCardId = gpuCardId;
    }

    public Integer getGpuCount() {
        return gpuCount;
    }

    public void setGpuCount(Integer gpuCount) {
        this.gpuCount = gpuCount;
    }

    public Boolean getGpuDisplay() {
        return gpuDisplay;
    }

    public void setGpuDisplay(Boolean gpuDisplay) {
        this.gpuDisplay = gpuDisplay;
    }

    public void setPurgeResources(Boolean purgeResources) {
        this.purgeResources = purgeResources;
    }
}
