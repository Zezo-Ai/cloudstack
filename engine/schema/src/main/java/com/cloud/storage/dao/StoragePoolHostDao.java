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
package com.cloud.storage.dao;

import java.util.List;

import com.cloud.host.Status;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.GenericDao;

public interface StoragePoolHostDao extends GenericDao<StoragePoolHostVO, Long> {
    public List<StoragePoolHostVO> listByPoolId(long id);

    public List<StoragePoolHostVO> listByHostIdIncludingRemoved(long hostId);

    public StoragePoolHostVO findByPoolHost(long poolId, long hostId);

    List<StoragePoolHostVO> findByLocalPath(String path);

    List<StoragePoolHostVO> listByHostStatus(long poolId, Status hostStatus);

    List<Long> findHostsConnectedToPools(List<Long> poolIds);

    boolean hasDatacenterStoragePoolHostInfo(long dcId, boolean sharedOnly);

    public void deletePrimaryRecordsForHost(long hostId);

    public void deleteStoragePoolHostDetails(long hostId, long poolId);

    List<StoragePoolHostVO> listByHostId(long hostId);

    Pair<List<StoragePoolHostVO>, Integer> listByPoolIdNotInCluster(long clusterId, long poolId);
}
