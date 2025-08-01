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
package com.cloud.network.router;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.network.dao.NetworkDao;
import com.cloud.network.vpc.dao.VpcDao;
import org.apache.cloudstack.agent.routing.ManageServiceCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.Command.OnError;
import com.cloud.agent.api.NetworkUsageCommand;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.SetupGuestNetworkCommand;
import com.cloud.agent.api.routing.AggregationControlCommand;
import com.cloud.agent.api.routing.AggregationControlCommand.Action;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.manager.Commands;
import com.cloud.dc.DataCenter;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.HypervisorGuru;
import com.cloud.hypervisor.HypervisorGuruManager;
import com.cloud.network.IpAddress;
import com.cloud.network.MonitoringService;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.RemoteAccessVpn;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.network.VirtualRouterProvider;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.MonitoringServiceVO;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.RemoteAccessVpnVO;
import com.cloud.network.dao.Site2SiteVpnConnectionVO;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;
import com.cloud.network.vpc.NetworkACLItemDao;
import com.cloud.network.vpc.NetworkACLItemVO;
import com.cloud.network.vpc.NetworkACLManager;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.PrivateIpAddress;
import com.cloud.network.vpc.PrivateIpVO;
import com.cloud.network.vpc.StaticRouteVO;
import com.cloud.network.vpc.StaticRouteProfile;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcManager;
import com.cloud.network.vpc.VpcVO;
import com.cloud.network.vpc.dao.PrivateIpDao;
import com.cloud.network.vpc.dao.StaticRouteDao;
import com.cloud.network.vpc.dao.VpcGatewayDao;
import com.cloud.network.vpn.Site2SiteVpnManager;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.UserStatisticsVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.fsm.StateMachine2;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.Nic;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfile.Param;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.dao.VMInstanceDao;

@Component
public class VpcVirtualNetworkApplianceManagerImpl extends VirtualNetworkApplianceManagerImpl implements VpcVirtualNetworkApplianceManager {

    @Inject
    private NetworkACLManager _networkACLMgr;
    @Inject
    private VMInstanceDao _vmDao;
    @Inject
    private StaticRouteDao _staticRouteDao;
    @Inject
    private VpcManager _vpcMgr;
    @Inject
    private PrivateIpDao _privateIpDao;
    @Inject
    private Site2SiteVpnManager _s2sVpnMgr;
    @Inject
    private VpcGatewayDao _vpcGatewayDao;
    @Inject
    private NetworkACLItemDao _networkACLItemDao;
    @Inject
    private EntityManager _entityMgr;
    @Inject
    protected HypervisorGuruManager _hvGuruMgr;
    @Inject
    protected NetworkDao networkDao;
    @Inject
    protected VpcDao vpcDao;
    @Inject
    private LoadBalancerDao loadBalancerDao;

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _itMgr.registerGuru(VirtualMachine.Type.DomainRouter, this);
        return super.configure(name, params);
    }

    @Override
    public boolean addVpcRouterToGuestNetwork(final VirtualRouter router, final Network network, final Map<VirtualMachineProfile.Param, Object> params)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        if (network.getTrafficType() != TrafficType.Guest) {
            logger.warn("Network " + network + " is not of type " + TrafficType.Guest);
            return false;
        }

        // Add router to the Guest network
        boolean result = true;
        try {

            // 1) add nic to the router
            _routerDao.addRouterToGuestNetwork(router, network);

            final NicProfile guestNic = _itMgr.addVmToNetwork(router, network, null);
            if (network.getVpcId() != null) {
                VpcVO vpc = _vpcDao.findById(network.getVpcId());
                guestNic.setMtu(vpc.getPublicMtu());
            }

            // 2) setup guest network
            if (guestNic != null) {
                result = setupVpcGuestNetwork(network, router, true, guestNic);
            } else {
                logger.warn("Failed to add router " + router + " to guest network " + network);
                result = false;
            }
            // 3) apply networking rules
            if (result) {
                boolean reprogramNetwork = params != null && params.get(Param.ReProgramGuestNetworks) != null && (Boolean) params.get(Param.ReProgramGuestNetworks) == true;
                sendNetworkRulesToRouter(router.getId(), network.getId(), reprogramNetwork);
            }
        } catch (final Exception ex) {
            logger.warn("Failed to add router " + router + " to network " + network + " due to ", ex);
            result = false;
        } finally {
            if (!result) {
                logger.debug("Removing the router " + router + " from network " + network + " as a part of cleanup");
                if (removeVpcRouterFromGuestNetwork(router, network)) {
                    logger.debug("Removed the router " + router + " from network " + network + " as a part of cleanup");
                } else {
                    logger.warn("Failed to remove the router " + router + " from network " + network + " as a part of cleanup");
                }
            } else {
                logger.debug("Successfully added router " + router + " to guest network " + network);
            }
        }

        return result;
    }

    @Override
    public boolean removeVpcRouterFromGuestNetwork(final VirtualRouter router, final Network network) throws ConcurrentOperationException,
    ResourceUnavailableException {
        if (network.getTrafficType() != TrafficType.Guest) {
            logger.warn("Network " + network + " is not of type " + TrafficType.Guest);
            return false;
        }

        boolean result = true;
        try {
            // Check if router is a part of the Guest network
            if (!_networkModel.isVmPartOfNetwork(router.getId(), network.getId())) {
                logger.debug("Router " + router + " is not a part of the Guest network " + network);
                return result;
            }

            result = setupVpcGuestNetwork(network, router, false, _networkModel.getNicProfile(router, network.getId(), null));
            if (!result) {
                logger.warn("Failed to destroy guest network config " + network + " on router " + router);
                return false;
            }

            result = result && _itMgr.removeVmFromNetwork(router, network, null);
        } finally {
            if (result) {
                _routerDao.removeRouterFromGuestNetwork(router.getId(), network.getId());
            }
        }

        return result;
    }

    @Override
    public boolean stopKeepAlivedOnRouter(VirtualRouter router,
            Network network) throws ConcurrentOperationException, ResourceUnavailableException {
        return manageKeepalivedServiceOnRouter(router, network, "stop");
    }

    @Override
    public boolean startKeepAlivedOnRouter(VirtualRouter router,
            Network network) throws ConcurrentOperationException, ResourceUnavailableException {
        return manageKeepalivedServiceOnRouter(router, network, "start");
    }

    private boolean manageKeepalivedServiceOnRouter(VirtualRouter router,
            Network network, String action) throws ConcurrentOperationException, ResourceUnavailableException {
        if (network.getTrafficType() != TrafficType.Guest) {
            logger.warn("Network {} is not of type {}", network, TrafficType.Guest);
            return false;
        }
        boolean result = true;
        try {
            if (router.getState() == State.Running) {
                final ManageServiceCommand stopCommand = new ManageServiceCommand("keepalived", action);
                stopCommand.setAccessDetail(NetworkElementCommand.ROUTER_IP, _routerControlHelper.getRouterControlIp(router.getId()));

                final Commands cmds = new Commands(Command.OnError.Stop);
                cmds.addCommand("manageKeepalived", stopCommand);
                _nwHelper.sendCommandsToRouter(router, cmds);

                final Answer setupAnswer = cmds.getAnswer("manageKeepalived");
                if (!(setupAnswer != null && setupAnswer.getResult())) {
                    logger.warn("Unable to {} keepalived on router {}", action, router);
                    result = false;
                }
            } else if (router.getState() == State.Stopped || router.getState() == State.Stopping) {
                logger.debug("Router {} is in {}, so not sending command to the backend", router.getInstanceName(), router.getState());
            } else {
                String message = "Unable to " + action + " keepalived on virtual router [" + router + "] is not in the right state " + router.getState();
                logger.warn(message);
                throw new ResourceUnavailableException(message, DataCenter.class, router.getDataCenterId());
            }
        } catch (final Exception ex) {
            logger.warn("Failed to {}  keepalived on router {} to network {} due to {}", action, router, network, ex.getLocalizedMessage());
            logger.debug("Failed to {}  keepalived on router {} to network {}", action, router, network, ex);
            result = false;
        }
        return result;
    }

    protected boolean setupVpcGuestNetwork(final Network network, final VirtualRouter router, final boolean add, final NicProfile guestNic) throws ConcurrentOperationException,
    ResourceUnavailableException {

        boolean result = true;
        if (router.getState() == State.Running) {
            final SetupGuestNetworkCommand setupCmd = _commandSetupHelper.createSetupGuestNetworkCommand((DomainRouterVO) router, add, guestNic);

            final Commands cmds = new Commands(Command.OnError.Stop);
            cmds.addCommand("setupguestnetwork", setupCmd);
            _nwHelper.sendCommandsToRouter(router, cmds);

            final Answer setupAnswer = cmds.getAnswer("setupguestnetwork");
            final String setup = add ? "set" : "destroy";
            if (!(setupAnswer != null && setupAnswer.getResult())) {
                logger.warn("Unable to " + setup + " guest network on router " + router);
                result = false;
            }
            return result;
        } else if (router.getState() == State.Stopped || router.getState() == State.Stopping) {
            logger.debug("Router " + router.getInstanceName() + " is in " + router.getState() + ", so not sending setup guest network command to the backend");
            return true;
        } else {
            logger.warn("Unable to setup guest network on virtual router " + router + " is not in the right state " + router.getState());
            throw new ResourceUnavailableException("Unable to setup guest network on the backend," + " virtual router " + router + " is not in the right state", DataCenter.class,
                    router.getDataCenterId());
        }
    }

    @Override
    public boolean finalizeVirtualMachineProfile(final VirtualMachineProfile profile, final DeployDestination dest, final ReservationContext context) {
        final DomainRouterVO domainRouterVO = _routerDao.findById(profile.getId());

        final Long vpcId = domainRouterVO.getVpcId();

        if (vpcId != null) {
            if (domainRouterVO.getState() == State.Starting || domainRouterVO.getState() == State.Running) {
                String defaultDns1 = null;
                String defaultDns2 = null;
                String defaultIp6Dns1 = null;
                String defaultIp6Dns2 = null;
                boolean isDnsConfigured = false;
                // remove public and guest nics as we will plug them later
                final Iterator<NicProfile> it = profile.getNics().iterator();
                while (it.hasNext()) {
                    final NicProfile nic = it.next();
                    if (nic.getTrafficType() == TrafficType.Public || nic.getTrafficType() == TrafficType.Guest) {
                        // save dns information
                        if (nic.getTrafficType() == TrafficType.Public || !isDnsConfigured) {
                            defaultDns1 = nic.getIPv4Dns1();
                            defaultDns2 = nic.getIPv4Dns2();
                            defaultIp6Dns1 = nic.getIPv6Dns1();
                            defaultIp6Dns2 = nic.getIPv6Dns2();
                            isDnsConfigured = true;
                        }
                        logger.debug("Removing nic " + nic + " of type " + nic.getTrafficType() + " from the nics passed on vm start. " + "The nic will be plugged later");
                        it.remove();
                    }
                }

                // add vpc cidr/dns/networkdomain to the boot load args
                final StringBuilder buf = profile.getBootArgsBuilder();
                final Vpc vpc = _entityMgr.findById(Vpc.class, vpcId);
                buf.append(" vpccidr=" + vpc.getCidr() + " domain=" + vpc.getNetworkDomain());
                buf.append(" publicMtu=").append(vpc.getPublicMtu());
                buf.append(" dns1=").append(defaultDns1);
                if (defaultDns2 != null) {
                    buf.append(" dns2=").append(defaultDns2);
                }
                if (defaultIp6Dns1 != null) {
                    buf.append(" ip6dns1=").append(defaultIp6Dns1);
                }
                if (defaultIp6Dns2 != null) {
                    buf.append(" ip6dns2=").append(defaultIp6Dns2);
                }
                if (routedIpv4Manager.isRoutedVpc(vpc)) {
                    buf.append(" is_routed=true");
                }
            }
        }

        super.finalizeVirtualMachineProfile(profile, dest, context);
        appendSourceNatIpToBootArgs(profile);
        return true;
    }

    private void appendSourceNatIpToBootArgs(final VirtualMachineProfile profile) {
        final StringBuilder buf = profile.getBootArgsBuilder();
        final DomainRouterVO router = _routerDao.findById(profile.getVirtualMachine().getId());
        if (router != null && router.getVpcId() != null) {
            List<IPAddressVO> vpcIps = _ipAddressDao.listByAssociatedVpc(router.getVpcId(), true);
            if (CollectionUtils.isNotEmpty(vpcIps)) {
                buf.append(String.format(" source_nat_ip=%s", vpcIps.get(0).getAddress().toString()));
                logger.debug("The final Boot Args for " + profile + ": " + buf);
            }
        }
    }

    @Override
    public boolean finalizeCommandsOnStart(final Commands cmds, final VirtualMachineProfile profile) {
        final DomainRouterVO domainRouterVO = _routerDao.findById(profile.getId());

        Map<String, String> details = new HashMap<String, String>();

        if(profile.getHypervisorType() == Hypervisor.HypervisorType.VMware){
            HypervisorGuru hvGuru = _hvGuruMgr.getGuru(profile.getHypervisorType());
            VirtualMachineTO vmTO = hvGuru.implement(profile);
            if(vmTO.getDetails() != null){
                details = vmTO.getDetails();
            }
        }

        final boolean isVpc = domainRouterVO.getVpcId() != null;
        if (!isVpc) {
            return super.finalizeCommandsOnStart(cmds, profile);
        }

        if (domainRouterVO.getState() == State.Starting || domainRouterVO.getState() == State.Running) {
            // 1) FORM SSH CHECK COMMAND
            final NicProfile controlNic = getControlNic(profile);
            if (controlNic == null) {
                logger.error("Control network doesn't exist for the router " + domainRouterVO);
                return false;
            }

            finalizeSshAndVersionAndNetworkUsageOnStart(cmds, profile, domainRouterVO, controlNic);

            // 2) FORM PLUG NIC COMMANDS
            final List<Pair<Nic, Network>> guestNics = new ArrayList<Pair<Nic, Network>>();
            final List<Pair<Nic, Network>> publicNics = new ArrayList<Pair<Nic, Network>>();
            final List<Pair<Nic, Network>> privateGatewayNics = new ArrayList<Pair<Nic, Network>>();
            final Map<String, String> vlanMacAddress = new HashMap<String, String>();

            final List<? extends Nic> routerNics = _nicDao.listByVmIdOrderByDeviceId(profile.getId());
            for (final Nic routerNic : routerNics) {
                final Network network = _networkModel.getNetwork(routerNic.getNetworkId());
                if (network.getTrafficType() == TrafficType.Guest) {
                    final Pair<Nic, Network> guestNic = new Pair<Nic, Network>(routerNic, network);
                    if (_networkModel.isPrivateGateway(routerNic.getNetworkId())) {
                        privateGatewayNics.add(guestNic);
                    } else {
                        guestNics.add(guestNic);
                    }
                } else if (network.getTrafficType() == TrafficType.Public) {
                    final Pair<Nic, Network> publicNic = new Pair<Nic, Network>(routerNic, network);
                    publicNics.add(publicNic);
                    String vlanTag = null;
                    if (Objects.nonNull(routerNic.getBroadcastUri())) {
                        vlanTag = BroadcastDomainType.getValue(routerNic.getBroadcastUri());
                    } else {
                        vlanTag = "nsx-"+routerNic.getIPv4Address();
                    }
                    vlanMacAddress.put(vlanTag, routerNic.getMacAddress());
                }
            }
            int deviceId = 1; //Public and Guest networks start from device_id = 1

            final List<Command> usageCmds = new ArrayList<Command>();

            // 3) PREPARE PLUG NIC COMMANDS
            try {
                // add VPC router to public networks
                final List<PublicIp> sourceNat = new ArrayList<PublicIp>(1);
                for (final Pair<Nic, Network> nicNtwk : publicNics) {
                    final Nic publicNic = updateNicWithDeviceId(nicNtwk.first().getId(), deviceId);
                    deviceId ++;
                    final Network publicNtwk = nicNtwk.second();
                    final IPAddressVO userIp = _ipAddressDao.findByIpAndSourceNetworkId(publicNtwk.getId(), publicNic.getIPv4Address());

                    if (userIp.isSourceNat()) {
                        final PublicIp publicIp = PublicIp.createFromAddrAndVlan(userIp, _vlanDao.findById(userIp.getVlanId()));
                        sourceNat.add(publicIp);

                        if (domainRouterVO.getPublicIpAddress() == null) {
                            final DomainRouterVO routerVO = _routerDao.findById(domainRouterVO.getId());
                            routerVO.setPublicIpAddress(publicNic.getIPv4Address());
                            routerVO.setPublicNetmask(publicNic.getIPv4Netmask());
                            routerVO.setPublicMacAddress(publicNic.getMacAddress());
                            _routerDao.update(routerVO.getId(), routerVO);
                        }
                    }
                    String broadcastURI = publicNic.getBroadcastUri() != null ? publicNic.getBroadcastUri().toString() : null;
                    final PlugNicCommand plugNicCmd = new PlugNicCommand(_nwHelper.getNicTO(domainRouterVO, publicNic.getNetworkId(), broadcastURI),
                            domainRouterVO.getInstanceName(), domainRouterVO.getType(), details);
                    cmds.addCommand(plugNicCmd);
                    final VpcVO vpc = _vpcDao.findById(domainRouterVO.getVpcId());
                    if (routedIpv4Manager.isRoutedVpc(vpc)) {
                        continue;
                    }
                    final NetworkUsageCommand netUsageCmd = new NetworkUsageCommand(domainRouterVO.getPrivateIpAddress(), domainRouterVO.getInstanceName(), true, publicNic.getIPv4Address(), vpc.getCidr());
                    usageCmds.add(netUsageCmd);
                    UserStatisticsVO stats = _userStatsDao.findBy(domainRouterVO.getAccountId(), domainRouterVO.getDataCenterId(), publicNtwk.getId(), publicNic.getIPv4Address(), domainRouterVO.getId(),
                            domainRouterVO.getType().toString());
                    if (stats == null) {
                        stats = new UserStatisticsVO(domainRouterVO.getAccountId(), domainRouterVO.getDataCenterId(), publicNic.getIPv4Address(), domainRouterVO.getId(), domainRouterVO.getType().toString(),
                                publicNtwk.getId());
                        _userStatsDao.persist(stats);
                    }
                }

                // create ip assoc for source nat
                if (!sourceNat.isEmpty()) {
                    _commandSetupHelper.createVpcAssociatePublicIPCommands(domainRouterVO, sourceNat, cmds, vlanMacAddress);
                }

                // add VPC router to private gateway networks
                for (final Pair<Nic, Network> nicNtwk : privateGatewayNics) {
                    final Nic guestNic = updateNicWithDeviceId(nicNtwk.first().getId(), deviceId);
                    deviceId ++;
                    // plug guest nic
                    final PlugNicCommand plugNicCmd = new PlugNicCommand(_nwHelper.getNicTO(domainRouterVO, guestNic.getNetworkId(), null), domainRouterVO.getInstanceName(), domainRouterVO.getType(), details);
                    cmds.addCommand(plugNicCmd);
                    // set private network
                    final PrivateIpVO ipVO = _privateIpDao.findByIpAndSourceNetworkId(guestNic.getNetworkId(), guestNic.getIPv4Address());
                    final Network network = _networkDao.findById(guestNic.getNetworkId());
                    BroadcastDomainType.getValue(network.getBroadcastUri());
                    final String netmask = NetUtils.getCidrNetmask(network.getCidr());
                    final PrivateIpAddress ip = new PrivateIpAddress(ipVO, network.getBroadcastUri().toString(), network.getGateway(), netmask, guestNic.getMacAddress());

                    final List<PrivateIpAddress> privateIps = new ArrayList<PrivateIpAddress>(1);
                    privateIps.add(ip);
                    _commandSetupHelper.createVpcAssociatePrivateIPCommands(domainRouterVO, privateIps, cmds, true);

                    final Long privateGwAclId = _vpcGatewayDao.getNetworkAclIdForPrivateIp(ipVO.getVpcId(), ipVO.getNetworkId(), ipVO.getIpAddress());

                    if (privateGwAclId != null) {
                        // set network acl on private gateway
                        final List<NetworkACLItemVO> networkACLs = _networkACLItemDao.listByACL(privateGwAclId);
                        logger.debug("Found " + networkACLs.size() + " network ACLs to apply as a part of VPC VR " + domainRouterVO + " start for private gateway ip = "
                                + ipVO.getIpAddress());

                        _commandSetupHelper.createNetworkACLsCommands(networkACLs, domainRouterVO, cmds, ipVO.getNetworkId(), true);
                    }
                }

                // add VPC router to guest networks
                for (final Pair<Nic, Network> nicNtwk : guestNics) {
                    final Nic guestNic = updateNicWithDeviceId(nicNtwk.first().getId(), deviceId);
                    deviceId ++;
                    // plug guest nic
                    final PlugNicCommand plugNicCmd = new PlugNicCommand(_nwHelper.getNicTO(domainRouterVO, guestNic.getNetworkId(), null), domainRouterVO.getInstanceName(), domainRouterVO.getType(), details);
                    cmds.addCommand(plugNicCmd);
                    // set guest network
                    final VirtualMachine vm = _vmDao.findById(domainRouterVO.getId());
                    final NicProfile nicProfile = _networkModel.getNicProfile(vm, guestNic.getNetworkId(), null);
                    final SetupGuestNetworkCommand setupCmd = _commandSetupHelper.createSetupGuestNetworkCommand(domainRouterVO, true, nicProfile);
                    cmds.addCommand(setupCmd);
                }
            } catch (final Exception ex) {
                logger.warn("Failed to add router " + domainRouterVO + " to network due to exception ", ex);
                return false;
            }

            // 4) RE-APPLY ALL STATIC ROUTE RULES
            final List<StaticRouteVO> routes = _vpcMgr.getVpcStaticRoutes(domainRouterVO.getVpcId());
            final List<StaticRouteProfile> staticRouteProfiles = _vpcMgr.getVpcStaticRoutes(routes);

            logger.debug("Found " + staticRouteProfiles.size() + " static routes to apply as a part of vpc route " + domainRouterVO + " start");
            if (!staticRouteProfiles.isEmpty()) {
                _commandSetupHelper.createStaticRouteCommands(staticRouteProfiles, domainRouterVO, cmds);
            }

            // 5) RE-APPLY ALL REMOTE ACCESS VPNs
            final RemoteAccessVpnVO vpn = _vpnDao.findByAccountAndVpc(domainRouterVO.getAccountId(), domainRouterVO.getVpcId());
            if (vpn != null) {
                _commandSetupHelper.createApplyVpnCommands(true, vpn, domainRouterVO, cmds);
            }

            // 6) REPROGRAM GUEST NETWORK
            boolean reprogramGuestNtwks = true;
            if (profile.getParameter(Param.ReProgramGuestNetworks) != null && (Boolean) profile.getParameter(Param.ReProgramGuestNetworks) == false) {
                reprogramGuestNtwks = false;
            }

            final VirtualRouterProvider vrProvider = _vrProviderDao.findById(domainRouterVO.getElementId());
            if (vrProvider == null) {
                throw new CloudRuntimeException("Cannot find related virtual router provider of router: " + domainRouterVO.getHostName());
            }
            final Provider provider = Network.Provider.getProvider(vrProvider.getType().toString());
            if (provider == null) {
                throw new CloudRuntimeException("Cannot find related provider of virtual router provider: " + vrProvider.getType().toString());
            }

            Map<String, String> routerHealthCheckConfig = getRouterHealthChecksConfig(domainRouterVO);
            if (reprogramGuestNtwks && publicNics.size() > 0) {
                finalizeMonitorService(cmds, profile, domainRouterVO, provider, publicNics.get(0).second().getId(), true, routerHealthCheckConfig);
            }

            for (final Pair<Nic, Network> nicNtwk : guestNics) {
                final Nic guestNic = nicNtwk.first();
                final long guestNetworkId = guestNic.getNetworkId();
                final AggregationControlCommand startCmd = new AggregationControlCommand(Action.Start, domainRouterVO.getInstanceName(), controlNic.getIPv4Address(), _routerControlHelper.getRouterIpInNetwork(
                        guestNetworkId, domainRouterVO.getId()));
                cmds.addCommand(startCmd);
                if (reprogramGuestNtwks) {
                    finalizeIpAssocForNetwork(cmds, domainRouterVO, provider, guestNetworkId, vlanMacAddress);
                    finalizeNetworkRulesForNetwork(cmds, domainRouterVO, provider, guestNetworkId);
                    finalizeMonitorService(cmds, profile, domainRouterVO, provider, guestNetworkId, true, routerHealthCheckConfig);
                }

                finalizeUserDataAndDhcpOnStart(cmds, domainRouterVO, provider, guestNetworkId);
                final AggregationControlCommand finishCmd = new AggregationControlCommand(Action.Finish, domainRouterVO.getInstanceName(), controlNic.getIPv4Address(), _routerControlHelper.getRouterIpInNetwork(
                        guestNetworkId, domainRouterVO.getId()));
                cmds.addCommand(finishCmd);
            }

            createApplyLoadBalancingRulesCommandsForVpc(cmds, domainRouterVO, provider, guestNics);

            // Add network usage commands
            cmds.addCommands(usageCmds);
        }
        return true;
    }

    private void createApplyLoadBalancingRulesCommandsForVpc(final Commands cmds, DomainRouterVO domainRouterVO, Provider provider,
                                                             List<Pair<Nic, Network>> guestNics) {
        final List<LoadBalancerVO> lbs = loadBalancerDao.listByVpcIdAndScheme(domainRouterVO.getVpcId(), Scheme.Public);
        final List<LoadBalancingRule> lbRules = new ArrayList<>();
        createLoadBalancingRulesList(lbRules, lbs);
        logger.debug("Found " + lbRules.size() + " load balancing rule(s) to apply as a part of VPC VR " + domainRouterVO + " start.");
        if (!lbRules.isEmpty()) {
            for (final Pair<Nic, Network> nicNtwk : guestNics) {
                final Nic guestNic = nicNtwk.first();
                final long guestNetworkId = guestNic.getNetworkId();
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Lb, provider)) {
                    _commandSetupHelper.createApplyLoadBalancingRulesCommands(lbRules, domainRouterVO, cmds, guestNetworkId);
                    break;
                }
            }
        }
    }

    @Override
    protected List<MonitoringServiceVO> getDefaultServicesToMonitor(NetworkVO network) {
        if (network.getTrafficType() == TrafficType.Public) {
            return Arrays.asList(_monitorServiceDao.getServiceByName(MonitoringService.Service.Ssh.toString()));
        }
        return super.getDefaultServicesToMonitor(network);
    }

    @Override
    protected void finalizeNetworkRulesForNetwork(final Commands cmds, final DomainRouterVO domainRouterVO, final Provider provider, final Long guestNetworkId) {

        super.finalizeNetworkRulesForNetwork(cmds, domainRouterVO, provider, guestNetworkId);

        if (domainRouterVO.getVpcId() != null) {

            if (domainRouterVO.getState() == State.Starting || domainRouterVO.getState() == State.Running) {
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.NetworkACL, Provider.VPCVirtualRouter)) {
                    final List<NetworkACLItemVO> networkACLs = _networkACLMgr.listNetworkACLItems(guestNetworkId);
                    if (networkACLs != null && !networkACLs.isEmpty()) {
                        logger.debug("Found {} network ACLs to apply as a part of VPC VR {} start for guest network {}", networkACLs.size(), domainRouterVO, _networkModel.getNetwork(guestNetworkId));
                        _commandSetupHelper.createNetworkACLsCommands(networkACLs, domainRouterVO, cmds, guestNetworkId, false);
                    }
                }
            }
        }
    }

    protected boolean sendNetworkRulesToRouter(final long routerId, final long networkId, final boolean reprogramNetwork) throws ResourceUnavailableException {
        final DomainRouterVO router = _routerDao.findById(routerId);
        final Commands cmds = new Commands(OnError.Continue);

        final VirtualRouterProvider vrProvider = _vrProviderDao.findById(router.getElementId());
        if (vrProvider == null) {
            throw new CloudRuntimeException("Cannot find related virtual router provider of router: " + router.getHostName());
        }
        final Provider provider = Network.Provider.getProvider(vrProvider.getType().toString());
        if (provider == null) {
            throw new CloudRuntimeException("Cannot find related provider of virtual router provider: " + vrProvider.getType().toString());
        }

        if (reprogramNetwork) {
            finalizeNetworkRulesForNetwork(cmds, router, provider, networkId);
        }

        finalizeMonitorService(cmds, getVirtualMachineProfile(router), router, provider, networkId, false, getRouterHealthChecksConfig(router));

        return _nwHelper.sendCommandsToRouter(router, cmds);
    }

    private VirtualMachineProfile getVirtualMachineProfile(DomainRouterVO router) {
        final ServiceOfferingVO offering = _serviceOfferingDao.findById(router.getId(), router.getServiceOfferingId());
        final VirtualMachineTemplate template = _entityMgr.findByIdIncludingRemoved(VirtualMachineTemplate.class, router.getTemplateId());
        final Account owner = _entityMgr.findById(Account.class, router.getAccountId());
        final VirtualMachineProfileImpl profile = new VirtualMachineProfileImpl(router, template, offering, owner, null);
        for (final NicProfile nic : _networkMgr.getNicProfiles(router)) {
            profile.addNic(nic);
        }
        return profile;
    }

    /**
     * @param router
     * @param add
     * @param privateNic
     * @return
     * @throws ResourceUnavailableException
     */
    protected boolean setupVpcPrivateNetwork(final VirtualRouter router, final boolean add, final NicProfile privateNic) throws ResourceUnavailableException {

        if (router.getState() == State.Running) {
            final PrivateIpVO ipVO = _privateIpDao.findByIpAndSourceNetworkId(privateNic.getNetworkId(), privateNic.getIPv4Address());
            final Network network = _networkDao.findById(privateNic.getNetworkId());
            final String netmask = NetUtils.getCidrNetmask(network.getCidr());
            final PrivateIpAddress ip = new PrivateIpAddress(ipVO, network.getBroadcastUri().toString(), network.getGateway(), netmask, privateNic.getMacAddress());

            final List<PrivateIpAddress> privateIps = new ArrayList<PrivateIpAddress>(1);
            privateIps.add(ip);
            final Commands cmds = new Commands(Command.OnError.Stop);
            _commandSetupHelper.createVpcAssociatePrivateIPCommands(router, privateIps, cmds, add);

            try {
                if (_nwHelper.sendCommandsToRouter(router, cmds)) {
                    logger.debug("Successfully applied ip association for ip " + ip + " in vpc network " + network);
                    return true;
                } else {
                    logger.warn("Failed to associate ip address " + ip + " in vpc network " + network);
                    return false;
                }
            } catch (final Exception ex) {
                logger.warn("Failed to send  " + (add ? "add " : "delete ") + " private network " + network + " commands to rotuer ");
                return false;
            }
        } else if (router.getState() == State.Stopped || router.getState() == State.Stopping) {
            logger.debug("Router " + router.getInstanceName() + " is in " + router.getState() + ", so not sending setup private network command to the backend");
        } else {
            logger.warn("Unable to setup private gateway, virtual router " + router + " is not in the right state " + router.getState());

            throw new ResourceUnavailableException("Unable to setup Private gateway on the backend," + " virtual router " + router + " is not in the right state",
                    DataCenter.class, router.getDataCenterId());
        }
        return true;
    }

    @Override
    public boolean destroyPrivateGateway(final PrivateGateway gateway, final VirtualRouter router) throws ConcurrentOperationException, ResourceUnavailableException {
        boolean result = true;

        if (!_networkModel.isVmPartOfNetwork(router.getId(), gateway.getNetworkId())) {
            logger.debug("Router doesn't have nic for gateway " + gateway + " so no need to removed it");
            return result;
        }

        final Network privateNetwork = _networkModel.getNetwork(gateway.getNetworkId());
        final NicProfile nicProfile = _networkModel.getNicProfile(router, privateNetwork.getId(), null);

        logger.debug("Releasing private ip for gateway " + gateway + " from " + router);
        result = setupVpcPrivateNetwork(router, false, nicProfile);
        if (!result) {
            logger.warn("Failed to release private ip for gateway " + gateway + " on router " + router);
            return false;
        }

        // revoke network acl on the private gateway.
        if (!_networkACLMgr.revokeACLItemsForPrivateGw(gateway)) {
            logger.debug("Failed to delete network acl items on " + gateway + " from router " + router);
            return false;
        }

        logger.debug("Removing router " + router + " from private network " + privateNetwork + " as a part of delete private gateway");
        result = result && _itMgr.removeVmFromNetwork(router, privateNetwork, null);
        logger.debug("Private gateawy " + gateway + " is removed from router " + router);
        return result;
    }

    @Override
    protected void finalizeIpAssocForNetwork(final Commands cmds, final VirtualRouter domainRouterVO, final Provider provider, final Long guestNetworkId,
            final Map<String, String> vlanMacAddress) {

        if (domainRouterVO.getVpcId() == null) {
            super.finalizeIpAssocForNetwork(cmds, domainRouterVO, provider, guestNetworkId, vlanMacAddress);
            return;
        }

        if (domainRouterVO.getState() == State.Starting || domainRouterVO.getState() == State.Running) {
            final ArrayList<? extends PublicIpAddress> publicIps = getPublicIpsToApply(provider, guestNetworkId, IpAddress.State.Releasing);

            if (publicIps != null && !publicIps.isEmpty()) {
                logger.debug("Found " + publicIps.size() + " ip(s) to apply as a part of domR " + domainRouterVO + " start.");
                // Re-apply public ip addresses - should come before PF/LB/VPN
                _commandSetupHelper.createVpcAssociatePublicIPCommands(domainRouterVO, publicIps, cmds, vlanMacAddress);
            }
        }
    }

    @Override
    public boolean startSite2SiteVpn(final Site2SiteVpnConnection conn, final VirtualRouter router) throws ResourceUnavailableException {
        if (router.getState() != State.Running) {
            logger.warn("Unable to apply site-to-site VPN configuration, virtual router is not in the right state " + router.getState());
            throw new ResourceUnavailableException("Unable to apply site 2 site VPN configuration," + " virtual router is not in the right state", DataCenter.class,
                    router.getDataCenterId());
        }

        return applySite2SiteVpn(true, router, conn);
    }

    @Override
    public boolean startSite2SiteVpn(DomainRouterVO router) throws ResourceUnavailableException {
        boolean result = true;
        List<Site2SiteVpnConnectionVO> conns = _s2sVpnMgr.getConnectionsForRouter(router);
        for (Site2SiteVpnConnectionVO conn : conns) {
            result = result && startSite2SiteVpn(conn, router);
        }

        return result;
    }

    @Override
    public boolean stopSite2SiteVpn(final Site2SiteVpnConnection conn, final VirtualRouter router) throws ResourceUnavailableException {
        if (router.getState() != State.Running) {
            logger.warn("Unable to apply site-to-site VPN configuration, virtual router is not in the right state " + router.getState());
            throw new ResourceUnavailableException("Unable to apply site 2 site VPN configuration," + " virtual router is not in the right state", DataCenter.class,
                    router.getDataCenterId());
        }

        return applySite2SiteVpn(false, router, conn);
    }

    protected boolean applySite2SiteVpn(final boolean isCreate, final VirtualRouter router, final Site2SiteVpnConnection conn) throws ResourceUnavailableException {
        final Commands cmds = new Commands(Command.OnError.Continue);
        _commandSetupHelper.createSite2SiteVpnCfgCommands(conn, isCreate, router, cmds);
        return _nwHelper.sendCommandsToRouter(router, cmds);
    }

    protected Pair<Map<String, PublicIpAddress>, Map<String, PublicIpAddress>> getNicsToChangeOnRouter(final List<? extends PublicIpAddress> publicIps, final VirtualRouter router) {
        // 1) check which nics need to be plugged/unplugged and plug/unplug them

        final Map<String, PublicIpAddress> nicsToPlug = new HashMap<String, PublicIpAddress>();
        final Map<String, PublicIpAddress> nicsToUnplug = new HashMap<String, PublicIpAddress>();

        // find out nics to unplug
        for (final PublicIpAddress ip : publicIps) {
            final long publicNtwkId = ip.getNetworkId();

            // if ip is not associated to any network, and there are no firewall
            // rules, release it on the backend
            if (!_vpcMgr.isIpAllocatedToVpc(ip)) {
                ip.setState(IpAddress.State.Releasing);
            }

            if (ip.getState() == IpAddress.State.Releasing) {
                final Nic nic = _nicDao.findByIp4AddressAndNetworkIdAndInstanceId(publicNtwkId, router.getId(), ip.getAddress().addr());
                if (nic != null) {
                    nicsToUnplug.put(ip.getVlanTag(), ip);
                    logger.debug("Need to unplug the nic for ip=" + ip + "; vlan=" + ip.getVlanTag() + " in public network id =" + publicNtwkId);
                }
            }
        }

        // find out nics to plug
        for (final PublicIpAddress ip : publicIps) {
            final URI broadcastUri = BroadcastDomainType.Vlan.toUri(ip.getVlanTag());
            final long publicNtwkId = ip.getNetworkId();

            // if ip is not associated to any network, and there are no firewall
            // rules, release it on the backend
            if (!_vpcMgr.isIpAllocatedToVpc(ip)) {
                ip.setState(IpAddress.State.Releasing);
            }

            if (ip.getState() == IpAddress.State.Allocated || ip.getState() == IpAddress.State.Allocating) {
                // nic has to be plugged only when there are no nics for this
                // vlan tag exist on VR
                final Nic nic = _nicDao.findByNetworkIdInstanceIdAndBroadcastUri(publicNtwkId, router.getId(), broadcastUri.toString());

                if (nic == null && nicsToPlug.get(ip.getVlanTag()) == null) {
                    nicsToPlug.put(ip.getVlanTag(), ip);
                    logger.debug("Need to plug the nic for ip=" + ip + "; vlan=" + ip.getVlanTag() + " in public network id =" + publicNtwkId);
                } else {
                    final PublicIpAddress nicToUnplug = nicsToUnplug.get(ip.getVlanTag());
                    if (nicToUnplug != null) {
                        final NicVO nicVO = _nicDao.findByIp4AddressAndNetworkIdAndInstanceId(publicNtwkId, router.getId(), nicToUnplug.getAddress().addr());
                        nicVO.setIPv4Address(ip.getAddress().addr());
                        _nicDao.update(nicVO.getId(), nicVO);
                        logger.debug("Updated the nic " + nicVO + " with the new ip address " + ip.getAddress().addr());
                        nicsToUnplug.remove(ip.getVlanTag());
                    }
                }
            }
        }

        final Pair<Map<String, PublicIpAddress>, Map<String, PublicIpAddress>> nicsToChange = new Pair<Map<String, PublicIpAddress>, Map<String, PublicIpAddress>>(nicsToPlug,
                nicsToUnplug);
        return nicsToChange;
    }

    @Override
    public void finalizeStop(final VirtualMachineProfile profile, final Answer answer) {
        super.finalizeStop(profile, answer);
        // Mark VPN connections as Disconnected
        final DomainRouterVO router = _routerDao.findById(profile.getId());
        final Long vpcId = router.getVpcId();
        if (vpcId != null) {
            _s2sVpnMgr.markDisconnectVpnConnByVpc(vpcId);
        }
    }

    @Override
    public List<DomainRouterVO> getVpcRouters(final long vpcId) {
        return _routerDao.listByVpcId(vpcId);
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public boolean startRemoteAccessVpn(final RemoteAccessVpn vpn, final VirtualRouter router) throws ResourceUnavailableException {
        if (router.getState() != State.Running) {
            logger.warn("Unable to apply remote access VPN configuration, virtual router is not in the right state " + router.getState());
            throw new ResourceUnavailableException("Unable to apply remote access VPN configuration," + " virtual router is not in the right state", DataCenter.class,
                    router.getDataCenterId());
        }

        final Commands cmds = new Commands(Command.OnError.Stop);
        _commandSetupHelper.createApplyVpnCommands(true, vpn, router, cmds);

        try {
            _agentMgr.send(router.getHostId(), cmds);
        } catch (final OperationTimedoutException e) {
            logger.debug("Failed to start remote access VPN: ", e);
            throw new AgentUnavailableException("Unable to send commands to virtual router ", router.getHostId(), e);
        }
        Answer answer = cmds.getAnswer("users");
        if (answer == null || !answer.getResult()) {
            String errorMessage = (answer == null) ? "null answer object" : answer.getDetails();
            DataCenter zone = _entityMgr.findById(DataCenter.class, router.getDataCenterId());
            Account account = _entityMgr.findById(Account.class, vpn.getAccountId());
            logger.error("Unable to start vpn: unable add users to vpn in zone {} for account {} on domR: {} due to {}", zone, account, router, errorMessage);
            throw new ResourceUnavailableException(String.format("Unable to start vpn: Unable to add users to vpn in zone %s for account %s on domR: %s due to %s", zone, account, router.getInstanceName(), errorMessage), DataCenter.class, router.getDataCenterId());
        }
        answer = cmds.getAnswer("startVpn");
        if (answer == null || !answer.getResult()) {
            String errorMessage = (answer == null) ? "null answer object" : answer.getDetails();
            DataCenter zone = _entityMgr.findById(DataCenter.class, router.getDataCenterId());
            Account account = _entityMgr.findById(Account.class, vpn.getAccountId());
            logger.error("Unable to start vpn in zone {} for account {} on domR: {} due to {}", zone, account, router, errorMessage);
            throw new ResourceUnavailableException(String.format("Unable to start vpn in zone %s for account %s on domR: %s due to %s", zone, account, router.getInstanceName(), errorMessage), DataCenter.class, router.getDataCenterId());
        }

        return true;
    }

    @Override
    public boolean stopRemoteAccessVpn(final RemoteAccessVpn vpn, final VirtualRouter router) throws ResourceUnavailableException {
        boolean result = true;

        if (router.getState() == State.Running) {
            final Commands cmds = new Commands(Command.OnError.Continue);
            _commandSetupHelper.createApplyVpnCommands(false, vpn, router, cmds);
            result = result && _nwHelper.sendCommandsToRouter(router, cmds);
        } else if (router.getState() == State.Stopped) {
            logger.debug("Router " + router + " is in Stopped state, not sending deleteRemoteAccessVpn command to it");
        } else {
            logger.warn("Failed to stop remote access VPN: domR " + router + " is not in right state " + router.getState());
            throw new ResourceUnavailableException("Failed to stop remote access VPN: domR is not in right state " + router.getState(), DataCenter.class,
                    router.getDataCenterId());
        }
        return true;
    }

    @Override
    public boolean postStateTransitionEvent(final StateMachine2.Transition<State, VirtualMachine.Event> transition, final VirtualMachine vo, final boolean status, final Object opaque) {
        // Without this VirtualNetworkApplianceManagerImpl.postStateTransitionEvent() gets called twice as part of listeners -
        // once from VpcVirtualNetworkApplianceManagerImpl and once from VirtualNetworkApplianceManagerImpl itself
        return true;
    }

    private Nic updateNicWithDeviceId(final long nicId, int deviceId) {
        NicVO nic = _nicDao.findById(nicId);
        nic.setDeviceId(deviceId);
        _nicDao.update(nic.getId(), nic);
        return nic;
    }
}
