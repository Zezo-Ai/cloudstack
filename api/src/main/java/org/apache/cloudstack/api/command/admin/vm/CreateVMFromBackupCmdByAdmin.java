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
package org.apache.cloudstack.api.command.admin.vm;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.command.user.vm.CreateVMFromBackupCmd;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.cloudstack.api.response.UserVmResponse;

import com.cloud.vm.VirtualMachine;

@APICommand(name = "createVMFromBackup",
        description = "Creates and automatically starts a VM from a backup.",
        responseObject = UserVmResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true,
        since = "4.21.0",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class CreateVMFromBackupCmdByAdmin extends CreateVMFromBackupCmd implements AdminCmd {

    @Parameter(name = ApiConstants.POD_ID, type = CommandType.UUID, entityType = PodResponse.class, description = "destination Pod ID to deploy the VM to - parameter available for root admin only", since = "4.21")
    private Long podId;

    @Parameter(name = ApiConstants.CLUSTER_ID, type = CommandType.UUID, entityType = ClusterResponse.class, description = "destination Cluster ID to deploy the VM to - parameter available for root admin only", since = "4.21")
    private Long clusterId;

    public Long getPodId() {
        return podId;
    }

    public Long getClusterId() {
        return clusterId;
    }
}
