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
package org.apache.cloudstack.storage.allocator;

import com.cloud.api.query.dao.StoragePoolJoinDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.storage.ScopeType;
import com.cloud.storage.StoragePoolStatus;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;
import org.apache.commons.collections.CollectionUtils;

import com.cloud.utils.Pair;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.StoragePoolAllocator;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;

import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StorageUtil;
import com.cloud.storage.Volume;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VirtualMachineProfile;

import org.apache.cloudstack.engine.orchestration.service.VolumeOrchestrationService;

import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractStoragePoolAllocator extends AdapterBase implements StoragePoolAllocator {

    protected BigDecimal storageOverprovisioningFactor = new BigDecimal(1);
    protected long extraBytesPerVolume = 0;
    static DecimalFormat decimalFormat = new DecimalFormat("#.##");
    @Inject protected DataStoreManager dataStoreMgr;
    @Inject protected PrimaryDataStoreDao storagePoolDao;
    @Inject protected VolumeDao volumeDao;
    @Inject protected ConfigurationDao configDao;
    @Inject protected ClusterDao clusterDao;
    @Inject protected CapacityDao capacityDao;
    @Inject private StorageManager storageMgr;
    @Inject private StorageUtil storageUtil;
    @Inject private StoragePoolDetailsDao storagePoolDetailsDao;
    @Inject
    protected HostDao hostDao;
    @Inject
    protected HostPodDao podDao;

    /**
     * make sure shuffled lists of Pools are really shuffled
     */
    private SecureRandom secureRandom = new SecureRandom();

    @Inject
    protected StoragePoolJoinDao storagePoolJoinDao;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        if(configDao != null) {
            Map<String, String> configs = configDao.getConfiguration(null, params);
            String globalStorageOverprovisioningFactor = configs.get("storage.overprovisioning.factor");
            storageOverprovisioningFactor = new BigDecimal(NumbersUtil.parseFloat(globalStorageOverprovisioningFactor, 2.0f));
            extraBytesPerVolume = 0;
            return true;
        }
        return false;
    }

    protected abstract List<StoragePool> select(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, ExcludeList avoid, int returnUpTo, boolean bypassStorageTypeCheck, String keyword);

    @Override
    public List<StoragePool> allocateToPool(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, ExcludeList avoid, int returnUpTo) {
        return allocateToPool(dskCh, vmProfile, plan, avoid, returnUpTo, false, null);
    }

    @Override
    public List<StoragePool> allocateToPool(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, ExcludeList avoid, int returnUpTo, boolean bypassStorageTypeCheck) {
        return allocateToPool(dskCh, vmProfile, plan, avoid, returnUpTo, bypassStorageTypeCheck, null);
    }

    public List<StoragePool> allocateToPool(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, ExcludeList avoid, int returnUpTo, boolean bypassStorageTypeCheck, String keyword) {
        List<StoragePool> pools = select(dskCh, vmProfile, plan, avoid, returnUpTo, bypassStorageTypeCheck, keyword);
        return reorderPools(pools, vmProfile, plan, dskCh);
    }

    protected List<StoragePool> reorderPoolsByCapacity(DeploymentPlan plan, List<StoragePool> pools) {
        Long zoneId = plan.getDataCenterId();
        Long clusterId = plan.getClusterId();

        if (CollectionUtils.isEmpty(pools)) {
            return null;
        }

        short capacityType = Capacity.CAPACITY_TYPE_LOCAL_STORAGE;
        String storageType = "local";
        StoragePool storagePool = pools.get(0);
        if (storagePool.isShared()) {
            capacityType = Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED;
            storageType = "shared";
        }

        logger.debug(String.format(
                "Filtering storage pools by capacity type [%s] as the first storage pool of the list, with name [%s] and ID [%s], is a [%s] storage.",
                capacityType, storagePool.getName(), storagePool.getUuid(), storageType
        ));

        Pair<List<Long>, Map<Long, Double>> result = capacityDao.orderHostsByFreeCapacity(zoneId, clusterId, capacityType);
        List<Long> poolIdsByCapacity = result.first();
        Map<Long, String> sortedHostByCapacity = result.second().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> decimalFormat.format(entry.getValue() * 100) + "%", (e1, e2) -> e1, LinkedHashMap::new));
        logger.debug("List of pools in descending order of hostId: [{}] available capacity (percentage): {}",
                poolIdsByCapacity, sortedHostByCapacity);

        // now filter the given list of Pools by this ordered list
        Map<Long, StoragePool> poolMap = new HashMap<>();
        for (StoragePool pool : pools) {
            poolMap.put(pool.getId(), pool);
        }
        List<Long> matchingPoolIds = new ArrayList<>(poolMap.keySet());

        poolIdsByCapacity.retainAll(matchingPoolIds);

        List<StoragePool> reorderedPools = new ArrayList<>();
        for (Long id: poolIdsByCapacity) {
            reorderedPools.add(poolMap.get(id));
        }

        return reorderedPools;
    }

    protected List<StoragePool> reorderPoolsByNumberOfVolumes(DeploymentPlan plan, List<StoragePool> pools, Account account) {
        if (account == null) {
            return pools;
        }
        long dcId = plan.getDataCenterId();
        Long podId = plan.getPodId();
        Long clusterId = plan.getClusterId();

        List<Long> poolIdsByVolCount = volumeDao.listPoolIdsByVolumeCount(dcId, podId, clusterId, account.getAccountId());
        logger.debug(String.format("List of pools in ascending order of number of volumes for account [%s] is [%s].", account, poolIdsByVolCount));

        // now filter the given list of Pools by this ordered list
        Map<Long, StoragePool> poolMap = new HashMap<>();
        for (StoragePool pool : pools) {
            poolMap.put(pool.getId(), pool);
        }
        List<Long> matchingPoolIds = new ArrayList<>(poolMap.keySet());

        poolIdsByVolCount.retainAll(matchingPoolIds);

        List<StoragePool> reorderedPools = new ArrayList<>();
        for (Long id : poolIdsByVolCount) {
            reorderedPools.add(poolMap.get(id));
        }

        return reorderedPools;
    }

    @Override
    public List<StoragePool> reorderPools(List<StoragePool> pools, VirtualMachineProfile vmProfile, DeploymentPlan plan, DiskProfile dskCh) {
        if (logger.isTraceEnabled()) {
            logger.trace("reordering pools");
        }
        if (pools == null) {
            logger.trace("There are no pools to reorder; returning null.");
            return null;
        }
        if (logger.isTraceEnabled()) {
            logger.trace(String.format("reordering %d pools", pools.size()));
        }
        Account account = null;
        if (vmProfile.getVirtualMachine() != null) {
            account = vmProfile.getOwner();
        }

        pools = reorderStoragePoolsBasedOnAlgorithm(pools, plan, account);

        if (vmProfile.getVirtualMachine() == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("The VM is null, skipping pools reordering by disk provisioning type.");
            }
            return pools;
        }

        if (vmProfile.getHypervisorType() == HypervisorType.VMware &&
                !storageMgr.DiskProvisioningStrictness.valueIn(plan.getDataCenterId())) {
            pools = reorderPoolsByDiskProvisioningType(pools, dskCh);
        }

        return pools;
    }

    List<StoragePool> reorderStoragePoolsBasedOnAlgorithm(List<StoragePool> pools, DeploymentPlan plan, Account account) {
        String volumeAllocationAlgorithm = VolumeOrchestrationService.VolumeAllocationAlgorithm.value();
        logger.debug("Using volume allocation algorithm {} to reorder pools.", volumeAllocationAlgorithm);
        if (volumeAllocationAlgorithm.equals("random") || volumeAllocationAlgorithm.equals("userconcentratedpod_random") || (account == null)) {
            reorderRandomPools(pools);
        } else if (StringUtils.equalsAny(volumeAllocationAlgorithm, "userdispersing", "firstfitleastconsumed")) {
            if (logger.isTraceEnabled()) {
                logger.trace("Using reordering algorithm {}", volumeAllocationAlgorithm);
            }

            if (volumeAllocationAlgorithm.equals("userdispersing")) {
                pools = reorderPoolsByNumberOfVolumes(plan, pools, account);
            } else {
                pools = reorderPoolsByCapacity(plan, pools);
            }
        }
        return pools;
    }

    void reorderRandomPools(List<StoragePool> pools) {
        StorageUtil.traceLogStoragePools(pools, logger, "pools to choose from: ");
        if (logger.isTraceEnabled()) {
            logger.trace("Shuffle this so that we don't check the pools in the same order. Algorithm == 'random' (or no account?)");
        }
        StorageUtil.traceLogStoragePools(pools, logger, "pools to shuffle: ");
        Collections.shuffle(pools, secureRandom);
        StorageUtil.traceLogStoragePools(pools, logger, "shuffled list of pools to choose from: ");
    }

    private List<StoragePool> reorderPoolsByDiskProvisioningType(List<StoragePool> pools, DiskProfile diskProfile) {
        if (diskProfile != null && diskProfile.getProvisioningType() != null && !diskProfile.getProvisioningType().equals(Storage.ProvisioningType.THIN)) {
            List<StoragePool> reorderedPools = new ArrayList<>();
            int preferredIndex = 0;
            for (StoragePool pool : pools) {
                StoragePoolDetailVO hardwareAcceleration = storagePoolDetailsDao.findDetail(pool.getId(), Storage.Capability.HARDWARE_ACCELERATION.toString());
                if (pool.getPoolType() == Storage.StoragePoolType.NetworkFilesystem &&
                        (hardwareAcceleration == null || !hardwareAcceleration.getValue().equals("true"))) {
                    // add to the bottom of the list
                    reorderedPools.add(pool);
                } else {
                    // add to the top of the list
                    reorderedPools.add(preferredIndex++, pool);
                }
            }
            return reorderedPools;
        } else {
            return pools;
        }
    }

    protected boolean filter(ExcludeList avoid, StoragePool pool, DiskProfile dskCh, DeploymentPlan plan) {
        logger.debug(String.format("Checking if storage pool [%s] is suitable to disk [%s].", pool, dskCh));
        if (avoid.shouldAvoid(pool)) {
            logger.debug(String.format("StoragePool [%s] is in avoid set, skipping this pool to allocation of disk [%s].", pool, dskCh));
            return false;
        }

        if (dskCh.requiresEncryption() && !pool.getPoolType().supportsEncryption()) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Storage pool type '%s' doesn't support encryption required for volume, skipping this pool", pool.getPoolType()));
            }
            return false;
        }

        Long clusterId = pool.getClusterId();
        if (clusterId != null) {
            ClusterVO cluster = clusterDao.findById(clusterId);
            if (!(cluster.getHypervisorType() == dskCh.getHypervisorType())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("StoragePool's Cluster does not have required hypervisorType, skipping this pool");
                }
                return false;
            }
        } else if (pool.getHypervisor() != null && !pool.getHypervisor().equals(HypervisorType.Any) && !(pool.getHypervisor() == dskCh.getHypervisorType())) {
            if (logger.isDebugEnabled()) {
                logger.debug("StoragePool does not have required hypervisorType, skipping this pool");
            }
            return false;
        }

        if (!checkDiskProvisioningSupport(dskCh, pool)) {
            logger.debug(String.format("Storage pool [%s] does not have support to disk provisioning of disk [%s].", pool, ReflectionToStringBuilderUtils.reflectOnlySelectedFields(dskCh,
                    "type", "name", "diskOfferingId", "templateId", "volumeId", "provisioningType", "hyperType")));
            return false;
        }

        if(!checkHypervisorCompatibility(dskCh.getHypervisorType(), dskCh.getType(), pool.getPoolType())){
            return false;
        }

        if (plan.getHostId() != null) {
            HostVO plannedHost = hostDao.findById(plan.getHostId());
            if (!storageMgr.checkIfHostAndStoragePoolHasCommonStorageAccessGroups(plannedHost, pool)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("StoragePool %s and host %s does not have matching storage access groups", pool, plannedHost));
                }
                return false;
            }
        }

        Volume volume = null;
        boolean isTempVolume = dskCh.getVolumeId() == Volume.DISK_OFFERING_SUITABILITY_CHECK_VOLUME_ID;
        if (!isTempVolume) {
            volume = volumeDao.findById(dskCh.getVolumeId());
            if (!storageMgr.storagePoolCompatibleWithVolumePool(pool, volume)) {
                logger.debug(String.format("Pool [%s] is not compatible with volume [%s], skipping it.", pool, volume));
                return false;
            }
        }

        if (pool.isManaged() && !storageUtil.managedStoragePoolCanScale(pool, plan.getClusterId(), plan.getHostId())) {
            logger.debug(String.format("Cannot allocate pool [%s] to volume [%s] because the max number of managed clustered filesystems has been exceeded.", pool, volume));
            return false;
        }

        // check capacity
        List<Pair<Volume, DiskProfile>> requestVolumeDiskProfilePairs = new ArrayList<>();
        requestVolumeDiskProfilePairs.add(new Pair<>(volume, dskCh));
        if (dskCh.getHypervisorType() == HypervisorType.VMware) {
            if (pool.getPoolType() == Storage.StoragePoolType.DatastoreCluster && storageMgr.isStoragePoolDatastoreClusterParent(pool)) {
                logger.debug(String.format("Skipping allocation of pool [%s] to volume [%s] because this pool is a parent datastore cluster.", pool, volume));
                return false;
            }
            if (pool.getParent() != 0L) {
                StoragePoolVO datastoreCluster = storagePoolDao.findById(pool.getParent());
                if (datastoreCluster == null || (datastoreCluster != null && datastoreCluster.getStatus() != StoragePoolStatus.Up)) {
                    logger.debug(String.format("Skipping allocation of pool [%s] to volume [%s] because this pool is not in [%s] state.", datastoreCluster, volume, StoragePoolStatus.Up));
                    return false;
                }
            }

            try {
                boolean isStoragePoolStoragePolicyCompliance = isTempVolume ?
                        storageMgr.isStoragePoolCompliantWithStoragePolicy(dskCh.getDiskOfferingId(), pool) :
                        storageMgr.isStoragePoolCompliantWithStoragePolicy(requestVolumeDiskProfilePairs, pool);
                if (!isStoragePoolStoragePolicyCompliance) {
                    logger.debug(String.format("Skipping allocation of pool [%s] to volume [%s] because this pool is not compliant with the storage policy required by the volume.", pool, volume));
                    return false;
                }
            } catch (StorageUnavailableException e) {
                logger.warn(String.format("Could not verify storage policy complaince against storage pool %s due to exception %s", pool.getUuid(), e.getMessage()));
                return false;
            }
        }
        return isTempVolume ?
                (storageMgr.storagePoolHasEnoughIops(dskCh.getMinIops(), pool) &&
                        storageMgr.storagePoolHasEnoughSpace(dskCh.getSize(), pool)):
                (storageMgr.storagePoolHasEnoughIops(requestVolumeDiskProfilePairs, pool) &&
                        storageMgr.storagePoolHasEnoughSpace(requestVolumeDiskProfilePairs, pool, plan.getClusterId()));
    }

    private boolean checkDiskProvisioningSupport(DiskProfile dskCh, StoragePool pool) {
        if (dskCh.getHypervisorType() != null && dskCh.getHypervisorType() == HypervisorType.VMware && pool.getPoolType() == Storage.StoragePoolType.NetworkFilesystem &&
                storageMgr.DiskProvisioningStrictness.valueIn(pool.getDataCenterId())) {
            StoragePoolDetailVO hardwareAcceleration = storagePoolDetailsDao.findDetail(pool.getId(), Storage.Capability.HARDWARE_ACCELERATION.toString());
            if (dskCh.getProvisioningType() == null || !dskCh.getProvisioningType().equals(Storage.ProvisioningType.THIN) &&
                    (hardwareAcceleration == null || hardwareAcceleration.getValue() == null || !hardwareAcceleration.getValue().equals("true"))) {
                return false;
            }
        }
        return true;
    }

    /*
    Check StoragePool and Volume type compatibility for the hypervisor
     */
    private boolean checkHypervisorCompatibility(HypervisorType hyperType, Volume.Type volType, Storage.StoragePoolType poolType){
        if(HypervisorType.LXC.equals(hyperType)){
            if(Volume.Type.ROOT.equals(volType)){
                //LXC ROOT disks supports NFS and local storage pools only
                if(!(Storage.StoragePoolType.NetworkFilesystem.equals(poolType) ||
                        Storage.StoragePoolType.Filesystem.equals(poolType)) ){
                    logger.debug("StoragePool does not support LXC ROOT disk, skipping this pool");
                    return false;
                }
            } else if (Volume.Type.DATADISK.equals(volType)){
                //LXC DATA disks supports RBD storage pool only
                if(!Storage.StoragePoolType.RBD.equals(poolType)){
                    logger.debug("StoragePool does not support LXC DATA disk, skipping this pool");
                    return false;
                }
            }
        }
        return true;
    }

    protected void logDisabledStoragePools(long dcId, Long podId, Long clusterId, ScopeType scope) {
        List<StoragePoolVO> disabledPools = storagePoolDao.findDisabledPoolsByScope(dcId, podId, clusterId, scope);
        if (disabledPools != null && !disabledPools.isEmpty()) {
            logger.trace(String.format("Ignoring pools [%s] as they are in disabled state.", ReflectionToStringBuilderUtils.reflectOnlySelectedFields(disabledPools)));
        }
    }

    protected void logStartOfSearch(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, int returnUpTo,
            boolean bypassStorageTypeCheck){
        logger.trace(String.format("%s is looking for storage pools that match the VM's disk profile [%s], virtual machine profile [%s] and "
                + "deployment plan [%s]. Returning up to [%d] and bypassStorageTypeCheck [%s].", this.getClass().getSimpleName(), dskCh, vmProfile, plan, returnUpTo, bypassStorageTypeCheck));
    }

    protected void logEndOfSearch(List<StoragePool> storagePoolList) {
        logger.debug(String.format("%s is returning [%s] suitable storage pools [%s].", this.getClass().getSimpleName(), storagePoolList.size(),
                Arrays.toString(storagePoolList.toArray())));
    }

}
