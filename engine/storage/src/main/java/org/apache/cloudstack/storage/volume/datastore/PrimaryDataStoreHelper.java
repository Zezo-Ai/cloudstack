/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.volume.datastore;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.cloud.dc.dao.ClusterDao;
import org.apache.cloudstack.annotation.AnnotationService;
import org.apache.cloudstack.annotation.dao.AnnotationDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import org.apache.cloudstack.engine.subsystem.api.storage.ClusterScope;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProviderManager;
import org.apache.cloudstack.engine.subsystem.api.storage.HostScope;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreParameters;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.CapacityVO;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.ScopeType;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.storage.StoragePoolStatus;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;

@Component
public class PrimaryDataStoreHelper {
    protected Logger logger = LogManager.getLogger(getClass());
    @Inject
    private PrimaryDataStoreDao dataStoreDao;
    @Inject
    DataStoreManager dataStoreMgr;
    @Inject
    StorageManager storageMgr;
    @Inject
    protected CapacityDao _capacityDao;
    @Inject
    protected StoragePoolHostDao storagePoolHostDao;
    @Inject
    protected ClusterDao clusterDao;
    @Inject
    private AnnotationDao annotationDao;
    @Inject
    DataStoreProviderManager dataStoreProviderMgr;

    public DataStore createPrimaryDataStore(PrimaryDataStoreParameters params) {
        if(params == null)
        {
            throw new InvalidParameterValueException("createPrimaryDataStore: Input params is null, please check");
        }
        StoragePoolVO dataStoreVO = dataStoreDao.findPoolByUUID(params.getUuid());
        if (dataStoreVO != null) {
            throw new CloudRuntimeException("duplicate uuid: " + params.getUuid());
        }
        dataStoreVO = new StoragePoolVO();
        dataStoreVO.setStorageProviderName(params.getProviderName());
        dataStoreVO.setHostAddress(params.getHost());
        dataStoreVO.setPoolType(params.getType());
        dataStoreVO.setPath(params.getPath());
        dataStoreVO.setPort(params.getPort());
        dataStoreVO.setName(params.getName());
        dataStoreVO.setUuid(params.getUuid());
        dataStoreVO.setDataCenterId(params.getZoneId());
        dataStoreVO.setPodId(params.getPodId());
        dataStoreVO.setClusterId(params.getClusterId());
        dataStoreVO.setStatus(StoragePoolStatus.Initialized);
        dataStoreVO.setUserInfo(params.getUserInfo());
        dataStoreVO.setManaged(params.isManaged());
        dataStoreVO.setCapacityIops(params.getCapacityIops());
        dataStoreVO.setCapacityBytes(params.getCapacityBytes());
        dataStoreVO.setUsedBytes(params.getUsedBytes());
        dataStoreVO.setHypervisor(params.getHypervisorType());

        Map<String, String> details = params.getDetails();
        if (params.getType() == StoragePoolType.SMB && details != null) {
            String user = details.get("user");
            String password = details.get("password");
            String domain = details.get("domain");
            String updatedPath = params.getPath();

            if (user == null || password == null) {
                String errMsg = "Missing cifs user and password details. Add them as details parameter.";
                logger.warn(errMsg);
                throw new InvalidParameterValueException(errMsg);
            } else {
                try {
                    password = DBEncryptionUtil.encrypt(URLEncoder.encode(password, "UTF-8"));
                    details.put("password", password);
                    updatedPath += "?user=" + user + "&password=" + password + "&domain=" + domain;
                } catch (UnsupportedEncodingException e) {
                    throw new CloudRuntimeException("Error while generating the cifs url. " + e.getMessage());
                }
            }

            dataStoreVO.setPath(updatedPath);
        }
        String tags = params.getTags();
        List<String> storageTags = new ArrayList<String>();

        if (tags != null) {
            String[] tokens = tags.split(",");

            for (String tag : tokens) {
                tag = tag.trim();
                if (tag.length() == 0) {
                    continue;
                }
                storageTags.add(tag);
            }
        }

        boolean displayDetails = true;
        DataStoreProvider storeProvider = dataStoreProviderMgr.getDataStoreProvider(params.getProviderName());
        if (storeProvider != null) {
            DataStoreDriver storeDriver = storeProvider.getDataStoreDriver();
            if (storeDriver != null) {
                displayDetails = storeDriver.canDisplayDetails();
            }
        }

        String storageAccessGroupsParams = params.getStorageAccessGroups();
        List<String> storageAccessGroupsList = new ArrayList<String>();

        if (storageAccessGroupsParams != null) {
            String[] storageAccessGroups = storageAccessGroupsParams.split(",");

            for (String storageAccessGroup : storageAccessGroups) {
                storageAccessGroup = storageAccessGroup.trim();
                if (storageAccessGroup.length() == 0) {
                    continue;
                }
                storageAccessGroupsList.add(storageAccessGroup);
            }
        }

        dataStoreVO = dataStoreDao.persist(dataStoreVO, details, storageTags, params.isTagARule(), displayDetails, storageAccessGroupsList);

        return dataStoreMgr.getDataStore(dataStoreVO.getId(), DataStoreRole.Primary);
    }

    public DataStore attachHost(DataStore store, HostScope scope, StoragePoolInfo existingInfo) {
        StoragePoolHostVO poolHost = storagePoolHostDao.findByPoolHost(store.getId(), scope.getScopeId());
        if (poolHost == null) {
            poolHost = new StoragePoolHostVO(store.getId(), scope.getScopeId(), existingInfo.getLocalPath());
            storagePoolHostDao.persist(poolHost);
        }

        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        pool.setScope(scope.getScopeType());
        pool.setUsedBytes(existingInfo.getCapacityBytes() - existingInfo.getAvailableBytes());
        pool.setCapacityBytes(existingInfo.getCapacityBytes());
        pool.setStatus(StoragePoolStatus.Up);
        this.dataStoreDao.update(pool.getId(), pool);
        this.storageMgr.createCapacityEntry(pool, Capacity.CAPACITY_TYPE_LOCAL_STORAGE, pool.getUsedBytes());
        return dataStoreMgr.getDataStore(pool.getId(), DataStoreRole.Primary);
    }

    public DataStore attachCluster(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());

        storageMgr.createCapacityEntry(pool.getId());

        pool.setScope(ScopeType.CLUSTER);
        pool.setStatus(StoragePoolStatus.Up);
        this.dataStoreDao.update(pool.getId(), pool);
        if(pool.getPoolType() == StoragePoolType.DatastoreCluster && pool.getParent() == 0) {
            List<StoragePoolVO> childDatastores = dataStoreDao.listChildStoragePoolsInDatastoreCluster(pool.getId());
            for (StoragePoolVO child : childDatastores) {
                child.setScope(ScopeType.CLUSTER);
                this.dataStoreDao.update(child.getId(), child);
            }
        }
        return dataStoreMgr.getDataStore(store.getId(), DataStoreRole.Primary);
    }

    public DataStore attachZone(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        storageMgr.createCapacityEntry(pool.getId());
        pool.setScope(ScopeType.ZONE);
        pool.setStatus(StoragePoolStatus.Up);
        this.dataStoreDao.update(pool.getId(), pool);
        return dataStoreMgr.getDataStore(store.getId(), DataStoreRole.Primary);
    }

    public DataStore attachZone(DataStore store, HypervisorType hypervisor) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        storageMgr.createCapacityEntry(pool.getId());
        pool.setScope(ScopeType.ZONE);
        pool.setHypervisor(hypervisor);
        pool.setStatus(StoragePoolStatus.Up);
        this.dataStoreDao.update(pool.getId(), pool);
        if(pool.getPoolType() == StoragePoolType.DatastoreCluster && pool.getParent() == 0) {
            List<StoragePoolVO> childDatastores = dataStoreDao.listChildStoragePoolsInDatastoreCluster(pool.getId());
            for (StoragePoolVO child : childDatastores) {
                child.setScope(ScopeType.ZONE);
                this.dataStoreDao.update(child.getId(), child);
            }
        }
        return dataStoreMgr.getDataStore(store.getId(), DataStoreRole.Primary);
    }

    public boolean maintain(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        pool.setStatus(StoragePoolStatus.Maintenance);
        this.dataStoreDao.update(pool.getId(), pool);
        return true;
    }

    public boolean cancelMaintain(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        pool.setStatus(StoragePoolStatus.Up);
        dataStoreDao.update(store.getId(), pool);
        return true;
    }

    public boolean disable(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        pool.setStatus(StoragePoolStatus.Disabled);
        this.dataStoreDao.update(pool.getId(), pool);
        return true;
    }

    public boolean enable(DataStore store) {
        StoragePoolVO pool = this.dataStoreDao.findById(store.getId());
        pool.setStatus(StoragePoolStatus.Up);
        dataStoreDao.update(pool.getId(), pool);
        return true;
    }

    protected boolean deletePoolStats(Long poolId) {
        CapacityVO capacity1 = _capacityDao.findByHostIdType(poolId, Capacity.CAPACITY_TYPE_STORAGE);
        CapacityVO capacity2 = _capacityDao.findByHostIdType(poolId, Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED);
        if (capacity1 != null) {
            _capacityDao.remove(capacity1.getId());
        }

        if (capacity2 != null) {
            _capacityDao.remove(capacity2.getId());
        }

        return true;
    }

    public boolean deletePrimaryDataStore(DataStore store) {
        List<StoragePoolHostVO> hostPoolRecords = this.storagePoolHostDao.listByPoolId(store.getId());
        StoragePoolVO poolVO = this.dataStoreDao.findById(store.getId());
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        txn.start();
        for (StoragePoolHostVO host : hostPoolRecords) {
            storagePoolHostDao.deleteStoragePoolHostDetails(host.getHostId(), host.getPoolId());
        }
        poolVO.setUuid(null);
        this.dataStoreDao.update(poolVO.getId(), poolVO);
        dataStoreDao.remove(poolVO.getId());
        dataStoreDao.deletePoolTags(poolVO.getId());
        dataStoreDao.deleteStoragePoolAccessGroups(poolVO.getId());
        annotationDao.removeByEntityType(AnnotationService.EntityType.PRIMARY_STORAGE.name(), poolVO.getUuid());
        deletePoolStats(poolVO.getId());
        // Delete op_host_capacity entries
        this._capacityDao.removeBy(Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED, null, null, null, poolVO.getId());
        txn.commit();

        logger.debug("Storage pool {} is removed successfully", poolVO);
        return true;
    }

    public void switchToZone(DataStore store, HypervisorType hypervisorType) {
        StoragePoolVO pool = dataStoreDao.findById(store.getId());
        CapacityVO capacity = _capacityDao.findByHostIdType(store.getId(), Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED);
        Transaction.execute(new TransactionCallbackNoReturn() {
            public void doInTransactionWithoutResult(TransactionStatus status) {
                pool.setScope(ScopeType.ZONE);
                pool.setPodId(null);
                pool.setClusterId(null);
                pool.setHypervisor(hypervisorType);
                dataStoreDao.update(pool.getId(), pool);

                capacity.setPodId(null);
                capacity.setClusterId(null);
                _capacityDao.update(capacity.getId(), capacity);
            }
        });
        logger.debug("Scope of storage pool {} is changed to zone", pool);
    }

    public void switchToCluster(DataStore store, ClusterScope clusterScope) {
        List<StoragePoolHostVO> hostPoolRecords = storagePoolHostDao.listByPoolIdNotInCluster(clusterScope.getScopeId(), store.getId()).first();
        StoragePoolVO pool = dataStoreDao.findById(store.getId());
        CapacityVO capacity = _capacityDao.findByHostIdType(store.getId(), Capacity.CAPACITY_TYPE_STORAGE_ALLOCATED);

        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                if (hostPoolRecords != null) {
                    for (StoragePoolHostVO host : hostPoolRecords) {
                        storagePoolHostDao.deleteStoragePoolHostDetails(host.getHostId(), host.getPoolId());
                    }
                }
                pool.setScope(ScopeType.CLUSTER);
                pool.setPodId(clusterScope.getPodId());
                pool.setClusterId(clusterScope.getScopeId());
                dataStoreDao.update(pool.getId(), pool);

                capacity.setPodId(clusterScope.getPodId());
                capacity.setClusterId(clusterScope.getScopeId());
                _capacityDao.update(capacity.getId(), capacity);
            }
        });
        logger.debug("Scope of storage pool {} is changed to cluster {}", pool::toString, () -> clusterDao.findById(clusterScope.getScopeId()));
    }
}
