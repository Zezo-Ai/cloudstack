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
package com.cloud.api.query.vo;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cloud.user.Account;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import com.cloud.user.Account.State;
import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "account_view")
public class AccountJoinVO extends BaseViewVO implements InternalIdentity, Identity {

    @Id
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "account_name")
    private String accountName = null;

    @Column(name = "type")
    @Enumerated(value = EnumType.ORDINAL)
    private Account.Type type;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;

    @Column(name = GenericDao.CREATED_COLUMN)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;

    @Column(name = "cleanup_needed")
    private boolean needsCleanup = false;

    @Column(name = "network_domain")
    private String networkDomain;

    @Column(name = "domain_id")
    private long domainId;

    @Column(name = "domain_uuid")
    private String domainUuid;

    @Column(name = "domain_name")
    private String domainName = null;

    @Column(name = "domain_path")
    private String domainPath = null;

    @Column(name = "data_center_id")
    private long dataCenterId;

    @Column(name = "data_center_uuid")
    private String dataCenterUuid;

    @Column(name = "data_center_name")
    private String dataCenterName;

    @Column(name = "bytesReceived")
    private Long bytesReceived;

    @Column(name = "bytesSent")
    private Long bytesSent;

    @Column(name = "vmLimit")
    private Long vmLimit;

    @Column(name = "vmTotal")
    private Long vmTotal;

    @Column(name = "ipLimit")
    private Long ipLimit;

    @Column(name = "ipTotal")
    private Long ipTotal;

    @Column(name = "ipFree")
    private Long ipFree;

    @Column(name = "volumeLimit")
    private Long volumeLimit;

    @Column(name = "volumeTotal")
    private Long volumeTotal;

    @Column(name = "snapshotLimit")
    private Long snapshotLimit;

    @Column(name = "snapshotTotal")
    private Long snapshotTotal;

    @Column(name = "backupLimit")
    private Long backupLimit;

    @Column(name = "backupTotal")
    private Long backupTotal;

    @Column(name = "backupStorageLimit")
    private Long backupStorageLimit;

    @Column(name = "backupStorageTotal")
    private Long backupStorageTotal;

    @Column(name = "templateLimit")
    private Long templateLimit;

    @Column(name = "templateTotal")
    private Long templateTotal;

    @Column(name = "stoppedVms")
    private Integer vmStopped;

    @Column(name = "runningVms")
    private Integer vmRunning;

    @Column(name = "projectLimit")
    private Long projectLimit;

    @Column(name = "projectTotal")
    private Long projectTotal;

    @Column(name = "networkLimit")
    private Long networkLimit;

    @Column(name = "networkTotal")
    private Long networkTotal;

    @Column(name = "vpcLimit")
    private Long vpcLimit;

    @Column(name = "vpcTotal")
    private Long vpcTotal;

    @Column(name = "cpuLimit")
    private Long cpuLimit;

    @Column(name = "cpuTotal")
    private Long cpuTotal;

    @Column(name = "memoryLimit")
    private Long memoryLimit;

    @Column(name = "memoryTotal")
    private Long memoryTotal;

    @Column(name = "gpuLimit")
    private Long gpuLimit;

    @Column(name = "gpuTotal")
    private Long gpuTotal;

    @Column(name = "primaryStorageLimit")
    private Long primaryStorageLimit;

    @Column(name = "primaryStorageTotal")
    private Long primaryStorageTotal;

    @Column(name = "secondaryStorageLimit")
    private Long secondaryStorageLimit;

    @Column(name = "secondaryStorageTotal")
    private Long secondaryStorageTotal;

    @Column(name = "bucketLimit")
    private Long bucketLimit;

    @Column(name = "bucketTotal")
    private Long bucketTotal;

    @Column(name = "objectStorageLimit")
    private Long objectStorageLimit;

    @Column(name = "objectStorageTotal")
    private Long objectStorageTotal;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_uuid")
    private String jobUuid;

    @Column(name = "job_status")
    private int jobStatus;

    @Column(name = "default")
    boolean isDefault;

    @Column(name = "api_key_access")
    Boolean apiKeyAccess;

    public AccountJoinVO() {
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public String getAccountName() {
        return accountName;
    }

    public Account.Type getType() {
        return type;
    }

    public Long getRoleId() {
        return roleId;
    }

    public State getState() {
        return state;
    }

    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }

    public boolean isNeedsCleanup() {
        return needsCleanup;
    }

    public String getNetworkDomain() {
        return networkDomain;
    }

    public long getDomainId() {
        return domainId;
    }

    public String getDomainUuid() {
        return domainUuid;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getDomainPath() {
        return domainPath;
    }

    public long getDataCenterId() {
        return dataCenterId;
    }

    public String getDataCenterUuid() {
        return dataCenterUuid;
    }

    public String getDataCenterName() {
        return dataCenterName;
    }

    public Long getBytesReceived() {
        return bytesReceived;
    }

    public Long getBytesSent() {
        return bytesSent;
    }

    public Long getVmTotal() {
        return vmTotal;
    }

    public Long getIpTotal() {
        return ipTotal;
    }

    public Long getIpFree() {
        return ipFree;
    }

    public Long getVolumeTotal() {
        return volumeTotal;
    }

    public Long getSnapshotTotal() {
        return snapshotTotal;
    }

    public Long getBackupTotal() {
        return backupTotal;
    }

    public Long getBackupStorageTotal() {
        return backupStorageTotal;
    }

    public Long getTemplateTotal() {
        return templateTotal;
    }

    public Integer getVmStopped() {
        return vmStopped;
    }

    public Integer getVmRunning() {
        return vmRunning;
    }

    public Long getProjectTotal() {
        return projectTotal;
    }

    public Long getNetworkTotal() {
        return networkTotal;
    }

    public Long getVpcTotal() {
        return vpcTotal;
    }

    public Long getCpuTotal() {
        return cpuTotal;
    }

    public Long getMemoryTotal() {
        return memoryTotal;
    }

    public Long getGpuTotal() {
        return gpuTotal;
    }

    public Long getPrimaryStorageTotal() {
        return primaryStorageTotal;
    }

    public Long getSecondaryStorageTotal() {
        return secondaryStorageTotal;
    }

    public Long getBucketTotal() {
        return bucketTotal;
    }

    public Long getObjectStorageTotal() {
        return objectStorageTotal;
    }

    public Long getVmLimit() {
        return vmLimit;
    }

    public Long getIpLimit() {
        return ipLimit;
    }

    public Long getVolumeLimit() {
        return volumeLimit;
    }

    public Long getSnapshotLimit() {
        return snapshotLimit;
    }

    public Long getBackupLimit() {
        return backupLimit;
    }

    public Long getBackupStorageLimit() {
        return backupStorageLimit;
    }

    public Long getTemplateLimit() {
        return templateLimit;
    }

    public Long getProjectLimit() {
        return projectLimit;
    }

    public Long getNetworkLimit() {
        return networkLimit;
    }

    public Long getVpcLimit() {
        return vpcLimit;
    }

    public Long getCpuLimit() {
        return cpuLimit;
    }

    public Long getMemoryLimit() {
        return memoryLimit;
    }

    public Long getGpuLimit() {
        return gpuLimit;
    }

    public Long getPrimaryStorageLimit() {
        return primaryStorageLimit;
    }

    public Long getSecondaryStorageLimit() {
        return secondaryStorageLimit;
    }

    public Long getBucketLimit() {
        return bucketLimit;
    }

    public Long getObjectStorageLimit() {
        return objectStorageLimit;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getJobUuid() {
        return jobUuid;
    }

    public int getJobStatus() {
        return jobStatus;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Boolean getApiKeyAccess() {
        return apiKeyAccess;
    }
}
