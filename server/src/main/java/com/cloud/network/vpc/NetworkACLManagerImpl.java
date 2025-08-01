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
package com.cloud.network.vpc;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.messagebus.MessageBus;
import org.apache.cloudstack.framework.messagebus.PublishScope;

import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.element.NetworkACLServiceProvider;
import com.cloud.network.element.VpcProvider;
import com.cloud.network.vpc.NetworkACLItem.State;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.network.vpc.dao.VpcGatewayDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.server.ResourceTag;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;

public class NetworkACLManagerImpl extends ManagerBase implements NetworkACLManager {

    @Inject
    private NetworkModel _networkMgr;
    @Inject
    private NetworkACLDao _networkACLDao;
    @Inject
    private NetworkACLItemDao _networkACLItemDao;
    @Inject
    private NetworkModel _networkModel;
    @Inject
    private NetworkDao _networkDao;
    @Inject
    private VpcGatewayDao _vpcGatewayDao;
    @Inject
    private NetworkModel _ntwkModel;
    @Inject
    private EntityManager _entityMgr;
    @Inject
    private VpcService _vpcSvc;
    @Inject
    private MessageBus _messageBus;
    @Inject
    private ResourceTagDao resourceTagDao;

    private List<NetworkACLServiceProvider> _networkAclElements;

    private boolean containsIpv6Cidr(List<String> cidrs) {
        for (String cidr : cidrs) {
            if (NetUtils.isValidIp6Cidr(cidr)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NetworkACL createNetworkACL(final String name, final String description, final long vpcId, final Boolean forDisplay) {
        final NetworkACLVO acl = new NetworkACLVO(name, description, vpcId);
        if (forDisplay != null) {
            acl.setDisplay(forDisplay);
        }
        return _networkACLDao.persist(acl);
    }

    @Override
    public boolean applyNetworkACL(final long aclId) throws ResourceUnavailableException {
        boolean handled = true;
        boolean aclApplyStatus = true;

        final List<NetworkACLItemVO> rules = _networkACLItemDao.listByACL(aclId);
        //Find all networks using this ACL and apply the ACL
        final List<NetworkVO> networks = _networkDao.listByAclId(aclId);
        for (final NetworkVO network : networks) {
            if (!applyACLItemsToNetwork(network.getId(), rules)) {
                handled = false;
                break;
            }
        }

        final List<VpcGatewayVO> vpcGateways = _vpcGatewayDao.listByAclIdAndType(aclId, VpcGateway.Type.Private);
        for (final VpcGatewayVO vpcGateway : vpcGateways) {
            final PrivateGateway privateGateway = _vpcSvc.getVpcPrivateGateway(vpcGateway.getId());

            if (!applyACLToPrivateGw(privateGateway)) {
                aclApplyStatus = false;
                logger.debug("failed to apply network acl item on private gateway {} acl {}", privateGateway::getUuid, () -> _networkACLDao.findById(aclId));
                break;
            }
        }

        if (handled && aclApplyStatus) {
            for (final NetworkACLItem rule : rules) {
                if (rule.getState() == NetworkACLItem.State.Revoke) {
                    removeRule(rule);
                } else if (rule.getState() == NetworkACLItem.State.Add) {
                    final NetworkACLItemVO ruleVO = _networkACLItemDao.findById(rule.getId());
                    ruleVO.setState(NetworkACLItem.State.Active);
                    _networkACLItemDao.update(ruleVO.getId(), ruleVO);
                }
            }
        }
        return handled && aclApplyStatus;
    }

    @Override
    public NetworkACL getNetworkACL(final long id) {
        return _networkACLDao.findById(id);
    }

    @Override
    public boolean deleteNetworkACL(final NetworkACL acl) {
        final long aclId = acl.getId();
        final List<NetworkVO> networks = _networkDao.listByAclId(aclId);
        if (networks != null && networks.size() > 0) {
            throw new CloudRuntimeException("ACL is still associated with " + networks.size() + " tier(s). Cannot delete network ACL: " + acl.getUuid());
        }

        final List<VpcGatewayVO> pvtGateways = _vpcGatewayDao.listByAclIdAndType(aclId, VpcGateway.Type.Private);

        if (pvtGateways != null && pvtGateways.size() > 0) {
            throw new CloudRuntimeException("ACL is still associated with " + pvtGateways.size() + " private gateway(s). Cannot delete network ACL: " + acl.getUuid());
        }

        final List<NetworkACLItemVO> aclItems = _networkACLItemDao.listByACL(aclId);
        for (final NetworkACLItemVO networkACLItem : aclItems) {
            revokeNetworkACLItem(networkACLItem.getId());
        }

        return _networkACLDao.remove(aclId);
    }

    @Override
    public boolean replaceNetworkACLForPrivateGw(final NetworkACL acl, final PrivateGateway gateway) throws ResourceUnavailableException {
        final VpcGatewayVO vpcGatewayVo = _vpcGatewayDao.findById(gateway.getId());
        final List<NetworkACLItemVO> aclItems = _networkACLItemDao.listByACL(acl.getId());
        if (aclItems == null || aclItems.isEmpty()) {
            //Revoke ACL Items of the existing ACL if the new network acl is empty
            //Otherwise existing rules will not be removed on the router element
            logger.debug("New network ACL is empty. Revoke existing rules before applying ACL");
            if (!revokeACLItemsForPrivateGw(gateway)) {
                throw new CloudRuntimeException(String.format("Failed to replace network ACL. Error while removing existing ACL items for private gateway: [id: %d, uuid: %s]", gateway.getId(), gateway.getUuid()));
            }
        }

        vpcGatewayVo.setNetworkACLId(acl.getId());
        if (_vpcGatewayDao.update(vpcGatewayVo.getId(), vpcGatewayVo)) {
            return applyACLToPrivateGw(gateway);

        }
        return false;
    }

    @Override
    public boolean replaceNetworkACL(final NetworkACL acl, final NetworkVO network) throws ResourceUnavailableException {

        final NetworkOffering guestNtwkOff = _entityMgr.findById(NetworkOffering.class, network.getNetworkOfferingId());

        if (guestNtwkOff == null) {
            throw new InvalidParameterValueException("Can't find network offering associated with network: " + network.getUuid());
        }

        //verify that ACLProvider is supported by network offering
        if (!_ntwkModel.areServicesSupportedByNetworkOffering(guestNtwkOff.getId(), Service.NetworkACL)) {
            throw new InvalidParameterValueException("Cannot apply NetworkACL. Network Offering does not support NetworkACL service");
        }

        if (network.getNetworkACLId() != null) {
            //Revoke ACL Items of the existing ACL if the new ACL is empty
            //Existing rules won't be removed otherwise
            final List<NetworkACLItemVO> aclItems = _networkACLItemDao.listByACL(acl.getId());
            if (aclItems == null || aclItems.isEmpty()) {
                logger.debug("New network ACL is empty. Revoke existing rules before applying ACL");
            } else {
                if (!revokeACLItemsForNetwork(network.getId())) {
                    throw new CloudRuntimeException(String.format("Failed to replace network ACL. Error while removing existing ACL items for network: %s", network));
                }
            }
        }

        network.setNetworkACLId(acl.getId());
        //Update Network ACL
        if (_networkDao.update(network.getId(), network)) {
            logger.debug("Updated network: {} with Network ACL: {}, Applying ACL items", network, acl);
            //Apply ACL to network
            final Boolean result = applyACLToNetwork(network.getId());
            if (result) {
                // public message on message bus, so that network elements implementing distributed routing capability
                // can act on the event
                _messageBus.publish(_name, "Network_ACL_Replaced", PublishScope.LOCAL, network);
            }
            return result;
        }
        return false;
    }

    @DB
    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NETWORK_ACL_ITEM_CREATE, eventDescription = "creating network ACL Item", create = true)
    public NetworkACLItem createNetworkACLItem(NetworkACLItemVO networkACLItemVO) {
        NetworkACLItemVO newRule = Transaction.execute(new TransactionCallback<NetworkACLItemVO>() {
            @Override
            public NetworkACLItemVO doInTransaction(final TransactionStatus status) {
                NetworkACLItemVO networkACLItemVOFromDatabase = _networkACLItemDao.persist(networkACLItemVO);

                if (!_networkACLItemDao.setStateToAdd(networkACLItemVOFromDatabase)) {
                    throw new CloudRuntimeException("Unable to update the state to add for " + networkACLItemVOFromDatabase);
                }
                CallContext.current().setEventDetails("ACL Item Id: " + networkACLItemVOFromDatabase.getId());
                CallContext.current().putContextParameter(NetworkACLItem.class, networkACLItemVOFromDatabase.getAclId());
                return networkACLItemVOFromDatabase;
            }
        });

        return getNetworkACLItem(newRule.getId());
    }

    @Override
    public NetworkACLItem getNetworkACLItem(long ruleId) {
        return _networkACLItemDao.findById(ruleId);
    }

    @Override
    public boolean revokeNetworkACLItem(final long ruleId) {

        final NetworkACLItemVO rule = _networkACLItemDao.findById(ruleId);

        revokeRule(rule);

        boolean success = false;

        try {
            applyNetworkACL(rule.getAclId());
            success = true;
        } catch (final ResourceUnavailableException e) {
            return false;
        }

        return success;
    }

    @DB
    private void revokeRule(final NetworkACLItemVO rule) {
        if (rule.getState() == State.Staged) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found a rule that is still in stage state so just removing it: " + rule);
            }
            removeRule(rule);
        } else if (rule.getState() == State.Add || rule.getState() == State.Active) {
            rule.setState(State.Revoke);
            _networkACLItemDao.update(rule.getId(), rule);
        }
    }

    @Override
    public boolean revokeACLItemsForNetwork(final long networkId) throws ResourceUnavailableException {
        final Network network = _networkDao.findById(networkId);
        if (network.getNetworkACLId() == null) {
            return true;
        }
        final List<NetworkACLItemVO> aclItems = _networkACLItemDao.listByACL(network.getNetworkACLId());
        if (aclItems.isEmpty()) {
            logger.debug("Found no network ACL Items for network={}", network);
            return true;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Releasing {} Network ACL Items for network={}", aclItems.size(), network);
        }

        for (final NetworkACLItemVO aclItem : aclItems) {
            // Mark all Network ACLs rules as Revoke, but don't update in DB
            if (aclItem.getState() == State.Add || aclItem.getState() == State.Active) {
                aclItem.setState(State.Revoke);
            }
        }

        final boolean success = applyACLItemsToNetwork(network.getId(), aclItems);

        if (logger.isDebugEnabled() && success) {
            logger.debug("Successfully released Network ACLs for network={} and # of rules now = {}", network, aclItems.size());
        }

        return success;
    }

    @Override
    public boolean revokeACLItemsForPrivateGw(final PrivateGateway gateway) throws ResourceUnavailableException {
        final long networkACLId = gateway.getNetworkACLId();
        final List<NetworkACLItemVO> aclItems = _networkACLItemDao.listByACL(networkACLId);
        if (aclItems.isEmpty()) {
            logger.debug("Found no network ACL Items for private gateway {}", gateway);
            return true;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Releasing {} Network ACL Items for private gateway {}", aclItems.size(), gateway);
        }

        for (final NetworkACLItemVO aclItem : aclItems) {
            // Mark all Network ACLs rules as Revoke, but don't update in DB
            if (aclItem.getState() == State.Add || aclItem.getState() == State.Active) {
                aclItem.setState(State.Revoke);
            }
        }

        final boolean success = applyACLToPrivateGw(gateway, aclItems);

        if (logger.isDebugEnabled() && success) {
            logger.debug("Successfully released Network ACLs for private gateway={} and # of rules now = {}", gateway, aclItems.size());
        }

        return success;
    }

    @Override
    public List<NetworkACLItemVO> listNetworkACLItems(final long guestNtwkId) {
        final Network network = _networkMgr.getNetwork(guestNtwkId);
        if (network.getNetworkACLId() == null) {
            return null;
        }
        List<NetworkACLItemVO> rules = _networkACLItemDao.listByACL(network.getNetworkACLId());
        return rules;
    }

    boolean removeRule(final NetworkACLItem rule) {
        boolean rc = resourceTagDao.removeByIdAndType(rule.getId(), ResourceTag.ResourceObjectType.NetworkACL);
        return rc && _networkACLItemDao.remove(rule.getId());
    }

    @Override
    public boolean applyACLToPrivateGw(final PrivateGateway gateway) throws ResourceUnavailableException {
        final VpcGatewayVO vpcGatewayVO = _vpcGatewayDao.findById(gateway.getId());
        final List<? extends NetworkACLItem> rules = _networkACLItemDao.listByACL(vpcGatewayVO.getNetworkACLId());
        return applyACLToPrivateGw(gateway, rules);
    }

    @Override
    public boolean reorderAclRules(VpcVO vpc, List<? extends Network> networks,
                                   List<? extends NetworkACLItem> networkACLItems, Network.Provider networkProvider) {
        List<NetworkACLServiceProvider> providers = new ArrayList<>();
        providers.add((NetworkACLServiceProvider) _ntwkModel.getElementImplementingProvider(networkProvider.getName()));
        try {
            for (final NetworkACLServiceProvider provider : providers) {
                return provider.reorderAclRules(vpc, networks, networkACLItems);
            }
        } catch (final Exception ex) {
            logger.debug(String.format("Failed to reorder ACLs on %s due to: %s", networkProvider.getName(), ex.getLocalizedMessage()));
        }
        return false;
    }

    private boolean applyACLToPrivateGw(final PrivateGateway gateway, final List<? extends NetworkACLItem> rules) throws ResourceUnavailableException {
        List<VpcProvider> vpcElements = new ArrayList<VpcProvider>();
        vpcElements.add((VpcProvider)_ntwkModel.getElementImplementingProvider(Network.Provider.VPCVirtualRouter.getName()));

        try {
            for (final VpcProvider provider : vpcElements) {
                return provider.applyACLItemsToPrivateGw(gateway, rules);
            }
        } catch (final Exception ex) {
            logger.debug("Failed to apply acl to private gateway " + gateway);
        }
        return false;
    }

    @Override
    public boolean applyACLToNetwork(final long networkId) throws ResourceUnavailableException {
        final Network network = _networkDao.findById(networkId);
        if (network.getNetworkACLId() == null) {
            return true;
        }
        final List<NetworkACLItemVO> rules = _networkACLItemDao.listByACL(network.getNetworkACLId());
        return applyACLItemsToNetwork(networkId, rules);
    }

    /**
     * Updates and applies the network ACL rule ({@link NetworkACLItemVO}).
     * We will first try to update the ACL rule in the database using {@link NetworkACLItemDao#updateNumberFieldNetworkItem(long, int)}. If it does not work, a {@link CloudRuntimeException} is thrown.
     * If we manage to update the ACL rule in the database, we proceed to apply it using {@link #applyNetworkACL(long)}. If this does not work we throw a {@link CloudRuntimeException}.
     * If all is working we return the {@link NetworkACLItemVO} given as parameter. We wil set the state of the rule to {@link com.cloud.network.vpc.NetworkACLItem.State#Add}.
     */
    @Override
    public NetworkACLItem updateNetworkACLItem(NetworkACLItemVO networkACLItemVO) throws ResourceUnavailableException {
        networkACLItemVO.setState(State.Add);

        if (_networkACLItemDao.update(networkACLItemVO.getId(), networkACLItemVO)) {
            if (applyNetworkACL(networkACLItemVO.getAclId())) {
                return networkACLItemVO;
            } else {
                throw new CloudRuntimeException("Failed to apply Network ACL rule: " + networkACLItemVO.getUuid());
            }
        }
        throw new CloudRuntimeException(String.format("Network ACL rule [id=%s] acl rule list [id=%s] could not be updated.", networkACLItemVO.getUuid(), networkACLItemVO.getAclId()));
    }

    public boolean applyACLItemsToNetwork(final long networkId, final List<NetworkACLItemVO> rules) throws ResourceUnavailableException {
        final Network network = _networkDao.findById(networkId);
        boolean handled = false;
        boolean foundProvider = false;
        for (final NetworkACLServiceProvider element : _networkAclElements) {
            final Network.Provider provider = element.getProvider();
            final boolean isAclProvider = _networkModel.isProviderSupportServiceInNetwork(network.getId(), Service.NetworkACL, provider);
            if (!isAclProvider) {
                continue;
            }
            foundProvider = true;
            logger.debug("Applying NetworkACL for network: {} with Network ACL service provider", network);
            handled = element.applyNetworkACLs(network, rules);
            if (handled) {
                // publish message on message bus, so that network elements implementing distributed routing
                // capability can act on the event
                _messageBus.publish(_name, "Network_ACL_Replaced", PublishScope.LOCAL, network);
                break;
            }
        }
        if (!foundProvider) {
            logger.debug("Unable to find NetworkACL service provider for network: {}", network);
        }
        return handled;
    }

    public List<NetworkACLServiceProvider> getNetworkAclElements() {
        return _networkAclElements;
    }

    @Inject
    public void setNetworkAclElements(final List<NetworkACLServiceProvider> networkAclElements) {
        _networkAclElements = networkAclElements;
    }

}
