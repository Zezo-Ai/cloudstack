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

package org.apache.cloudstack.quota.activationrule.presetvariables;

import com.cloud.utils.DateUtil;

import java.util.Date;
import java.util.TimeZone;

public class Account extends GenericPresetVariable {
    @PresetVariableDefinition(description = "Role of the account. This field will not exist if the account is a project.")

    private Role role;

    @PresetVariableDefinition(description = "The date the account was created in GMT. This field will not exist for the first root admin account.")
    private String created;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
        fieldNamesToIncludeInToString.add("role");
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = DateUtil.displayDateInTimezone(TimeZone.getTimeZone("GMT"), created);
        fieldNamesToIncludeInToString.add("created");
    }
}
