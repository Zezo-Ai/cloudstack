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
package org.apache.cloudstack.storage.datastore.provider;

import com.cloud.exception.StorageConflictException;
import com.cloud.host.HostVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

public class LinstorHostListener extends DefaultHostListener {
    @Override
    public boolean hostConnect(long hostId, long poolId) throws StorageConflictException {
        HostVO host = hostDao.findById(hostId);
        if (host.getParent() == null) {
            host.setParent(host.getName());
            hostDao.update(host.getId(), host);
        }
        StoragePoolVO pool = primaryStoreDao.findById(poolId);
        return super.hostConnect(host, pool);
    }
}
