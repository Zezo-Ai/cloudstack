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
package com.cloud.capacity;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "op_host_capacity")
public class CapacityVO implements Capacity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "host_id")
    private Long hostOrPoolId;

    @Column(name = "data_center_id")
    private Long dataCenterId;

    @Column(name = "pod_id")
    private Long podId;

    @Column(name = "cluster_id")
    private Long clusterId;

    @Column(name = "used_capacity")
    private long usedCapacity;

    @Column(name = "reserved_capacity")
    private long reservedCapacity;

    @Column(name = "total_capacity")
    private long totalCapacity;

    @Column(name = "capacity_type")
    private short capacityType;

    @Column(name = "capacity_state")
    private CapacityState capacityState;

    @Column(name = GenericDao.CREATED_COLUMN)
    protected Date created;

    @Column(name = "update_time", updatable = true, nullable = true)
    @Temporal(value = TemporalType.TIMESTAMP)
    protected Date updateTime;

    @Transient
    private Float usedPercentage;

    @Transient
    private Long allocatedCapacity;

    @Transient
    private String tag;

    public CapacityVO() {
    }

    public CapacityVO(Long hostId, Long dataCenterId, Long podId, Long clusterId, long usedCapacity, long totalCapacity, short capacityType) {
        this.hostOrPoolId = hostId;
        this.dataCenterId = dataCenterId;
        this.podId = podId;
        this.clusterId = clusterId;
        this.usedCapacity = usedCapacity;
        this.totalCapacity = totalCapacity;
        this.capacityType = capacityType;
        this.updateTime = new Date();
        this.capacityState = CapacityState.Enabled;
    }

    public CapacityVO(Long dataCenterId, Long podId, Long clusterId, short capacityType, float usedPercentage) {
        this.dataCenterId = dataCenterId;
        this.podId = podId;
        this.clusterId = clusterId;
        this.capacityType = capacityType;
        this.usedPercentage = usedPercentage;
        this.capacityState = CapacityState.Enabled;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Long getHostOrPoolId() {
        return hostOrPoolId;
    }

    public void setHostId(Long hostId) {
        this.hostOrPoolId = hostId;
    }

    @Override
    public Long getDataCenterId() {
        return dataCenterId;
    }

    public void setDataCenterId(Long dataCenterId) {
        this.dataCenterId = dataCenterId;
    }

    @Override
    public Long getPodId() {
        return podId;
    }

    public void setPodId(Long podId) {
        this.podId = podId;
    }

    @Override
    public Long getClusterId() {
        return clusterId;
    }

    public void setClusterId(Long clusterId) {
        this.clusterId = clusterId;
    }

    @Override
    public long getUsedCapacity() {
        return usedCapacity;
    }

    public void setUsedCapacity(long usedCapacity) {
        this.usedCapacity = usedCapacity;
        this.setUpdateTime(new Date());
    }

    @Override
    public long getReservedCapacity() {
        return reservedCapacity;
    }

    public void setReservedCapacity(long reservedCapacity) {
        this.reservedCapacity = reservedCapacity;
        this.setUpdateTime(new Date());
    }

    @Override
    public long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(long totalCapacity) {
        this.totalCapacity = totalCapacity;
        this.setUpdateTime(new Date());
    }

    @Override
    public short getCapacityType() {
        return capacityType;
    }

    public void setCapacityType(short capacityType) {
        this.capacityType = capacityType;
    }

    public CapacityState getCapacityState() {
        return capacityState;
    }

    public void setCapacityState(CapacityState capacityState) {
        this.capacityState = capacityState;
    }

    public Date getCreated() {
        return created;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public Float getUsedPercentage() {
        return usedPercentage;
    }

    public void setUsedPercentage(float usedPercentage) {
        this.usedPercentage = usedPercentage;
    }

    public Long getAllocatedCapacity() {
        return allocatedCapacity;
    }

    public void setAllocatedCapacity(Long allocatedCapacity) {
        this.allocatedCapacity = allocatedCapacity;
    }

    @Override
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public String getUuid() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private static Map<Short, String> capacityNames = null;
    static {
        capacityNames = new HashMap<Short, String>();
        capacityNames.put(CAPACITY_TYPE_MEMORY, "MEMORY");
        capacityNames.put(CAPACITY_TYPE_CPU, "CPU");
        capacityNames.put(CAPACITY_TYPE_STORAGE, "STORAGE");
        capacityNames.put(CAPACITY_TYPE_STORAGE_ALLOCATED, "STORAGE_ALLOCATED");
        capacityNames.put(CAPACITY_TYPE_VIRTUAL_NETWORK_PUBLIC_IP, "VIRTUAL_NETWORK_PUBLIC_IP");
        capacityNames.put(CAPACITY_TYPE_PRIVATE_IP, "PRIVATE_IP");
        capacityNames.put(CAPACITY_TYPE_SECONDARY_STORAGE, "SECONDARY_STORAGE");
        capacityNames.put(CAPACITY_TYPE_VLAN, "VLAN");
        capacityNames.put(CAPACITY_TYPE_DIRECT_ATTACHED_PUBLIC_IP, "DIRECT_ATTACHED_PUBLIC_IP");
        capacityNames.put(CAPACITY_TYPE_LOCAL_STORAGE, "LOCAL_STORAGE");
        capacityNames.put(CAPACITY_TYPE_GPU, "GPU");
        capacityNames.put(CAPACITY_TYPE_CPU_CORE, "CPU_CORE");
        capacityNames.put(CAPACITY_TYPE_VIRTUAL_NETWORK_IPV6_SUBNET, "VIRTUAL_NETWORK_IPV6_SUBNET");
        capacityNames.put(CAPACITY_TYPE_BACKUP_STORAGE, "BACKUP_STORAGE");
        capacityNames.put(CAPACITY_TYPE_OBJECT_STORAGE, "OBJECT_STORAGE");
    }

    public static String getCapacityName (Short capacityType) {
        return capacityNames.get(capacityType);
    }
}
