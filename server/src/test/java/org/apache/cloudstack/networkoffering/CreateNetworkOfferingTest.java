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

package org.apache.cloudstack.networkoffering;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.annotation.dao.AnnotationDao;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.resourcedetail.dao.UserIpAddressDetailsDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.dc.dao.VlanDetailsDao;
import com.cloud.event.dao.UsageEventDao;
import com.cloud.event.dao.UsageEventDetailsDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.Network;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.PublicIpQuarantineDao;
import com.cloud.network.vpc.VpcManager;
import com.cloud.offering.NetworkOffering.Availability;
import com.cloud.offerings.NetworkOfferingServiceMapVO;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.vm.dao.VMInstanceDetailsDao;

import junit.framework.TestCase;

@RunWith(MockitoJUnitRunner.class)
public class CreateNetworkOfferingTest extends TestCase {

    @Mock
    ConfigurationDao configDao;

    @Mock
    NetworkOfferingDao offDao;

    @Mock
    NetworkOfferingServiceMapDao mapDao;

    @Mock
    AccountManager accountMgr;

    @Mock
    VpcManager vpcMgr;

    @Mock
    VMInstanceDetailsDao vmInstanceDetailsDao;

    @Mock
    UsageEventDao UsageEventDao;

    @Mock
    UsageEventDetailsDao usageEventDetailsDao;

    @Mock
    UserIpAddressDetailsDao userIpAddressDetailsDao;

    @Mock
    LoadBalancerVMMapDao _loadBalancerVMMapDao;

    @Mock
    AnnotationDao annotationDao;
    @Inject
    VlanDetailsDao vlanDetailsDao;

    @Inject
    PublicIpQuarantineDao publicIpQuarantineDao;

    @InjectMocks
    ConfigurationManager configMgr = new ConfigurationManagerImpl();

    @Override
    @Before
    public void setUp() {
        Mockito.when(offDao.persist(any(NetworkOfferingVO.class), nullable(Map.class))).thenReturn(new NetworkOfferingVO());
        Mockito.when(mapDao.persist(any(NetworkOfferingServiceMapVO.class))).thenReturn(new NetworkOfferingServiceMapVO());
        Mockito.when(accountMgr.getSystemUser()).thenReturn(new UserVO(1));
        Mockito.when(accountMgr.getSystemAccount()).thenReturn(new AccountVO(2));

        CallContext.register(accountMgr.getSystemUser(), accountMgr.getSystemAccount());
    }

    @Override
    @After
    public void tearDown() {
        CallContext.unregister();
    }

    //Test Shared network offerings
    @Test
    public void createSharedNtwkOffWithVlan() {
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("shared", "shared", TrafficType.Guest, null, true, Availability.Optional, 200, null, false, Network.GuestType.Shared, false,
                null, false, null, true, false, null, false, null, true, false, false, false,  false,null,null, null, false, null, null, false);
        assertNotNull("Shared network offering with specifyVlan=true failed to create ", off);
    }

    @Test
    public void createSharedNtwkOffWithNoVlan() {
        NetworkOfferingVO off =
                configMgr.createNetworkOffering("shared", "shared", TrafficType.Guest, null, false, Availability.Optional, 200, null, false, Network.GuestType.Shared,
                    false, null, false, null, true, false, null, false, null, true, false, false, false, false,null, null,null, false, null, null, false);
        assertNotNull("Shared network offering with specifyVlan=false was created", off);
    }

    @Test
    public void createSharedNtwkOffWithSpecifyIpRanges() {
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("shared", "shared", TrafficType.Guest, null, true, Availability.Optional, 200, null, false, Network.GuestType.Shared, false,
                null, false, null, true, false, null, false, null, true, false, false, false, false,null,null, null, false, null, null, false);

        assertNotNull("Shared network offering with specifyIpRanges=true failed to create ", off);
    }

    @Test(expected=InvalidParameterValueException.class)
    public void createSharedNtwkOffWithoutSpecifyIpRanges() {
        NetworkOfferingVO off =
                configMgr.createNetworkOffering("shared", "shared", TrafficType.Guest, null, true, Availability.Optional, 200, null, false, Network.GuestType.Shared,
                        false, null, false, null, false, false, null, false, null, true, false, false, false,false, null,null, null, false, null, null, false);
        assertNull("Shared network offering with specifyIpRanges=false was created", off);
    }

    //Test Isolated network offerings
    @Test
    public void createIsolatedNtwkOffWithNoVlan() {
        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        vrProvider.add(Provider.VirtualRouter);
        serviceProviderMap.put(Network.Service.SourceNat, vrProvider);
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, false, Availability.Optional, 200, serviceProviderMap, false,
                Network.GuestType.Isolated, false, null, false, null, false, false, null, false, null, true, false, false, false, false,null, null, null, false, null, null, false);

        assertNotNull("Isolated network offering with specifyIpRanges=false failed to create ", off);
    }

    @Test
    public void createIsolatedNtwkOffWithVlan() {
        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        vrProvider.add(Provider.VirtualRouter);
        serviceProviderMap.put(Network.Service.SourceNat, vrProvider);
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, true, Availability.Optional, 200, serviceProviderMap, false,
                Network.GuestType.Isolated, false, null, false, null, false, false, null, false, null, true, false, false, false, false,null,null, null, false, null, null, false);
        assertNotNull("Isolated network offering with specifyVlan=true wasn't created", off);

    }

    @Test(expected=InvalidParameterValueException.class)
    public void createIsolatedNtwkOffWithSpecifyIpRangesAndSourceNat() {
        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        vrProvider.add(Provider.VirtualRouter);
        serviceProviderMap.put(Network.Service.SourceNat, vrProvider);
        NetworkOfferingVO off =
                configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, false, Availability.Optional, 200, serviceProviderMap, false,
                        Network.GuestType.Isolated, false, null, false, null, true, false, null, false, null, true, false, false, false, false,null,null, null, false, null, null, false);
        assertNull("Isolated network offering with specifyIpRanges=true and source nat service enabled, was created", off);
    }

    @Test
    public void createIsolatedNtwkOffWithSpecifyIpRangesAndNoSourceNat() {

        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, false, Availability.Optional, 200, serviceProviderMap, false,
                Network.GuestType.Isolated, false, null, false, null, true, false, null, false, null, true, false, false, false, false,null,null, null, false, null, null, false);
        assertNotNull("Isolated network offering with specifyIpRanges=true and with no sourceNatService, failed to create", off);

    }

    @Test
    public void createVpcNtwkOff() {
        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        vrProvider.add(Provider.VPCVirtualRouter);
        serviceProviderMap.put(Network.Service.Dhcp, vrProvider);
        serviceProviderMap.put(Network.Service.Dns, vrProvider);
        serviceProviderMap.put(Network.Service.Lb, vrProvider);
        serviceProviderMap.put(Network.Service.SourceNat, vrProvider);
        serviceProviderMap.put(Network.Service.Gateway, vrProvider);
        serviceProviderMap.put(Network.Service.Lb, vrProvider);
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, true, Availability.Optional, 200, serviceProviderMap, false,
                Network.GuestType.Isolated, false, null, false, null, false, false, null, false, null, true, true, false, false, false,null, null, null, false, null, null, false);
        // System.out.println("Creating Vpc Network Offering");
        assertNotNull("Vpc Isolated network offering with Vpc provider ", off);
    }

    @Test
    public void createVpcNtwkOffWithNetscaler() {
        Map<Service, Set<Provider>> serviceProviderMap = new HashMap<Network.Service, Set<Network.Provider>>();
        Set<Network.Provider> vrProvider = new HashSet<Network.Provider>();
        Set<Network.Provider> lbProvider = new HashSet<Network.Provider>();
        vrProvider.add(Provider.VPCVirtualRouter);
        lbProvider.add(Provider.Netscaler);
        serviceProviderMap.put(Network.Service.Dhcp, vrProvider);
        serviceProviderMap.put(Network.Service.Dns, vrProvider);
        serviceProviderMap.put(Network.Service.Lb, vrProvider);
        serviceProviderMap.put(Network.Service.SourceNat, vrProvider);
        serviceProviderMap.put(Network.Service.Gateway, vrProvider);
        serviceProviderMap.put(Network.Service.Lb, lbProvider);
        NetworkOfferingVO off =
            configMgr.createNetworkOffering("isolated", "isolated", TrafficType.Guest, null, true, Availability.Optional, 200, serviceProviderMap, false,
                Network.GuestType.Isolated, false, null, false, null, false, false, null, false, null, true, true, false, false, false,null, null, null, false, null, null, false);
        // System.out.println("Creating Vpc Network Offering");
        assertNotNull("Vpc Isolated network offering with Vpc and Netscaler provider ", off);
    }
}
