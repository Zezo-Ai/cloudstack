//
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
//

package com.cloud.vm.dao;

import com.cloud.utils.Pair;
import com.cloud.vm.ConsoleSessionVO;
import com.cloud.utils.db.GenericDao;

import java.util.Date;
import java.util.List;

public interface ConsoleSessionDao extends GenericDao<ConsoleSessionVO, Long> {

    void removeSession(String sessionUuid);

    boolean isSessionAllowed(String sessionUuid);

    int expungeSessionsOlderThanDate(Date date);

    void acquireSession(String sessionUuid, String clientAddress);

    int expungeByVmList(List<Long> vmIds, Long batchSize);

    Pair<List<ConsoleSessionVO>, Integer> listConsoleSessions(Long id, List<Long> domainIds, Long accountId, Long userId, Long hostId,
                                                              Date startDate, Date endDate, Long instanceId,
                                                              String consoleEndpointCreatorAddress, String clientAddress,
                                                              boolean activeOnly, boolean acquired, Long pageSizeVal, Long startIndex);
}
