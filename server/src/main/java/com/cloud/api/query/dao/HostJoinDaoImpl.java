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
package com.cloud.api.query.dao;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.annotation.AnnotationService;
import org.apache.cloudstack.annotation.dao.AnnotationDao;
import org.apache.cloudstack.api.ApiConstants.HostDetails;
import org.apache.cloudstack.api.response.GpuResponse;
import org.apache.cloudstack.api.response.HostForMigrationResponse;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.VgpuResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.dao.HAConfigDao;
import org.apache.cloudstack.outofbandmanagement.dao.OutOfBandManagementDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.query.vo.HostJoinVO;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.gpu.HostGpuGroupsVO;
import com.cloud.gpu.VGPUTypesVO;
import com.cloud.host.Host;
import com.cloud.host.HostStats;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.StorageStats;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;

@Component
public class HostJoinDaoImpl extends GenericDaoBase<HostJoinVO, Long> implements HostJoinDao {

    @Inject
    private ConfigurationDao _configDao;
    @Inject
    private HostDetailsDao hostDetailsDao;
    @Inject
    private HAConfigDao haConfigDao;
    @Inject
    private OutOfBandManagementDao outOfBandManagementDao;
    @Inject
    private ManagementServerHostDao managementServerHostDao;
    @Inject
    private VMInstanceDao virtualMachineDao;
    @Inject
    private AnnotationDao annotationDao;
    @Inject
    private AccountManager accountManager;

    private final SearchBuilder<HostJoinVO> hostSearch;

    private final SearchBuilder<HostJoinVO> hostIdSearch;

    private final SearchBuilder<HostJoinVO> ClusterSearch;

    protected HostJoinDaoImpl() {

        hostSearch = createSearchBuilder();
        hostSearch.and("idIN", hostSearch.entity().getId(), SearchCriteria.Op.IN);
        hostSearch.done();

        hostIdSearch = createSearchBuilder();
        hostIdSearch.and("id", hostIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        hostIdSearch.done();

        ClusterSearch = createSearchBuilder();
        ClusterSearch.and("clusterId", ClusterSearch.entity().getClusterId(), SearchCriteria.Op.EQ);
        ClusterSearch.and("type", ClusterSearch.entity().getType(), SearchCriteria.Op.EQ);
        ClusterSearch.done();

        this._count = "select count(distinct id) from host_view WHERE ";
    }

    private boolean containsHostHATag(final String tags) {
        boolean result = false;
        String haTag = ApiDBUtils.getHaTag();
        if (StringUtils.isNoneEmpty(haTag, tags)) {
            List<String> tagsList = Arrays.asList(tags.split(","));
            if (tagsList.contains(haTag)) {
                result = true;
            }
        }
        return result;
    }

    private void setNewHostResponseBase(HostJoinVO host, EnumSet<HostDetails> details, HostResponse hostResponse) {
        hostResponse.setId(host.getUuid());
        hostResponse.setCapabilities(host.getCapabilities());
        hostResponse.setClusterInternalId(host.getClusterId());
        hostResponse.setClusterId(host.getClusterUuid());
        hostResponse.setCpuSockets(host.getCpuSockets());
        hostResponse.setCpuNumber(host.getCpus());
        hostResponse.setZoneId(host.getZoneUuid());
        hostResponse.setDisconnectedOn(host.getDisconnectedOn());
        if (host.getHypervisorType() != null) {
            String hypervisorType = host.getHypervisorType().getHypervisorDisplayName();
            hostResponse.setHypervisor(hypervisorType);
        }
        hostResponse.setHostType(host.getType());
        if (host.getType().equals(Host.Type.ConsoleProxy) || host.getType().equals(Host.Type.SecondaryStorageVM)) {
            VMInstanceVO vm = virtualMachineDao.findVMByInstanceNameIncludingRemoved(host.getName());
            if (vm != null) {
                hostResponse.setVirtualMachineId(vm.getUuid());
            }
        }
        hostResponse.setLastPinged(new Date(host.getLastPinged()));
        Long mshostId = host.getManagementServerId();
        if (mshostId != null) {
            ManagementServerHostVO managementServer = managementServerHostDao.findByMsid(host.getManagementServerId());
            if (managementServer != null) {
                hostResponse.setManagementServerId(managementServer.getUuid());
                hostResponse.setManagementServerName(managementServer.getName());
            }
        }
        hostResponse.setName(host.getName());
        hostResponse.setPodId(host.getPodUuid());
        hostResponse.setRemoved(host.getRemoved());
        hostResponse.setCpuSpeed(host.getSpeed());
        hostResponse.setState(host.getStatus());
        hostResponse.setIpAddress(host.getPrivateIpAddress());
        hostResponse.setVersion(host.getVersion());
        hostResponse.setCreated(host.getCreated());

        List<HostGpuGroupsVO> gpuGroups = ApiDBUtils.getGpuGroups(host.getId());
        if (gpuGroups != null && !gpuGroups.isEmpty()) {
            List<GpuResponse> gpus = new ArrayList<GpuResponse>();
            long gpuRemaining = 0;
            long gpuTotal = 0;
            for (HostGpuGroupsVO entry : gpuGroups) {
                GpuResponse gpuResponse = new GpuResponse();
                gpuResponse.setGpuGroupName(entry.getGroupName());
                List<VGPUTypesVO> vgpuTypes = ApiDBUtils.getVgpus(entry.getId());
                if (vgpuTypes != null && !vgpuTypes.isEmpty()) {
                    List<VgpuResponse> vgpus = new ArrayList<VgpuResponse>();
                    for (VGPUTypesVO vgpuType : vgpuTypes) {
                        VgpuResponse vgpuResponse = new VgpuResponse();
                        vgpuResponse.setName(vgpuType.getVgpuType());
                        vgpuResponse.setVideoRam(vgpuType.getVideoRam());
                        vgpuResponse.setMaxHeads(vgpuType.getMaxHeads());
                        vgpuResponse.setMaxResolutionX(vgpuType.getMaxResolutionX());
                        vgpuResponse.setMaxResolutionY(vgpuType.getMaxResolutionY());
                        vgpuResponse.setMaxVgpuPerPgpu(vgpuType.getMaxVgpuPerPgpu());
                        vgpuResponse.setRemainingCapacity(vgpuType.getRemainingCapacity());
                        vgpuResponse.setmaxCapacity(vgpuType.getMaxCapacity());
                        vgpus.add(vgpuResponse);
                        gpuRemaining += vgpuType.getRemainingCapacity();
                        gpuTotal += vgpuType.getMaxCapacity();
                    }
                    gpuResponse.setVgpu(vgpus);
                }
                gpus.add(gpuResponse);
            }
            hostResponse.setGpuTotal(gpuTotal);
            hostResponse.setGpuUsed(gpuTotal - gpuRemaining);
            hostResponse.setGpuGroup(gpus);
        }
        if (details.contains(HostDetails.all) || details.contains(HostDetails.capacity) || details.contains(HostDetails.stats) || details.contains(HostDetails.events)) {

            hostResponse.setOsCategoryId(host.getOsCategoryUuid());
            hostResponse.setOsCategoryName(host.getOsCategoryName());
            hostResponse.setZoneName(host.getZoneName());
            hostResponse.setPodName(host.getPodName());
            if (host.getClusterId() > 0) {
                hostResponse.setClusterName(host.getClusterName());
                hostResponse.setClusterType(host.getClusterType().toString());
            }
        }

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        if (host.getType() == Host.Type.Routing) {
            float cpuOverprovisioningFactor = ApiDBUtils.getCpuOverprovisioningFactor(host.getClusterId());
            if (details.contains(HostDetails.all) || details.contains(HostDetails.capacity)) {
                // set allocated capacities
                Long mem = host.getMemReservedCapacity() + host.getMemUsedCapacity();
                Long cpu = host.getCpuReservedCapacity() + host.getCpuUsedCapacity();

                Float memWithOverprovisioning = host.getTotalMemory() * ApiDBUtils.getMemOverprovisioningFactor(host.getClusterId());
                hostResponse.setMemoryTotal(memWithOverprovisioning.longValue());
                hostResponse.setMemWithOverprovisioning(decimalFormat.format(memWithOverprovisioning));
                hostResponse.setMemoryAllocated(mem);
                hostResponse.setMemoryAllocatedBytes(mem);
                hostResponse.setMemoryAllocatedPercentage(calculateResourceAllocatedPercentage(mem, memWithOverprovisioning));

                String hostTags = host.getTag();
                hostResponse.setHostTags(hostTags);
                hostResponse.setIsTagARule(host.getIsTagARule());
                hostResponse.setHaHost(containsHostHATag(hostTags));
                hostResponse.setExplicitHostTags(host.getExplicitTag());
                hostResponse.setImplicitHostTags(host.getImplicitTag());

                hostResponse.setHypervisorVersion(host.getHypervisorVersion());
                if (host.getArch() != null) {
                    hostResponse.setArch(host.getArch().getType());
                }

                hostResponse.setStorageAccessGroups(host.getStorageAccessGroups());
                hostResponse.setClusterStorageAccessGroups(host.getClusterStorageAccessGroups());
                hostResponse.setPodStorageAccessGroups(host.getPodStorageAccessGroups());
                hostResponse.setZoneStorageAccessGroups(host.getZoneStorageAccessGroups());

                float cpuWithOverprovisioning = host.getCpus() * host.getSpeed() * cpuOverprovisioningFactor;
                hostResponse.setCpuAllocatedValue(cpu);
                String cpuAllocated = calculateResourceAllocatedPercentage(cpu, cpuWithOverprovisioning);
                hostResponse.setCpuAllocated(cpuAllocated);
                hostResponse.setCpuAllocatedPercentage(cpuAllocated);
                hostResponse.setCpuAllocatedWithOverprovisioning(cpuAllocated);
                hostResponse.setCpuWithOverprovisioning(decimalFormat.format(cpuWithOverprovisioning));
            }

            if (details.contains(HostDetails.all) || details.contains(HostDetails.stats)) {
                // set CPU/RAM/Network stats
                String cpuUsed = null;
                HostStats hostStats = ApiDBUtils.getHostStatistics(host.getId());
                if (hostStats != null) {
                    float cpuUtil = (float)hostStats.getCpuUtilization();
                    cpuUsed = decimalFormat.format(cpuUtil) + "%";
                    hostResponse.setCpuUsed(cpuUsed);
                    hostResponse.setCpuAverageLoad(hostStats.getLoadAverage());
                    hostResponse.setMemoryUsed((new Double(hostStats.getUsedMemory())).longValue());
                    hostResponse.setNetworkKbsRead((new Double(hostStats.getNetworkReadKBs())).longValue());
                    hostResponse.setNetworkKbsWrite((new Double(hostStats.getNetworkWriteKBs())).longValue());

                }
            }

            Map<String, String> hostDetails = hostDetailsDao.findDetails(host.getId());
            if (hostDetails != null) {
                if (hostDetails.containsKey(Host.HOST_UEFI_ENABLE)) {
                    hostResponse.setUefiCapability(Boolean.parseBoolean((String) hostDetails.get(Host.HOST_UEFI_ENABLE)));
                } else {
                    hostResponse.setUefiCapability(new Boolean(false));
                }
            }
            if (details.contains(HostDetails.all) &&
                    Arrays.asList(Hypervisor.HypervisorType.KVM,
                            Hypervisor.HypervisorType.Custom,
                            Hypervisor.HypervisorType.External).contains(host.getHypervisorType())) {
                //only kvm has the requirement to return host details
                try {
                    hostResponse.setDetails(hostDetails, host.getHypervisorType());
                } catch (Exception e) {
                    logger.debug("failed to get host details", e);
                }
            }

        } else if (host.getType() == Host.Type.SecondaryStorage) {
            StorageStats secStorageStats = ApiDBUtils.getSecondaryStorageStatistics(host.getId());
            if (secStorageStats != null) {
                hostResponse.setDiskSizeTotal(secStorageStats.getCapacityBytes());
                hostResponse.setDiskSizeAllocated(secStorageStats.getByteUsed());
            }
        }

        hostResponse.setLocalStorageActive(ApiDBUtils.isLocalStorageActiveOnHost(host.getId()));

        if (details.contains(HostDetails.all) || details.contains(HostDetails.events)) {
            Set<com.cloud.host.Status.Event> possibleEvents = host.getStatus().getPossibleEvents();
            if ((possibleEvents != null) && !possibleEvents.isEmpty()) {
                String events = "";
                Iterator<com.cloud.host.Status.Event> iter = possibleEvents.iterator();
                while (iter.hasNext()) {
                    com.cloud.host.Status.Event event = iter.next();
                    events += event.toString();
                    if (iter.hasNext()) {
                        events += "; ";
                    }
                }
                hostResponse.setEvents(events);
            }
        }

        hostResponse.setHostHAResponse(haConfigDao.findHAResource(host.getId(), HAResource.ResourceType.Host));
        hostResponse.setOutOfBandManagementResponse(outOfBandManagementDao.findByHost(host.getId()));
        hostResponse.setResourceState(host.getResourceState().toString());

        // set async job
        if (host.getJobId() != null) {
            hostResponse.setJobId(host.getJobUuid());
            hostResponse.setJobStatus(host.getJobStatus());
        }
        hostResponse.setHasAnnotation(annotationDao.hasAnnotations(host.getUuid(), AnnotationService.EntityType.HOST.name(),
                accountManager.isRootAdmin(CallContext.current().getCallingAccount().getId())));
        hostResponse.setAnnotation(host.getAnnotation());
        hostResponse.setLastAnnotated(host.getLastAnnotated ());
        hostResponse.setUsername(host.getUsername());

        hostResponse.setObjectName("host");
    }

    @Override
    public HostResponse newMinimalHostResponse(HostJoinVO host) {
        HostResponse hostResponse = new HostResponse();
        hostResponse.setId(host.getUuid());
        hostResponse.setName(host.getName());
        hostResponse.setObjectName("host");

        return hostResponse;
    }

    @Override
    public HostResponse newHostResponse(HostJoinVO host, EnumSet<HostDetails> details) {
        HostResponse hostResponse = new HostResponse();
        setNewHostResponseBase(host, details, hostResponse);
        return hostResponse;
    }

    @Override
    public HostForMigrationResponse newHostForMigrationResponse(HostJoinVO host, EnumSet<HostDetails> details) {
        HostForMigrationResponse hostResponse = new HostForMigrationResponse();
        setNewHostResponseBase(host, details, hostResponse);
        return hostResponse;
    }

    @Override
    public List<HostJoinVO> newHostView(Host host) {
        SearchCriteria<HostJoinVO> sc = hostIdSearch.create();
        sc.setParameters("id", host.getId());
        return searchIncludingRemoved(sc, null, null, false);

    }

    @Override
    public List<HostJoinVO> searchByIds(Long... hostIds) {
        // set detail batch query size
        int DETAILS_BATCH_SIZE = 2000;
        String batchCfg = _configDao.getValue("detail.batch.query.size");
        if (batchCfg != null) {
            DETAILS_BATCH_SIZE = Integer.parseInt(batchCfg);
        }
        // query details by batches
        List<HostJoinVO> uvList = new ArrayList<HostJoinVO>();
        // query details by batches
        int curr_index = 0;
        if (hostIds.length > DETAILS_BATCH_SIZE) {
            while ((curr_index + DETAILS_BATCH_SIZE) <= hostIds.length) {
                Long[] ids = new Long[DETAILS_BATCH_SIZE];
                for (int k = 0, j = curr_index; j < curr_index + DETAILS_BATCH_SIZE; j++, k++) {
                    ids[k] = hostIds[j];
                }
                SearchCriteria<HostJoinVO> sc = hostSearch.create();
                sc.setParameters("idIN", ids);
                List<HostJoinVO> vms = searchIncludingRemoved(sc, null, null, false);
                if (vms != null) {
                    uvList.addAll(vms);
                }
                curr_index += DETAILS_BATCH_SIZE;
            }
        }
        if (curr_index < hostIds.length) {
            int batch_size = (hostIds.length - curr_index);
            // set the ids value
            Long[] ids = new Long[batch_size];
            for (int k = 0, j = curr_index; j < curr_index + batch_size; j++, k++) {
                ids[k] = hostIds[j];
            }
            SearchCriteria<HostJoinVO> sc = hostSearch.create();
            sc.setParameters("idIN", ids);
            List<HostJoinVO> vms = searchIncludingRemoved(sc, null, null, false);
            if (vms != null) {
                uvList.addAll(vms);
            }
        }
        return uvList;
    }

    @Override
    public List<HostJoinVO> findByClusterId(Long clusterId, Host.Type type) {
        SearchCriteria<HostJoinVO> sc = ClusterSearch.create();
        sc.setParameters("clusterId", clusterId);
        sc.setParameters("type", type);
        return listBy(sc);
    }

    private String calculateResourceAllocatedPercentage(float resource, float resourceWithOverProvision) {
        if (resource == 0 || resourceWithOverProvision == 0) {
            return "0.00%";
        }
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return decimalFormat.format(((float)resource / resourceWithOverProvision * 100.0f)) + "%";
    }

}
