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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.cloud.utils.Pair;
import org.springframework.stereotype.Component;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import com.cloud.capacity.Capacity;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.ScopeType;
import com.cloud.storage.StoragePool;
import com.cloud.user.Account;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VirtualMachineProfile;

@Component
public class ZoneWideStoragePoolAllocator extends AbstractStoragePoolAllocator {
    @Inject
    private DataStoreManager dataStoreMgr;
    @Inject
    protected CapacityDao capacityDao;

    @Override
    protected List<StoragePool> select(DiskProfile dskCh, VirtualMachineProfile vmProfile, DeploymentPlan plan, ExcludeList avoid, int returnUpTo, boolean bypassStorageTypeCheck, String keyword) {
        logStartOfSearch(dskCh, vmProfile, plan, returnUpTo, bypassStorageTypeCheck);

        if (!bypassStorageTypeCheck && dskCh.useLocalStorage()) {
            return null;
        }

        if (logger.isTraceEnabled()) {
            // Log the pools details that are ignored because they are in disabled state
            logDisabledStoragePools(plan.getDataCenterId(), null, null, ScopeType.ZONE);
        }

        List<StoragePool> suitablePools = new ArrayList<>();
        List<StoragePoolVO> storagePools = storagePoolDao.findZoneWideStoragePoolsByTags(plan.getDataCenterId(), dskCh.getTags(), true);
        storagePools.addAll(storagePoolJoinDao.findStoragePoolByScopeAndRuleTags(plan.getDataCenterId(), null, null, ScopeType.ZONE, List.of(dskCh.getTags())));
        if (storagePools.isEmpty()) {
            logger.debug(String.format("Could not find any zone wide storage pool that matched with any of the following tags [%s].", Arrays.toString(dskCh.getTags())));
            storagePools = new ArrayList<>();
        }

        List<StoragePoolVO> anyHypervisorStoragePools = new ArrayList<>();
        for (StoragePoolVO storagePool : storagePools) {
            if (HypervisorType.Any.equals(storagePool.getHypervisor())) {
                anyHypervisorStoragePools.add(storagePool);
            }
        }

        List<StoragePoolVO> storagePoolsByHypervisor = storagePoolDao.findZoneWideStoragePoolsByHypervisor(plan.getDataCenterId(), dskCh.getHypervisorType());
        storagePools.retainAll(storagePoolsByHypervisor);
        storagePools.addAll(anyHypervisorStoragePools);

        // add remaining pools in zone, that did not match tags, to avoid set
        List<StoragePoolVO> allPools = storagePoolDao.findZoneWideStoragePoolsByTags(plan.getDataCenterId(), null, false);
        allPools.removeAll(storagePools);
        for (StoragePoolVO pool : allPools) {
            avoid.addPool(pool.getId());
        }

        for (StoragePoolVO storage : storagePools) {
            if (suitablePools.size() == returnUpTo) {
                break;
            }
            StoragePool storagePool = (StoragePool)this.dataStoreMgr.getPrimaryDataStore(storage.getId());
            if (filter(avoid, storagePool, dskCh, plan)) {
                logger.debug("Found suitable zone wide storage pool [{}] to allocate disk [{}] to it, adding to list.", storagePool, dskCh);
                suitablePools.add(storagePool);
            } else {
                if (canAddStoragePoolToAvoidSet(storage)) {
                    logger.debug(String.format("Adding storage pool [%s] to avoid set during allocation of disk [%s].", storagePool, dskCh));
                    avoid.addPool(storagePool.getId());
                }
            }
        }
        logEndOfSearch(suitablePools);

        return suitablePools;
    }

    // Don't add zone-wide, managed storage to the avoid list because it may be usable for another cluster.
    private boolean canAddStoragePoolToAvoidSet(StoragePoolVO storagePoolVO) {
        return !ScopeType.ZONE.equals(storagePoolVO.getScope()) || !storagePoolVO.isManaged();
    }

    @Override
    protected List<StoragePool> reorderPoolsByCapacity(DeploymentPlan plan,
        List<StoragePool> pools) {
        Long zoneId = plan.getDataCenterId();
        short capacityType;
        if(pools != null && pools.size() != 0){
            capacityType = pools.get(0).getPoolType().isShared() ? Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED : Capacity.CAPACITY_TYPE_LOCAL_STORAGE;
        } else{
            return null;
        }

        Pair<List<Long>, Map<Long, Double>> result = capacityDao.orderHostsByFreeCapacity(zoneId, null, capacityType);
        List<Long> poolIdsByCapacity = result.first();
        Map<Long, String> sortedHostByCapacity = result.second().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> decimalFormat.format(entry.getValue() * 100) + "%",
                        (e1, e2) -> e1, LinkedHashMap::new));
        if (logger.isDebugEnabled()) {
            logger.debug("List of zone-wide storage pools: [{}] in descending order of free capacity (percentage): {}",
                    poolIdsByCapacity, sortedHostByCapacity);
        }

      //now filter the given list of Pools by this ordered list
      Map<Long, StoragePool> poolMap = new HashMap<>();
      for (StoragePool pool : pools) {
          poolMap.put(pool.getId(), pool);
      }
      List<Long> matchingPoolIds = new ArrayList<>(poolMap.keySet());

      poolIdsByCapacity.retainAll(matchingPoolIds);

      List<StoragePool> reorderedPools = new ArrayList<>();
      for(Long id: poolIdsByCapacity){
          reorderedPools.add(poolMap.get(id));
      }

      return reorderedPools;
    }

    @Override
    protected List<StoragePool> reorderPoolsByNumberOfVolumes(DeploymentPlan plan, List<StoragePool> pools, Account account) {
        if (account == null) {
            return pools;
        }
        long dcId = plan.getDataCenterId();

        List<Long> poolIdsByVolCount = volumeDao.listZoneWidePoolIdsByVolumeCount(dcId, account.getAccountId());
        if (logger.isDebugEnabled()) {
            logger.debug("List of pools in ascending order of number of volumes for account id: " + account.getAccountId() + " is: " + poolIdsByVolCount);
        }

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
}
