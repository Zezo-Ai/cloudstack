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
package org.apache.cloudstack.api.command.admin.offering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cloud.offering.ServiceOffering.State;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import com.cloud.dc.DataCenter;
import com.cloud.domain.Domain;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.offering.ServiceOffering;
import com.cloud.user.Account;

@APICommand(name = "updateServiceOffering", description = "Updates a service offering.", responseObject = ServiceOfferingResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateServiceOfferingCmd extends BaseCmd {

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = ServiceOfferingResponse.class,
            required = true,
            description = "the ID of the service offering to be updated")
    private Long id;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, description = "the display text of the service offering to be updated")
    private String displayText;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the service offering to be updated")
    private String serviceOfferingName;

    @Parameter(name = ApiConstants.SORT_KEY, type = CommandType.INTEGER, description = "sort key of the service offering, integer")
    private Integer sortKey;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.STRING,
            description = "the ID of the containing domain(s) as comma separated string, public for public offerings",
            length = 4096)
    private String domainIds;

    @Parameter(name = ApiConstants.ZONE_ID,
            type = CommandType.STRING,
            description = "the ID of the containing zone(s) as comma separated string, all for all zones offerings",
            since = "4.13")
    private String zoneIds;

    @Parameter(name = ApiConstants.STORAGE_TAGS,
            type = CommandType.STRING,
            description = "comma-separated list of tags for the service offering, tags should match with existing storage pool tags",
            since = "4.16")
    private String storageTags;

    @Parameter(name = ApiConstants.HOST_TAGS,
            type = CommandType.STRING,
            description = "the host tag for this service offering.",
            since = "4.16")
    private String hostTags;

    @Parameter(name = ApiConstants.STATE,
            type = CommandType.STRING,
            description = "state of the service offering")
    private String serviceOfferingState;

    @Parameter(name = ApiConstants.PURGE_RESOURCES, type = CommandType.BOOLEAN,
            description = "Whether to cleanup VM and its associated resource upon expunge",
            since="4.20")
    private Boolean purgeResources;

    @Parameter(name = ApiConstants.EXTERNAL_DETAILS,
            type = CommandType.MAP,
            description = "Details in key/value pairs using format externaldetails[i].keyname=keyvalue. Example: externaldetails[0].endpoint.url=urlvalue",
            since = "4.21.0")
    private Map externalDetails;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getDisplayText() {
        return displayText;
    }

    public Long getId() {
        return id;
    }

    public String getServiceOfferingName() {
        return serviceOfferingName;
    }

    public Integer getSortKey() {
        return sortKey;
    }

    public List<Long> getDomainIds() {
        List<Long> validDomainIds = new ArrayList<>();
        if (StringUtils.isNotEmpty(domainIds)) {
            if (domainIds.contains(",")) {
                String[] domains = domainIds.split(",");
                for (String domain : domains) {
                    Domain validDomain = _entityMgr.findByUuid(Domain.class, domain.trim());
                    if (validDomain != null) {
                        validDomainIds.add(validDomain.getId());
                    } else {
                        throw new InvalidParameterValueException("Failed to create service offering because invalid domain has been specified.");
                    }
                }
            } else {
                domainIds = domainIds.trim();
                if (!domainIds.matches("public")) {
                    Domain validDomain = _entityMgr.findByUuid(Domain.class, domainIds.trim());
                    if (validDomain != null) {
                        validDomainIds.add(validDomain.getId());
                    } else {
                        throw new InvalidParameterValueException("Failed to create service offering because invalid domain has been specified.");
                    }
                }
            }
        } else {
            validDomainIds.addAll(_configService.getServiceOfferingDomains(id));
        }
        return validDomainIds;
    }

    public List<Long> getZoneIds() {
        List<Long> validZoneIds = new ArrayList<>();
        if (StringUtils.isNotEmpty(zoneIds)) {
            if (zoneIds.contains(",")) {
                String[] zones = zoneIds.split(",");
                for (String zone : zones) {
                    DataCenter validZone = _entityMgr.findByUuid(DataCenter.class, zone.trim());
                    if (validZone != null) {
                        validZoneIds.add(validZone.getId());
                    } else {
                        throw new InvalidParameterValueException("Failed to create service offering because invalid zone has been specified.");
                    }
                }
            } else {
                zoneIds = zoneIds.trim();
                if (!zoneIds.matches("all")) {
                    DataCenter validZone = _entityMgr.findByUuid(DataCenter.class, zoneIds.trim());
                    if (validZone != null) {
                        validZoneIds.add(validZone.getId());
                    } else {
                        throw new InvalidParameterValueException("Failed to create service offering because invalid zone has been specified.");
                    }
                }
            }
        } else {
            validZoneIds.addAll(_configService.getServiceOfferingZones(id));
        }
        return validZoneIds;
    }

    public String getStorageTags() {
        return storageTags;
    }

    public String getHostTags() {
        return hostTags;
    }

    public State getState() {
        State state = EnumUtils.getEnumIgnoreCase(State.class, serviceOfferingState);
        if (StringUtils.isNotBlank(serviceOfferingState) && state == null) {
            throw new InvalidParameterValueException("Invalid state value: " + serviceOfferingState);
        }
        return state;
    }

    public boolean isPurgeResources() {
        return Boolean.TRUE.equals(purgeResources);
    }

    public Map<String, String> getExternalDetails() {
        return convertExternalDetailsToMap(externalDetails);
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public Long getApiResourceId() {
        return id;
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.ServiceOffering;
    }

    @Override
    public void execute() {
        //Note
        //Once an offering is created, we cannot update the domainId field (keeping consistent with zones logic)
        ServiceOffering result = _configService.updateServiceOffering(this);
        if (result != null) {
            ServiceOfferingResponse response = _responseGenerator.createServiceOfferingResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update service offering");
        }
    }
}
