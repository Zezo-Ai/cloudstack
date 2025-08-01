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
package org.apache.cloudstack.api.command.user.vpc;


import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.PrivateGatewayResponse;
import org.apache.cloudstack.api.response.StaticRouteResponse;
import org.apache.cloudstack.api.response.VpcResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.vpc.StaticRoute;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcGateway;

@APICommand(name = "createStaticRoute", description = "Creates a static route", responseObject = StaticRouteResponse.class, entityType = {StaticRoute.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateStaticRouteCmd extends BaseAsyncCreateCmd {

    @Parameter(name = ApiConstants.GATEWAY_ID,
               type = CommandType.UUID,
               entityType = PrivateGatewayResponse.class,
               description = "the gateway id we are creating static route for. Mutually exclusive with the nexthop parameter")
    private Long gatewayId;

    @Parameter(name = ApiConstants.VPC_ID,
            type = CommandType.UUID,
            entityType = VpcResponse.class,
            description = "the vpc id for which the static route is created. This is required for nexthop parameter",
            since = "4.21.0")
    private Long vpcId;

    @Parameter(name = ApiConstants.NEXT_HOP,
            type = CommandType.STRING,
            description = "the next hop of static route. Mutually exclusive with the gatewayid parameter",
            since = "4.21.0")
    private String nextHop;

    @Parameter(name = ApiConstants.CIDR, required = true, type = CommandType.STRING, description = "static route cidr")
    private String cidr;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getGatewayId() {
        return gatewayId;
    }

    public Long getVpcId() {
        return vpcId;
    }

    public String getNextHop() {
        return nextHop;
    }

    public String getCidr() {
        return cidr;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public void create() throws ResourceAllocationException {
        try {
            StaticRoute result = _vpcService.createStaticRoute(getGatewayId(), getVpcId(), getNextHop(), getCidr());
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } catch (NetworkRuleConflictException ex) {
            logger.info("Network rule conflict: " + ex.getMessage());
            logger.trace("Network rule conflict: ", ex);
            throw new ServerApiException(ApiErrorCode.NETWORK_RULE_CONFLICT_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_STATIC_ROUTE_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Applying static route. Static route Id: " + getEntityId();
    }

    @Override
    public void execute() throws ResourceUnavailableException {
        boolean success = false;
        StaticRoute route = null;
        try {
            CallContext.current().setEventDetails("Static route Id: " + getEntityId());
            success = _vpcService.applyStaticRoute(getEntityId());
            // State is different after the route is applied, so retrieve the object only here
            route = _entityMgr.findById(StaticRoute.class, getEntityId());
            StaticRouteResponse routeResponse = new StaticRouteResponse();
            if (route != null) {
                routeResponse = _responseGenerator.createStaticRouteResponse(route);
                setResponseObject(routeResponse);
            }
            routeResponse.setResponseName(getCommandName());
        } finally {
            if (!success || route == null) {
                _entityMgr.remove(StaticRoute.class, getEntityId());
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create static route");
            }
        }
    }

    @Override
    public long getEntityOwnerId() {
        Long vpcId = getSyncObjId();
        return _entityMgr.findById(Vpc.class, vpcId).getAccountId();
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.vpcSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        if (gatewayId != null) {
            VpcGateway gateway = _entityMgr.findById(VpcGateway.class, gatewayId);
            if (gateway == null) {
                throw new InvalidParameterValueException("Invalid id is specified for the gateway");
            }
            return gateway.getVpcId();
        } else if (vpcId != null) {
            Vpc vpc = _entityMgr.findById(Vpc.class, vpcId);
            if (vpc == null) {
                throw new InvalidParameterValueException("Invalid vpc id is specified");
            }
            return vpc.getId();
        }
        throw new InvalidParameterValueException("One of vpcId or gatewayId must be specified");
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.StaticRoute;
    }
}
