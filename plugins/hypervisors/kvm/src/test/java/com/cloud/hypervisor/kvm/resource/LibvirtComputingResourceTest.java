/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.cloud.hypervisor.kvm.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.cloud.utils.net.NetUtils;

import com.cloud.vm.VmDetailConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.cloudstack.api.ApiConstants.IoDriverPolicy;
import org.apache.cloudstack.storage.command.AttachAnswer;
import org.apache.cloudstack.storage.command.AttachCommand;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.cloudstack.utils.bytescale.ByteScaleUtils;
import org.apache.cloudstack.utils.linux.CPUStat;
import org.apache.cloudstack.utils.linux.MemStat;
import org.apache.cloudstack.utils.qemu.QemuImg.PhysicalDiskFormat;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainBlockStats;
import org.libvirt.DomainInfo;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.DomainInterfaceStats;
import org.libvirt.LibvirtException;
import org.libvirt.MemoryStatistic;
import org.libvirt.NodeInfo;
import org.libvirt.SchedUlongParameter;
import org.libvirt.StorageVol;
import org.libvirt.VcpuInfo;
import org.libvirt.jna.virDomainMemoryStats;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.AttachIsoCommand;
import com.cloud.agent.api.BackupSnapshotCommand;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.CheckNetworkCommand;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.agent.api.CheckRouterAnswer;
import com.cloud.agent.api.CheckRouterCommand;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.CleanupNetworkRulesCmd;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreatePrivateTemplateFromSnapshotCommand;
import com.cloud.agent.api.CreatePrivateTemplateFromVolumeCommand;
import com.cloud.agent.api.CreateStoragePoolCommand;
import com.cloud.agent.api.CreateVolumeFromSnapshotCommand;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.FenceCommand;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmDiskStatsCommand;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.GetVncPortCommand;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.ModifySshKeysCommand;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.NetworkRulesSystemVmCommand;
import com.cloud.agent.api.NetworkRulesVmSecondaryIpCommand;
import com.cloud.agent.api.NetworkUsageCommand;
import com.cloud.agent.api.OvsCreateTunnelCommand;
import com.cloud.agent.api.OvsDestroyBridgeCommand;
import com.cloud.agent.api.OvsDestroyTunnelCommand;
import com.cloud.agent.api.OvsFetchInterfaceCommand;
import com.cloud.agent.api.OvsSetupBridgeCommand;
import com.cloud.agent.api.OvsVpcPhysicalTopologyConfigCommand;
import com.cloud.agent.api.OvsVpcPhysicalTopologyConfigCommand.Host;
import com.cloud.agent.api.OvsVpcPhysicalTopologyConfigCommand.Tier;
import com.cloud.agent.api.OvsVpcPhysicalTopologyConfigCommand.Vm;
import com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand;
import com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand.Acl;
import com.cloud.agent.api.PingTestCommand;
import com.cloud.agent.api.PlugNicCommand;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.PvlanSetupCommand;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.RebootRouterCommand;
import com.cloud.agent.api.SecurityGroupRulesCmd;
import com.cloud.agent.api.SecurityGroupRulesCmd.IpPortAndProto;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.UnPlugNicCommand;
import com.cloud.agent.api.UnsupportedAnswer;
import com.cloud.agent.api.UpdateHostPasswordCommand;
import com.cloud.agent.api.UpgradeSnapshotCommand;
import com.cloud.agent.api.VgpuTypesInfo;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.cloud.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.cloud.agent.api.storage.CopyVolumeCommand;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.storage.ResizeVolumeCommand;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.agent.properties.AgentProperties;
import com.cloud.agent.properties.AgentPropertiesFileHandler;
import com.cloud.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.cloud.exception.InternalErrorException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.kvm.resource.KVMHABase.HAStoragePool;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.ChannelDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.ClockDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.ConsoleDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.CpuTuneDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.DevicesDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.DiskDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.FeaturesDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.GraphicDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.GuestDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.GuestDef.GuestType;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.GuestResourceDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.InputDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.InterfaceDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.MemBalloonDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.RngDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.SCSIDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.SerialDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.TermPolicy;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.VideoDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.WatchDogDef;
import com.cloud.hypervisor.kvm.resource.wrapper.LibvirtRequestWrapper;
import com.cloud.hypervisor.kvm.resource.wrapper.LibvirtUtilitiesHelper;
import com.cloud.hypervisor.kvm.storage.KVMPhysicalDisk;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetworkSetupInfo;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageLayer;
import com.cloud.storage.StoragePool;
import com.cloud.storage.Volume;
import com.cloud.storage.resource.StorageSubsystemCommandHandler;
import com.cloud.storage.template.Processor;
import com.cloud.storage.template.Processor.FormatInfo;
import com.cloud.storage.template.TemplateLocation;
import com.cloud.template.VirtualMachineTemplate.BootloaderType;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.utils.script.OutputInterpreter.OneLineParser;
import com.cloud.utils.ssh.SshHelper;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.PowerState;
import com.cloud.vm.VirtualMachine.Type;

@RunWith(MockitoJUnitRunner.class)
public class LibvirtComputingResourceTest {

    @Mock
    private LibvirtComputingResource libvirtComputingResourceMock;
    @Mock
    VirtualMachineTO vmTO;
    @Mock
    LibvirtVMDef vmDef;
    @Mock
    Logger loggerMock;
    @Mock
    Connect connMock;
    @Mock
    LibvirtDomainXMLParser parserMock;

    @Mock
    DiskTO diskToMock;

    @Mock
    private VolumeObjectTO volumeObjectToMock;


    @Spy
    private LibvirtComputingResource libvirtComputingResourceSpy = Mockito.spy(new LibvirtComputingResource());

    @Mock
    Domain domainMock;
    @Mock
    DomainInfo domainInfoMock;
    @Mock
    DomainInterfaceStats domainInterfaceStatsMock;
    @Mock
    DomainBlockStats domainBlockStatsMock;

    @Mock
    SnapshotObjectTO snapshotObjectToMock;

    @Mock
    BlockCommitListener blockCommitListenerMock;

    private final static long HYPERVISOR_LIBVIRT_VERSION_SUPPORTS_IOURING = 6003000;
    private final static long HYPERVISOR_QEMU_VERSION_SUPPORTS_IOURING = 5000000;

    private final static String VM_NAME = "test";

    String hyperVisorType = "kvm";
    Random random = new Random();
    final String memInfo = "MemTotal:        5830236 kB\n" +
            "MemFree:          156752 kB\n" +
            "Buffers:          326836 kB\n" +
            "Cached:          2606764 kB\n" +
            "SwapCached:            0 kB\n" +
            "Active:          4260808 kB\n" +
            "Inactive:         949392 kB\n";

    final static long[] defaultStats = new long[2];
    final static long[] vpcStats = { 1L, 2L };
    final static long[] networkStats = { 3L, 4L };
    final static long[] lbStats = { 5L };
    final static String privateIp = "192.168.1.1";
    final static String publicIp = "10.10.10.10";
    final static Integer port = 8080;

    final OneLineParser statsParserMock = Mockito.mock(OneLineParser.class);

    @Before
    public void setup() throws Exception {
        libvirtComputingResourceSpy.qemuSocketsPath = new File("/var/run/qemu");
        libvirtComputingResourceSpy.parser = parserMock;
        LibvirtComputingResource.LOGGER = loggerMock;
    }

    /**
     This test tests if the Agent can handle a vmSpec coming
     from a <=4.1 management server.

     The overcommit feature has not been merged in there and thus
     only 'speed' is set.
     */
    @Test
    public void testCreateVMFromSpecLegacy() {
        final int id = random.nextInt(65534);
        final String name = "test-instance-1";

        final int cpus = random.nextInt(2) + 1;
        final int speed = 1024;
        final int minRam = 256 * 1024;
        final int maxRam = 512 * 1024;

        final String os = "Ubuntu";

        final String vncAddr = "";
        final String vncPassword = "mySuperSecretPassword";

        final VirtualMachineTO to = new VirtualMachineTO(id, name, VirtualMachine.Type.User, cpus, speed, minRam, maxRam, BootloaderType.HVM, os, false, false, vncPassword);
        to.setVncAddr(vncAddr);
        to.setArch("x86_64");
        to.setUuid("b0f0a72d-7efb-3cad-a8ff-70ebf30b3af9");
        to.setVcpuMaxLimit(cpus + 1);

        final LibvirtVMDef vm = libvirtComputingResourceSpy.createVMFromSpec(to);
        vm.setHvsType(hyperVisorType);

        verifyVm(to, vm);
    }

    /**
     This test verifies that CPU topology is properly set for hex-core
     */
    @Test
    public void testCreateVMFromSpecWithTopology6() {
        final int id = random.nextInt(65534);
        final String name = "test-instance-1";

        final int cpus = 12;
        final int minSpeed = 1024;
        final int maxSpeed = 2048;
        final int minRam = 256 * 1024;
        final int maxRam = 512 * 1024;

        final String os = "Ubuntu";

        final String vncAddr = "";
        final String vncPassword = "mySuperSecretPassword";

        final VirtualMachineTO to = new VirtualMachineTO(id, name, VirtualMachine.Type.User, cpus, minSpeed, maxSpeed, minRam, maxRam, BootloaderType.HVM, os, false, false, vncPassword);
        to.setVncAddr(vncAddr);
        to.setArch("x86_64");
        to.setUuid("b0f0a72d-7efb-3cad-a8ff-70ebf30b3af9");
        to.setVcpuMaxLimit(cpus + 1);

        final LibvirtVMDef vm = libvirtComputingResourceSpy.createVMFromSpec(to);
        vm.setHvsType(hyperVisorType);

        verifyVm(to, vm);
    }

    /**
     This test verifies that CPU topology is properly set for quad-core
     */
    @Test
    public void testCreateVMFromSpecWithTopology4() {
        final int id = random.nextInt(65534);
        final String name = "test-instance-1";

        final int cpus = 8;
        final int minSpeed = 1024;
        final int maxSpeed = 2048;
        final int minRam = 256 * 1024;
        final int maxRam = 512 * 1024;

        final String os = "Ubuntu";

        final String vncAddr = "";
        final String vncPassword = "mySuperSecretPassword";

        final VirtualMachineTO to = new VirtualMachineTO(id, name, VirtualMachine.Type.User, cpus, minSpeed, maxSpeed, minRam, maxRam, BootloaderType.HVM, os, false, false, vncPassword);
        to.setVncAddr(vncAddr);
        to.setUuid("b0f0a72d-7efb-3cad-a8ff-70ebf30b3af9");
        to.setVcpuMaxLimit(cpus + 1);

        LibvirtVMDef vm = libvirtComputingResourceSpy.createVMFromSpec(to);
        vm.setHvsType(hyperVisorType);

        verifyVm(to, vm);
    }

    /**
     This test tests if the Agent can handle a vmSpec coming
     from a >4.1 management server.

     It tests if the Agent can handle a vmSpec with overcommit
     data like minSpeed and maxSpeed in there
     */
    @Test
    public void testCreateVMFromSpec() {
        VirtualMachineTO to = createDefaultVM(false);
        final LibvirtVMDef vm = libvirtComputingResourceSpy.createVMFromSpec(to);
        vm.setHvsType(hyperVisorType);

        verifyVm(to, vm);
    }

    @Test
    public void testCreateGuestFromSpecWithoutCustomParam() {
        VirtualMachineTO to = createDefaultVM(false);
        LibvirtVMDef vm = new LibvirtVMDef();
        GuestDef guestDef = libvirtComputingResourceSpy.createGuestFromSpec(to, vm, to.getUuid(), null);
        verifySysInfo(guestDef, "smbios", to.getUuid(), "s390x".equals(System.getProperty("os.arch")) ? "s390-ccw-virtio" : "pc");
        Assert.assertEquals(GuestDef.BootType.BIOS, guestDef.getBootType());
        Assert.assertNull(guestDef.getBootMode());
    }

    @Test
    public void testCreateGuestFromSpecWithCustomParamAndUefi() {
        VirtualMachineTO to = createDefaultVM(false);

        Map<String, String> extraConfig = new HashMap<>();
        extraConfig.put(GuestDef.BootType.UEFI.toString(), "legacy");

        LibvirtVMDef vm = new LibvirtVMDef();

        GuestDef guestDef = libvirtComputingResourceSpy.createGuestFromSpec(to, vm, to.getUuid(), extraConfig);
        verifySysInfo(guestDef, "smbios", to.getUuid(), "q35");
        Assert.assertEquals(GuestDef.BootType.UEFI, guestDef.getBootType());
        Assert.assertEquals(GuestDef.BootMode.LEGACY, guestDef.getBootMode());
    }

    @Test
    public void testCreateGuestFromSpecWithCustomParamUefiAndSecure() {
        VirtualMachineTO to = createDefaultVM(false);

        Map<String, String> extraConfig = new HashMap<>();
        extraConfig.put(GuestDef.BootType.UEFI.toString(), "secure");

        LibvirtVMDef vm = new LibvirtVMDef();

        GuestDef guestDef = libvirtComputingResourceSpy.createGuestFromSpec(to, vm, to.getUuid(), extraConfig);
        verifySysInfo(guestDef, "smbios", to.getUuid(), "q35");
        Assert.assertEquals(GuestDef.BootType.UEFI, guestDef.getBootType());
        Assert.assertEquals(GuestDef.BootMode.SECURE, guestDef.getBootMode());
    }

    @Test
    public void testCreateGuestResourceDef() {
        VirtualMachineTO to = createDefaultVM(false);

        GuestResourceDef guestResourceDef = libvirtComputingResourceSpy.createGuestResourceDef(to);
        verifyGuestResourceDef(guestResourceDef, to);
    }

    @Test
    public void testCreateDevicesDef() {
        VirtualMachineTO to = createDefaultVM(false);

        GuestDef guest = new GuestDef();
        guest.setGuestType(GuestType.KVM);

        DevicesDef devicesDef = libvirtComputingResourceSpy.createDevicesDef(to, guest, to.getCpus() + 1, false);
        verifyDevices(devicesDef, to);
    }

    @Test
    public void testCreateDevicesWithSCSIDisk() {
        VirtualMachineTO to = createDefaultVM(false);
        to.setDetails(new HashMap<>());
        to.setPlatformEmulator("Other PV Virtio-SCSI");

        final DiskTO diskTO = Mockito.mock(DiskTO.class);
        to.setDisks(new DiskTO[]{diskTO});

        GuestDef guest = new GuestDef();
        guest.setGuestType(GuestType.KVM);

        DevicesDef devicesDef = libvirtComputingResourceSpy.createDevicesDef(to, guest, to.getCpus() + 1, false);
        verifyDevices(devicesDef, to);

        Document domainDoc = parse(devicesDef.toString());
        assertNodeExists(domainDoc, "/devices/controller[@type='scsi']");
        assertNodeExists(domainDoc, "/devices/controller[@model='virtio-scsi']");
        assertNodeExists(domainDoc, "/devices/controller/address[@type='pci']");
        assertNodeExists(domainDoc, "/devices/controller/driver[@queues='" + (to.getCpus() + 1) + "']");
    }

    @Test
    public void testConfigureGuestAndSystemVMToUseKVM() {
        VirtualMachineTO to = createDefaultVM(false);
        libvirtComputingResourceSpy.hypervisorLibvirtVersion = 100;
        libvirtComputingResourceSpy.hypervisorQemuVersion = 10;
        LibvirtVMDef vm = new LibvirtVMDef();

        GuestDef guestFromSpec = libvirtComputingResourceSpy.createGuestFromSpec(to, vm, to.getUuid(), null);
        Assert.assertEquals(GuestDef.GuestType.KVM, guestFromSpec.getGuestType());
        Assert.assertEquals(HypervisorType.KVM.toString().toLowerCase(), vm.getHvsType());
    }

    @Test
    public void testConfigureGuestAndUserVMToUseLXC() {
        VirtualMachineTO to = createDefaultVM(false);
        libvirtComputingResourceSpy.hypervisorType = HypervisorType.LXC;
        LibvirtVMDef vm = new LibvirtVMDef();

        GuestDef guestFromSpec = libvirtComputingResourceSpy.createGuestFromSpec(to, vm, to.getUuid(), null);
        Assert.assertEquals(GuestDef.GuestType.LXC, guestFromSpec.getGuestType());
        Assert.assertEquals(HypervisorType.LXC.toString().toLowerCase(), vm.getHvsType());
    }

    @Test
    public void testCreateCpuTuneDefWithoutQuotaAndPeriod() {
        VirtualMachineTO to = createDefaultVM(false);

        CpuTuneDef cpuTuneDef = libvirtComputingResourceSpy.createCpuTuneDef(to);
        Document domainDoc = parse(cpuTuneDef.toString());
        assertXpath(domainDoc, "/cputune/shares/text()", String.valueOf(cpuTuneDef.getShares()));
    }

    @Test
    public void testCreateCpuTuneDefWithQuotaAndPeriod() {
        VirtualMachineTO to = createDefaultVM(true);
        to.setCpuQuotaPercentage(10.0);

        CpuTuneDef cpuTuneDef = libvirtComputingResourceSpy.createCpuTuneDef(to);
        Document domainDoc = parse(cpuTuneDef.toString());
        assertXpath(domainDoc, "/cputune/shares/text()", String.valueOf(cpuTuneDef.getShares()));
        assertXpath(domainDoc, "/cputune/quota/text()", String.valueOf(cpuTuneDef.getQuota()));
        assertXpath(domainDoc, "/cputune/period/text()", String.valueOf(cpuTuneDef.getPeriod()));
    }

    @Test
    public void testCreateCpuTuneDefWithMinQuota() {
        VirtualMachineTO to = createDefaultVM(true);
        to.setCpuQuotaPercentage(0.01);

        CpuTuneDef cpuTuneDef = libvirtComputingResourceSpy.createCpuTuneDef(to);
        Document domainDoc = parse(cpuTuneDef.toString());
        assertXpath(domainDoc, "/cputune/shares/text()", String.valueOf(cpuTuneDef.getShares()));
        assertXpath(domainDoc, "/cputune/quota/text()", "1000");
        assertXpath(domainDoc, "/cputune/period/text()", String.valueOf(cpuTuneDef.getPeriod()));
    }

    @Test
    public void testCreateDefaultClockDef() {
        VirtualMachineTO to = createDefaultVM(false);

        ClockDef clockDef = libvirtComputingResourceSpy.createClockDef(to);
        Document domainDoc = parse(clockDef.toString());

        assertXpath(domainDoc, "/clock/@offset", "utc");
    }

    @Test
    public void testCreateClockDefWindows() {
        VirtualMachineTO to = createDefaultVM(false);
        to.setOs("Windows");

        ClockDef clockDef = libvirtComputingResourceSpy.createClockDef(to);
        Document domainDoc = parse(clockDef.toString());

        assertXpath(domainDoc, "/clock/@offset", "localtime");
        assertXpath(domainDoc, "/clock/timer/@name", "hypervclock");
        assertXpath(domainDoc, "/clock/timer/@present", "yes");
    }

    @Test
    public void testCreateClockDefKvmclock() {
        VirtualMachineTO to = createDefaultVM(false);
        libvirtComputingResourceSpy.hypervisorLibvirtVersion = 9020;

        ClockDef clockDef = libvirtComputingResourceSpy.createClockDef(to);
        Document domainDoc = parse(clockDef.toString());

        assertXpath(domainDoc, "/clock/@offset", "utc");
        assertXpath(domainDoc, "/clock/timer/@name", "kvmclock");
    }

    @Test
    public void testCreateTermPolicy() {
        TermPolicy termPolicy = libvirtComputingResourceSpy.createTermPolicy();

        String xml = "<terms>\n" + termPolicy.toString() + "</terms>";
        Document domainDoc = parse(xml);

        assertXpath(domainDoc, "/terms/on_reboot/text()", "restart");
        assertXpath(domainDoc, "/terms/on_poweroff/text()", "destroy");
        assertXpath(domainDoc, "/terms/on_crash/text()", "destroy");
    }

    @Test
    public void testCreateFeaturesDef() {
        VirtualMachineTO to = createDefaultVM(false);
        FeaturesDef featuresDef = libvirtComputingResourceSpy.createFeaturesDef(null, false, false);

        String xml = "<domain>" + featuresDef.toString() + "</domain>";
        Document domainDoc = parse(xml);

        verifyFeatures(domainDoc);
    }

    @Test
    public void testCreateFeaturesDefWithUefi() {
        VirtualMachineTO to = createDefaultVM(false);
        HashMap<String, String> extraConfig = new HashMap<>();
        extraConfig.put(GuestDef.BootType.UEFI.toString(), "");

        FeaturesDef featuresDef = libvirtComputingResourceSpy.createFeaturesDef(extraConfig, true, true);

        String xml = "<domain>" + featuresDef.toString() + "</domain>";
        Document domainDoc = parse(xml);

        verifyFeatures(domainDoc);
    }

    @Test
    public void testCreateWatchDog() {
        WatchDogDef watchDogDef = libvirtComputingResourceSpy.createWatchDogDef();
        verifyWatchDogDevices(parse(watchDogDef.toString()), "");
    }

    @Test
    public void testCreateArm64UsbDef() {
        DevicesDef devicesDef = new DevicesDef();

        libvirtComputingResourceSpy.createArm64UsbDef(devicesDef);
        Document domainDoc = parse(devicesDef.toString());

        assertXpath(domainDoc, "/devices/controller/@type", "usb");
        assertXpath(domainDoc, "/devices/controller/@model", "qemu-xhci");
        assertXpath(domainDoc, "/devices/controller/address/@type", "pci");
        assertNodeExists(domainDoc, "/devices/input[@type='keyboard']");
        assertNodeExists(domainDoc, "/devices/input[@bus='usb']");
        assertNodeExists(domainDoc, "/devices/input[@type='mouse']");
        assertNodeExists(domainDoc, "/devices/input[@bus='usb']");
    }

    @Test
    public void testCreateInputDef() {
        InputDef inputDef = libvirtComputingResourceSpy.createTabletInputDef();
        verifyTabletInputDevice(parse(inputDef.toString()), "");
    }

    @Test
    public void testCreateGraphicDef() {
        VirtualMachineTO to = createDefaultVM(false);
        GraphicDef graphicDef = libvirtComputingResourceSpy.createGraphicDef(to);
        verifyGraphicsDevices(to, parse(graphicDef.toString()), "");
    }

    @Test
    public void testCreateChannelDef() {
        VirtualMachineTO to = createDefaultVM(false);
        ChannelDef channelDef = libvirtComputingResourceSpy.createChannelDef(to);
        verifyChannelDevices(to, parse(channelDef.toString()), "");
    }

    @Test
    public void testCreateSCSIDef() {
        VirtualMachineTO to = createDefaultVM(false);

        SCSIDef scsiDef = libvirtComputingResourceSpy.createSCSIDef((short)0, to.getCpus(), false);
        Document domainDoc = parse(scsiDef.toString());
        verifyScsi(to, domainDoc, "");
    }

    @Test
    public void testCreateConsoleDef() {
        VirtualMachineTO to = createDefaultVM(false);
        ConsoleDef consoleDef = libvirtComputingResourceSpy.createConsoleDef();
        verifyConsoleDevices(parse(consoleDef.toString()), "");
    }

    @Test
    public void testCreateVideoDef() {
        VirtualMachineTO to = createDefaultVM(false);
        libvirtComputingResourceSpy.videoRam = 200;
        libvirtComputingResourceSpy.videoHw = "vGPU";

        VideoDef videoDef = libvirtComputingResourceSpy.createVideoDef(to);
        Document domainDoc = parse(videoDef.toString());
        assertXpath(domainDoc, "/video/model/@type", "vGPU");
        assertXpath(domainDoc, "/video/model/@vram", "200");
    }

    @Test
    public void testCreateRngDef() {
        VirtualMachineTO to = createDefaultVM(false);
        RngDef rngDef = libvirtComputingResourceSpy.createRngDef();
        Document domainDoc = parse(rngDef.toString());

        assertXpath(domainDoc, "/rng/@model", "virtio");
        assertXpath(domainDoc, "/rng/rate/@period", "1000");
        assertXpath(domainDoc, "/rng/rate/@bytes", "2048");
        assertXpath(domainDoc, "/rng/backend/@model", "random");
        assertXpath(domainDoc, "/rng/backend/text()", "/dev/random");
    }

    @Test
    public void testCreateSerialDef() {
        VirtualMachineTO to = createDefaultVM(false);
        SerialDef serialDef = libvirtComputingResourceSpy.createSerialDef();
        verifySerialDevices(parse(serialDef.toString()), "");
    }

    private VirtualMachineTO createDefaultVM(boolean limitCpuUse) {
        int id = random.nextInt(65534);
        String name = "test-instance-1";

        int cpus = random.nextInt(2) + 1;
        int minSpeed = 1024;
        int maxSpeed = 2048;
        int minRam = 256 * 1024;
        int maxRam = 512 * 1024;

        String os = "Ubuntu";
        String vncAddr = "";
        String vncPassword = "mySuperSecretPassword";

        final VirtualMachineTO to = new VirtualMachineTO(id, name, VirtualMachine.Type.User, cpus, minSpeed, maxSpeed, minRam, maxRam, BootloaderType.HVM, os, false, limitCpuUse,
                vncPassword);
        to.setArch("x86_64");
        to.setVncAddr(vncAddr);
        to.setUuid("b0f0a72d-7efb-3cad-a8ff-70ebf30b3af9");
        to.setVcpuMaxLimit(cpus + 1);

        return to;
    }

    private void verifyGuestResourceDef(GuestResourceDef guestResourceDef, VirtualMachineTO to) {
        String xml = "<domain>" + guestResourceDef.toString() + "</domain>";
        Document domainDoc = parse(xml);

        String minRam = String.valueOf(to.getMinRam() / 1024);
        verifyMemory(to, domainDoc, minRam);
        assertNodeExists(domainDoc, "/domain/vcpu");
        verifyMemballoonDevices(domainDoc);
        verifyVcpu(to, domainDoc);
    }

    private void verifyVm(VirtualMachineTO to, LibvirtVMDef vm) {
        Document domainDoc = parse(vm.toString());
        verifyHeader(domainDoc, vm.getHvsType(), to.getName(), to.getUuid(), to.getOs());
        verifyFeatures(domainDoc);
        verifyClock(domainDoc);
        verifySerialDevices(domainDoc, "/domain/devices");
        verifyGraphicsDevices(to, domainDoc, "/domain/devices");
        verifyConsoleDevices(domainDoc, "/domain/devices");
        verifyTabletInputDevice(domainDoc, "/domain/devices");
        verifyChannelDevices(to, domainDoc, "/domain/devices");

        String minRam = String.valueOf(to.getMinRam() / 1024);
        verifyMemory(to, domainDoc, minRam);
        assertNodeExists(domainDoc, "/domain/cpu");

        verifyMemballoonDevices(domainDoc);
        verifyVcpu(to, domainDoc);
        verifyOsType(domainDoc);
        verifyOsBoot(domainDoc);
        verifyPoliticOn_(domainDoc);
        verifyWatchDogDevices(domainDoc, "/domain/devices");
    }

    private void verifyMemballoonDevices(Document domainDoc) {
        assertXpath(domainDoc, "/domain/devices/memballoon/@model", "virtio");
    }

    private void verifyVcpu(VirtualMachineTO to, Document domainDoc) {
        assertXpath(domainDoc, "/domain/cpu/numa/cell/@cpus", String.format("0-%s", to.getVcpuMaxLimit() - 1));
        assertXpath(domainDoc, "/domain/vcpu/@current", String.valueOf(to.getCpus()));
        assertXpath(domainDoc, "/domain/vcpu/text()", String.valueOf(to.getVcpuMaxLimit()));
    }

    private void verifyMemory(VirtualMachineTO to, Document domainDoc, String minRam) {
        assertXpath(domainDoc, "/domain/maxMemory/text()", String.valueOf( to.getMaxRam() / 1024 ));
        assertXpath(domainDoc, "/domain/memory/text()",minRam);
        assertXpath(domainDoc, "/domain/cpu/numa/cell/@memory", minRam);
        assertXpath(domainDoc, "/domain/currentMemory/text()", minRam);
    }

    private void verifyWatchDogDevices(Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/watchdog/@model", "i6300esb");
        assertXpath(domainDoc, prefix + "/watchdog/@action", "none");
    }

    private void verifyChannelDevices(VirtualMachineTO to, Document domainDoc, String prefix) {
        assertNodeExists(domainDoc, prefix + "/channel");
        assertXpath(domainDoc, prefix + "/channel/@type", ChannelDef.ChannelType.UNIX.toString());

        /*
           The configure() method of LibvirtComputingResource has not been called, so the default path for the sockets
           hasn't been initialized. That's why we check for 'null'

           Calling configure is also not possible since that looks for certain files on the system which are not present
           during testing
         */
        assertXpath(domainDoc, prefix + "/channel/source/@path", "/var/run/qemu/" + to.getName() + ".org.qemu.guest_agent.0");
        assertXpath(domainDoc, prefix + "/channel/target/@name", "org.qemu.guest_agent.0");
    }

    private void verifyTabletInputDevice(Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/input/@type", "tablet");
        assertXpath(domainDoc, prefix + "/input/@bus", "usb");
    }

    private void verifyConsoleDevices(Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/console/@type", "pty");
        assertXpath(domainDoc, prefix + "/console/target/@port", "0");
    }

    private void verifyScsi(VirtualMachineTO to, Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/controller/@type", "scsi");
        assertXpath(domainDoc, prefix + "/controller/@model", "virtio-scsi");
        assertXpath(domainDoc, prefix + "/controller/address/@type", "pci");
        assertXpath(domainDoc, prefix + "/controller/driver/@queues", String.valueOf(to.getCpus()));
    }

    private void verifyClock(Document domainDoc) {
        assertXpath(domainDoc, "/domain/clock/@offset", "utc");
    }

    private void verifyGraphicsDevices(VirtualMachineTO to, Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/graphics/@type", "vnc");
        assertXpath(domainDoc, prefix + "/graphics/@listen", to.getVncAddr());
        assertXpath(domainDoc, prefix + "/graphics/@autoport", "yes");
        assertXpath(domainDoc, prefix + "/graphics/@passwd", StringUtils.truncate(to.getVncPassword(), 8));
    }

    private void verifySerialDevices(Document domainDoc, String prefix) {
        assertXpath(domainDoc, prefix + "/serial/@type", "pty");
        assertXpath(domainDoc, prefix + "/serial/target/@port", "0");
    }

    private void verifyOsBoot(Document domainDoc) {
        assertNodeExists(domainDoc, "/domain/os/boot[@dev='cdrom']");
        assertNodeExists(domainDoc, "/domain/os/boot[@dev='hd']");
    }

    private void verifyOsType(Document domainDoc) {
        assertXpath(domainDoc, "/domain/os/type/@machine", "s390x".equals(System.getProperty("os.arch")) ? "s390-ccw-virtio" : "pc");
        assertXpath(domainDoc, "/domain/os/type/text()", "hvm");
    }

    private void verifyPoliticOn_(Document domainDoc) {
        assertXpath(domainDoc, "/domain/on_reboot/text()", "restart");
        assertXpath(domainDoc, "/domain/on_poweroff/text()", "destroy");
        assertXpath(domainDoc, "/domain/on_crash/text()", "destroy");
    }

    private void verifyFeatures(Document domainDoc) {
        assertNodeExists(domainDoc, "/domain/features/pae");
        assertNodeExists(domainDoc, "/domain/features/apic");
        assertNodeExists(domainDoc, "/domain/features/acpi");
    }

    private void verifyHeader(Document domainDoc, String hvsType, String name, String uuid, String os) {
        assertXpath(domainDoc, "/domain/@type", hvsType);
        assertXpath(domainDoc, "/domain/name/text()", name);
        assertXpath(domainDoc, "/domain/uuid/text()", uuid);
        assertXpath(domainDoc, "/domain/description/text()", os);
    }

    private void verifyDevices(DevicesDef devicesDef, VirtualMachineTO to) {
        Document domainDoc = parse(devicesDef.toString());

        verifyWatchDogDevices(domainDoc, "/devices");
        verifyConsoleDevices(domainDoc, "/devices");
        verifySerialDevices(domainDoc, "/devices");
        verifyGraphicsDevices(to, domainDoc, "/devices");
        verifyChannelDevices(to, domainDoc, "/devices");
        verifyTabletInputDevice(domainDoc, "/devices");
    }

    private void verifySysInfo(GuestDef guestDef, String type, String uuid, String machine) {
        // Need put <guestdef> because the string of guestdef generate two root element in XML, raising a error in parse.
        String xml = "<guestdef>\n" + guestDef.toString() + "</guestdef>";

        Document domainDoc = parse(xml);
        assertXpath(domainDoc, "/guestdef/sysinfo/@type", type);
        assertNodeExists(domainDoc, "/guestdef/sysinfo/system/entry[@name='manufacturer']");
        assertNodeExists(domainDoc, "/guestdef/sysinfo/system/entry[@name='product']");
        assertXpath(domainDoc, "/guestdef/sysinfo/system/entry[@name='uuid']/text()", uuid);
        assertXpath(domainDoc, "/guestdef/os/type/@machine", machine);
    }

    static Document parse(final String input) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(input.getBytes()));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new IllegalArgumentException("Cloud not parse: "+input, e);
        }
    }

    static void assertNodeExists(final Document doc, final String xPathExpr) {
        try {
            Assert.assertNotNull(XPathFactory.newInstance().newXPath()
                    .evaluate(xPathExpr, doc, XPathConstants.NODE));
        } catch (final XPathExpressionException e) {
            Assert.fail(e.getMessage());
        }
    }

    static void assertXpath(final Document doc, final String xPathExpr,
                            final String expected) {
        try {
            Assert.assertEquals(expected, XPathFactory.newInstance().newXPath()
                    .evaluate(xPathExpr, doc));
        } catch (final XPathExpressionException e) {
            Assert.fail("Could not evaluate xpath" + xPathExpr + ":"
                    + e.getMessage());
        }
    }

    @Test
    public void testGetNicStats() {
        //this test is only working on linux because of the loopback interface name
        //also the tested code seems to work only on linux
        if(SystemUtils.IS_OS_LINUX) {
            final LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();
            final Pair<Double, Double> stats = libvirtComputingResource.getNicStats("lo");
            assertNotNull(stats);
        } // else SUCCESS
    }

    @Test
    public void diskUuidToSerialTest() {
        final String uuid = "38400000-8cf0-11bd-b24e-10b96e4ef00d";
        final String expected = "384000008cf011bdb24e";
        final LibvirtComputingResource lcr = new LibvirtComputingResource();
        Assert.assertEquals(expected, lcr.diskUuidToSerial(uuid));
    }

    @Test
    public void testUUID() {
        String uuid = "1";
        final LibvirtComputingResource lcr = new LibvirtComputingResource();
        uuid = lcr.getUuid(uuid);
        assertNotEquals("1", uuid);

        final String oldUuid = UUID.randomUUID().toString();
        uuid = oldUuid;
        uuid = lcr.getUuid(uuid);
        assertEquals(uuid, oldUuid);
    }

    /*
     * New Tests
     */

    @Test
    public void testStopCommandNoCheck() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final StopCommand command = new StopCommand(vmName, false, false);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStopCommandCheckVmNOTRunning() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_SHUTDOWN;
        info.state = state;

        final String vmName = "Test";
        final StopCommand command = new StopCommand(vmName, false, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(conn.domainLookupByName(command.getVmName())).thenReturn(vm);

            when(vm.getInfo()).thenReturn(info);

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(2)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStopCommandCheckException1() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_RUNNING;
        info.state = state;

        final String vmName = "Test";
        final StopCommand command = new StopCommand(vmName, false, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(2)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStopCommandCheckVmRunning() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_RUNNING;
        info.state = state;

        final String vmName = "Test";
        final StopCommand command = new StopCommand(vmName, false, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(conn.domainLookupByName(command.getVmName())).thenReturn(vm);

            when(vm.getInfo()).thenReturn(info);

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGetVmStatsCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final String uuid = "e8d6b4d0-bc6d-4613-b8bb-cb9e0600f3c6";
        final List<String> vms = new ArrayList<String>();
        vms.add(vmName);

        final GetVmStatsCommand command = new GetVmStatsCommand(vms, uuid, "hostname");

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGetVmDiskStatsCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final String uuid = "e8d6b4d0-bc6d-4613-b8bb-cb9e0600f3c6";
        final List<String> vms = new ArrayList<String>();
        vms.add(vmName);

        final GetVmDiskStatsCommand command = new GetVmDiskStatsCommand(vms, uuid, "hostname");

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnection()).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnection();
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetVmDiskStatsCommandException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final String uuid = "e8d6b4d0-bc6d-4613-b8bb-cb9e0600f3c6";
        final List<String> vms = new ArrayList<String>();
        vms.add(vmName);

        final GetVmDiskStatsCommand command = new GetVmDiskStatsCommand(vms, uuid, "hostname");

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnection()).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnection();
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRebootCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootCommand command = new RebootCommand(vmName, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRebootCommandException1() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootCommand command = new RebootCommand(vmName, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRebootCommandError() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootCommand command = new RebootCommand(vmName, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(libvirtComputingResourceMock.rebootVM(conn, command.getVmName())).thenReturn("error");
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRebootCommandException2() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootCommand command = new RebootCommand(vmName, true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(libvirtComputingResourceMock.rebootVM(conn, command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRebootRouterCommand() {
        final VirtualRoutingResource routingResource = Mockito.mock(VirtualRoutingResource.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootRouterCommand command = new RebootRouterCommand(vmName, "127.0.0.1");

        when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(routingResource);
        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVirtRouterResource();

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRebootRouterCommandConnect() {
        final VirtualRoutingResource routingResource = Mockito.mock(VirtualRoutingResource.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final RebootRouterCommand command = new RebootRouterCommand(vmName, "127.0.0.1");

        when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(routingResource);
        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(routingResource.connect(command.getPrivateIpAddress())).thenReturn(true);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVirtRouterResource();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGetHostStatsCommand() {
        // A bit difficult to test due to the logger being passed and the parser itself relying on the connection.
        // Have to spend some more time afterwards in order to refactor the wrapper itself.
        final CPUStat cpuStat = Mockito.mock(CPUStat.class);
        final MemStat memStat = Mockito.mock(MemStat.class);

        final String uuid = "e8d6b4d0-bc6d-4613-b8bb-cb9e0600f3c6";
        final GetHostStatsCommand command = new GetHostStatsCommand(uuid, "summer", 1l);

        when(libvirtComputingResourceMock.getCPUStat()).thenReturn(cpuStat);
        when(libvirtComputingResourceMock.getMemStat()).thenReturn(memStat);
        when(libvirtComputingResourceMock.getNicStats(nullable(String.class))).thenReturn(new Pair<Double, Double>(1.0d, 1.0d));
        when(cpuStat.getCpuUsedPercent()).thenReturn(0.5d);
        when(memStat.getAvailable()).thenReturn(1500L);
        when(memStat.getTotal()).thenReturn(15000L);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getCPUStat();
        verify(libvirtComputingResourceMock, times(1)).getMemStat();
        verify(cpuStat, times(1)).getCpuUsedPercent();
        verify(memStat, times(1)).getAvailable();
        verify(memStat, times(1)).getTotal();
    }

    @Test
    public void testCheckHealthCommand() {
        final CheckHealthCommand command = new CheckHealthCommand();

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testPrepareForMigrationCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        final KVMStoragePoolManager storagePoolManager = Mockito.mock(KVMStoragePoolManager.class);
        final NicTO nicTO = Mockito.mock(NicTO.class);
        final DiskTO diskTO = Mockito.mock(DiskTO.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);

        final PrepareForMigrationCommand command = new PrepareForMigrationCommand(vm);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vm.getName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(vm.getNics()).thenReturn(new NicTO[]{nicTO});
        when(vm.getDisks()).thenReturn(new DiskTO[]{diskTO});

        when(nicTO.getType()).thenReturn(TrafficType.Guest);
        when(diskTO.getType()).thenReturn(Volume.Type.ISO);

        when(libvirtComputingResourceMock.getVifDriver(nicTO.getType(), nicTO.getName())).thenReturn(vifDriver);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolManager);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vm.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(vm, times(1)).getNics();
        verify(vm, times(1)).getDisks();
        verify(diskTO, times(1)).getType();
    }

    @Test
    public void testPrepareForMigrationCommandMigration() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        final KVMStoragePoolManager storagePoolManager = Mockito.mock(KVMStoragePoolManager.class);
        final NicTO nicTO = Mockito.mock(NicTO.class);
        final DiskTO diskTO = Mockito.mock(DiskTO.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);

        final PrepareForMigrationCommand command = new PrepareForMigrationCommand(vm);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vm.getName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(vm.getNics()).thenReturn(new NicTO[]{nicTO});
        when(vm.getDisks()).thenReturn(new DiskTO[]{diskTO});

        when(nicTO.getType()).thenReturn(TrafficType.Guest);
        when(diskTO.getType()).thenReturn(Volume.Type.ISO);

        when(libvirtComputingResourceMock.getVifDriver(nicTO.getType(), nicTO.getName())).thenReturn(vifDriver);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolManager);
        when(storagePoolManager.connectPhysicalDisksViaVmSpec(vm, true)).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vm.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(vm, times(1)).getNics();
        verify(vm, times(1)).getDisks();
        verify(diskTO, times(1)).getType();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPrepareForMigrationCommandLibvirtException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        final KVMStoragePoolManager storagePoolManager = Mockito.mock(KVMStoragePoolManager.class);
        final NicTO nicTO = Mockito.mock(NicTO.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);

        final PrepareForMigrationCommand command = new PrepareForMigrationCommand(vm);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vm.getName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(vm.getNics()).thenReturn(new NicTO[]{nicTO});

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolManager);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vm.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(vm, times(1)).getNics();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPrepareForMigrationCommandURISyntaxException() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        final KVMStoragePoolManager storagePoolManager = Mockito.mock(KVMStoragePoolManager.class);
        final NicTO nicTO = Mockito.mock(NicTO.class);
        final DiskTO volume = Mockito.mock(DiskTO.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);

        final PrepareForMigrationCommand command = new PrepareForMigrationCommand(vm);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vm.getName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(vm.getNics()).thenReturn(new NicTO[]{nicTO});
        when(vm.getDisks()).thenReturn(new DiskTO[]{volume});

        when(nicTO.getType()).thenReturn(TrafficType.Guest);
        when(volume.getType()).thenReturn(Volume.Type.ISO);

        when(libvirtComputingResourceMock.getVifDriver(nicTO.getType(), nicTO.getName())).thenReturn(vifDriver);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolManager);
        try {
            when(libvirtComputingResourceMock.getVolumePath(conn, volume)).thenThrow(URISyntaxException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vm.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(vm, times(1)).getNics();
        verify(vm, times(1)).getDisks();
        verify(volume, times(1)).getType();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPrepareForMigrationCommandInternalErrorException() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        final KVMStoragePoolManager storagePoolManager = Mockito.mock(KVMStoragePoolManager.class);
        final NicTO nicTO = Mockito.mock(NicTO.class);
        final DiskTO volume = Mockito.mock(DiskTO.class);

        final PrepareForMigrationCommand command = new PrepareForMigrationCommand(vm);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vm.getName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(vm.getNics()).thenReturn(new NicTO[]{nicTO});
        when(nicTO.getType()).thenReturn(TrafficType.Guest);

        BDDMockito.given(libvirtComputingResourceMock.getVifDriver(nicTO.getType(), nicTO.getName())).willAnswer(invocationOnMock -> {throw new InternalErrorException("Exception Occurred");});
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolManager);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vm.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(vm, times(1)).getNics();
    }

    @Test
    public void testMigrateCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final Connect dconn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final String destIp = "127.0.0.100";
        final boolean isWindows = false;
        final VirtualMachineTO vmTO = Mockito.mock(VirtualMachineTO.class);
        final boolean executeInSequence = false;
        DiskTO[] diskTOS = new DiskTO[]{};
        final MigrateCommand command = new MigrateCommand(vmName, destIp, isWindows, vmTO, executeInSequence );

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(vmTO.getDisks()).thenReturn(diskTOS);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(libvirtUtilitiesHelper.retrieveQemuConnection("qemu+tcp://" + command.getDestinationIp() + "/system")).thenReturn(dconn);

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final InterfaceDef interfaceDef = Mockito.mock(InterfaceDef.class);
        final List<InterfaceDef> ifaces = new ArrayList<InterfaceDef>();
        ifaces.add(interfaceDef);

        when(libvirtComputingResourceMock.getInterfaces(conn, vmName)).thenReturn(ifaces);

        final DiskDef diskDef = Mockito.mock(DiskDef.class);
        final List<DiskDef> disks = new ArrayList<DiskDef>();
        disks.add(diskDef);

        when(libvirtComputingResourceMock.getDisks(conn, vmName)).thenReturn(disks);
        final Domain dm = Mockito.mock(Domain.class);
        try {
            when(conn.domainLookupByName(vmName)).thenReturn(dm);

            when(dm.getXMLDesc(1)).thenReturn("<domain type='kvm' id='3'>" + "  <devices>" + "    <graphics type='vnc' port='5900' autoport='yes' listen='10.10.10.1'>"
                    + "      <listen type='address' address='10.10.10.1'/>" + "    </graphics>" + "  </devices>" + "</domain>");
            when(dm.isPersistent()).thenReturn(1);
            doNothing().when(dm).undefine();

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final Exception e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
            verify(libvirtUtilitiesHelper, times(1)).retrieveQemuConnection("qemu+tcp://" + command.getDestinationIp() + "/system");
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        verify(libvirtComputingResourceMock, times(1)).getInterfaces(conn, vmName);
        verify(libvirtComputingResourceMock, times(1)).getDisks(conn, vmName);
        try {
            verify(conn, times(1)).domainLookupByName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        try {
            verify(dm, times(1)).getXMLDesc(8);
        } catch (final Throwable t) {
            try {
                verify(dm, times(1)).getXMLDesc(1);
            }
            catch (final LibvirtException e) {
                fail(e.getMessage());
            }
        }
    }

    @Test
    public void testPingTestHostIpCommand() {
        final PingTestCommand command = new PingTestCommand("127.0.0.1");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testPingTestPvtIpCommand() {
        final PingTestCommand command = new PingTestCommand("127.0.0.1", "127.0.0.1");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testPingOnlyOneIpCommand() {
        final PingTestCommand command = new PingTestCommand("127.0.0.1", null);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testCheckVirtualMachineCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo domainInfo = Mockito.mock(DomainInfo.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final CheckVirtualMachineCommand command = new CheckVirtualMachineCommand(vmName);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(conn.domainLookupByName(vmName)).thenReturn(vm);
            when(vm.getInfo()).thenReturn(domainInfo);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(libvirtComputingResourceMock.getVmState(conn, command.getVmName())).thenReturn(PowerState.PowerOn);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExceptionCheckVirtualMachineCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final CheckVirtualMachineCommand command = new CheckVirtualMachineCommand(vmName);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testReadyCommand() {
        final ReadyCommand command = new ReadyCommand(1l);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testAttachIsoCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final AttachIsoCommand command = new AttachIsoCommand(vmName, "/path", true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAttachIsoCommandLibvirtException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final AttachIsoCommand command = new AttachIsoCommand(vmName, "/path", true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAttachIsoCommandURISyntaxException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final AttachIsoCommand command = new AttachIsoCommand(vmName, "/path", true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            BDDMockito.given(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).willAnswer(invocationOnMock -> {throw new URISyntaxException("Exception trying to get connection by VM name", vmName);});
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAttachIsoCommandInternalErrorException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String vmName = "Test";
        final AttachIsoCommand command = new AttachIsoCommand(vmName, "/path", true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            BDDMockito.given(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).willAnswer(invocationOnMock -> {throw new InternalErrorException("Exception Occurred");});
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testWatchConsoleProxyLoadCommand() {
        final int interval = 0;
        final long proxyVmId = 0l;
        final String proxyVmName = "host";
        final String proxyManagementIp = "127.0.0.1";
        final int proxyCmdPort = 0;

        final WatchConsoleProxyLoadCommand command = new WatchConsoleProxyLoadCommand(interval, proxyVmId, proxyVmName, proxyManagementIp, proxyCmdPort);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testCheckConsoleProxyLoadCommand() {
        final long proxyVmId = 0l;
        final String proxyVmName = "host";
        final String proxyManagementIp = "127.0.0.1";
        final int proxyCmdPort = 0;

        final CheckConsoleProxyLoadCommand command = new CheckConsoleProxyLoadCommand(proxyVmId, proxyVmName, proxyManagementIp, proxyCmdPort);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testGetVncPortCommand() {
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final GetVncPortCommand command = new GetVncPortCommand(1l, "host");

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetVncPortCommandLibvirtException() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final GetVncPortCommand command = new GetVncPortCommand(1l, "host");

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testModifySshKeysCommand() {
        final ModifySshKeysCommand command = new ModifySshKeysCommand("", "");

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);

        when(libvirtUtilitiesHelper.retrieveSshKeysPath()).thenReturn("/path/keys");
        when(libvirtUtilitiesHelper.retrieveSshPubKeyPath()).thenReturn("/path/pub/keys");
        when(libvirtUtilitiesHelper.retrieveSshPrvKeyPath()).thenReturn("/path/pvt/keys");

        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getTimeout();
    }

    @Test
    public void testMaintainCommand() {
        final MaintainCommand command = new MaintainCommand();

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testCreateCommandNoTemplate() {
        final DiskProfile diskCharacteristics = Mockito.mock(DiskProfile.class);
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final boolean executeInSequence = false;

        final CreateCommand command = new CreateCommand(diskCharacteristics, pool, executeInSequence );

        final KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(poolManager);
        when(poolManager.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);

        when(primary.createPhysicalDisk(diskCharacteristics.getPath(), diskCharacteristics.getProvisioningType(), diskCharacteristics.getSize(), null)).thenReturn(vol);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(poolManager, times(1)).getStoragePool(pool.getType(), pool.getUuid());
    }

    @Test
    public void testCreateCommand() {
        final DiskProfile diskCharacteristics = Mockito.mock(DiskProfile.class);
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final String templateUrl = "http://template";
        final boolean executeInSequence = false;

        final CreateCommand command = new CreateCommand(diskCharacteristics, templateUrl, pool, executeInSequence );

        final KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(poolManager);
        when(poolManager.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);

        when(primary.getType()).thenReturn(StoragePoolType.CLVM);
        when(libvirtComputingResourceMock.templateToPrimaryDownload(command.getTemplateUrl(), primary, diskCharacteristics.getPath())).thenReturn(vol);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(poolManager, times(1)).getStoragePool(pool.getType(), pool.getUuid());
    }

    @Test
    public void testCreateCommandCLVM() {
        final DiskProfile diskCharacteristics = Mockito.mock(DiskProfile.class);
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final String templateUrl = "http://template";
        final boolean executeInSequence = false;

        final CreateCommand command = new CreateCommand(diskCharacteristics, templateUrl, pool, executeInSequence );

        final KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);
        final KVMPhysicalDisk baseVol = Mockito.mock(KVMPhysicalDisk.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(poolManager);
        when(poolManager.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);

        when(primary.getPhysicalDisk(command.getTemplateUrl())).thenReturn(baseVol);
        when(poolManager.createDiskFromTemplate(baseVol, diskCharacteristics.getPath(), diskCharacteristics.getProvisioningType(), primary, baseVol.getSize(), 0,null)).thenReturn(vol);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(poolManager, times(1)).getStoragePool(pool.getType(), pool.getUuid());
    }

    @Test
    public void testDestroyCommand() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final Volume volume = Mockito.mock(Volume.class);
        final String vmName = "Test";

        final DestroyCommand command = new DestroyCommand(pool, volume, vmName);

        final KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final VolumeTO vol = command.getVolume();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(poolManager);
        when(poolManager.getStoragePool(vol.getPoolType(), vol.getPoolUuid())).thenReturn(primary);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(poolManager, times(1)).getStoragePool(vol.getPoolType(), vol.getPoolUuid());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDestroyCommandError() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final Volume volume = Mockito.mock(Volume.class);
        final String vmName = "Test";

        final DestroyCommand command = new DestroyCommand(pool, volume, vmName);

        final KVMStoragePoolManager poolManager = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final VolumeTO vol = command.getVolume();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(poolManager);
        when(poolManager.getStoragePool(vol.getPoolType(), vol.getPoolUuid())).thenReturn(primary);

        when(primary.deletePhysicalDisk(vol.getPath(), null)).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(poolManager, times(1)).getStoragePool(vol.getPoolType(), vol.getPoolUuid());
    }

    @Test(expected = NullPointerException.class)
    public void testPrimaryStorageDownloadCommandNOTemplateDisk() {
        final StoragePool pool = Mockito.mock(StoragePool.class);

        final List<KVMPhysicalDisk> disks = new ArrayList<KVMPhysicalDisk>();

        final String name = "Test";
        final String url = "http://template/";
        final ImageFormat format = ImageFormat.QCOW2;
        final long accountId = 1l;
        final int wait = 0;
        final PrimaryStorageDownloadCommand command = new PrimaryStorageDownloadCommand(name, url, format, accountId, pool, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk tmplVol = Mockito.mock(KVMPhysicalDisk.class);
        final KVMPhysicalDisk primaryVol = Mockito.mock(KVMPhysicalDisk.class);

        final KVMPhysicalDisk disk = new KVMPhysicalDisk("/path", "disk.qcow2", primaryPool);
        disks.add(disk);

        final int index = url.lastIndexOf("/");
        final String mountpoint = url.substring(0, index);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(mountpoint)).thenReturn(secondaryPool);
        when(secondaryPool.listPhysicalDisks()).thenReturn(disks);
        when(storagePoolMgr.getStoragePool(command.getPool().getType(), command.getPoolUuid())).thenReturn(primaryPool);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testPrimaryStorageDownloadCommandNOTemplateNODisk() {
        final StoragePool pool = Mockito.mock(StoragePool.class);

        final List<KVMPhysicalDisk> disks = new ArrayList<KVMPhysicalDisk>();

        final String name = "Test";
        final String url = "http://template/";
        final ImageFormat format = ImageFormat.QCOW2;
        final long accountId = 1l;
        final int wait = 0;
        final PrimaryStorageDownloadCommand command = new PrimaryStorageDownloadCommand(name, url, format, accountId, pool, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk tmplVol = Mockito.mock(KVMPhysicalDisk.class);
        final KVMPhysicalDisk primaryVol = Mockito.mock(KVMPhysicalDisk.class);

        final int index = url.lastIndexOf("/");
        final String mountpoint = url.substring(0, index);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(mountpoint)).thenReturn(secondaryPool);
        when(secondaryPool.listPhysicalDisks()).thenReturn(disks);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testPrimaryStorageDownloadCommandNOTemplateNOQcow2() {
        final StoragePool pool = Mockito.mock(StoragePool.class);

        final List<KVMPhysicalDisk> disks = new ArrayList<KVMPhysicalDisk>();
        final List<KVMPhysicalDisk> spiedDisks = Mockito.spy(disks);

        final String name = "Test";
        final String url = "http://template/";
        final ImageFormat format = ImageFormat.QCOW2;
        final long accountId = 1l;
        final int wait = 0;
        final PrimaryStorageDownloadCommand command = new PrimaryStorageDownloadCommand(name, url, format, accountId, pool, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk tmplVol = Mockito.mock(KVMPhysicalDisk.class);
        final KVMPhysicalDisk primaryVol = Mockito.mock(KVMPhysicalDisk.class);

        final int index = url.lastIndexOf("/");
        final String mountpoint = url.substring(0, index);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(mountpoint)).thenReturn(secondaryPool);
        when(secondaryPool.listPhysicalDisks()).thenReturn(spiedDisks);
        when(spiedDisks.isEmpty()).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test(expected = NullPointerException.class)
    public void testPrimaryStorageDownloadCommandTemplateNoDisk() {
        final StoragePool pool = Mockito.mock(StoragePool.class);

        final String name = "Test";
        final String url = "http://template/template.qcow2";
        final ImageFormat format = ImageFormat.VHD;
        final long accountId = 1l;
        final int wait = 0;
        final PrimaryStorageDownloadCommand command = new PrimaryStorageDownloadCommand(name, url, format, accountId, pool, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk tmplVol = Mockito.mock(KVMPhysicalDisk.class);
        final KVMPhysicalDisk primaryVol = Mockito.mock(KVMPhysicalDisk.class);

        final int index = url.lastIndexOf("/");
        final String mountpoint = url.substring(0, index);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(mountpoint)).thenReturn(secondaryPool);
        when(secondaryPool.getPhysicalDisk("template.qcow2")).thenReturn(tmplVol);
        when(storagePoolMgr.getStoragePool(command.getPool().getType(), command.getPoolUuid())).thenReturn(primaryPool);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePool(command.getPool().getType(), command.getPoolUuid());
    }

    @Test
    public void testGetStorageStatsCommand() {
        final DataStoreTO store = Mockito.mock(DataStoreTO.class);
        final GetStorageStatsCommand command = new GetStorageStatsCommand(store );

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(command.getPooltype(), command.getStorageId(), true)).thenReturn(secondaryPool);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePool(command.getPooltype(), command.getStorageId(), true);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetStorageStatsCommandException() {
        final DataStoreTO store = Mockito.mock(DataStoreTO.class);
        final GetStorageStatsCommand command = new GetStorageStatsCommand(store );

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testUpgradeSnapshotCommand() {
        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final String secondaryStoragePoolURL = "url";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final Long templateId = 1l;
        final Long tmpltAccountId = 1l;
        final String volumePath = "/opt/path";
        final String snapshotUuid = "uuid:/8edb1156-a851-4914-afc6-468ee52ac861/";
        final String snapshotName = "uuid:/8edb1156-a851-4914-afc6-468ee52ac861/";
        final String version = "1";

        final UpgradeSnapshotCommand command = new UpgradeSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, templateId, tmpltAccountId, volumePath, snapshotUuid, snapshotName, version);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testDeleteStoragePoolCommand() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);

        final DeleteStoragePoolCommand command = new DeleteStoragePoolCommand(storagePool);

        final StorageFilerTO pool = command.getPool();
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.deleteStoragePool(pool.getType(), pool.getUuid())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).deleteStoragePool(pool.getType(), pool.getUuid());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeleteStoragePoolCommandException() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);

        final DeleteStoragePoolCommand command = new DeleteStoragePoolCommand(storagePool);

        final StorageFilerTO pool = command.getPool();
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.deleteStoragePool(pool.getType(), pool.getUuid())).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).deleteStoragePool(pool.getType(), pool.getUuid());
    }

    @Test
    public void testOvsSetupBridgeCommand() {
        final String name = "Test";
        final Long hostId = 1l;
        final Long networkId = 1l;

        final OvsSetupBridgeCommand command = new OvsSetupBridgeCommand(name, hostId, networkId);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenReturn(true);
        when(libvirtComputingResourceMock.configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName())).thenReturn(true);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
        verify(libvirtComputingResourceMock, times(1)).configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName());
    }

    @Test
    public void testOvsSetupBridgeCommandFailure1() {
        final String name = "Test";
        final Long hostId = 1l;
        final Long networkId = 1l;

        final OvsSetupBridgeCommand command = new OvsSetupBridgeCommand(name, hostId, networkId);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenReturn(true);
        when(libvirtComputingResourceMock.configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName())).thenReturn(false);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
        verify(libvirtComputingResourceMock, times(1)).configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName());
    }

    @Test
    public void testOvsSetupBridgeCommandFailure2() {
        final String name = "Test";
        final Long hostId = 1l;
        final Long networkId = 1l;

        final OvsSetupBridgeCommand command = new OvsSetupBridgeCommand(name, hostId, networkId);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenReturn(false);
        when(libvirtComputingResourceMock.configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName())).thenReturn(true);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
        verify(libvirtComputingResourceMock, times(1)).configureTunnelNetwork(command.getNetworkId(), command.getHostId(),
                command.getBridgeName());
    }

    @Test
    public void testOvsDestroyBridgeCommand() {
        final String name = "Test";
        final Long hostId = 1l;
        final Long networkId = 1l;

        final OvsDestroyBridgeCommand command = new OvsDestroyBridgeCommand(networkId, name, hostId);

        when(libvirtComputingResourceMock.destroyTunnelNetwork(command.getBridgeName())).thenReturn(true);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).destroyTunnelNetwork(command.getBridgeName());
    }

    @Test
    public void testOvsDestroyBridgeCommandFailure() {
        final String name = "Test";
        final Long hostId = 1l;
        final Long networkId = 1l;

        final OvsDestroyBridgeCommand command = new OvsDestroyBridgeCommand(networkId, name, hostId);

        when(libvirtComputingResourceMock.destroyTunnelNetwork(command.getBridgeName())).thenReturn(false);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).destroyTunnelNetwork(command.getBridgeName());
    }

    @Test
    public void testOvsFetchInterfaceCommand() {
        final String label = "eth0";

        final OvsFetchInterfaceCommand command = new OvsFetchInterfaceCommand(label);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testOvsVpcPhysicalTopologyConfigCommand() {
        final Host[] hosts = null;
        final Tier[] tiers = null;
        final Vm[] vms = null;
        final String cidr = null;

        final OvsVpcPhysicalTopologyConfigCommand command = new OvsVpcPhysicalTopologyConfigCommand(hosts, tiers, vms, cidr);

        when(libvirtComputingResourceMock.getOvsTunnelPath()).thenReturn("/path");
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getOvsTunnelPath();
        verify(libvirtComputingResourceMock, times(1)).getTimeout();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = Exception.class)
    public void testOvsVpcPhysicalTopologyConfigCommandFailure() {
        final Host[] hosts = null;
        final Tier[] tiers = null;
        final Vm[] vms = null;
        final String cidr = null;

        final OvsVpcPhysicalTopologyConfigCommand command = new OvsVpcPhysicalTopologyConfigCommand(hosts, tiers, vms, cidr);

        when(libvirtComputingResourceMock.getOvsTunnelPath()).thenThrow(Exception.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getOvsTunnelPath();
    }

    @Test
    public void testOvsVpcRoutingPolicyConfigCommand() {
        final String id = null;
        final String cidr = null;
        final Acl[] acls = null;
        final com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand.Tier[] tiers = null;

        final OvsVpcRoutingPolicyConfigCommand command = new OvsVpcRoutingPolicyConfigCommand(id, cidr, acls, tiers);

        when(libvirtComputingResourceMock.getOvsTunnelPath()).thenReturn("/path");
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getOvsTunnelPath();
        verify(libvirtComputingResourceMock, times(1)).getTimeout();
    }

    @SuppressWarnings("unchecked")
    @Test(expected = Exception.class)
    public void testOvsVpcRoutingPolicyConfigCommandFailure() {
        final String id = null;
        final String cidr = null;
        final Acl[] acls = null;
        final com.cloud.agent.api.OvsVpcRoutingPolicyConfigCommand.Tier[] tiers = null;

        final OvsVpcRoutingPolicyConfigCommand command = new OvsVpcRoutingPolicyConfigCommand(id, cidr, acls, tiers);

        when(libvirtComputingResourceMock.getOvsTunnelPath()).thenThrow(Exception.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getOvsTunnelPath();
    }

    @Test
    public void testCreateStoragePoolCommand() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final CreateStoragePoolCommand command = new CreateStoragePoolCommand(true, pool);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testModifyStoragePoolCommand() {
        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final ModifyStoragePoolCommand command = new ModifyStoragePoolCommand(true, pool);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool kvmStoragePool = Mockito.mock(KVMStoragePool.class);


        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.createStoragePool(command.getPool().getUuid(), command.getPool().getHost(), command.getPool().getPort(), command.getPool().getPath(), command.getPool()
                .getUserInfo(), command.getPool().getType(), command.getDetails())).thenReturn(kvmStoragePool);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).createStoragePool(command.getPool().getUuid(), command.getPool().getHost(), command.getPool().getPort(), command.getPool().getPath(), command.getPool()
                .getUserInfo(), command.getPool().getType(), command.getDetails());
    }

    @Test
    public void testModifyStoragePoolCommandFailure() {
        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final ModifyStoragePoolCommand command = new ModifyStoragePoolCommand(true, pool);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.createStoragePool(command.getPool().getUuid(), command.getPool().getHost(), command.getPool().getPort(), command.getPool().getPath(), command.getPool()
                .getUserInfo(), command.getPool().getType(), command.getDetails())).thenReturn(null);


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).createStoragePool(command.getPool().getUuid(), command.getPool().getHost(), command.getPool().getPort(), command.getPool().getPath(), command.getPool()
                .getUserInfo(), command.getPool().getType(), command.getDetails());
    }

    @Test
    public void testCleanupNetworkRulesCmd() {
        final CleanupNetworkRulesCmd command = new CleanupNetworkRulesCmd(1);

        when(libvirtComputingResourceMock.cleanupRules()).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).cleanupRules();
    }

    @Test
    public void testNetworkRulesVmSecondaryIpCommand() {
        final String vmName = "Test";
        final String vmMac = "00:00:00:00";
        final String secondaryIp = "127.0.0.1";
        final boolean action = true;

        final NetworkRulesVmSecondaryIpCommand command = new NetworkRulesVmSecondaryIpCommand(vmName, vmMac, secondaryIp, action );

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        when(libvirtComputingResourceMock.configureNetworkRulesVMSecondaryIP(conn, command.getVmName(), command.getVmMac(), command.getVmSecIp(), command.getAction())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        verify(libvirtComputingResourceMock, times(1)).configureNetworkRulesVMSecondaryIP(conn, command.getVmName(), command.getVmMac(), command.getVmSecIp(), command.getAction());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNetworkRulesVmSecondaryIpCommandFailure() {
        final String vmName = "Test";
        final String vmMac = "00:00:00:00";
        final String secondaryIp = "127.0.0.1";
        final boolean action = true;

        final NetworkRulesVmSecondaryIpCommand command = new NetworkRulesVmSecondaryIpCommand(vmName, vmMac, secondaryIp, action );

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
    }

    @Test
    public void testNetworkRulesSystemVmCommand() {
        final String vmName = "Test";
        final Type type = Type.SecondaryStorageVm;

        final NetworkRulesSystemVmCommand command = new NetworkRulesSystemVmCommand(vmName, type);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        when(libvirtComputingResourceMock.configureDefaultNetworkRulesForSystemVm(conn, command.getVmName())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        verify(libvirtComputingResourceMock, times(1)).configureDefaultNetworkRulesForSystemVm(conn, command.getVmName());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNetworkRulesSystemVmCommandFailure() {
        final String vmName = "Test";
        final Type type = Type.SecondaryStorageVm;

        final NetworkRulesSystemVmCommand command = new NetworkRulesSystemVmCommand(vmName, type);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
    }

    @Test
    public void testCheckSshCommand() {
        final String instanceName = "Test";
        final String ip = "127.0.0.1";
        final int port = 22;

        final CheckSshCommand command = new CheckSshCommand(instanceName, ip, port);

        final VirtualRoutingResource virtRouterResource = Mockito.mock(VirtualRoutingResource.class);

        final String privateIp = command.getIp();
        final int cmdPort = command.getPort();

        when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(virtRouterResource);
        when(virtRouterResource.connect(privateIp, cmdPort)).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVirtRouterResource();
        verify(virtRouterResource, times(1)).connect(privateIp, cmdPort);
    }

    @Test
    public void testCheckSshCommandFailure() {
        final String instanceName = "Test";
        final String ip = "127.0.0.1";
        final int port = 22;

        final CheckSshCommand command = new CheckSshCommand(instanceName, ip, port);

        final VirtualRoutingResource virtRouterResource = Mockito.mock(VirtualRoutingResource.class);

        final String privateIp = command.getIp();
        final int cmdPort = command.getPort();

        when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(virtRouterResource);
        when(virtRouterResource.connect(privateIp, cmdPort)).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVirtRouterResource();
        verify(virtRouterResource, times(1)).connect(privateIp, cmdPort);
    }

    @Test
    public void testCheckNetworkCommand() {
        final List<PhysicalNetworkSetupInfo> networkInfoList = new ArrayList<PhysicalNetworkSetupInfo>();

        final PhysicalNetworkSetupInfo nic = Mockito.mock(PhysicalNetworkSetupInfo.class);
        networkInfoList.add(nic);

        final CheckNetworkCommand command = new CheckNetworkCommand(networkInfoList);

        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Guest, nic.getGuestNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Management, nic.getPrivateNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Public, nic.getPublicNetworkName())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Guest, nic.getGuestNetworkName());
        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Management, nic.getPrivateNetworkName());
        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Public, nic.getPublicNetworkName());
    }

    @Test
    public void testCheckNetworkCommandFail1() {
        final List<PhysicalNetworkSetupInfo> networkInfoList = new ArrayList<PhysicalNetworkSetupInfo>();

        final PhysicalNetworkSetupInfo networkSetupInfo = Mockito.mock(PhysicalNetworkSetupInfo.class);
        networkInfoList.add(networkSetupInfo);

        final CheckNetworkCommand command = new CheckNetworkCommand(networkInfoList);

        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName())).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName());
    }

    @Test
    public void testCheckNetworkCommandFail2() {
        final List<PhysicalNetworkSetupInfo> networkInfoList = new ArrayList<PhysicalNetworkSetupInfo>();

        final PhysicalNetworkSetupInfo networkSetupInfo = Mockito.mock(PhysicalNetworkSetupInfo.class);
        networkInfoList.add(networkSetupInfo);

        final CheckNetworkCommand command = new CheckNetworkCommand(networkInfoList);

        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Management, networkSetupInfo.getPrivateNetworkName())).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName());
        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Management, networkSetupInfo.getPrivateNetworkName());
    }

    @Test
    public void testCheckNetworkCommandFail3() {
        final List<PhysicalNetworkSetupInfo> networkInfoList = new ArrayList<PhysicalNetworkSetupInfo>();

        final PhysicalNetworkSetupInfo networkSetupInfo = Mockito.mock(PhysicalNetworkSetupInfo.class);
        networkInfoList.add(networkSetupInfo);

        final CheckNetworkCommand command = new CheckNetworkCommand(networkInfoList);

        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Management, networkSetupInfo.getPrivateNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.checkNetwork(TrafficType.Public, networkSetupInfo.getPublicNetworkName())).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Guest, networkSetupInfo.getGuestNetworkName());
        verify(libvirtComputingResourceMock, times(1)).checkNetwork(TrafficType.Management, networkSetupInfo.getPrivateNetworkName());
    }

    @Test
    public void testOvsDestroyTunnelCommand() {
        final String networkName = "Test";
        final Long networkId = 1l;
        final String inPortName = "eth";

        final OvsDestroyTunnelCommand command = new OvsDestroyTunnelCommand(networkId, networkName, inPortName);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
    }

    @Test
    public void testOvsDestroyTunnelCommandFailure1() {
        final String networkName = "Test";
        final Long networkId = 1l;
        final String inPortName = "eth";

        final OvsDestroyTunnelCommand command = new OvsDestroyTunnelCommand(networkId, networkName, inPortName);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = Exception.class)
    public void testOvsDestroyTunnelCommandFailure2() {
        final String networkName = "Test";
        final Long networkId = 1l;
        final String inPortName = "eth";

        final OvsDestroyTunnelCommand command = new OvsDestroyTunnelCommand(networkId, networkName, inPortName);

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(command.getBridgeName())).thenThrow(Exception.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(command.getBridgeName());
    }

    @Test
    public void testCheckOnHostCommand() {
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);;

        final CheckOnHostCommand command = new CheckOnHostCommand(host);

        final KVMHAMonitor monitor = Mockito.mock(KVMHAMonitor.class);

        when(libvirtComputingResourceMock.getMonitor()).thenReturn(monitor);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getMonitor();
    }

    @Test
    public void testOvsCreateTunnelCommand() {
        final String remoteIp = "127.0.0.1";
        final Integer key = 1;
        final Long from = 1l;
        final Long to = 2l;
        final long networkId = 1l;
        final String fromIp = "127.0.0.1";
        final String networkName = "eth";
        final String networkUuid = "8edb1156-a851-4914-afc6-468ee52ac861";

        final OvsCreateTunnelCommand command = new OvsCreateTunnelCommand(remoteIp, key, from, to, networkId, fromIp, networkName, networkUuid);

        final String bridge = command.getNetworkName();

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(bridge)).thenReturn(true);
        when(libvirtComputingResourceMock.configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
                command.getNetworkName())).thenReturn(true);
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(bridge);
        verify(libvirtComputingResourceMock, times(1)).configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
                command.getNetworkName());
    }

    @Test
    public void testOvsCreateTunnelCommandFailure1() {
        final String remoteIp = "127.0.0.1";
        final Integer key = 1;
        final Long from = 1l;
        final Long to = 2l;
        final long networkId = 1l;
        final String fromIp = "127.0.0.1";
        final String networkName = "eth";
        final String networkUuid = "8edb1156-a851-4914-afc6-468ee52ac861";

        final OvsCreateTunnelCommand command = new OvsCreateTunnelCommand(remoteIp, key, from, to, networkId, fromIp, networkName, networkUuid);

        final String bridge = command.getNetworkName();

        when(libvirtComputingResourceMock.findOrCreateTunnelNetwork(bridge)).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(bridge);
        verify(libvirtComputingResourceMock, times(0)).configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
                command.getNetworkName());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = Exception.class)
    public void testOvsCreateTunnelCommandFailure2() {
        final String remoteIp = "127.0.0.1";
        final Integer key = 1;
        final Long from = 1l;
        final Long to = 2l;
        final long networkId = 1l;
        final String fromIp = "127.0.0.1";
        final String networkName = "eth";
        final String networkUuid = "8edb1156-a851-4914-afc6-468ee52ac861";

        final OvsCreateTunnelCommand command = new OvsCreateTunnelCommand(remoteIp, key, from, to, networkId, fromIp, networkName, networkUuid);

        final String bridge = command.getNetworkName();

        when(libvirtComputingResourceMock.configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
                command.getNetworkName())).thenThrow(Exception.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).findOrCreateTunnelNetwork(bridge);
        verify(libvirtComputingResourceMock, times(1)).configureTunnelNetwork(command.getNetworkId(), command.getFrom(),
                command.getNetworkName());
    }

    @Test
    public void testCreateVolumeFromSnapshotCommand() {
        // This tests asserts to False because there will be a NPE due to UUID static method calls.

        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "/opt/storage/";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "uuid:/8edb1156-a851-4914-afc6-468ee52ac861/";
        final String backedUpSnapshotName = "uuid:/8edb1156-a851-4914-afc6-468ee52ac862/";
        final int wait = 0;

        final CreateVolumeFromSnapshotCommand command = new CreateVolumeFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        final String primaryUuid = command.getPrimaryStoragePoolNameLabel();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(secondaryPool);
        when(secondaryPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(storagePoolMgr.getStoragePool(command.getPool().getType(), primaryUuid)).thenReturn(primaryPool);

        //when(storagePoolMgr.copyPhysicalDisk(snapshot, volUuid, primaryPool, 0)).thenReturn(disk);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(secondaryPool, times(1)).getPhysicalDisk(command.getSnapshotName());
        verify(storagePoolMgr, times(1)).getStoragePool(command.getPool().getType(), primaryUuid);
        //verify(storagePoolMgr, times(1)).copyPhysicalDisk(snapshot, volUuid, primaryPool, 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateVolumeFromSnapshotCommandCloudException() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "/opt/storage/";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "uuid:/8edb1156-a851-4914-afc6-468ee52ac861/";
        final String backedUpSnapshotName = "uuid:/8edb1156-a851-4914-afc6-468ee52ac862/";
        final int wait = 0;

        final CreateVolumeFromSnapshotCommand command = new CreateVolumeFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        final String primaryUuid = command.getPrimaryStoragePoolNameLabel();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(secondaryPool);
        when(secondaryPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(storagePoolMgr.getStoragePool(command.getPool().getType(), primaryUuid)).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(secondaryPool, times(1)).getPhysicalDisk(command.getSnapshotName());
        verify(storagePoolMgr, times(1)).getStoragePool(command.getPool().getType(), primaryUuid);
    }

    @Test
    public void testFenceCommand() {
        final VirtualMachine vm = Mockito.mock(VirtualMachine.class);;
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);

        final FenceCommand command = new FenceCommand(vm, host);

        final KVMHAMonitor monitor = Mockito.mock(KVMHAMonitor.class);

        final HAStoragePool storagePool = Mockito.mock(HAStoragePool.class);
        final List<HAStoragePool> pools = new ArrayList<HAStoragePool>();
        pools.add(storagePool);

        when(libvirtComputingResourceMock.getMonitor()).thenReturn(monitor);
        when(monitor.getStoragePools()).thenReturn(pools);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getMonitor();
        verify(monitor, times(1)).getStoragePools();
    }

    @Test
    public void testSecurityGroupRulesCmdFalse() {
        final String guestIp = "127.0.0.1";
        final String guestIp6 = "2001:db8::cad:40ff:fefd:75c4";
        final String guestMac = "00:00:00:00";
        final String vmName = "Test";
        final Long vmId = 1l;
        final String signature = "signature";
        final Long seqNum = 1l;
        final IpPortAndProto[] ingressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final IpPortAndProto[] egressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final List<String> secIps = new Vector<String>();
        final List<String> cidrs = new Vector<String>();
        cidrs.add("0.0.0.0/0");

        final SecurityGroupRulesCmd command = new SecurityGroupRulesCmd(guestIp, guestIp6, guestMac, vmName, vmId, signature, seqNum, ingressRuleSet, egressRuleSet, secIps);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef interfaceDef = Mockito.mock(InterfaceDef.class);
        nics.add(interfaceDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }


        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSecurityGroupRulesCmdTrue() {
        final String guestIp = "127.0.0.1";
        final String guestIp6 = "2001:db8::cad:40ff:fefd:75c4";
        final String guestMac = "00:00:00:00";
        final String vmName = "Test";
        final Long vmId = 1l;
        final String signature = "signature";
        final Long seqNum = 1l;
        final IpPortAndProto[] ingressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final IpPortAndProto[] egressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final List<String> secIps = new Vector<String>();
        final List<String> cidrs = new Vector<String>();
        cidrs.add("0.0.0.0/0");

        final SecurityGroupRulesCmd command = new SecurityGroupRulesCmd(guestIp, guestIp6, guestMac, vmName, vmId, signature, seqNum, ingressRuleSet, egressRuleSet, secIps);
        final VirtualMachineTO vm = Mockito.mock(VirtualMachineTO.class);
        command.setVmTO(vm);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef interfaceDef = Mockito.mock(InterfaceDef.class);
        nics.add(interfaceDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(interfaceDef.getDevName()).thenReturn("eth0");
        when(interfaceDef.getBrName()).thenReturn("br0");

        final String vif = nics.get(0).getDevName();
        final String brname = nics.get(0).getBrName();

        when(ingressRuleSet[0].getProto()).thenReturn("tcp");
        when(ingressRuleSet[0].getStartPort()).thenReturn(22);
        when(ingressRuleSet[0].getEndPort()).thenReturn(22);
        when(ingressRuleSet[0].getAllowedCidrs()).thenReturn(cidrs);

        when(egressRuleSet[0].getProto()).thenReturn("tcp");
        when(egressRuleSet[0].getStartPort()).thenReturn(22);
        when(egressRuleSet[0].getEndPort()).thenReturn(22);
        when(egressRuleSet[0].getAllowedCidrs()).thenReturn(cidrs);

        when(libvirtComputingResourceMock.applyDefaultNetworkRules(conn, vm, true)).thenReturn(true);
        when(libvirtComputingResourceMock.addNetworkRules(command.getVmName(), Long.toString(command.getVmId()), command.getGuestIp(), command.getGuestIp6(), command.getSignature(),
                Long.toString(command.getSeqNum()), command.getGuestMac(), command.stringifyRules(), vif, brname, command.getSecIpsString())).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSecurityGroupRulesCmdException() {
        final String guestIp = "127.0.0.1";
        final String guestIp6 = "2001:db8::cad:40ff:fefd:75c4";
        final String guestMac = "00:00:00:00";
        final String vmName = "Test";
        final Long vmId = 1l;
        final String signature = "signature";
        final Long seqNum = 1l;
        final IpPortAndProto[] ingressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final IpPortAndProto[] egressRuleSet = new IpPortAndProto[]{Mockito.mock(IpPortAndProto.class)};
        final List<String> secIps = new Vector<String>();

        final SecurityGroupRulesCmd command = new SecurityGroupRulesCmd(guestIp, guestIp6, guestMac, vmName, vmId, signature, seqNum, ingressRuleSet, egressRuleSet, secIps);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef interfaceDef = Mockito.mock(InterfaceDef.class);
        nics.add(interfaceDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testPlugNicCommandMatchMack() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";
        final Type vmtype = Type.DomainRouter;

        final PlugNicCommand command = new PlugNicCommand(nic, instanceName, vmtype);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef intDef = Mockito.mock(InterfaceDef.class);
        nics.add(intDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);

        when(intDef.getMacAddress()).thenReturn("00:00:00:00");

        when(nic.getMac()).thenReturn("00:00:00:00");

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, instanceName)).thenReturn(vm);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
            verify(libvirtComputingResourceMock, times(1)).getDomain(conn, instanceName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testPlugNicCommandNoMatchMack() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";
        final Type vmtype = Type.DomainRouter;

        final PlugNicCommand command = new PlugNicCommand(nic, instanceName, vmtype);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);
        final InterfaceDef interfaceDef = Mockito.mock(InterfaceDef.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef intDef = Mockito.mock(InterfaceDef.class);
        nics.add(intDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);

        when(intDef.getMacAddress()).thenReturn("00:00:00:00");

        when(nic.getMac()).thenReturn("00:00:00:01");
        when(nic.getName()).thenReturn("br0");

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, instanceName)).thenReturn(vm);

            when(libvirtComputingResourceMock.getVifDriver(nic.getType(), nic.getName())).thenReturn(vifDriver);

            when(vifDriver.plug(nic, "Other PV", "", null)).thenReturn(interfaceDef);
            when(interfaceDef.toString()).thenReturn("Interface");

            final String interfaceDefStr = interfaceDef.toString();
            doNothing().when(vm).attachDevice(interfaceDefStr);

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
            verify(libvirtComputingResourceMock, times(1)).getDomain(conn, instanceName);
            verify(libvirtComputingResourceMock, times(1)).getVifDriver(nic.getType(), nic.getName());
            verify(vifDriver, times(1)).plug(nic, "Other PV", "", null);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPlugNicCommandLibvirtException() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";
        final Type vmtype = Type.DomainRouter;

        final PlugNicCommand command = new PlugNicCommand(nic, instanceName, vmtype);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPlugNicCommandInternalError() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";
        final Type vmtype = Type.DomainRouter;

        final PlugNicCommand command = new PlugNicCommand(nic, instanceName, vmtype);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);
        final VifDriver vifDriver = Mockito.mock(VifDriver.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef intDef = Mockito.mock(InterfaceDef.class);
        nics.add(intDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);

        when(intDef.getMacAddress()).thenReturn("00:00:00:00");

        when(nic.getMac()).thenReturn("00:00:00:01");

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, instanceName)).thenReturn(vm);

            when(libvirtComputingResourceMock.getVifDriver(nic.getType(), nic.getName())).thenReturn(vifDriver);

            when(vifDriver.plug(nic, "Other PV", "", null)).thenThrow(InternalErrorException.class);

        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
            verify(libvirtComputingResourceMock, times(1)).getDomain(conn, instanceName);
            verify(libvirtComputingResourceMock, times(1)).getVifDriver(nic.getType(), nic.getName());
            verify(vifDriver, times(1)).plug(nic, "Other PV", "", null);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testUnPlugNicCommandMatchMack() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";

        final UnPlugNicCommand command = new UnPlugNicCommand(nic, instanceName);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();
        final InterfaceDef intDef = Mockito.mock(InterfaceDef.class);
        nics.add(intDef);

        final VifDriver vifDriver = Mockito.mock(VifDriver.class);
        final List<VifDriver> drivers = new ArrayList<VifDriver>();
        drivers.add(vifDriver);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);

        when(intDef.getBrName()).thenReturn("br0");
        when(intDef.getMacAddress()).thenReturn("00:00:00:00");

        when(nic.getMac()).thenReturn("00:00:00:00");

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, instanceName)).thenReturn(vm);
            when(libvirtComputingResourceMock.getAllVifDrivers()).thenReturn(drivers);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
            verify(libvirtComputingResourceMock, times(1)).getDomain(conn, instanceName);
            verify(libvirtComputingResourceMock, times(1)).getAllVifDrivers();
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testUnPlugNicCommandNoNics() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";

        final UnPlugNicCommand command = new UnPlugNicCommand(nic, instanceName);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);

        final List<InterfaceDef> nics = new ArrayList<InterfaceDef>();

        final VifDriver vifDriver = Mockito.mock(VifDriver.class);
        final List<VifDriver> drivers = new ArrayList<VifDriver>();
        drivers.add(vifDriver);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getInterfaces(conn, command.getVmName())).thenReturn(nics);

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, instanceName)).thenReturn(vm);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
            verify(libvirtComputingResourceMock, times(1)).getDomain(conn, instanceName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUnPlugNicCommandLibvirtException() {
        final NicTO nic = Mockito.mock(NicTO.class);
        final String instanceName = "Test";

        final UnPlugNicCommand command = new UnPlugNicCommand(nic, instanceName);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testNetworkUsageCommandNonVpc() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = false;
        final String gatewayIP = "127.0.0.1";

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, forVpc, gatewayIP);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP())).thenReturn(new long[]{10l, 10l});

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        //Being called twice, although I did not find the second place yet.
        verify(libvirtComputingResourceMock, times(2)).getNetworkStats(command.getPrivateIP());
    }

    @Test
    public void testNetworkUsageCommandNonVpcCreate() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = false;

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, "create", forVpc);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.networkUsage(command.getPrivateIP(), "create", null)).thenReturn("SUCCESS");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).networkUsage(command.getPrivateIP(), "create", null);
    }

    @Test
    public void testNetworkUsageCommandVpcCreate() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = true;
        final String gatewayIP = "127.0.0.1";
        final String vpcCidr = "10.1.1.0/24";

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, forVpc, gatewayIP, vpcCidr);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.configureVPCNetworkUsage(command.getPrivateIP(), command.getGatewayIP(), "create", command.getVpcCIDR())).thenReturn("SUCCESS");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).configureVPCNetworkUsage(command.getPrivateIP(), command.getGatewayIP(), "create", command.getVpcCIDR());
    }

    @Test
    public void testNetworkUsageCommandVpcGet() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = true;
        final String gatewayIP = "127.0.0.1";

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, forVpc, gatewayIP);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.getVPCNetworkStats(command.getPrivateIP(), command.getGatewayIP(), command.getOption())).thenReturn(new long[]{10l, 10l});

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVPCNetworkStats(command.getPrivateIP(), command.getGatewayIP(), command.getOption());
    }

    @Test
    public void testNetworkUsageCommandVpcVpn() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = true;
        final String gatewayIP = "127.0.0.1";

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, "vpn", forVpc, gatewayIP);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.getVPCNetworkStats(command.getPrivateIP(), command.getGatewayIP(), command.getOption())).thenReturn(new long[]{10l, 10l});

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getVPCNetworkStats(command.getPrivateIP(), command.getGatewayIP(), command.getOption());
    }

    @Test
    public void testNetworkUsageCommandVpcNoOption() {
        final String privateIP = "127.0.0.1";
        final String domRName = "domR";
        final boolean forVpc = true;
        final String gatewayIP = "127.0.0.1";

        final NetworkUsageCommand command = new NetworkUsageCommand(privateIP, domRName, null, forVpc, gatewayIP);

        libvirtComputingResourceMock.getNetworkStats(command.getPrivateIP());

        when(libvirtComputingResourceMock.configureVPCNetworkUsage(command.getPrivateIP(), command.getGatewayIP(), command.getOption(), command.getVpcCIDR())).thenReturn("FAILURE");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).configureVPCNetworkUsage(command.getPrivateIP(), command.getGatewayIP(), command.getOption(), command.getVpcCIDR());
    }

    @Test
    public void testCreatePrivateTemplateFromVolumeCommand() {
        //Simple test used to make sure the flow (LibvirtComputingResource => Request => CommandWrapper) is working.
        //The code is way to big and complex. Will finish the refactor and come back to this to add more cases.

        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final String secondaryStorageUrl = "nfs:/127.0.0.1/storage/secondary";
        final long templateId = 1l;
        final long accountId = 1l;
        final String userSpecifiedName = "User";
        final String uniqueName = "Unique";
        final String volumePath = "/123/vol";
        final String vmName = "Test";
        final int wait = 0;

        final CreatePrivateTemplateFromVolumeCommand command = new CreatePrivateTemplateFromVolumeCommand(pool, secondaryStorageUrl, templateId, accountId, userSpecifiedName, uniqueName, volumePath, vmName, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryStorage = Mockito.mock(KVMStoragePool.class);
        //final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePoolByURI(secondaryStorageUrl)).thenReturn(secondaryStorage);
        when(storagePoolMgr.getStoragePool(command.getPool().getType(), command.getPrimaryStoragePoolNameLabel())).thenThrow(new CloudRuntimeException("error"));

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(secondaryStorageUrl);
        verify(storagePoolMgr, times(1)).getStoragePool(command.getPool().getType(), command.getPrimaryStoragePoolNameLabel());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testManageSnapshotCommandLibvirtException() {
        //Simple test used to make sure the flow (LibvirtComputingResource => Request => CommandWrapper) is working.
        //The code is way to big and complex. Will finish the refactor and come back to this to add more cases.

        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final String volumePath = "/123/vol";
        final String vmName = "Test";

        final long snapshotId = 1l;
        final String preSnapshotPath = "/snapshot/path";
        final String snapshotName = "snap";

        final ManageSnapshotCommand command = new ManageSnapshotCommand(snapshotId, volumePath, pool, preSnapshotPath, snapshotName, vmName);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        //final Connect conn = Mockito.mock(Connect.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testManageSnapshotCommandLibvirt() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);;
        final String volumePath = "/123/vol";
        final String vmName = "Test";
        final long snapshotId = 1l;
        final String preSnapshotPath = "/snapshot/path";
        final String snapshotName = "snap";

        final ManageSnapshotCommand command = new ManageSnapshotCommand(snapshotId, volumePath, storagePool, preSnapshotPath, snapshotName, vmName);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool primaryPool = Mockito.mock(KVMStoragePool.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_RUNNING;
        info.state = state;

        final KVMPhysicalDisk disk = Mockito.mock(KVMPhysicalDisk.class);

        final StorageFilerTO pool = command.getPool();

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmName)).thenReturn(conn);
            when(libvirtComputingResourceMock.getDomain(conn, command.getVmName())).thenReturn(vm);
            when(vm.getInfo()).thenReturn(info);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primaryPool);
        when(primaryPool.getPhysicalDisk(command.getVolumePath())).thenReturn(disk);
        when(primaryPool.isExternalSnapshot()).thenReturn(false);

        try {
            when(vm.getUUIDString()).thenReturn("cdb18980-546d-4153-b916-70ee9edf0908");
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmName);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBackupSnapshotCommandLibvirtException() {
        //Simple test used to make sure the flow (LibvirtComputingResource => Request => CommandWrapper) is working.
        //The code is way to big and complex. Will finish the refactor and come back to this to add more cases.

        final StoragePool pool = Mockito.mock(StoragePool.class);;
        final String secondaryStorageUrl = "nfs:/127.0.0.1/storage/secondary";
        final long accountId = 1l;
        final String volumePath = "/123/vol";
        final String vmName = "Test";
        final int wait = 0;

        final long snapshotId = 1l;
        final String snapshotName = "snap";

        final Long dcId = 1l;
        final Long volumeId = 1l;
        final Long secHostId = 1l;
        final String snapshotUuid = "9a0afe7c-26a7-4585-bf87-abf82ae106d9";
        final String prevBackupUuid = "003a0cc2-2e04-417a-bee0-534ef1724561";
        final boolean isVolumeInactive = false;
        final String prevSnapshotUuid = "1791efae-f22d-474b-87c6-92547d6c5877";

        final BackupSnapshotCommand command = new BackupSnapshotCommand(secondaryStorageUrl, dcId, accountId, volumeId, snapshotId, secHostId, volumePath, pool, snapshotUuid, snapshotName, prevSnapshotUuid, prevBackupUuid, isVolumeInactive, vmName, wait);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        //final Connect conn = Mockito.mock(Connect.class);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);

        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(command.getVmName())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(command.getVmName());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testCreatePrivateTemplateFromSnapshotCommand() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "/run/9a0afe7c-26a7-4585-bf87-abf82ae106d9/";
        final String backedUpSnapshotName = "snap";
        final String origTemplateInstallPath = "/install/path/";
        final Long newTemplateId = 2l;
        final String templateName = "templ";
        final int wait = 0;

        final CreatePrivateTemplateFromSnapshotCommand command = new CreatePrivateTemplateFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, origTemplateInstallPath, newTemplateId, templateName, wait);

        final String templatePath = "/template/path";
        final String localPath = "/mnt/local";
        final String tmplName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool snapshotPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);
        final StorageLayer storage = Mockito.mock(StorageLayer.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final TemplateLocation location = Mockito.mock(TemplateLocation.class);
        final Processor qcow2Processor = Mockito.mock(Processor.class);
        final FormatInfo info = Mockito.mock(FormatInfo.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(snapshotPool);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl())).thenReturn(secondaryPool);
        when(snapshotPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(secondaryPool.getLocalPath()).thenReturn(localPath);
        when(libvirtComputingResourceMock.getStorage()).thenReturn(storage);

        when(libvirtComputingResourceMock.createTmplPath()).thenReturn(templatePath);
        when(libvirtComputingResourceMock.getCmdsTimeout()).thenReturn(1);

        final String templateFolder = command.getAccountId() + File.separator + command.getNewTemplateId();
        final String templateInstallFolder = "template/tmpl/" + templateFolder;
        final String tmplPath = secondaryPool.getLocalPath() + File.separator + templateInstallFolder;

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.buildTemplateLocation(storage, tmplPath)).thenReturn(location);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(tmplName);

        try {
            when(libvirtUtilitiesHelper.buildQCOW2Processor(storage)).thenReturn(qcow2Processor);
            when(qcow2Processor.process(tmplPath, null, tmplName)).thenReturn(info);
        } catch (final ConfigurationException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreatePrivateTemplateFromSnapshotCommandConfigurationException() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "/run/9a0afe7c-26a7-4585-bf87-abf82ae106d9/";
        final String backedUpSnapshotName = "snap";
        final String origTemplateInstallPath = "/install/path/";
        final Long newTemplateId = 2l;
        final String templateName = "templ";
        final int wait = 0;

        final CreatePrivateTemplateFromSnapshotCommand command = new CreatePrivateTemplateFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, origTemplateInstallPath, newTemplateId, templateName, wait);

        final String templatePath = "/template/path";
        final String localPath = "/mnt/local";
        final String tmplName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool snapshotPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);
        final StorageLayer storage = Mockito.mock(StorageLayer.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final TemplateLocation location = Mockito.mock(TemplateLocation.class);
        final Processor qcow2Processor = Mockito.mock(Processor.class);
        final FormatInfo info = Mockito.mock(FormatInfo.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(snapshotPool);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl())).thenReturn(secondaryPool);
        when(snapshotPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(secondaryPool.getLocalPath()).thenReturn(localPath);
        when(libvirtComputingResourceMock.getStorage()).thenReturn(storage);

        when(libvirtComputingResourceMock.createTmplPath()).thenReturn(templatePath);
        when(libvirtComputingResourceMock.getCmdsTimeout()).thenReturn(1);

        final String templateFolder = command.getAccountId() + File.separator + command.getNewTemplateId();
        final String templateInstallFolder = "template/tmpl/" + templateFolder;
        final String tmplPath = secondaryPool.getLocalPath() + File.separator + templateInstallFolder;

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(tmplName);

        try {
            when(libvirtUtilitiesHelper.buildQCOW2Processor(storage)).thenThrow(ConfigurationException.class);
        } catch (final ConfigurationException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreatePrivateTemplateFromSnapshotCommandInternalErrorException() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "/run/9a0afe7c-26a7-4585-bf87-abf82ae106d9/";
        final String backedUpSnapshotName = "snap";
        final String origTemplateInstallPath = "/install/path/";
        final Long newTemplateId = 2l;
        final String templateName = "templ";
        final int wait = 0;

        final CreatePrivateTemplateFromSnapshotCommand command = new CreatePrivateTemplateFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, origTemplateInstallPath, newTemplateId, templateName, wait);

        final String templatePath = "/template/path";
        final String localPath = "/mnt/local";
        final String tmplName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool snapshotPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);
        final StorageLayer storage = Mockito.mock(StorageLayer.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final TemplateLocation location = Mockito.mock(TemplateLocation.class);
        final Processor qcow2Processor = Mockito.mock(Processor.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(snapshotPool);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl())).thenReturn(secondaryPool);
        when(snapshotPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(secondaryPool.getLocalPath()).thenReturn(localPath);
        when(libvirtComputingResourceMock.getStorage()).thenReturn(storage);

        when(libvirtComputingResourceMock.createTmplPath()).thenReturn(templatePath);
        when(libvirtComputingResourceMock.getCmdsTimeout()).thenReturn(1);

        final String templateFolder = command.getAccountId() + File.separator + command.getNewTemplateId();
        final String templateInstallFolder = "template/tmpl/" + templateFolder;
        final String tmplPath = secondaryPool.getLocalPath() + File.separator + templateInstallFolder;

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(tmplName);

        try {
            when(libvirtUtilitiesHelper.buildQCOW2Processor(storage)).thenReturn(qcow2Processor);
            when(qcow2Processor.process(tmplPath, null, tmplName)).thenThrow(InternalErrorException.class);
        } catch (final ConfigurationException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreatePrivateTemplateFromSnapshotCommandIOException() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "/run/9a0afe7c-26a7-4585-bf87-abf82ae106d9/";
        final String backedUpSnapshotName = "snap";
        final String origTemplateInstallPath = "/install/path/";
        final Long newTemplateId = 2l;
        final String templateName = "templ";
        final int wait = 0;

        final CreatePrivateTemplateFromSnapshotCommand command = new CreatePrivateTemplateFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, origTemplateInstallPath, newTemplateId, templateName, wait);

        final String templatePath = "/template/path";
        final String localPath = "/mnt/local";
        final String tmplName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool snapshotPool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk snapshot = Mockito.mock(KVMPhysicalDisk.class);
        final StorageLayer storage = Mockito.mock(StorageLayer.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final TemplateLocation location = Mockito.mock(TemplateLocation.class);
        final Processor qcow2Processor = Mockito.mock(Processor.class);
        final FormatInfo info = Mockito.mock(FormatInfo.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(snapshotPool);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl())).thenReturn(secondaryPool);
        when(snapshotPool.getPhysicalDisk(command.getSnapshotName())).thenReturn(snapshot);
        when(secondaryPool.getLocalPath()).thenReturn(localPath);
        when(libvirtComputingResourceMock.getStorage()).thenReturn(storage);

        when(libvirtComputingResourceMock.createTmplPath()).thenReturn(templatePath);
        when(libvirtComputingResourceMock.getCmdsTimeout()).thenReturn(1);

        final String templateFolder = command.getAccountId() + File.separator + command.getNewTemplateId();
        final String templateInstallFolder = "template/tmpl/" + templateFolder;
        final String tmplPath = secondaryPool.getLocalPath() + File.separator + templateInstallFolder;

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.buildTemplateLocation(storage, tmplPath)).thenReturn(location);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(tmplName);

        try {
            when(libvirtUtilitiesHelper.buildQCOW2Processor(storage)).thenReturn(qcow2Processor);
            when(qcow2Processor.process(tmplPath, null, tmplName)).thenReturn(info);

            when(location.create(1, true, tmplName)).thenThrow(IOException.class);

        } catch (final ConfigurationException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final IOException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreatePrivateTemplateFromSnapshotCommandCloudRuntime() {
        final StoragePool pool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long dcId = 1l;
        final Long accountId = 1l;
        final Long volumeId = 1l;
        final String backedUpSnapshotUuid = "/run/9a0afe7c-26a7-4585-bf87-abf82ae106d9/";
        final String backedUpSnapshotName = "snap";
        final String origTemplateInstallPath = "/install/path/";
        final Long newTemplateId = 2l;
        final String templateName = "templ";
        final int wait = 0;

        final CreatePrivateTemplateFromSnapshotCommand command = new CreatePrivateTemplateFromSnapshotCommand(pool, secondaryStoragePoolURL, dcId, accountId, volumeId, backedUpSnapshotUuid, backedUpSnapshotName, origTemplateInstallPath, newTemplateId, templateName, wait);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondaryPool = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool snapshotPool = Mockito.mock(KVMStoragePool.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final String tmplName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);

        String snapshotPath = command.getSnapshotUuid();
        final int index = snapshotPath.lastIndexOf("/");
        snapshotPath = snapshotPath.substring(0, index);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(tmplName);

        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath)).thenReturn(snapshotPool);
        when(storagePoolMgr.getStoragePoolByURI(command.getSecondaryStorageUrl())).thenReturn(secondaryPool);
        when(snapshotPool.getPhysicalDisk(command.getSnapshotName())).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl() + snapshotPath);
        verify(storagePoolMgr, times(1)).getStoragePoolByURI(command.getSecondaryStorageUrl());
    }

    @Test
    public void testCopyVolumeCommand() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long volumeId = 1l;
        final int wait = 0;
        final String volumePath = "/vol/path";
        final boolean toSecondaryStorage = true;
        final boolean executeInSequence = false;

        final CopyVolumeCommand command = new CopyVolumeCommand(volumeId, volumePath, storagePool, secondaryStoragePoolURL, toSecondaryStorage, wait, executeInSequence );

        final String destVolumeName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";
        final String volumeDestPath = "/volumes/" + command.getVolumeId() + File.separator;

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondary = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final KVMPhysicalDisk disk = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final StorageFilerTO pool = command.getPool();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(destVolumeName);
        when(primary.getPhysicalDisk(command.getVolumePath())).thenReturn(disk);
        when(storagePoolMgr.getStoragePoolByURI(secondaryStoragePoolURL)).thenReturn(secondary);
        when(secondary.getType()).thenReturn(StoragePoolType.ManagedNFS);
        when(secondary.getUuid()).thenReturn("60d979d8-d132-4181-8eca-8dfde50d7df6");
        when(secondary.createFolder(volumeDestPath)).thenReturn(true);
        when(storagePoolMgr.deleteStoragePool(secondary.getType(), secondary.getUuid())).thenReturn(true);
        when(storagePoolMgr.getStoragePoolByURI(secondaryStoragePoolURL + volumeDestPath)).thenReturn(secondary);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testCopyVolumeCommandToSecFalse() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long volumeId = 1l;
        final int wait = 0;
        final String volumePath = "/vol/path";
        final boolean toSecondaryStorage = false;
        final boolean executeInSequence = false;

        final CopyVolumeCommand command = new CopyVolumeCommand(volumeId, volumePath, storagePool, secondaryStoragePoolURL, toSecondaryStorage, wait, executeInSequence );

        final String destVolumeName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";
        final String volumeDestPath = "/volumes/" + command.getVolumeId() + File.separator;

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondary = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final KVMPhysicalDisk disk = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final StorageFilerTO pool = command.getPool();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);
        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(destVolumeName);
        when(secondary.getType()).thenReturn(StoragePoolType.ManagedNFS);
        when(secondary.getUuid()).thenReturn("60d979d8-d132-4181-8eca-8dfde50d7df6");
        when(storagePoolMgr.getStoragePoolByURI(secondaryStoragePoolURL + volumeDestPath)).thenReturn(secondary);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCopyVolumeCommandCloudRuntime() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long volumeId = 1l;
        final int wait = 0;
        final String volumePath = "/vol/path";
        final boolean toSecondaryStorage = false;
        final boolean executeInSequence = false;

        final CopyVolumeCommand command = new CopyVolumeCommand(volumeId, volumePath, storagePool, secondaryStoragePoolURL, toSecondaryStorage, wait, executeInSequence );

        final String destVolumeName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";
        final String volumeDestPath = "/volumes/" + command.getVolumeId() + File.separator;

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondary = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final StorageFilerTO pool = command.getPool();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(primary);
        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(destVolumeName);
        when(secondary.getType()).thenReturn(StoragePoolType.ManagedNFS);
        when(secondary.getUuid()).thenReturn("60d979d8-d132-4181-8eca-8dfde50d7df6");
        when(storagePoolMgr.getStoragePoolByURI(secondaryStoragePoolURL + volumeDestPath)).thenReturn(secondary);
        when(secondary.getPhysicalDisk(command.getVolumePath() + ".qcow2")).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testCopyVolumeCommandCloudRuntime2() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long volumeId = 1l;
        final int wait = 0;
        final String volumePath = "/vol/path";
        final boolean toSecondaryStorage = false;
        final boolean executeInSequence = false;

        final CopyVolumeCommand command = new CopyVolumeCommand(volumeId, volumePath, storagePool, secondaryStoragePoolURL, toSecondaryStorage, wait, executeInSequence );

        final StorageFilerTO pool = command.getPool();

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenThrow(new CloudRuntimeException("error"));

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testCopyVolumeCommandPrimaryNotFound() {
        final StoragePool storagePool = Mockito.mock(StoragePool.class);
        final String secondaryStoragePoolURL = "nfs:/127.0.0.1/storage/secondary";
        final Long volumeId = 1l;
        final int wait = 0;
        final String volumePath = "/vol/path";
        final boolean toSecondaryStorage = false;
        final boolean executeInSequence = false;

        final CopyVolumeCommand command = new CopyVolumeCommand(volumeId, volumePath, storagePool, secondaryStoragePoolURL, toSecondaryStorage, wait, executeInSequence );

        final String destVolumeName = "ce97bbc1-34fe-4259-9202-74bbce2562ab";
        final String volumeDestPath = "/volumes/" + command.getVolumeId() + File.separator;

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool secondary = Mockito.mock(KVMStoragePool.class);
        final KVMStoragePool primary = Mockito.mock(KVMStoragePool.class);

        final KVMPhysicalDisk disk = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        final StorageFilerTO pool = command.getPool();

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenThrow(new CloudRuntimeException("not found"));

        when(storagePoolMgr.createStoragePool(pool.getUuid(), pool.getHost(), pool.getPort(), pool.getPath(),
                pool.getUserInfo(), pool.getType())).thenReturn(primary);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.generateUUIDName()).thenReturn(destVolumeName);
        when(secondary.getType()).thenReturn(StoragePoolType.ManagedNFS);
        when(secondary.getUuid()).thenReturn("60d979d8-d132-4181-8eca-8dfde50d7df6");
        when(storagePoolMgr.getStoragePoolByURI(secondaryStoragePoolURL + volumeDestPath)).thenReturn(secondary);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testPvlanSetupCommandDhcpAdd() {
        final String op = "add";
        final URI uri = URI.create("pvlan://200-p200");
        final String networkTag = "/105";
        final String dhcpName = "dhcp";
        final String dhcpMac = "00:00:00:00";
        final String dhcpIp = "127.0.0.1";

        final PvlanSetupCommand command = PvlanSetupCommand.createDhcpSetup(op, uri, networkTag, dhcpName, dhcpMac, dhcpIp);

        final String guestBridgeName = "br0";
        when(libvirtComputingResourceMock.getGuestBridgeName()).thenReturn(guestBridgeName);
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);

        final String ovsPvlanDhcpHostPath = "/pvlan";
        when(libvirtComputingResourceMock.getOvsPvlanDhcpHostPath()).thenReturn(ovsPvlanDhcpHostPath);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testPvlanSetupCommandVm() {
        final String op = "add";
        final URI uri = URI.create("pvlan://200-p200");
        final String networkTag = "/105";
        final String vmMac = "00:00:00:00";

        final PvlanSetupCommand command = PvlanSetupCommand.createVmSetup(op, uri, networkTag, vmMac);

        final String guestBridgeName = "br0";
        when(libvirtComputingResourceMock.getGuestBridgeName()).thenReturn(guestBridgeName);
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);

        final String ovsPvlanVmPath = "/pvlan";
        when(libvirtComputingResourceMock.getOvsPvlanVmPath()).thenReturn(ovsPvlanVmPath);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testPvlanSetupCommandDhcpDelete() {
        final String op = "delete";
        final URI uri = URI.create("pvlan://200-p200");
        final String networkTag = "/105";
        final String dhcpName = "dhcp";
        final String dhcpMac = "00:00:00:00";
        final String dhcpIp = "127.0.0.1";

        final PvlanSetupCommand command = PvlanSetupCommand.createDhcpSetup(op, uri, networkTag, dhcpName, dhcpMac, dhcpIp);

        final String guestBridgeName = "br0";
        when(libvirtComputingResourceMock.getGuestBridgeName()).thenReturn(guestBridgeName);
        when(libvirtComputingResourceMock.getTimeout()).thenReturn(Duration.ZERO);

        final String ovsPvlanDhcpHostPath = "/pvlan";
        when(libvirtComputingResourceMock.getOvsPvlanDhcpHostPath()).thenReturn(ovsPvlanDhcpHostPath);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testResizeVolumeCommand() {
        final String path = "nfs:/127.0.0.1/storage/secondary";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 200l;
        final boolean shrinkOk = true;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final StorageVol v = Mockito.mock(StorageVol.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_RUNNING;
        info.state = state;

        when(pool.getType()).thenReturn(StoragePoolType.RBD);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(storagePool);
        when(storagePool.getPhysicalDisk(path)).thenReturn(vol);
        when(vol.getPath()).thenReturn(path);
        when(storagePool.getType()).thenReturn(StoragePoolType.RBD);
        when(vol.getFormat()).thenReturn(PhysicalDiskFormat.FILE);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnection()).thenReturn(conn);
            when(conn.storageVolLookupByPath(path)).thenReturn(v);
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmInstance)).thenReturn(conn);
            when(conn.domainLookupByName(vmInstance)).thenReturn(vm);
            when(vm.getInfo()).thenReturn(info);

            when(conn.getLibVirVersion()).thenReturn(10010l);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(2)).getLibvirtUtilitiesHelper();

        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnection();
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmInstance);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testResizeVolumeCommandLinstorNotifyOnly() {
        final String path = "/dev/drbd1000";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 200l;
        final boolean shrinkOk = false;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final Domain vm = Mockito.mock(Domain.class);
        final DomainInfo info = Mockito.mock(DomainInfo.class);
        final DomainState state = DomainInfo.DomainState.VIR_DOMAIN_RUNNING;
        info.state = state;

        when(pool.getType()).thenReturn(StoragePoolType.Linstor);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(storagePool);
        when(storagePool.getPhysicalDisk(path)).thenReturn(vol);
        when(vol.getPath()).thenReturn(path);
        when(storagePool.getType()).thenReturn(StoragePoolType.Linstor);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByVmName(vmInstance)).thenReturn(conn);
            when(conn.domainLookupByName(vmInstance)).thenReturn(vm);
            when(vm.getInfo()).thenReturn(info);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(0)).getResizeScriptType(storagePool, vol);

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(0)).getConnection();
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByVmName(vmInstance);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testResizeVolumeCommandSameSize() {
        final String path = "nfs:/127.0.0.1/storage/secondary";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 100l;
        final boolean shrinkOk = false;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testResizeVolumeCommandShrink() {
        final String path = "nfs:/127.0.0.1/storage/secondary";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 200l;
        final boolean shrinkOk = true;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);

        when(pool.getType()).thenReturn(StoragePoolType.Filesystem);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(storagePool);
        when(storagePool.getPhysicalDisk(path)).thenReturn(vol);
        when(vol.getPath()).thenReturn(path);
        when(libvirtComputingResourceMock.getResizeScriptType(storagePool, vol)).thenReturn("QCOW2");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResizeVolumeCommandException() {
        final String path = "nfs:/127.0.0.1/storage/secondary";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 200l;
        final boolean shrinkOk = false;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);

        when(pool.getType()).thenReturn(StoragePoolType.RBD);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(storagePool);
        when(storagePool.getPhysicalDisk(path)).thenReturn(vol);
        when(vol.getPath()).thenReturn(path);
        when(storagePool.getType()).thenReturn(StoragePoolType.RBD);
        when(vol.getFormat()).thenReturn(PhysicalDiskFormat.FILE);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnection()).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();

        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnection();
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResizeVolumeCommandException2() {
        final String path = "nfs:/127.0.0.1/storage/secondary";
        final StorageFilerTO pool = Mockito.mock(StorageFilerTO.class);
        final Long currentSize = 100l;
        final Long newSize = 200l;
        final boolean shrinkOk = false;
        final String vmInstance = "Test";

        final ResizeVolumeCommand command = new ResizeVolumeCommand(path, pool, currentSize, newSize, shrinkOk, vmInstance, null);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);

        when(pool.getType()).thenReturn(StoragePoolType.RBD);
        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid())).thenReturn(storagePool);
        when(storagePool.getPhysicalDisk(path)).thenThrow(CloudRuntimeException.class);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
    }

    @Test
    public void testNetworkElementCommand() {
        final CheckRouterCommand command = new CheckRouterCommand();

        final VirtualRoutingResource virtRouterResource = Mockito.mock(VirtualRoutingResource.class);
        when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(virtRouterResource);

        when(virtRouterResource.executeRequest(command)).thenReturn(new CheckRouterAnswer(command, "mock_resource"));

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());
    }

    @Test
    public void testStorageSubSystemCommand() {
        final DiskTO disk = Mockito.mock(DiskTO.class);
        final String vmName = "Test";
        final AttachCommand command = new AttachCommand(disk, vmName);

        final StorageSubsystemCommandHandler handler = Mockito.mock(StorageSubsystemCommandHandler.class);
        when(libvirtComputingResourceMock.getStorageHandler()).thenReturn(handler);

        when(handler.handleStorageCommands(command)).thenReturn(new AttachAnswer(disk));

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }

    @Test
    public void testStartCommandFailedConnect() {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doNothing().when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        when(storagePoolMgr.connectPhysicalDisksViaVmSpec(vmSpec, false)).thenReturn(false);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStartCommandLibvirtException() {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenThrow(LibvirtException.class);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStartCommandInternalError() {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doThrow(InternalErrorException.class).when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStartCommandUriException() {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doThrow(URISyntaxException.class).when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertFalse(answer.getResult());

        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStartCommand() throws Exception {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);
        final VirtualRoutingResource virtRouterResource = Mockito.mock(VirtualRoutingResource.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";
        final String controlIp = "127.0.0.1";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doNothing().when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        when(storagePoolMgr.connectPhysicalDisksViaVmSpec(vmSpec, false)).thenReturn(true);
        try {
            doNothing().when(libvirtComputingResourceMock).createVifs(vmSpec, vmDef);

            when(libvirtComputingResourceMock.startVM(conn, vmName, vmDef.toString())).thenReturn("SUCCESS");

            when(vmSpec.getBootArgs()).thenReturn("ls -lart");
            when(libvirtComputingResourceMock.passCmdLine(vmName, vmSpec.getBootArgs())).thenReturn(true);

            when(nic.getIp()).thenReturn(controlIp);
            when(nic.getType()).thenReturn(TrafficType.Control);
            when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(virtRouterResource);
            when(virtRouterResource.connect(controlIp, 1, 5000)).thenReturn(true);
            when(virtRouterResource.isSystemVMSetup(vmName, controlIp)).thenReturn(true);
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        try (MockedStatic<SshHelper> sshHelperMockedStatic = Mockito.mockStatic(SshHelper.class)) {
            sshHelperMockedStatic.when(() -> SshHelper.scpTo(
                    Mockito.anyString(), Mockito.anyInt(),
                    Mockito.anyString(), any(File.class), nullable(String.class), Mockito.anyString(),
                    any(String[].class), Mockito.anyString())).thenAnswer(invocation -> null);

            final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
            assertNotNull(wrapper);

            final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
            assertTrue(answer.getResult());
        }
        verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
        verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
        try {
            verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testStartCommandIsolationEc2() throws Exception {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);
        final VirtualRoutingResource virtRouterResource = Mockito.mock(VirtualRoutingResource.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};

        final String vmName = "Test";
        final String controlIp = "127.0.0.1";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.DomainRouter);
        when(vmSpec.getName()).thenReturn(vmName);
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doNothing().when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        when(storagePoolMgr.connectPhysicalDisksViaVmSpec(vmSpec, false)).thenReturn(true);
        try {
            doNothing().when(libvirtComputingResourceMock).createVifs(vmSpec, vmDef);

            when(libvirtComputingResourceMock.startVM(conn, vmName, vmDef.toString())).thenReturn("SUCCESS");


            when(vmSpec.getBootArgs()).thenReturn("ls -lart");
            when(libvirtComputingResourceMock.passCmdLine(vmName, vmSpec.getBootArgs())).thenReturn(true);

            when(nic.getIp()).thenReturn(controlIp);
            when(nic.getType()).thenReturn(TrafficType.Control);
            when(libvirtComputingResourceMock.getVirtRouterResource()).thenReturn(virtRouterResource);
            when(virtRouterResource.connect(controlIp, 1, 5000)).thenReturn(true);
            when(virtRouterResource.isSystemVMSetup(vmName, controlIp)).thenReturn(true);
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        }

        try (MockedStatic<SshHelper> sshHelperMockedStatic = Mockito.mockStatic(SshHelper.class)) {
            sshHelperMockedStatic.when(() -> SshHelper.scpTo(
                    Mockito.anyString(), Mockito.anyInt(),
                    Mockito.anyString(), any(File.class), nullable(String.class), Mockito.anyString(),
                    any(String[].class), Mockito.anyString())).thenAnswer(invocation -> null);
            final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
            assertNotNull(wrapper);

            final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
            assertTrue(answer.getResult());

            verify(libvirtComputingResourceMock, times(1)).getStoragePoolMgr();
            verify(libvirtComputingResourceMock, times(1)).getLibvirtUtilitiesHelper();
            try {
                verify(libvirtUtilitiesHelper, times(1)).getConnectionByType(vmDef.getHvsType());
            } catch (final LibvirtException e) {
                fail(e.getMessage());
            }
        }
    }

    @Test
    public void testStartCommandHostMemory() {
        final VirtualMachineTO vmSpec = Mockito.mock(VirtualMachineTO.class);
        final com.cloud.host.Host host = Mockito.mock(com.cloud.host.Host.class);
        final boolean executeInSequence = false;

        final StartCommand command = new StartCommand(vmSpec, host, executeInSequence);

        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Connect conn = Mockito.mock(Connect.class);
        final LibvirtVMDef vmDef = Mockito.mock(LibvirtVMDef.class);

        final NicTO nic = Mockito.mock(NicTO.class);
        final NicTO[] nics = new NicTO[]{nic};
        final String vmName = "Test";

        when(libvirtComputingResourceMock.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(vmSpec.getNics()).thenReturn(nics);
        when(vmSpec.getType()).thenReturn(VirtualMachine.Type.User);
        when(vmSpec.getName()).thenReturn(vmName);
        when(vmSpec.getDisks()).thenReturn(new DiskTO[]{diskToMock});
        when(diskToMock.getData()).thenReturn(new VolumeObjectTO());
        when(libvirtComputingResourceMock.createVMFromSpec(vmSpec)).thenReturn(vmDef);

        when(libvirtComputingResourceMock.recreateCheckpointsOnVm(any(), any(), any())).thenReturn(true);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        try {
            when(libvirtUtilitiesHelper.getConnectionByType(vmDef.getHvsType())).thenReturn(conn);
            doNothing().when(libvirtComputingResourceMock).createVbd(conn, vmSpec, vmName, vmDef);
        } catch (final LibvirtException e) {
            fail(e.getMessage());
        } catch (final InternalErrorException e) {
            fail(e.getMessage());
        } catch (final URISyntaxException e) {
            fail(e.getMessage());
        }

        when(storagePoolMgr.connectPhysicalDisksViaVmSpec(vmSpec, false)).thenReturn(true);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);
        assertTrue(answer.getResult());
    }


    @Test
    public void testUpdateHostPasswordCommand() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Script script = Mockito.mock(Script.class);

        final String hostIp = "127.0.0.1";
        final String username = "root";
        final String newPassword = "password";

        final UpdateHostPasswordCommand command = new UpdateHostPasswordCommand(username, newPassword, hostIp);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getUpdateHostPasswdPath()).thenReturn("/tmp");
        when(libvirtUtilitiesHelper.buildScript(libvirtComputingResourceMock.getUpdateHostPasswdPath())).thenReturn(script);

        when(script.execute()).thenReturn(null);

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertTrue(answer.getResult());
    }

    @Test
    public void testUpdateHostPasswordCommandFail() {
        final LibvirtUtilitiesHelper libvirtUtilitiesHelper = Mockito.mock(LibvirtUtilitiesHelper.class);
        final Script script = Mockito.mock(Script.class);

        final String hostIp = "127.0.0.1";
        final String username = "root";
        final String newPassword = "password";

        final UpdateHostPasswordCommand command = new UpdateHostPasswordCommand(username, newPassword, hostIp);

        when(libvirtComputingResourceMock.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtComputingResourceMock.getUpdateHostPasswdPath()).thenReturn("/tmp");
        when(libvirtUtilitiesHelper.buildScript(libvirtComputingResourceMock.getUpdateHostPasswdPath())).thenReturn(script);

        when(script.execute()).thenReturn("#FAIL");

        final LibvirtRequestWrapper wrapper = LibvirtRequestWrapper.getInstance();
        assertNotNull(wrapper);

        final Answer answer = wrapper.execute(command, libvirtComputingResourceMock);

        assertFalse(answer.getResult());
    }

    @Test
    public void testIsInterface () {
        final LibvirtComputingResource lvcr = new LibvirtComputingResource();
        assertFalse(lvcr.isInterface("bla"));
        assertTrue(lvcr.isInterface("p99p00"));
        assertTrue(lvcr.isInterface("lo1"));
        assertTrue(lvcr.isInterface("lo_11"));
        assertTrue(lvcr.isInterface("lo_public_1"));
        assertTrue(lvcr.isInterface("dummy0"));
        assertTrue(lvcr.isInterface("dummy_0"));
        assertTrue(lvcr.isInterface("dummy_private_0"));
        for  (final String ifNamePattern : lvcr.ifNamePatterns) {
            // excluding regexps as "\\\\d+" won't replace with String.replaceAll(String,String);
            if (!ifNamePattern.contains("\\")) {
                final String ifName = ifNamePattern.replaceFirst("\\^", "") + "0";
                assertTrue("The pattern '" + ifNamePattern + "' is expected to be valid for interface " + ifName,lvcr.isInterface(ifName));
            }
        }
    }

    @Test
    public void testMemoryFreeInKBsDomainReturningOfSomeMemoryStatistics() throws LibvirtException {
        if (!System.getProperty("os.name").equals("Linux")) {
            return;
        }
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();

        MemoryStatistic[] mem = createMemoryStatisticFreeMemory100();
        Domain domainMock = getDomainConfiguredToReturnMemoryStatistic(mem);
        long memoryFreeInKBs = libvirtComputingResource.getMemoryFreeInKBs(domainMock);

        Assert.assertEquals(100, memoryFreeInKBs);
    }

    @Test
    public void testMemoryFreeInKBsDomainReturningNoMemoryStatistics() throws LibvirtException {
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();

        Domain domainMock = getDomainConfiguredToReturnMemoryStatistic(null);
        long memoryFreeInKBs = libvirtComputingResource.getMemoryFreeInKBs(domainMock);

        Assert.assertEquals(-1, memoryFreeInKBs);
    }

    @Test
    public void getMemoryFreeInKBsTestDomainReturningIncompleteArray() throws LibvirtException {
        if (!System.getProperty("os.name").equals("Linux")) {
            return;
        }
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();

        MemoryStatistic[] mem = createMemoryStatisticFreeMemory100();
        mem[0].setTag(0);
        Domain domainMock = getDomainConfiguredToReturnMemoryStatistic(mem);
        long memoryFreeInKBs = libvirtComputingResource.getMemoryFreeInKBs(domainMock);

        Assert.assertEquals(-1, memoryFreeInKBs);
    }

    private MemoryStatistic[] createMemoryStatisticFreeMemory100() {
        virDomainMemoryStats stat = new virDomainMemoryStats();
        stat.val = 100;
        stat.tag = 4;

        MemoryStatistic[] mem = new MemoryStatistic[1];
        mem[0] = new MemoryStatistic(stat);
        return mem;
    }

    private Domain getDomainConfiguredToReturnMemoryStatistic(MemoryStatistic[] mem) throws LibvirtException {
        Domain domainMock = Mockito.mock(Domain.class);
        when(domainMock.memoryStats(20)).thenReturn(mem);
        return domainMock;
    }

    @Test
    public void testSetQuotaAndPeriod() {
        double pct = 0.33d;
        Mockito.when(vmTO.isLimitCpuUse()).thenReturn(true);
        Mockito.when(vmTO.getCpuQuotaPercentage()).thenReturn(pct);
        CpuTuneDef cpuTuneDef = new CpuTuneDef();
        final LibvirtComputingResource lcr = new LibvirtComputingResource();
        lcr.setQuotaAndPeriod(vmTO, cpuTuneDef);
        Assert.assertEquals((int) (CpuTuneDef.DEFAULT_PERIOD * pct), cpuTuneDef.getQuota());
        Assert.assertEquals(CpuTuneDef.DEFAULT_PERIOD, cpuTuneDef.getPeriod());
    }

    @Test
    public void testSetQuotaAndPeriodNoCpuLimitUse() {
        Mockito.when(vmTO.isLimitCpuUse()).thenReturn(false);
        CpuTuneDef cpuTuneDef = new CpuTuneDef();
        final LibvirtComputingResource lcr = new LibvirtComputingResource();
        lcr.setQuotaAndPeriod(vmTO, cpuTuneDef);
        Assert.assertEquals(0, cpuTuneDef.getQuota());
        Assert.assertEquals(0, cpuTuneDef.getPeriod());
    }

    @Test
    public void testSetQuotaAndPeriodMinQuota() {
        double pct = 0.01d;
        Mockito.when(vmTO.isLimitCpuUse()).thenReturn(true);
        Mockito.when(vmTO.getCpuQuotaPercentage()).thenReturn(pct);
        CpuTuneDef cpuTuneDef = new CpuTuneDef();
        final LibvirtComputingResource lcr = new LibvirtComputingResource();
        lcr.setQuotaAndPeriod(vmTO, cpuTuneDef);
        Assert.assertEquals(CpuTuneDef.MIN_QUOTA, cpuTuneDef.getQuota());
        Assert.assertEquals((int) (CpuTuneDef.MIN_QUOTA / pct), cpuTuneDef.getPeriod());
    }

    @Test
    public void testUnknownCommand() {
        libvirtComputingResourceMock = new LibvirtComputingResource();
        Command cmd = new Command() {
            @Override public boolean executeInSequence() {
                return false;
            }
        };
        Answer ans = libvirtComputingResourceMock.executeRequest(cmd);
        assertTrue(ans instanceof UnsupportedAnswer);
    }

    @Test
    public void testKnownCommand() {
        libvirtComputingResourceMock = new LibvirtComputingResource();
        Command cmd = new PingTestCommand() {
            @Override public boolean executeInSequence() {
                throw new NullPointerException("test succeeded");
            }
        };
        Answer ans = libvirtComputingResourceMock.executeRequest(cmd);
        assertFalse(ans instanceof UnsupportedAnswer);
        assertTrue(ans instanceof Answer);
    }

    @Test
    public void testAddExtraConfigComponentEmptyExtraConfig() {
        libvirtComputingResourceMock = new LibvirtComputingResource();
        libvirtComputingResourceMock.addExtraConfigComponent(new HashMap<>(), vmDef);
        Mockito.verify(vmDef, never()).addComp(any());
    }

    @Test
    public void testAddExtraConfigComponentNotEmptyExtraConfig() {
        libvirtComputingResourceMock = new LibvirtComputingResource();
        Map<String, String> extraConfig = new HashMap<>();
        extraConfig.put("extraconfig-1", "value1");
        extraConfig.put("extraconfig-2", "value2");
        extraConfig.put("extraconfig-3", "value3");
        libvirtComputingResourceMock.addExtraConfigComponent(extraConfig, vmDef);
        Mockito.verify(vmDef, times(1)).addComp(any());
    }

    public void validateGetCurrentMemAccordingToMemBallooningWithoutMemBalooning(){
        VirtualMachineTO vmTo = Mockito.mock(VirtualMachineTO.class);
        Mockito.when(vmTo.getType()).thenReturn(Type.User);
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();
        libvirtComputingResource.noMemBalloon = true;
        long maxMemory = 2048;

        long currentMemory = libvirtComputingResource.getCurrentMemAccordingToMemBallooning(vmTo, maxMemory);
        Assert.assertEquals(maxMemory, currentMemory);
        Mockito.verify(vmTo, Mockito.times(0)).getMinRam();
    }

    @Test
    public void validateGetCurrentMemAccordingToMemBallooningWithtMemBalooning(){
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();
        libvirtComputingResource.noMemBalloon = false;

        long maxMemory = 2048;
        long minMemory = ByteScaleUtils.mebibytesToBytes(64);

        VirtualMachineTO vmTo = Mockito.mock(VirtualMachineTO.class);
        Mockito.when(vmTo.getType()).thenReturn(Type.User);
        Mockito.when(vmTo.getMinRam()).thenReturn(minMemory);

        long currentMemory = libvirtComputingResource.getCurrentMemAccordingToMemBallooning(vmTo, maxMemory);
        Assert.assertEquals(ByteScaleUtils.bytesToKibibytes(minMemory), currentMemory);
        Mockito.verify(vmTo).getMinRam();
    }

    @Test
    public void validateCreateGuestResourceDefWithVcpuMaxLimit(){
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();
        VirtualMachineTO vmTo = Mockito.mock(VirtualMachineTO.class);
        int maxCpu = 16;

        Mockito.when(vmTo.getVcpuMaxLimit()).thenReturn(maxCpu);

        LibvirtVMDef.GuestResourceDef grd = libvirtComputingResource.createGuestResourceDef(vmTo);
        Assert.assertEquals(maxCpu, grd.getMaxVcpu());
    }

    @Test
    public void validateCreateGuestResourceDefWithVcpuMaxLimitAsNull(){
        LibvirtComputingResource libvirtComputingResource = new LibvirtComputingResource();
        VirtualMachineTO vmTo = Mockito.mock(VirtualMachineTO.class);
        int min = 1;

        Mockito.when(vmTo.getCpus()).thenReturn(min);
        Mockito.when(vmTo.getVcpuMaxLimit()).thenReturn(null);

        LibvirtVMDef.GuestResourceDef grd = libvirtComputingResource.createGuestResourceDef(vmTo);
        Assert.assertEquals(min, grd.getMaxVcpu());
    }

    @Test
    public void validateGetDomainMemory() throws LibvirtException{
        long valueExpected = ByteScaleUtils.KiB;

        Mockito.doReturn(valueExpected).when(domainMock).getMaxMemory();
        Assert.assertEquals(valueExpected, LibvirtComputingResource.getDomainMemory(domainMock));
    }

    private VcpuInfo createVcpuInfoWithState(VcpuInfo.VcpuState state) {
        VcpuInfo vcpu = new VcpuInfo();
        vcpu.state = state;
        return vcpu;
    }

    @Test
    public void validateCountDomainRunningVcpus() throws LibvirtException{
        VcpuInfo vcpus[] = new VcpuInfo[5];
        long valueExpected = 3; // 3 vcpus with state VIR_VCPU_RUNNING

        vcpus[0] = createVcpuInfoWithState(VcpuInfo.VcpuState.VIR_VCPU_BLOCKED);
        vcpus[1] = createVcpuInfoWithState(VcpuInfo.VcpuState.VIR_VCPU_OFFLINE);
        vcpus[2] = createVcpuInfoWithState(VcpuInfo.VcpuState.VIR_VCPU_RUNNING);
        vcpus[3] = createVcpuInfoWithState(VcpuInfo.VcpuState.VIR_VCPU_RUNNING);
        vcpus[4] = createVcpuInfoWithState(VcpuInfo.VcpuState.VIR_VCPU_RUNNING);

        Mockito.doReturn(vcpus).when(domainMock).getVcpusInfo();
        long result =  LibvirtComputingResource.countDomainRunningVcpus(domainMock);

        Assert.assertEquals(valueExpected, result);
    }

    public void setDiskIoDriverTestIoUring() {
        DiskDef diskDef = configureAndTestSetDiskIoDriverTest(HYPERVISOR_LIBVIRT_VERSION_SUPPORTS_IOURING, HYPERVISOR_QEMU_VERSION_SUPPORTS_IOURING);
        Assert.assertEquals(IoDriverPolicy.IO_URING, diskDef.getIoDriver());
    }

    @Test
    public void setDiskIoDriverTestLibvirtSupportsIoUring() {
        DiskDef diskDef = configureAndTestSetDiskIoDriverTest(123l, HYPERVISOR_QEMU_VERSION_SUPPORTS_IOURING);
        Assert.assertNotEquals(IoDriverPolicy.IO_URING, diskDef.getIoDriver());
    }

    @Test
    public void setDiskIoDriverTestQemuSupportsIoUring() {
        DiskDef diskDef = configureAndTestSetDiskIoDriverTest(HYPERVISOR_LIBVIRT_VERSION_SUPPORTS_IOURING, 123l);
        Assert.assertNotEquals(IoDriverPolicy.IO_URING, diskDef.getIoDriver());
    }

    @Test
    public void setDiskIoDriverTestNoSupportToIoUring() {
        DiskDef diskDef = configureAndTestSetDiskIoDriverTest(123l, 123l);
        Assert.assertNotEquals(IoDriverPolicy.IO_URING, diskDef.getIoDriver());
    }

    private DiskDef configureAndTestSetDiskIoDriverTest(long hypervisorLibvirtVersion, long hypervisorQemuVersion) {
        DiskDef diskDef = new DiskDef();
        LibvirtComputingResource libvirtComputingResourceSpy = Mockito.spy(new LibvirtComputingResource());
        libvirtComputingResourceSpy.setDiskIoDriver(diskDef, IoDriverPolicy.IO_URING);
        return diskDef;
    }

    private SchedUlongParameter[] createSchedParametersWithCpuSharesOf2000 () {
        SchedUlongParameter[] params = new SchedUlongParameter[1];
        params[0] = new SchedUlongParameter();
        params[0].field = "cpu_shares";
        params[0].value = 2000;

        return params;
    }

    private SchedUlongParameter[] createSchedParametersWithoutCpuShares () {
        SchedUlongParameter[] params = new SchedUlongParameter[1];
        params[0] = new SchedUlongParameter();
        params[0].field = "weight";
        params[0].value = 200;

        return params;
    }

    @Test
    public void getCpuSharesTestReturnCpuSharesIfFound() throws LibvirtException {
        SchedUlongParameter[] cpuSharesOf2000 = createSchedParametersWithCpuSharesOf2000();

        Mockito.when(domainMock.getSchedulerParameters()).thenReturn(cpuSharesOf2000);
        int cpuShares = LibvirtComputingResource.getCpuShares(domainMock);

        Assert.assertEquals(2000, cpuShares);
    }

    @Test
    public void getCpuSharesTestReturnZeroIfCpuSharesNotFound() throws LibvirtException {
        SchedUlongParameter[] withoutCpuShares = createSchedParametersWithoutCpuShares();

        Mockito.when(domainMock.getSchedulerParameters()).thenReturn(withoutCpuShares);
        int actualValue = LibvirtComputingResource.getCpuShares(domainMock);

        Assert.assertEquals(0, actualValue);
    }

    @Test
    public void setCpuSharesTestSuccessfullySetCpuShares() throws LibvirtException {
        LibvirtComputingResource.setCpuShares(domainMock, 2000);
        Mockito.verify(domainMock, times(1)).setSchedulerParameters(Mockito.argThat(schedParameters -> {
            if (schedParameters == null || schedParameters.length > 1 || !(schedParameters[0] instanceof SchedUlongParameter)) {
                return false;
            }
            SchedUlongParameter param = (SchedUlongParameter) schedParameters[0];
            if (param.field != "cpu_shares" || param.value != 2000) {
                return false;
            }
            return true;
        }));
    }

    @Test
    public void testConfigureLocalStorageWithEmptyParams() throws ConfigurationException {
        libvirtComputingResourceSpy.configureLocalStorage();
    }

    @Test
    public void testConfigureLocalStorageWithMultiplePaths() throws ConfigurationException {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_PATH)))
                   .thenReturn("/var/lib/libvirt/images/,/var/lib/libvirt/images2/");
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_UUID)))
                   .thenReturn(UUID.randomUUID().toString() + "," + UUID.randomUUID().toString());

            libvirtComputingResourceSpy.configureLocalStorage();
        }
    }

    @Test(expected = ConfigurationException.class)
    public void testConfigureLocalStorageWithDifferentLength() throws Exception {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_PATH)))
                   .thenReturn("/var/lib/libvirt/images/,/var/lib/libvirt/images2/");
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_UUID)))
                   .thenReturn(UUID.randomUUID().toString());

            libvirtComputingResourceSpy.configureLocalStorage();
        }
    }

    @Test(expected = ConfigurationException.class)
    public void testConfigureLocalStorageWithInvalidUUID() throws ConfigurationException {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_PATH)))
                   .thenReturn("/var/lib/libvirt/images/");
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.LOCAL_STORAGE_UUID)))
                   .thenReturn("111111");

            libvirtComputingResourceSpy.configureLocalStorage();
        }
    }

    @Test
    public void defineResourceNetworkInterfacesTestUseProperties() {
        NetworkInterface networkInterfaceMock1 = Mockito.mock(NetworkInterface.class);
        NetworkInterface networkInterfaceMock2 = Mockito.mock(NetworkInterface.class);

        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class);
             MockedStatic<NetUtils> netUtilsMockedStatic = Mockito.mockStatic(NetUtils.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(any())).thenReturn("cloudbr15",
                    "cloudbr28");

            Mockito.when(NetUtils.getNetworkInterface(Mockito.anyString())).thenReturn(networkInterfaceMock1,
                    networkInterfaceMock2);

            libvirtComputingResourceSpy.defineResourceNetworkInterfaces(null);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            netUtilsMockedStatic.verify(() -> NetUtils.getNetworkInterface(keyCaptor.capture()), Mockito.times(2));


            List<String> keys = keyCaptor.getAllValues();
            Assert.assertEquals("cloudbr15", keys.get(0));
            Assert.assertEquals("cloudbr28", keys.get(1));

            Assert.assertEquals("cloudbr15", libvirtComputingResourceSpy.privBridgeName);
            Assert.assertEquals("cloudbr28", libvirtComputingResourceSpy.publicBridgeName);

            Assert.assertEquals(networkInterfaceMock1, libvirtComputingResourceSpy.getPrivateNic());
            Assert.assertEquals(networkInterfaceMock2, libvirtComputingResourceSpy.getPublicNic());
        }
    }

    @Test
    public void testGetNetworkStats() {
        doReturn(networkStats[0] + ":" + networkStats[1]).when(libvirtComputingResourceSpy).networkUsage(privateIp, "get", null, publicIp);
        doReturn(defaultStats[0] + ":" + defaultStats[1]).when(libvirtComputingResourceSpy).networkUsage(privateIp, "get", null, null);

        long[] stats = libvirtComputingResourceSpy.getNetworkStats(privateIp, publicIp);
        assertEquals(2, stats.length);
        assertEquals(networkStats[0], stats[0]);
        assertEquals(networkStats[1], stats[1]);

        stats = libvirtComputingResourceSpy.getNetworkStats(privateIp);
        assertEquals(2, stats.length);
        Assert.assertEquals(0, stats[0]);
        Assert.assertEquals(0, stats[1]);
    }

    @Test
    public void testGetVPCNetworkStats() {
        doReturn(vpcStats[0] + ":" + vpcStats[1]).when(libvirtComputingResourceSpy).configureVPCNetworkUsage(privateIp, publicIp, "get", null);
        doReturn(defaultStats[0] + ":" + defaultStats[1]).when(libvirtComputingResourceSpy).configureVPCNetworkUsage(privateIp, null, "get", null);

        long[] stats = libvirtComputingResourceSpy.getVPCNetworkStats(privateIp, publicIp, "get");
        assertEquals(2, stats.length);
        assertEquals(vpcStats[0], stats[0]);
        assertEquals(vpcStats[1], stats[1]);

        stats = libvirtComputingResourceSpy.getVPCNetworkStats(privateIp, null, "get");
        assertEquals(2, stats.length);
        Assert.assertEquals(0, stats[0]);
        Assert.assertEquals(0, stats[1]);
    }

    @Test
    public void testGetHaproxyStats() {
        doReturn(lbStats[0] + "").when(libvirtComputingResourceSpy).getHaproxyStats(privateIp, publicIp, port);
        long[] stats = libvirtComputingResourceSpy.getNetworkLbStats(privateIp, publicIp, port);
        assertEquals(1, stats.length);
        assertEquals(lbStats[0], stats[0]);

        doReturn("0").when(libvirtComputingResourceSpy).getHaproxyStats(privateIp, publicIp, port);
        stats = libvirtComputingResourceSpy.getNetworkLbStats(privateIp, publicIp, port);
        assertEquals(1, stats.length);
        Assert.assertEquals(0, stats[0]);
    }

    @Test
    public void testGetHaproxyStatsMethod() throws Exception {
        try (MockedConstruction<Script> scriptMockedConstruction = Mockito.mockConstruction(Script.class,
                (mock, context) -> {
                    doNothing().when(mock).add(Mockito.anyString());
                    when(mock.execute()).thenReturn(null);
                    when(mock.execute(any())).thenReturn(null);
                });
             MockedConstruction<OneLineParser> ignored = Mockito.mockConstruction(OneLineParser.class, (mock, context) -> {
                 when(mock.getLine()).thenReturn("result");
             })) {

            String result = libvirtComputingResourceSpy.getHaproxyStats(privateIp, publicIp, port);

            Assert.assertEquals("result", result);
            verify(scriptMockedConstruction.constructed().get(0), times(4)).add(Mockito.anyString());
            verify(scriptMockedConstruction.constructed().get(0)).add("get_haproxy_stats.sh");
            verify(scriptMockedConstruction.constructed().get(0)).add(privateIp);
            verify(scriptMockedConstruction.constructed().get(0)).add(publicIp);
            verify(scriptMockedConstruction.constructed().get(0)).add(String.valueOf(port));
        }
    }

   @Test
    public void testGetDiskPathFromDiskDefForRBD() {
        DiskDef diskDef = new DiskDef();
        diskDef.defNetworkBasedDisk("cloudstack/diskpath", "1.1.1.1", 3300, "username", "uuid", 0,
                DiskDef.DiskBus.VIRTIO, DiskDef.DiskProtocol.RBD, DiskDef.DiskFmtType.RAW);
        String diskPath = libvirtComputingResourceSpy.getDiskPathFromDiskDef(diskDef);
        Assert.assertEquals("diskpath", diskPath);
    }

    @Test
    public void testGetDiskPathFromDiskDefForNFS() {
        DiskDef diskDef = new DiskDef();
        diskDef.defFileBasedDisk("/mnt/pool/filepath", 0, DiskDef.DiskBus.VIRTIO, DiskDef.DiskFmtType.QCOW2);
        String diskPath = libvirtComputingResourceSpy.getDiskPathFromDiskDef(diskDef);
        Assert.assertEquals("filepath", diskPath);
    }

    @Test
    public void testGetDiskPathFromDiskDefForNFSWithNullPath() {
        DiskDef diskDef = new DiskDef();
        diskDef.defFileBasedDisk(null, 0, DiskDef.DiskBus.VIRTIO, DiskDef.DiskFmtType.QCOW2);
        String diskPath = libvirtComputingResourceSpy.getDiskPathFromDiskDef(diskDef);
        Assert.assertNull(diskPath);
    }

    @Test
    public void testGetDiskPathFromDiskDefForNFSWithUnsupportedPath() {
        DiskDef diskDef = new DiskDef();
        diskDef.defFileBasedDisk("/mnt/unsupported-path", 0, DiskDef.DiskBus.VIRTIO, DiskDef.DiskFmtType.QCOW2);
        String diskPath = libvirtComputingResourceSpy.getDiskPathFromDiskDef(diskDef);
        Assert.assertNull(diskPath);
    }

    @Test
    public void testNetworkUsageMethod1() throws Exception {
        try (MockedConstruction<Script> scriptMockedConstruction = Mockito.mockConstruction(Script.class,
                (mock, context) -> {
                    doNothing().when(mock).add(Mockito.anyString());
                    when(mock.execute()).thenReturn(null);
                    when(mock.execute(any())).thenReturn(null);
                }); MockedConstruction<OneLineParser> ignored2 = Mockito.mockConstruction(OneLineParser.class,
                (mock, context) -> {when(mock.getLine()).thenReturn("result");})) {

            String result = libvirtComputingResourceSpy.networkUsage(privateIp, "get", "eth0", publicIp);

            Assert.assertEquals("result", result);
            verify(scriptMockedConstruction.constructed().get(0), times(3)).add(Mockito.anyString());
            verify(scriptMockedConstruction.constructed().get(0)).add("netusage.sh");
            verify(scriptMockedConstruction.constructed().get(0)).add(privateIp);
            verify(scriptMockedConstruction.constructed().get(0)).add("-g");

            verify(scriptMockedConstruction.constructed().get(0)).add("-l", publicIp);
        }
    }

    @Test
    public void testNetworkUsageMethod2() throws Exception {
        try (MockedConstruction<Script> scriptMockedConstruction = Mockito.mockConstruction(Script.class,
                (mock, context) -> {
                    doNothing().when(mock).add(Mockito.anyString());
                    when(mock.execute()).thenReturn(null);
                    when(mock.execute(any())).thenReturn(null);
                }); MockedConstruction<OneLineParser> ignored2 = Mockito.mockConstruction(OneLineParser.class,
                (mock, context) -> {when(mock.getLine()).thenReturn("result");})) {

            String result = libvirtComputingResourceSpy.networkUsage(privateIp, "get", "eth0", null);

            Assert.assertEquals("result", result);
            verify(scriptMockedConstruction.constructed().get(0), times(3)).add(Mockito.anyString());
            verify(scriptMockedConstruction.constructed().get(0)).add("netusage.sh");
            verify(scriptMockedConstruction.constructed().get(0)).add(privateIp);
            verify(scriptMockedConstruction.constructed().get(0)).add("-g");
        }
    }

    @Test
    public void getVmsToSetMemoryBalloonStatsPeriodTestLibvirtError() throws LibvirtException {
        Mockito.when(connMock.listDomains()).thenThrow(LibvirtException.class);

        List<Integer> result = libvirtComputingResourceSpy.getVmsToSetMemoryBalloonStatsPeriod(connMock);

        Mockito.verify(loggerMock).error(Mockito.anyString(), (Throwable) any());
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void getVmsToSetMemoryBalloonStatsPeriodTestWithNoVMs() throws LibvirtException {
        Mockito.when(connMock.listDomains()).thenReturn(new int[0]);

        List<Integer> result = libvirtComputingResourceSpy.getVmsToSetMemoryBalloonStatsPeriod(connMock);

        Mockito.verify(loggerMock).info("Skipping the memory balloon stats period setting, since there are no VMs (active Libvirt domains) on this host.");
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void getVmsToSetMemoryBalloonStatsPeriodTestWhenSuccessfullyGetVmIds() throws LibvirtException {
        int[] fakeList = new int[]{1};
        List<Integer> expected = Arrays.asList(ArrayUtils.toObject(fakeList));
        Mockito.when(connMock.listDomains()).thenReturn(fakeList);

        List<Integer> result = libvirtComputingResourceSpy.getVmsToSetMemoryBalloonStatsPeriod(connMock);

        Assert.assertEquals(expected, result);
    }

    @Test
    public void getCurrentVmBalloonStatsPeriodTestWhenMemBalloonIsDisabled() {
        Integer expected = 0;
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.VM_MEMBALLOON_DISABLE)))
                   .thenReturn(true);

            Integer result = libvirtComputingResourceSpy.getCurrentVmBalloonStatsPeriod();

            Assert.assertEquals(expected, result);
        }
    }

    @Test
    public void getCurrentVmBalloonStatsPeriodTestWhenStatsPeriodIsZero() {
        Integer expected = 0;
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.VM_MEMBALLOON_DISABLE)))
                   .thenReturn(false);
            Mockito.when(
                           AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.VM_MEMBALLOON_STATS_PERIOD)))
                   .thenReturn(0);

            Integer result = libvirtComputingResourceSpy.getCurrentVmBalloonStatsPeriod();

            Mockito.verify(loggerMock).info(String.format(
                    "The [%s] property is set to '0', this prevents memory statistics from being displayed correctly. "
                            + "Adjust (increase) the value of this parameter to correct this.",
                    AgentProperties.VM_MEMBALLOON_STATS_PERIOD.getName()));
            Assert.assertEquals(expected, result);
        }
    }

    @Test
    public void getCurrentVmBalloonStatsPeriodTestSuccess() {
        Integer expected = 60;
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.VM_MEMBALLOON_DISABLE)))
                        .thenReturn(false);
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.VM_MEMBALLOON_STATS_PERIOD)))
                        .thenReturn(60);

            Integer result = libvirtComputingResourceSpy.getCurrentVmBalloonStatsPeriod();

            Assert.assertEquals(expected, result);
        }
    }

    private void prepareMocksToSetupMemoryBalloonStatsPeriod(Integer currentVmBalloonStatsPeriod) throws LibvirtException {
        Integer[] fakeList = ArrayUtils.toObject(new int[]{1});
        Mockito.doReturn(Arrays.asList(fakeList)).when(libvirtComputingResourceSpy).getVmsToSetMemoryBalloonStatsPeriod(connMock);
        Mockito.doReturn(currentVmBalloonStatsPeriod).when(libvirtComputingResourceSpy).getCurrentVmBalloonStatsPeriod();
        Mockito.when(domainMock.getXMLDesc(Mockito.anyInt())).thenReturn("");
        Mockito.when(domainMock.getName()).thenReturn("fake-VM-name");
        Mockito.when(connMock.domainLookupByID(1)).thenReturn(domainMock);
    }

    @Test
    public void setupMemoryBalloonStatsPeriodTestMemBalloonPropertyDisabled() throws LibvirtException {
        prepareMocksToSetupMemoryBalloonStatsPeriod(0);
        MemBalloonDef memBalloonDef = new MemBalloonDef();
        memBalloonDef.defVirtioMemBalloon("60");
        Mockito.when(parserMock.parseDomainXML(Mockito.anyString())).thenReturn(true);
        Mockito.when(parserMock.getMemBalloon()).thenReturn(memBalloonDef);
        try (MockedStatic<Script> ignored = Mockito.mockStatic(Script.class)) {
            Mockito.when(Script.runSimpleBashScript(any())).thenReturn(null);

            libvirtComputingResourceSpy.setupMemoryBalloonStatsPeriod(connMock);

            Mockito.verify(loggerMock).debug(
                    "The memory balloon stats period [0] has been set successfully for the VM (Libvirt Domain) with ID [1] and name [fake-VM-name].");
        }
    }

    @Test
    public void setupMemoryBalloonStatsPeriodTestErrorWhenSetNewPeriod() throws LibvirtException {
        prepareMocksToSetupMemoryBalloonStatsPeriod(60);
        MemBalloonDef memBalloonDef = new MemBalloonDef();
        memBalloonDef.defVirtioMemBalloon("0");
        Mockito.when(parserMock.parseDomainXML(Mockito.anyString())).thenReturn(true);
        Mockito.when(parserMock.getMemBalloon()).thenReturn(memBalloonDef);
        try (MockedStatic<Script> ignored = Mockito.mockStatic(Script.class)) {
            Mockito.when(Script.runSimpleBashScript(Mockito.eq("virsh dommemstat 1 --period 60 --live")))
                        .thenReturn("some-fake-error");

            libvirtComputingResourceSpy.setupMemoryBalloonStatsPeriod(connMock);

            Mockito.verify(loggerMock).error(
                    "Unable to set up memory balloon stats period for VM (Libvirt Domain) with ID [1] due to an error when running the [virsh "
                            + "dommemstat 1 --period 60 --live] command. Output: [some-fake-error].");
        }
    }

    @Test
    public void setupMemoryBalloonStatsPeriodTestSetNewPeriodSuccessfully() throws LibvirtException {
        prepareMocksToSetupMemoryBalloonStatsPeriod(60);
        MemBalloonDef memBalloonDef = new MemBalloonDef();
        memBalloonDef.defVirtioMemBalloon("0");
        Mockito.when(parserMock.parseDomainXML(Mockito.anyString())).thenReturn(true);
        Mockito.when(parserMock.getMemBalloon()).thenReturn(memBalloonDef);
        try (MockedStatic<Script> scriptMockedStatic = Mockito.mockStatic(Script.class)) {
            Mockito.when(Script.runSimpleBashScript(Mockito.eq("virsh dommemstat 1 --period 60 --live")))
                        .thenReturn(null);

            libvirtComputingResourceSpy.setupMemoryBalloonStatsPeriod(connMock);

            scriptMockedStatic.verify(() -> Script.runSimpleBashScript("virsh dommemstat 1 --period 60 --live"),
                    Mockito.times(1));
            Mockito.verify(loggerMock, Mockito.never()).error(Mockito.anyString());
        }
    }

    @Test
    public void setupMemoryBalloonStatsPeriodTestSkipVm() throws LibvirtException {
        prepareMocksToSetupMemoryBalloonStatsPeriod(60);
        MemBalloonDef memBalloonDef = new MemBalloonDef();
        memBalloonDef.defNoneMemBalloon();
        Mockito.when(parserMock.parseDomainXML(Mockito.anyString())).thenReturn(true);
        Mockito.when(parserMock.getMemBalloon()).thenReturn(memBalloonDef);

        libvirtComputingResourceSpy.setupMemoryBalloonStatsPeriod(connMock);

        Mockito.verify(loggerMock).debug("Skipping the memory balloon stats period setting for the VM (Libvirt Domain) with ID [1] and name [fake-VM-name] because this"
                + " VM has no memory balloon.");
    }

    @Test
    public void calculateCpuSharesTestMinSpeedNullAndHostCgroupV1ShouldNotConsiderCgroupLimit() {
        int cpuCores = 2;
        int cpuSpeed = 2000;
        int maxCpuShares = 0;
        int expectedCpuShares = 4000;

        Mockito.doReturn(cpuCores).when(vmTO).getCpus();
        Mockito.doReturn(null).when(vmTO).getMinSpeed();
        Mockito.doReturn(cpuSpeed).when(vmTO).getSpeed();
        Mockito.doReturn(maxCpuShares).when(libvirtComputingResourceSpy).getHostCpuMaxCapacity();
        int calculatedCpuShares = libvirtComputingResourceSpy.calculateCpuShares(vmTO);

        Assert.assertEquals(expectedCpuShares, calculatedCpuShares);
    }

    @Test
    public void calculateCpuSharesTestMinSpeedNotNullAndHostCgroupV1ShouldNotConsiderCgroupLimit() {
        int cpuCores = 2;
        int cpuSpeed = 2000;
        int maxCpuShares = 0;
        int expectedCpuShares = 4000;

        Mockito.doReturn(cpuCores).when(vmTO).getCpus();
        Mockito.doReturn(cpuSpeed).when(vmTO).getMinSpeed();
        Mockito.doReturn(maxCpuShares).when(libvirtComputingResourceSpy).getHostCpuMaxCapacity();
        int calculatedCpuShares = libvirtComputingResourceSpy.calculateCpuShares(vmTO);

        Assert.assertEquals(expectedCpuShares, calculatedCpuShares);
    }


    @Test
    public void calculateCpuSharesTestMinSpeedNullAndHostCgroupV2ShouldConsiderCgroupLimit() {
        int cpuCores = 2;
        int cpuSpeed = 2000;
        int maxCpuShares = 5000;
        int expectedCpuShares = 8000;

        Mockito.doReturn(cpuCores).when(vmTO).getCpus();
        Mockito.doReturn(null).when(vmTO).getMinSpeed();
        Mockito.doReturn(cpuSpeed).when(vmTO).getSpeed();
        Mockito.doReturn(maxCpuShares).when(libvirtComputingResourceSpy).getHostCpuMaxCapacity();
        int calculatedCpuShares = libvirtComputingResourceSpy.calculateCpuShares(vmTO);

        Assert.assertEquals(expectedCpuShares, calculatedCpuShares);
    }

    @Test
    public void calculateCpuSharesTestMinSpeedNotNullAndHostCgroupV2ShouldConsiderCgroupLimit() {
        int cpuCores = 2;
        int cpuSpeed = 2000;
        int maxCpuShares = 5000;
        int expectedCpuShares = 8000;

        Mockito.doReturn(cpuCores).when(vmTO).getCpus();
        Mockito.doReturn(cpuSpeed).when(vmTO).getMinSpeed();
        Mockito.doReturn(maxCpuShares).when(libvirtComputingResourceSpy).getHostCpuMaxCapacity();
        int calculatedCpuShares = libvirtComputingResourceSpy.calculateCpuShares(vmTO);

        Assert.assertEquals(expectedCpuShares, calculatedCpuShares);
    }

    @Test
    public void setMaxHostCpuSharesIfCGroupV2TestShouldCalculateMaxCpuCapacityIfHostUtilizesCgroupV2() {
        int cpuCores = 2;
        long cpuSpeed = 2500L;
        int expectedShares = 5000;

        String hostCgroupVersion = LibvirtComputingResource.CGROUP_V2;
        try (MockedStatic<Script> ignored = Mockito.mockStatic(Script.class)) {
            Mockito.when(Script.runSimpleBashScript(Mockito.anyString())).thenReturn(hostCgroupVersion);

            libvirtComputingResourceSpy.calculateHostCpuMaxCapacity(cpuCores, cpuSpeed);

            Assert.assertEquals(expectedShares, libvirtComputingResourceSpy.getHostCpuMaxCapacity());
        }
    }

    @Test
    public void setMaxHostCpuSharesIfCGroupV2TestShouldNotCalculateMaxCpuCapacityIfHostDoesNotUtilizesCgroupV2() {
        int cpuCores = 2;
        long cpuSpeed = 2500L;
        int expectedShares = 0;

        String hostCgroupVersion = "tmpfs";
        try (MockedStatic<Script> ignored = Mockito.mockStatic(Script.class)) {
            Mockito.when(Script.runSimpleBashScript(Mockito.anyString())).thenReturn(hostCgroupVersion);

            libvirtComputingResourceSpy.calculateHostCpuMaxCapacity(cpuCores, cpuSpeed);

            Assert.assertEquals(expectedShares, libvirtComputingResourceSpy.getHostCpuMaxCapacity());
        }
    }

    @Test
    public void testGetHostTags() throws ConfigurationException {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.HOST_TAGS)))
                    .thenReturn("aa,bb,cc,dd");

            List<String> hostTagsList = libvirtComputingResourceSpy.getHostTags();
            Assert.assertEquals(4, hostTagsList.size());
            Assert.assertEquals("aa,bb,cc,dd", StringUtils.join(hostTagsList, ","));
        }
    }

    @Test
    public void testGetHostTagsWithSpace() throws ConfigurationException {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.HOST_TAGS)))
                    .thenReturn(" aa, bb , cc , dd ");

            List<String> hostTagsList = libvirtComputingResourceSpy.getHostTags();
            Assert.assertEquals(4, hostTagsList.size());
            Assert.assertEquals("aa,bb,cc,dd", StringUtils.join(hostTagsList, ","));
        }
    }

    @Test
    public void testGetHostTagsWithEmptyPropertyValue() throws ConfigurationException {
        try (MockedStatic<AgentPropertiesFileHandler> ignored = Mockito.mockStatic(AgentPropertiesFileHandler.class)) {
            Mockito.when(AgentPropertiesFileHandler.getPropertyValue(Mockito.eq(AgentProperties.HOST_TAGS)))
                    .thenReturn(" ");

            List<String> hostTagsList = libvirtComputingResourceSpy.getHostTags();
            Assert.assertEquals(0, hostTagsList.size());
            Assert.assertEquals("", StringUtils.join(hostTagsList, ","));
        }
    }

    @Test
    public void getVmStatTestVmIsNullReturnsNull() throws LibvirtException {
        doReturn(null).when(libvirtComputingResourceSpy).getDomain(connMock, VM_NAME);

        VmStatsEntry stat = libvirtComputingResourceSpy.getVmStat(connMock, VM_NAME);

        verify(libvirtComputingResourceSpy).getDomain(connMock, VM_NAME);
        verify(libvirtComputingResourceSpy, never()).getVmCurrentStats(domainMock);
        verify(libvirtComputingResourceSpy, never()).calculateVmMetrics(Mockito.any(), Mockito.any(), Mockito.any());
        Assert.assertNull(stat);
    }

    @Test
    public void getVmStatTestVmIsNotNullReturnsMetrics() throws LibvirtException {
        doReturn(domainMock).when(libvirtComputingResourceSpy).getDomain(connMock, VM_NAME);
        doReturn(Mockito.mock(LibvirtExtendedVmStatsEntry.class)).when(libvirtComputingResourceSpy).getVmCurrentStats(domainMock);
        doReturn(Mockito.mock(VmStatsEntry.class)).when(libvirtComputingResourceSpy).calculateVmMetrics(Mockito.any(), Mockito.any(), Mockito.any());

        VmStatsEntry stat = libvirtComputingResourceSpy.getVmStat(connMock, VM_NAME);

        verify(libvirtComputingResourceSpy).getDomain(connMock, VM_NAME);
        verify(libvirtComputingResourceSpy).getVmCurrentStats(domainMock);
        verify(libvirtComputingResourceSpy).calculateVmMetrics(Mockito.any(), Mockito.any(), Mockito.any());
        Assert.assertNotNull(stat);
    }

    private void prepareVmInfoForGetVmCurrentStats() throws LibvirtException {
        final NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.cpus = 8;
        nodeInfo.memory = 8 * 1024 * 1024;
        nodeInfo.sockets = 2;
        nodeInfo.threads = 2;
        nodeInfo.model = "Foo processor";

        Mockito.when(domainMock.getName()).thenReturn(VM_NAME);
        Mockito.when(domainMock.getConnect()).thenReturn(connMock);
        domainInfoMock.cpuTime = 500L;
        domainInfoMock.nrVirtCpu = 4;
        domainInfoMock.memory = 2048;
        domainInfoMock.maxMem = 4096;
        Mockito.when(domainMock.getInfo()).thenReturn(domainInfoMock);
        final MemoryStatistic[] domainMem = new MemoryStatistic[2];
        domainMem[0] = Mockito.mock(MemoryStatistic.class);
        doReturn(1024L).when(libvirtComputingResourceSpy).getMemoryFreeInKBs(domainMock);

        domainInterfaceStatsMock.rx_bytes = 1000L;
        domainInterfaceStatsMock.tx_bytes = 2000L;
        doReturn(domainInterfaceStatsMock).when(domainMock).interfaceStats(Mockito.any());
        doReturn(List.of(new InterfaceDef())).when(libvirtComputingResourceSpy).getInterfaces(connMock, VM_NAME);

        domainBlockStatsMock.rd_req = 3000L;
        domainBlockStatsMock.rd_bytes = 4000L;
        domainBlockStatsMock.wr_req = 5000L;
        domainBlockStatsMock.wr_bytes = 6000L;
        doReturn(domainBlockStatsMock).when(domainMock).blockStats(Mockito.any());
        doReturn(List.of(new DiskDef())).when(libvirtComputingResourceSpy).getDisks(connMock, VM_NAME);
    }

    @Test
    public void getVmCurrentStatsTestIfStatsAreAsExpected() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();

        LibvirtExtendedVmStatsEntry vmStatsEntry = libvirtComputingResourceSpy.getVmCurrentStats(domainMock);

        Assert.assertEquals(domainInfoMock.cpuTime, vmStatsEntry.getCpuTime());
        Assert.assertEquals((double) domainInterfaceStatsMock.rx_bytes / 1024, vmStatsEntry.getNetworkReadKBs(), 0);
        Assert.assertEquals((double) domainInterfaceStatsMock.tx_bytes / 1024, vmStatsEntry.getNetworkWriteKBs(), 0);
        Assert.assertEquals(domainBlockStatsMock.rd_req, vmStatsEntry.getDiskReadIOs(), 0);
        Assert.assertEquals((double) domainBlockStatsMock.rd_bytes / 1024, vmStatsEntry.getDiskReadKBs(), 0);
        Assert.assertEquals(domainBlockStatsMock.wr_req, vmStatsEntry.getDiskWriteIOs(), 0);
        Assert.assertEquals((double) domainBlockStatsMock.wr_bytes / 1024, vmStatsEntry.getDiskWriteKBs(), 0);
        Assert.assertNotNull(vmStatsEntry.getTimestamp());
    }

    @Test
    public void getVmCurrentCpuStatsTestIfStatsAreAsExpected() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();

        LibvirtExtendedVmStatsEntry vmStatsEntry = new LibvirtExtendedVmStatsEntry();
        libvirtComputingResourceSpy.getVmCurrentCpuStats(domainMock, vmStatsEntry);

        Assert.assertEquals(domainInfoMock.cpuTime, vmStatsEntry.getCpuTime());
    }

    @Test
    public void getVmCurrentNetworkStatsTestIfStatsAreAsExpected() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();

        LibvirtExtendedVmStatsEntry vmStatsEntry = new LibvirtExtendedVmStatsEntry();
        libvirtComputingResourceSpy.getVmCurrentNetworkStats(domainMock, vmStatsEntry);

        Assert.assertEquals((double) domainInterfaceStatsMock.rx_bytes / 1024, vmStatsEntry.getNetworkReadKBs(), 0);
        Assert.assertEquals((double) domainInterfaceStatsMock.tx_bytes / 1024, vmStatsEntry.getNetworkWriteKBs(), 0);
    }

    @Test
    public void getVmCurrentDiskStatsTestIfStatsAreAsExpected() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();

        LibvirtExtendedVmStatsEntry vmStatsEntry = new LibvirtExtendedVmStatsEntry();
        libvirtComputingResourceSpy.getVmCurrentDiskStats(domainMock, vmStatsEntry);

        Assert.assertEquals(domainBlockStatsMock.rd_req, vmStatsEntry.getDiskReadIOs(), 0);
        Assert.assertEquals((double) domainBlockStatsMock.rd_bytes / 1024, vmStatsEntry.getDiskReadKBs(), 0);
        Assert.assertEquals(domainBlockStatsMock.wr_req, vmStatsEntry.getDiskWriteIOs(), 0);
        Assert.assertEquals((double) domainBlockStatsMock.wr_bytes / 1024, vmStatsEntry.getDiskWriteKBs(), 0);
    }

    @Test
    public void calculateVmMetricsTestOldStatsIsNullDoesNotCalculateUtilization() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();

        LibvirtExtendedVmStatsEntry vmStatsEntry = libvirtComputingResourceSpy.getVmCurrentStats(domainMock);
        VmStatsEntry metrics = libvirtComputingResourceSpy.calculateVmMetrics(domainMock, null, vmStatsEntry);

        Assert.assertEquals(domainInfoMock.nrVirtCpu, metrics.getNumCPUs());
        Assert.assertEquals(domainInfoMock.maxMem, (long) metrics.getMemoryKBs());
        Assert.assertEquals(libvirtComputingResourceSpy.getMemoryFreeInKBs(domainMock), (long) metrics.getIntFreeMemoryKBs());
        Assert.assertEquals(domainInfoMock.memory, (long) metrics.getTargetMemoryKBs());
        Assert.assertEquals(0, metrics.getCPUUtilization(), 0);
        Assert.assertEquals(0, metrics.getNetworkReadKBs(), 0);
        Assert.assertEquals(0, metrics.getNetworkWriteKBs(), 0);
        Assert.assertEquals(0, metrics.getDiskReadKBs(), 0);
        Assert.assertEquals(0, metrics.getDiskReadIOs(), 0);
        Assert.assertEquals(0, metrics.getDiskWriteKBs(), 0);
        Assert.assertEquals(0, metrics.getDiskWriteIOs(), 0);
    }

    @Test
    public void calculateVmMetricsTestOldStatsIsNotNullCalculatesUtilization() throws LibvirtException {
        prepareVmInfoForGetVmCurrentStats();
        LibvirtExtendedVmStatsEntry oldStats = libvirtComputingResourceSpy.getVmCurrentStats(domainMock);
        domainInfoMock.cpuTime *= 3;
        domainInterfaceStatsMock.rx_bytes *= 3;
        domainInterfaceStatsMock.tx_bytes *= 3;
        domainBlockStatsMock.rd_req *= 3;
        domainBlockStatsMock.rd_bytes *= 3;
        domainBlockStatsMock.wr_req *= 3;
        domainBlockStatsMock.wr_bytes *= 3;
        LibvirtExtendedVmStatsEntry newStats = libvirtComputingResourceSpy.getVmCurrentStats(domainMock);

        VmStatsEntry metrics = libvirtComputingResourceSpy.calculateVmMetrics(domainMock, oldStats, newStats);

        Assert.assertEquals(domainInfoMock.nrVirtCpu, metrics.getNumCPUs());
        Assert.assertEquals(domainInfoMock.maxMem, (long) metrics.getMemoryKBs());
        Assert.assertEquals(libvirtComputingResourceSpy.getMemoryFreeInKBs(domainMock), (long) metrics.getIntFreeMemoryKBs());
        Assert.assertEquals(domainInfoMock.memory, (long) metrics.getTargetMemoryKBs());
        Assert.assertTrue(metrics.getCPUUtilization() > 0);
        Assert.assertEquals(newStats.getNetworkReadKBs() - oldStats.getNetworkReadKBs(), metrics.getNetworkReadKBs(), 0);
        Assert.assertEquals(newStats.getNetworkWriteKBs() - oldStats.getNetworkWriteKBs(), metrics.getNetworkWriteKBs(), 0);
        Assert.assertEquals(newStats.getDiskReadIOs() - oldStats.getDiskReadIOs(), metrics.getDiskReadIOs(), 0);
        Assert.assertEquals(newStats.getDiskWriteIOs() - oldStats.getDiskWriteIOs(), metrics.getDiskWriteIOs(), 0);
        Assert.assertEquals(newStats.getDiskReadKBs() - oldStats.getDiskReadKBs(), metrics.getDiskReadKBs(), 0);
        Assert.assertEquals(newStats.getDiskWriteKBs() - oldStats.getDiskWriteKBs(), metrics.getDiskWriteKBs(), 0);
    }

    @Test
    public void createLinstorVdb() throws LibvirtException, InternalErrorException, URISyntaxException {
        final Connect connect = Mockito.mock(Connect.class);

        final int id = random.nextInt(65534);
        final String name = "test-instance-1";

        final int cpus = 2;
        final int speed = 1024;
        final int minRam = 256 * 1024;
        final int maxRam = 512 * 1024;
        final String os = "Ubuntu";
        final String vncPassword = "mySuperSecretPassword";

        final VirtualMachineTO to = new VirtualMachineTO(id, name, VirtualMachine.Type.User, cpus, speed, minRam,
                maxRam, BootloaderType.HVM, os, false, false, vncPassword);
        to.setVncAddr("");
        to.setArch("x86_64");
        to.setUuid("b0f0a72d-7efb-3cad-a8ff-70ebf30b3af9");
        to.setVcpuMaxLimit(cpus + 1);
        final HashMap<String, String> vmToDetails = new HashMap<>();
        to.setDetails(vmToDetails);

        String diskLinPath = "9ebe53c1-3d35-46e5-b7aa-6fc223ba0fcf";
        final DiskTO diskTO = new DiskTO();
        diskTO.setDiskSeq(1L);
        diskTO.setType(Volume.Type.ROOT);
        diskTO.setDetails(new HashMap<>());
        diskTO.setPath(diskLinPath);

        final PrimaryDataStoreTO primaryDataStoreTO = Mockito.mock(PrimaryDataStoreTO.class);
        String pDSTOUUID = "9ebe53c1-3d35-46e5-b7aa-6fc223ac4fcf";
        when(primaryDataStoreTO.getPoolType()).thenReturn(StoragePoolType.Linstor);
        when(primaryDataStoreTO.getUuid()).thenReturn(pDSTOUUID);

        VolumeObjectTO dataTO = new VolumeObjectTO();

        dataTO.setUuid("12be53c1-3d35-46e5-b7aa-6fc223ba0f34");
        dataTO.setPath(diskTO.getPath());
        dataTO.setDataStore(primaryDataStoreTO);
        diskTO.setData(dataTO);
        to.setDisks(new DiskTO[]{diskTO});

        String path = "/dev/drbd1020";
        final KVMStoragePoolManager storagePoolMgr = Mockito.mock(KVMStoragePoolManager.class);
        final KVMStoragePool storagePool = Mockito.mock(KVMStoragePool.class);
        final KVMPhysicalDisk vol = Mockito.mock(KVMPhysicalDisk.class);

        when(libvirtComputingResourceSpy.getStoragePoolMgr()).thenReturn(storagePoolMgr);
        when(storagePool.getType()).thenReturn(StoragePoolType.Linstor);
        when(storagePoolMgr.getPhysicalDisk(StoragePoolType.Linstor, pDSTOUUID, diskLinPath)).thenReturn(vol);
        when(vol.getPath()).thenReturn(path);
        when(vol.getPool()).thenReturn(storagePool);
        when(vol.getFormat()).thenReturn(PhysicalDiskFormat.RAW);

        // 1. test Bus: IDE and broken qemu version -> NO discard
        when(libvirtComputingResourceSpy.getHypervisorQemuVersion()).thenReturn(6000000L);
        vmToDetails.put(VmDetailConstants.ROOT_DISK_CONTROLLER, DiskDef.DiskBus.IDE.name());
        {
            LibvirtVMDef vm = new LibvirtVMDef();
            vm.addComp(new DevicesDef());
            libvirtComputingResourceSpy.createVbd(connect, to, name, vm);

            DiskDef rootDisk = vm.getDevices().getDisks().get(0);
            assertEquals(DiskDef.DiskType.BLOCK, rootDisk.getDiskType());
            assertEquals(DiskDef.DiskBus.IDE, rootDisk.getBusType());
            assertEquals(DiskDef.DiscardType.IGNORE, rootDisk.getDiscard());
        }

        // 2. test Bus: VIRTIO and broken qemu version -> discard unmap
        vmToDetails.put(VmDetailConstants.ROOT_DISK_CONTROLLER, DiskDef.DiskBus.VIRTIO.name());
        {
            LibvirtVMDef vm = new LibvirtVMDef();
            vm.addComp(new DevicesDef());
            libvirtComputingResourceSpy.createVbd(connect, to, name, vm);

            DiskDef rootDisk = vm.getDevices().getDisks().get(0);
            assertEquals(DiskDef.DiskType.BLOCK, rootDisk.getDiskType());
            assertEquals(DiskDef.DiskBus.VIRTIO, rootDisk.getBusType());
            assertEquals(DiskDef.DiscardType.UNMAP, rootDisk.getDiscard());
        }

        // 3. test Bus; IDE and "good" qemu version -> discard unmap
        vmToDetails.put(VmDetailConstants.ROOT_DISK_CONTROLLER, DiskDef.DiskBus.IDE.name());
        when(libvirtComputingResourceSpy.getHypervisorQemuVersion()).thenReturn(7000000L);
        {
            LibvirtVMDef vm = new LibvirtVMDef();
            vm.addComp(new DevicesDef());
            libvirtComputingResourceSpy.createVbd(connect, to, name, vm);

            DiskDef rootDisk = vm.getDevices().getDisks().get(0);
            assertEquals(DiskDef.DiskType.BLOCK, rootDisk.getDiskType());
            assertEquals(DiskDef.DiskBus.IDE, rootDisk.getBusType());
            assertEquals(DiskDef.DiscardType.UNMAP, rootDisk.getDiscard());
        }

        // 4. test Bus: VIRTIO and "good" qemu version -> discard unmap
        vmToDetails.put(VmDetailConstants.ROOT_DISK_CONTROLLER, DiskDef.DiskBus.VIRTIO.name());
        {
            LibvirtVMDef vm = new LibvirtVMDef();
            vm.addComp(new DevicesDef());
            libvirtComputingResourceSpy.createVbd(connect, to, name, vm);

            DiskDef rootDisk = vm.getDevices().getDisks().get(0);
            assertEquals(DiskDef.DiskType.BLOCK, rootDisk.getDiskType());
            assertEquals(DiskDef.DiskBus.VIRTIO, rootDisk.getBusType());
            assertEquals(DiskDef.DiscardType.UNMAP, rootDisk.getDiscard());
        }
    }

    @Test
    public void testGetDiskModelFromVMDetailVirtioBlk() {
        VirtualMachineTO virtualMachineTO = Mockito.mock(VirtualMachineTO.class);
        Map<String, String> details = new HashMap<>();
        details.put(VmDetailConstants.ROOT_DISK_CONTROLLER, "virtio-blk");
        Mockito.when(virtualMachineTO.getDetails()).thenReturn(details);
        DiskDef.DiskBus diskBus = libvirtComputingResourceSpy.getDiskModelFromVMDetail(virtualMachineTO);
        assertEquals(DiskDef.DiskBus.VIRTIOBLK, diskBus);
    }

    @Test
    public void testCreateTpmDef() {
        VirtualMachineTO virtualMachineTO = Mockito.mock(VirtualMachineTO.class);
        Map<String, String> details = new HashMap<>();
        details.put(VmDetailConstants.VIRTUAL_TPM_MODEL, "tpm-tis");
        details.put(VmDetailConstants.VIRTUAL_TPM_VERSION, "2.0");
        Mockito.when(virtualMachineTO.getDetails()).thenReturn(details);
        LibvirtVMDef.TpmDef tpmDef = libvirtComputingResourceSpy.createTpmDef(virtualMachineTO);
        assertEquals(LibvirtVMDef.TpmDef.TpmModel.TIS, tpmDef.getModel());
        assertEquals(LibvirtVMDef.TpmDef.TpmVersion.V2_0, tpmDef.getVersion());
    }

    @Test
    public void testCreateTpmDefWithInvalidVersion() {
        VirtualMachineTO virtualMachineTO = Mockito.mock(VirtualMachineTO.class);
        Map<String, String> details = new HashMap<>();
        details.put(VmDetailConstants.VIRTUAL_TPM_MODEL, "tpm-crb");
        details.put(VmDetailConstants.VIRTUAL_TPM_VERSION, "3.0");
        Mockito.when(virtualMachineTO.getDetails()).thenReturn(details);
        LibvirtVMDef.TpmDef tpmDef = libvirtComputingResourceSpy.createTpmDef(virtualMachineTO);
        assertEquals(LibvirtVMDef.TpmDef.TpmModel.CRB, tpmDef.getModel());
        assertEquals(LibvirtVMDef.TpmDef.TpmVersion.V2_0, tpmDef.getVersion());
    }

    @Test
    public void recreateCheckpointsOnVmTestVersionIsNotSufficient() {
        Mockito.doThrow(new CloudRuntimeException("")).when(libvirtComputingResourceSpy).validateLibvirtAndQemuVersionForIncrementalSnapshots();

        boolean result = libvirtComputingResourceSpy.recreateCheckpointsOnVm(List.of(volumeObjectToMock), null, null);

        Assert.assertFalse(result);
    }

    @Test
    public void recreateCheckpointsOnVmTestVolumesDoNotHaveCheckpoints() {
        Mockito.doNothing().when(libvirtComputingResourceSpy).validateLibvirtAndQemuVersionForIncrementalSnapshots();

        Mockito.doReturn(null).when(libvirtComputingResourceSpy).getDisks(Mockito.any(), Mockito.any());
        Mockito.doReturn(null).when(libvirtComputingResourceSpy).mapVolumeToDiskDef(Mockito.any(), Mockito.any());

        boolean result = libvirtComputingResourceSpy.recreateCheckpointsOnVm(List.of(volumeObjectToMock), null, null);

        Mockito.verify(libvirtComputingResourceSpy, Mockito.never()).recreateCheckpointsOfDisk(Mockito.any(), Mockito.any(), Mockito.any());
        Assert.assertTrue(result);
    }

    @Test
    public void recreateCheckpointsOnVmTestVolumesHaveCheckpoints() {
        Mockito.doNothing().when(libvirtComputingResourceSpy).validateLibvirtAndQemuVersionForIncrementalSnapshots();

        Mockito.doReturn(null).when(libvirtComputingResourceSpy).getDisks(Mockito.any(), Mockito.any());
        Mockito.doReturn(null).when(libvirtComputingResourceSpy).mapVolumeToDiskDef(Mockito.any(), Mockito.any());

        Mockito.doReturn(List.of("path")).when(volumeObjectToMock).getCheckpointPaths();

        Mockito.doNothing().when(libvirtComputingResourceSpy)
                .recreateCheckpointsOfDisk(Mockito.any(), Mockito.any(), Mockito.any());

        boolean result = libvirtComputingResourceSpy.recreateCheckpointsOnVm(List.of(volumeObjectToMock), null, null);

        Mockito.verify(libvirtComputingResourceSpy, Mockito.times(1))
                .recreateCheckpointsOfDisk(Mockito.any(), Mockito.any(), Mockito.any());
        Assert.assertTrue(result);
    }

    @Test
    public void getSnapshotTemporaryPathTestReturnExpectedResult(){
        String path = "/path/to/disk";
        String snapshotName = "snapshot";
        String expectedResult = "/path/to/snapshot";

        String result = libvirtComputingResourceSpy.getSnapshotTemporaryPath(path, snapshotName);
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void mergeSnapshotIntoBaseFileTestActiveAndDeleteFlags() throws Exception {
        libvirtComputingResourceSpy.qcow2DeltaMergeTimeout = 10;

        try (MockedStatic<LibvirtUtilitiesHelper> libvirtUtilitiesHelperMockedStatic = Mockito.mockStatic(LibvirtUtilitiesHelper.class);
                MockedStatic<ThreadContext> threadContextMockedStatic = Mockito.mockStatic(ThreadContext.class)) {
            libvirtUtilitiesHelperMockedStatic.when(() ->
                    LibvirtUtilitiesHelper.isLibvirtSupportingFlagDeleteOnCommandVirshBlockcommit(Mockito.any())).thenAnswer(invocation -> true);
            Mockito.doReturn(new Semaphore(1)).when(libvirtComputingResourceSpy).getSemaphoreToWaitForMerge();

            threadContextMockedStatic.when(() ->
                    ThreadContext.get(Mockito.anyString())).thenReturn("logid");
            Mockito.doNothing().when(domainMock).addBlockJobListener(Mockito.any());
            Mockito.doReturn(null).when(domainMock).getBlockJobInfo(Mockito.anyString(), Mockito.anyInt());
            Mockito.doNothing().when(domainMock).removeBlockJobListener(Mockito.any());

            String diskLabel = "vda";
            String baseFilePath = "/file";
            String snapshotName = "snap";

            libvirtComputingResourceSpy.mergeSnapshotIntoBaseFileWithEventsAndConfigurableTimeout(domainMock, diskLabel, baseFilePath, null, true, snapshotName, volumeObjectToMock, connMock);

            Mockito.verify(domainMock, Mockito.times(1)).blockCommit(diskLabel, baseFilePath, null, 0, Domain.BlockCommitFlags.ACTIVE | Domain.BlockCommitFlags.DELETE);
            Mockito.verify(libvirtComputingResourceSpy, Mockito.times(1)).manuallyDeleteUnusedSnapshotFile(true, "/" + snapshotName);
        }
    }

    @Test
    public void mergeSnapshotIntoBaseFileTestActiveFlag() throws Exception {
        try (MockedStatic<LibvirtUtilitiesHelper> libvirtUtilitiesHelperMockedStatic = Mockito.mockStatic(LibvirtUtilitiesHelper.class);
                MockedStatic<ThreadContext> threadContextMockedStatic = Mockito.mockStatic(ThreadContext.class)) {
            libvirtUtilitiesHelperMockedStatic.when(() ->
                    LibvirtUtilitiesHelper.isLibvirtSupportingFlagDeleteOnCommandVirshBlockcommit(Mockito.any())).thenAnswer(invocation -> false);
            Mockito.doReturn(new Semaphore(1)).when(libvirtComputingResourceSpy).getSemaphoreToWaitForMerge();

            threadContextMockedStatic.when(() ->
                    ThreadContext.get(Mockito.anyString())).thenReturn("logid");
            Mockito.doNothing().when(domainMock).addBlockJobListener(Mockito.any());
            Mockito.doNothing().when(domainMock).removeBlockJobListener(Mockito.any());
            Mockito.doNothing().when(libvirtComputingResourceSpy).manuallyDeleteUnusedSnapshotFile(Mockito.anyBoolean(), Mockito.anyString());

            String diskLabel = "vda";
            String baseFilePath = "/file";
            String snapshotName = "snap";

            libvirtComputingResourceSpy.mergeSnapshotIntoBaseFileWithEventsAndConfigurableTimeout(domainMock, diskLabel, baseFilePath, null, true, snapshotName, volumeObjectToMock, connMock);

            Mockito.verify(domainMock, Mockito.times(1)).blockCommit(diskLabel, baseFilePath, null, 0, Domain.BlockCommitFlags.ACTIVE);
            Mockito.verify(libvirtComputingResourceSpy, Mockito.times(1)).manuallyDeleteUnusedSnapshotFile(false, "/" + snapshotName);
        }
    }

    @Test
    public void mergeSnapshotIntoBaseFileTestDeleteFlag() throws Exception {
        try (MockedStatic<LibvirtUtilitiesHelper> libvirtUtilitiesHelperMockedStatic = Mockito.mockStatic(LibvirtUtilitiesHelper.class);
                MockedStatic<ThreadContext> threadContextMockedStatic = Mockito.mockStatic(ThreadContext.class)) {
            libvirtComputingResourceSpy.qcow2DeltaMergeTimeout = 10;
            libvirtUtilitiesHelperMockedStatic.when(() -> LibvirtUtilitiesHelper.isLibvirtSupportingFlagDeleteOnCommandVirshBlockcommit(Mockito.any())).thenReturn(true);
            Mockito.doReturn(new Semaphore(1)).when(libvirtComputingResourceSpy).getSemaphoreToWaitForMerge();
            threadContextMockedStatic.when(() -> ThreadContext.get(Mockito.anyString())).thenReturn("logid");
            Mockito.doNothing().when(domainMock).addBlockJobListener(Mockito.any());
            Mockito.doReturn(null).when(domainMock).getBlockJobInfo(Mockito.anyString(), Mockito.anyInt());
            Mockito.doNothing().when(domainMock).removeBlockJobListener(Mockito.any());
            Mockito.doNothing().when(libvirtComputingResourceSpy).manuallyDeleteUnusedSnapshotFile(Mockito.anyBoolean(), Mockito.anyString());

            String diskLabel = "vda";
            String baseFilePath = "/file";
            String snapshotName = "snap";

            libvirtComputingResourceSpy.mergeSnapshotIntoBaseFileWithEventsAndConfigurableTimeout(domainMock, diskLabel, baseFilePath, null, false, snapshotName, volumeObjectToMock, connMock);

            Mockito.verify(domainMock, Mockito.times(1)).blockCommit(diskLabel, baseFilePath, null, 0, Domain.BlockCommitFlags.DELETE);
            Mockito.verify(libvirtComputingResourceSpy, Mockito.times(1)).manuallyDeleteUnusedSnapshotFile(true, "/" + snapshotName);
        }
    }

    @Test
    public void mergeSnapshotIntoBaseFileTestNoFlags() throws Exception {
        try (MockedStatic<LibvirtUtilitiesHelper> libvirtUtilitiesHelperMockedStatic = Mockito.mockStatic(LibvirtUtilitiesHelper.class);
                MockedStatic<ThreadContext> threadContextMockedStatic = Mockito.mockStatic(ThreadContext.class)) {
            libvirtComputingResourceSpy.qcow2DeltaMergeTimeout = 10;
            libvirtUtilitiesHelperMockedStatic.when(() -> LibvirtUtilitiesHelper.isLibvirtSupportingFlagDeleteOnCommandVirshBlockcommit(Mockito.any())).thenReturn(false);
            Mockito.doReturn(new Semaphore(1)).when(libvirtComputingResourceSpy).getSemaphoreToWaitForMerge();
            threadContextMockedStatic.when(() -> ThreadContext.get(Mockito.anyString())).thenReturn("logid");
            Mockito.doNothing().when(domainMock).addBlockJobListener(Mockito.any());
            Mockito.doReturn(null).when(domainMock).getBlockJobInfo(Mockito.anyString(), Mockito.anyInt());
            Mockito.doNothing().when(domainMock).removeBlockJobListener(Mockito.any());
            Mockito.doNothing().when(libvirtComputingResourceSpy).manuallyDeleteUnusedSnapshotFile(Mockito.anyBoolean(), Mockito.anyString());

            String diskLabel = "vda";
            String baseFilePath = "/file";
            String snapshotName = "snap";

            libvirtComputingResourceSpy.mergeSnapshotIntoBaseFileWithEventsAndConfigurableTimeout(domainMock, diskLabel, baseFilePath, null, false, snapshotName, volumeObjectToMock, connMock);

            Mockito.verify(domainMock, Mockito.times(1)).blockCommit(diskLabel, baseFilePath, null, 0, 0);
            Mockito.verify(libvirtComputingResourceSpy, Mockito.times(1)).manuallyDeleteUnusedSnapshotFile(false, "/" + snapshotName);
        }
    }

    @Test (expected = CloudRuntimeException.class)
    public void mergeSnapshotIntoBaseFileTestMergeFailsThrowException() throws Exception {
        try (MockedStatic<LibvirtUtilitiesHelper> libvirtUtilitiesHelperMockedStatic = Mockito.mockStatic(LibvirtUtilitiesHelper.class);
                MockedStatic<ThreadContext> threadContextMockedStatic = Mockito.mockStatic(ThreadContext.class)) {
            libvirtComputingResourceSpy.qcow2DeltaMergeTimeout = 10;
            libvirtUtilitiesHelperMockedStatic.when(() -> LibvirtUtilitiesHelper.isLibvirtSupportingFlagDeleteOnCommandVirshBlockcommit(Mockito.any())).thenReturn(false);
            Mockito.doReturn(new Semaphore(1)).when(libvirtComputingResourceSpy).getSemaphoreToWaitForMerge();
            threadContextMockedStatic.when(() -> ThreadContext.get(Mockito.anyString())).thenReturn("logid");
            Mockito.doNothing().when(domainMock).addBlockJobListener(Mockito.any());
            Mockito.doReturn(null).when(domainMock).getBlockJobInfo(Mockito.anyString(), Mockito.anyInt());
            Mockito.doNothing().when(domainMock).removeBlockJobListener(Mockito.any());

            Mockito.doReturn(blockCommitListenerMock).when(libvirtComputingResourceSpy).getBlockCommitListener(Mockito.any(), Mockito.any());
            Mockito.doReturn("Failed").when(blockCommitListenerMock).getResult();

            String diskLabel = "vda";
            String baseFilePath = "/file";
            String snapshotName = "snap";

            libvirtComputingResourceSpy.mergeSnapshotIntoBaseFileWithEventsAndConfigurableTimeout(domainMock, diskLabel, baseFilePath, null, false, snapshotName, volumeObjectToMock, connMock);
        }
    }

    @Test (expected = CloudRuntimeException.class)
    public void manuallyDeleteUnusedSnapshotFileTestLibvirtDoesNotSupportsFlagDeleteExceptionOnFileDeletionThrowsException() throws IOException {
        try (MockedStatic<Files> filesMockedStatic = Mockito.mockStatic(Files.class)) {
            filesMockedStatic.when(() -> Files.deleteIfExists(Mockito.any(Path.class))).thenThrow(IOException.class);

            libvirtComputingResourceSpy.manuallyDeleteUnusedSnapshotFile(false, "");
        }
    }

    @Test
    public void manuallyDeleteUnusedSnapshotFileTestLibvirtSupportingFlagDeleteOnCommandVirshBlockcommitIsTrueReturn() {
        libvirtComputingResourceSpy.manuallyDeleteUnusedSnapshotFile(true, "");
        Mockito.verify(libvirtComputingResourceSpy, Mockito.never()).deleteIfExists("");
    }

    @Test
    public void testGetJsonStringValueOrNullWithValidStringValue() {
        // Test case: field exists and has a string value
        String jsonString = "{\"testField\": \"testValue\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "testField");

        assertEquals("testValue", result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withEmptyStringValue() {
        // Test case: field exists and has an empty string value
        String jsonString = "{\"testField\": \"\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "testField");

        assertEquals("", result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withNullValue() {
        // Test case: field exists but is null
        String jsonString = "{\"testField\": null}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "testField");

        assertNull(result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withMissingField() {
        // Test case: field doesn't exist in the JSON object
        String jsonString = "{\"otherField\": \"otherValue\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "missingField");

        assertNull(result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withEmptyJsonObject() {
        // Test case: empty JSON object
        String jsonString = "{}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "anyField");

        assertNull(result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withNumericValue() {
        // Test case: field exists but contains a numeric value (should still work as it gets converted to string)
        String jsonString = "{\"numericField\": 123}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "numericField");

        assertEquals("123", result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withBooleanValue() {
        // Test case: field exists but contains a boolean value (should still work as it gets converted to string)
        String jsonString = "{\"booleanField\": true}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "booleanField");

        assertEquals("true", result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withNullFieldName() {
        // Test case: null field name should return null
        String jsonString = "{\"testField\": \"testValue\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, null);

        assertNull(result);
    }

    @Test
    public void testGetJsonStringValueOrNull_withLongStringValue() {
        // Test case: field exists and has a long string value
        String longValue = "This is a very long string value that contains multiple words and special characters like @#$%^&*()";
        String jsonString = "{\"longField\": \"" + longValue + "\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "longField");

        assertEquals(longValue, result);
    }

    @Test(expected = NullPointerException.class)
    public void testGetJsonStringValueOrNull_withNullJsonObject() {
        // Test case: null JSON object should throw NullPointerException
        // This tests that the method doesn't handle null objects gracefully, which is expected behavior
        libvirtComputingResourceSpy.getJsonStringValueOrNull(null, "testField");
    }

    @Test
    public void testGetJsonStringValueOrNull_withSpecialCharacters() {
        // Test case: field contains JSON special characters and unicode
        String jsonString = "{\"specialField\": \"Value with \\\"quotes\\\", \\n newlines, and unicode: \\u00E9\"}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(jsonString).getAsJsonObject();

        String result = libvirtComputingResourceSpy.getJsonStringValueOrNull(jsonObject, "specialField");

        assertEquals("Value with \"quotes\", \n newlines, and unicode: é", result);
    }

    @Test
    public void testParseGpuDevicesFromResult_withSuccess() {
        String result = "{\"gpus\": ["
                        + "    {"
                        + "      \"pci_address\": \"00:03.0\","
                        + "      \"vendor_id\": \"10de\","
                        + "      \"device_id\": \"2484\","
                        + "      \"vendor\": \"NVIDIA Corporation\","
                        + "      \"device\": \"GeForce RTX 3070\","
                        + "      \"driver\": \"nvidia\","
                        + "      \"pci_class\": \"VGA compatible controller\","
                        + "      \"iommu_group\": \"8\","
                        + "      \"sriov_totalvfs\": 0,"
                        + "      \"sriov_numvfs\": 0,"
                        + "      \"full_passthrough\": {"
                        + "        \"enabled\": 1,"
                        + "        \"libvirt_address\": {"
                        + "          \"domain\": \"0x0000\","
                        + "          \"bus\": \"0x00\","
                        + "          \"slot\": \"0x03\","
                        + "          \"function\": \"0x0\""
                        + "        },"
                        + "        \"used_by_vm\": \"win10\""
                        + "      },"
                        + "      \"vgpu_instances\": [],"
                        + "      \"vf_instances\": []"
                        + "    },"
                        + "    {"
                        + "      \"pci_address\": \"00:AF.0\","
                        + "      \"vendor_id\": \"10de\","
                        + "      \"device_id\": \"1EB8\","
                        + "      \"vendor\": \"NVIDIA Corporation\","
                        + "      \"device\": \"Tesla T4\","
                        + "      \"driver\": \"nvidia\","
                        + "      \"pci_class\": \"3D controller\","
                        + "      \"iommu_group\": \"12\","
                        + "      \"sriov_totalvfs\": 0,"
                        + "      \"sriov_numvfs\": 0,"
                        + "      \"full_passthrough\": {"
                        + "        \"enabled\": 0,"
                        + "        \"libvirt_address\": {"
                        + "          \"domain\": \"0x0000\","
                        + "          \"bus\": \"0x00\","
                        + "          \"slot\": \"0xAF\","
                        + "          \"function\": \"0x0\""
                        + "        },"
                        + "        \"used_by_vm\": null"
                        + "      },"
                        + "      \"vgpu_instances\": ["
                        + "        {"
                        + "          \"mdev_uuid\": \"a1b2c3d4-5678-4e9a-8b0c-d1e2f3a4b5c6\","
                        + "          \"profile_name\": \"grid_t4-16c\","
                        + "          \"max_instances\": 4,"
                        + "          \"libvirt_address\": {"
                        + "            \"domain\": \"0x0000\","
                        + "            \"bus\": \"0x00\","
                        + "            \"slot\": \"0xAF\","
                        + "            \"function\": \"0x0\""
                        + "          },"
                        + "          \"used_by_vm\": \"vm1\""
                        + "        },"
                        + "        {"
                        + "          \"mdev_uuid\": \"b2c3d4e5-6789-4f0a-9c1d-e2f3a4b5c6d7\","
                        + "          \"profile_name\": \"grid_t4-8c\","
                        + "          \"max_instances\": 8,"
                        + "          \"libvirt_address\": {"
                        + "            \"domain\": \"0x0000\","
                        + "            \"bus\": \"0x00\","
                        + "            \"slot\": \"0xAF\","
                        + "            \"function\": \"0x1\""
                        + "          },"
                        + "          \"used_by_vm\": \"vm2\""
                        + "        }"
                        + "      ],"
                        + "      \"vf_instances\": []"
                        + "    },"
                        + "    {"
                        + "      \"pci_address\": \"00:65.0\","
                        + "      \"vendor_id\": \"10de\","
                        + "      \"device_id\": \"20B0\","
                        + "      \"vendor\": \"NVIDIA Corporation\","
                        + "      \"device\": \"A100-SXM4-40GB\","
                        + "      \"driver\": \"nvidia\","
                        + "      \"pci_class\": \"VGA compatible controller\","
                        + "      \"iommu_group\": \"15\","
                        + "      \"sriov_totalvfs\": 7,"
                        + "      \"sriov_numvfs\": 7,"
                        + "      \"full_passthrough\": {"
                        + "        \"enabled\": 0,"
                        + "        \"libvirt_address\": {"
                        + "          \"domain\": \"0x0000\","
                        + "          \"bus\": \"0x00\","
                        + "          \"slot\": \"0x65\","
                        + "          \"function\": \"0x0\""
                        + "        },"
                        + "        \"used_by_vm\": null"
                        + "      },"
                        + "      \"vgpu_instances\": [],"
                        + "      \"vf_instances\": ["
                        + "        {"
                        + "          \"vf_pci_address\": \"00:65.2\","
                        + "          \"vf_profile\": \"1g.5gb\","
                        + "          \"libvirt_address\": {"
                        + "            \"domain\": \"0x0000\","
                        + "            \"bus\": \"0x00\","
                        + "            \"slot\": \"0x65\","
                        + "            \"function\": \"0x2\""
                        + "          },"
                        + "          \"used_by_vm\": \"ml\""
                        + "        },"
                        + "        {"
                        + "          \"vf_pci_address\": \"00:65.3\","
                        + "          \"vf_profile\": \"2g.10gb\","
                        + "          \"libvirt_address\": {"
                        + "            \"domain\": \"0x0000\","
                        + "            \"bus\": \"0x00\","
                        + "            \"slot\": \"0x65\","
                        + "            \"function\": \"0x3\""
                        + "          },"
                        + "          \"used_by_vm\": null"
                        + "        }"
                        + "      ]"
                        + "    }"
                        + "  ]"
                        + "}";
        List<VgpuTypesInfo> gpuDevices = libvirtComputingResourceSpy.parseGpuDevicesFromResult(result);
        assertEquals(7, gpuDevices.size());
        // Verify first GPU device (RTX 3070)
        VgpuTypesInfo firstGpu = gpuDevices.get(0);
        assertEquals("00:03.0", firstGpu.getBusAddress());
        assertEquals("10de", firstGpu.getVendorId());
        assertEquals("2484", firstGpu.getDeviceId());
        assertEquals("NVIDIA Corporation", firstGpu.getVendorName());
        assertEquals("GeForce RTX 3070", firstGpu.getDeviceName());
        assertEquals("passthrough", firstGpu.getModelName());
        assertEquals("NVIDIA Corporation GeForce RTX 3070", firstGpu.getGroupName());
        assertTrue(firstGpu.isPassthroughEnabled());
        assertEquals("win10", firstGpu.getVmName());

        // Verify second GPU device (Tesla T4)
        VgpuTypesInfo secondGpu = gpuDevices.get(1);
        assertEquals("00:AF.0", secondGpu.getBusAddress());
        assertEquals("10de", secondGpu.getVendorId());
        assertEquals("1EB8", secondGpu.getDeviceId());
        assertEquals("NVIDIA Corporation", secondGpu.getVendorName());
        assertEquals("Tesla T4", secondGpu.getDeviceName());
        assertEquals("passthrough", secondGpu.getModelName());
        assertEquals("NVIDIA Corporation Tesla T4", secondGpu.getGroupName());
        assertFalse(secondGpu.isPassthroughEnabled());
        assertNull(secondGpu.getVmName());

        // Verify third GPU device (A100-SXM4-40GB)
        VgpuTypesInfo thirdGpu = gpuDevices.get(4);
        assertEquals("00:65.0", thirdGpu.getBusAddress());
        assertEquals("10de", thirdGpu.getVendorId());
        assertEquals("20B0", thirdGpu.getDeviceId());
        assertEquals("NVIDIA Corporation", thirdGpu.getVendorName());
        assertEquals("A100-SXM4-40GB", thirdGpu.getDeviceName());
        assertEquals("NVIDIA Corporation A100-SXM4-40GB", thirdGpu.getGroupName());
        assertEquals("passthrough", thirdGpu.getModelName());
        assertEquals("NVIDIA Corporation A100-SXM4-40GB", thirdGpu.getGroupName());
        assertFalse(thirdGpu.isPassthroughEnabled());
        assertNull(thirdGpu.getVmName());

        // Verify vGPU instances from Tesla T4
        VgpuTypesInfo vgpuInstance1 = gpuDevices.get(2);
        assertEquals("a1b2c3d4-5678-4e9a-8b0c-d1e2f3a4b5c6", vgpuInstance1.getBusAddress());
        assertEquals("00:AF.0", vgpuInstance1.getParentBusAddress());
        assertEquals("10de", vgpuInstance1.getVendorId());
        assertEquals("1EB8", vgpuInstance1.getDeviceId());
        assertEquals("NVIDIA Corporation", vgpuInstance1.getVendorName());
        assertEquals("Tesla T4", vgpuInstance1.getDeviceName());
        assertEquals("NVIDIA Corporation Tesla T4", vgpuInstance1.getGroupName());
        assertEquals("grid_t4-16c", vgpuInstance1.getModelName());
        assertEquals(Long.valueOf(4), vgpuInstance1.getMaxVpuPerGpu());
        assertEquals("vm1", vgpuInstance1.getVmName());

        VgpuTypesInfo vgpuInstance2 = gpuDevices.get(3);
        assertEquals("b2c3d4e5-6789-4f0a-9c1d-e2f3a4b5c6d7", vgpuInstance2.getBusAddress());
        assertEquals("00:AF.0", vgpuInstance2.getParentBusAddress());
        assertEquals("10de", vgpuInstance2.getVendorId());
        assertEquals("1EB8", vgpuInstance2.getDeviceId());
        assertEquals("NVIDIA Corporation", vgpuInstance2.getVendorName());
        assertEquals("Tesla T4", vgpuInstance2.getDeviceName());
        assertEquals("NVIDIA Corporation Tesla T4", vgpuInstance2.getGroupName());
        assertEquals("grid_t4-8c", vgpuInstance2.getModelName());
        assertEquals(Long.valueOf(8), vgpuInstance2.getMaxVpuPerGpu());
        assertEquals("vm2", vgpuInstance2.getVmName());

        // Verify VF instances from NVIDIA Corporation A100-SXM4-40GB
        VgpuTypesInfo vfInstance1 = gpuDevices.get(5);
        assertEquals("00:65.0", vfInstance1.getParentBusAddress());
        assertEquals("00:65.2", vfInstance1.getBusAddress());
        assertEquals("10de", vfInstance1.getVendorId());
        assertEquals("20B0", vfInstance1.getDeviceId());
        assertEquals("NVIDIA Corporation", vfInstance1.getVendorName());
        assertEquals("A100-SXM4-40GB", vfInstance1.getDeviceName());
        assertEquals("NVIDIA Corporation A100-SXM4-40GB", vfInstance1.getGroupName());
        assertEquals("1g.5gb", vfInstance1.getModelName());
        assertEquals("ml", vfInstance1.getVmName());

        VgpuTypesInfo vfInstance2 = gpuDevices.get(6);
        assertEquals("00:65.0", vfInstance2.getParentBusAddress());
        assertEquals("00:65.3", vfInstance2.getBusAddress());
        assertEquals("10de", vfInstance2.getVendorId());
        assertEquals("20B0", vfInstance2.getDeviceId());
        assertEquals("NVIDIA Corporation", vfInstance2.getVendorName());
        assertEquals("A100-SXM4-40GB", vfInstance2.getDeviceName());
        assertEquals("NVIDIA Corporation A100-SXM4-40GB", vfInstance1.getGroupName());
        assertEquals("2g.10gb", vfInstance2.getModelName());
        assertNull(vfInstance2.getVmName());
    }
}
