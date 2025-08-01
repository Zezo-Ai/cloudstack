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
package com.cloud.hypervisor.kvm.storage;


import com.cloud.agent.api.to.DiskTO;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.Storage.ProvisioningType;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.cloudstack.storage.datastore.util.StorPoolUtil;
import org.apache.cloudstack.utils.qemu.QemuImg;
import org.apache.cloudstack.utils.qemu.QemuImg.PhysicalDiskFormat;
import org.apache.cloudstack.utils.qemu.QemuImgException;
import org.apache.cloudstack.utils.qemu.QemuImgFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.libvirt.LibvirtException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StorPoolStorageAdaptor implements StorageAdaptor {
    public static void SP_LOG(String fmt, Object... args) {
        try (PrintWriter spLogFile = new PrintWriter(new BufferedWriter(new FileWriter("/var/log/cloudstack/agent/storpool-agent.log", true)))) {
            final String line = String.format(fmt, args);
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,ms").format(Calendar.getInstance().getTime());
            spLogFile.println(timeStamp +" "+line);
            spLogFile.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Logger LOGGER = LogManager.getLogger(StorPoolStorageAdaptor.class);

    private static final Map<String, KVMStoragePool> storageUuidToStoragePool = new HashMap<String, KVMStoragePool>();

    @Override
    public KVMStoragePool createStoragePool(String uuid, String host, int port, String path, String userInfo, StoragePoolType storagePoolType, Map<String, String> details, boolean isPrimaryStorage) {
        SP_LOG("StorPoolStorageAdaptor.createStoragePool: uuid=%s, host=%s:%d, path=%s, userInfo=%s, type=%s", uuid, host, port, path, userInfo, storagePoolType);

        StorPoolStoragePool storagePool = new StorPoolStoragePool(uuid, host, port, storagePoolType, this);
        storageUuidToStoragePool.put(uuid, storagePool);
        return storagePool;
    }

    @Override
    public StoragePoolType getStoragePoolType() {
        return StoragePoolType.StorPool;
    }

    @Override
    public KVMStoragePool getStoragePool(String uuid) {
        SP_LOG("StorPoolStorageAdaptor.getStoragePool: uuid=%s", uuid);
        return storageUuidToStoragePool.get(uuid);
    }

    @Override
    public KVMStoragePool getStoragePool(String uuid, boolean refreshInfo) {
        SP_LOG("StorPoolStorageAdaptor.getStoragePool: uuid=%s, refresh=%s", uuid, refreshInfo);
        return storageUuidToStoragePool.get(uuid);
    }

    @Override
    public boolean deleteStoragePool(String uuid) {
        SP_LOG("StorPoolStorageAdaptor.deleteStoragePool: uuid=%s", uuid);
        return storageUuidToStoragePool.remove(uuid) != null;
    }

    @Override
    public boolean deleteStoragePool(KVMStoragePool pool) {
        SP_LOG("StorPoolStorageAdaptor.deleteStoragePool: uuid=%s", pool.getUuid());
        return deleteStoragePool(pool.getUuid());
    }

    private static long getDeviceSize(final String devPath) {
        SP_LOG("StorPoolStorageAdaptor.getDeviceSize: path=%s", devPath);

        if (getVolumeNameFromPath(devPath, true) == null) {
            return 0;
        }
        File file = new File(devPath);
        if (!file.exists()) {
            return 0;
        }
        Script sc = new Script("blockdev", 0, LOGGER);
        sc.add("--getsize64", devPath);

        OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();

        String res = sc.execute(parser);
        if (res != null) {
            SP_LOG("Unable to retrieve device size for %s. Res: %s", devPath, res);

            LOGGER.debug(String.format("Unable to retrieve device size for %s. Res: %s", devPath, res));
            return 0;
        }

        return Long.parseLong(parser.getLine());
    }

    private static boolean waitForDeviceSymlink(String devPath) {
        final int numTries = 10;
        final int sleepTime = 100;

        for(int i = 0; i < numTries; i++) {
            if (getDeviceSize(devPath) != 0) {
                return true;
            } else {
                try {
                    Thread.sleep(sleepTime);
                } catch (Exception ex) {
                    // don't do anything
                }
            }
        }
        return false;
    }

    public static String getVolumeNameFromPath(final String volumeUuid, boolean tildeNeeded) {
        if (volumeUuid == null) {
            return null;
        }
        if (volumeUuid.startsWith("/dev/storpool/")) {
            return volumeUuid.split("/")[3];
        } else if (volumeUuid.startsWith("/dev/storpool-byid/")) {
            return tildeNeeded ? "~" + volumeUuid.split("/")[3] : volumeUuid.split("/")[3];
        }

        return null;
    }

    public static boolean attachOrDetachVolume(String command, String type, String volumeUuid) {
        if (volumeUuid == null) {
            LOGGER.debug("Could not attach volume. The volume ID is null");
            return false;
        }
        final String name = getVolumeNameFromPath(volumeUuid, true);
        if (name == null) {
            return false;
        }

        SP_LOG("StorPoolStorageAdaptor.attachOrDetachVolume: cmd=%s, type=%s, uuid=%s, name=%s", command, type, volumeUuid, name);

        final int numTries = 10;
        final int sleepTime = 1000;
        String err = null;

        for(int i = 0; i < numTries; i++) {
            Script sc = new Script("storpool", 0, LOGGER);
            sc.add("-M");
            sc.add(command);
            sc.add(type, name);
            sc.add("here");
            if (command.equals("attach")) {
                sc.add("onRemoteAttached");
                sc.add("export");
            }

            OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();

            String res = sc.execute(parser);
            if (res == null) {
                err = null;
                break;
            }
            err = String.format("Unable to %s volume %s. Error: %s", command, name, res);

            if (command.equals("detach")) {
                try {
                    Thread.sleep(sleepTime);
                } catch (Exception ex) {
                    // don't do anything
                }
            } else {
                break;
            }
        }

        if (err != null) {
            SP_LOG(err);
            LOGGER.warn(err);
            throw new CloudRuntimeException(err);
        }

        if (command.equals("attach")) {
            return waitForDeviceSymlink(volumeUuid);
        } else {
            return true;
        }
    }

    public static boolean resize(String newSize, String volumeUuid ) {
        final String name = getVolumeNameFromPath(volumeUuid, true);
        if (name == null) {
            return false;
        }

        SP_LOG("StorPoolStorageAdaptor.resize: size=%s, uuid=%s, name=%s", newSize, volumeUuid, name);

        Script sc = new Script("storpool", 0, LOGGER);
        sc.add("-M");
        sc.add("volume");
        sc.add(name);
        sc.add("update");
        sc.add("size");
        sc.add(newSize);
        sc.add("shrinkOk");

        OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        String res = sc.execute(parser);
        if (res == null) {
            return true;
        }

        String err = String.format("Unable to resize volume %s. Error: %s", name, res);
        SP_LOG(err);
        LOGGER.warn(err);
        throw new CloudRuntimeException(err);
    }

    @Override
    public KVMPhysicalDisk getPhysicalDisk(String volumeUuid, KVMStoragePool pool) {
        SP_LOG("StorPoolStorageAdaptor.getPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool);

        LOGGER.debug(String.format("getPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool));

        final long deviceSize = getDeviceSize(volumeUuid);

        KVMPhysicalDisk physicalDisk = new KVMPhysicalDisk(volumeUuid, volumeUuid, pool);
        physicalDisk.setFormat(PhysicalDiskFormat.RAW);
        physicalDisk.setSize(deviceSize);
        physicalDisk.setVirtualSize(deviceSize);
        return physicalDisk;
    }

    @Override
    public boolean connectPhysicalDisk(String volumeUuid, KVMStoragePool pool, Map<String, String> details, boolean isVMMigrate) {
        SP_LOG("StorPoolStorageAdaptor.connectPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool);

        LOGGER.debug(String.format("connectPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool));

        return attachOrDetachVolume("attach", "volume", volumeUuid);
    }

    @Override
    public boolean disconnectPhysicalDisk(String volumeUuid, KVMStoragePool pool) {
        SP_LOG("StorPoolStorageAdaptor.disconnectPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool);

        LOGGER.debug(String.format("disconnectPhysicalDisk: uuid=%s, pool=%s", volumeUuid, pool));
        return attachOrDetachVolume("detach", "volume", volumeUuid);
    }

    public boolean disconnectPhysicalDisk(Map<String, String> volumeToDisconnect) {
        String volumeUuid = volumeToDisconnect.get(DiskTO.UUID);
        LOGGER.debug(String.format("StorPoolStorageAdaptor.disconnectPhysicalDisk: map. uuid=%s", volumeUuid));
        return attachOrDetachVolume("detach", "volume", volumeUuid);
    }

    @Override
    public boolean disconnectPhysicalDiskByPath(String localPath) {
        LOGGER.debug(String.format("disconnectPhysicalDiskByPath: localPath=%s", localPath));
        return attachOrDetachVolume("detach", "volume", localPath);
    }

    @Override
    public boolean deletePhysicalDisk(String volumeUuid, KVMStoragePool pool, Storage.ImageFormat format) {
        // Should only come here when cleaning-up StorPool snapshots associated with CloudStack templates.
        SP_LOG("StorPoolStorageAdaptor.deletePhysicalDisk: uuid=%s, pool=%s, format=%s", volumeUuid, pool, format);
        final String name = getVolumeNameFromPath(volumeUuid, true);
        if (name == null) {
            final String err = String.format("StorPoolStorageAdaptor.deletePhysicalDisk: '%s' is not a StorPool volume?", volumeUuid);
            SP_LOG(err);
            throw new UnsupportedOperationException(err);
        }

        Script sc = new Script("storpool", 0, LOGGER);
        sc.add("-M");
        sc.add("snapshot", name);
        sc.add("delete", name);

        OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();

        String res = sc.execute(parser);
        if (res != null) {
            final String err = String.format("Unable to delete StorPool snapshot '%s'. Error: %s", name, res);
            SP_LOG(err);
            LOGGER.warn(err);
            throw new UnsupportedOperationException(err);
        }
        return true; // apparently ignored
    }

    @Override
    public List<KVMPhysicalDisk> listPhysicalDisks(String storagePoolUuid, KVMStoragePool pool) {
        SP_LOG("StorPoolStorageAdaptor.listPhysicalDisks: uuid=%s, pool=%s", storagePoolUuid, pool);
        throw new UnsupportedOperationException("Listing disks is not supported for this configuration.");
    }

    @Override
    public KVMPhysicalDisk createTemplateFromDisk(KVMPhysicalDisk disk, String name, PhysicalDiskFormat format, long size, KVMStoragePool destPool) {
        SP_LOG("StorPoolStorageAdaptor.createTemplateFromDisk: disk=%s, name=%s, fmt=%s, size=%d, dst_pool=%s", disk, name, format, size, destPool.getUuid());
        throw new UnsupportedOperationException("Creating a template from a disk is not yet supported for this configuration.");
    }

    @Override
    public KVMPhysicalDisk copyPhysicalDisk(KVMPhysicalDisk disk, String name, KVMStoragePool destPool, int timeout, byte[] sourcePassphrase, byte[] destPassphrase, ProvisioningType provisioningType) {
        return copyPhysicalDisk(disk, name, destPool, timeout);
    }

    @Override
    public KVMPhysicalDisk copyPhysicalDisk(KVMPhysicalDisk disk, String name, KVMStoragePool destPool, int timeout) {
        SP_LOG("StorPoolStorageAdaptor.copyPhysicalDisk: disk=%s, name=%s, dst_pool=%s, to=%d", disk, name, destPool.getUuid(), timeout);
        throw new UnsupportedOperationException("Copying a disk is not supported in this configuration.");
    }

    public KVMPhysicalDisk createDiskFromSnapshot(KVMPhysicalDisk snapshot, String snapshotName, String name, KVMStoragePool destPool) {
        SP_LOG("StorPoolStorageAdaptor.createDiskFromSnapshot: snap=%s, snap_name=%s, name=%s, dst_pool=%s", snapshot, snapshotName, name, destPool.getUuid());
        throw new UnsupportedOperationException("Creating a disk from a snapshot is not supported in this configuration.");
    }

    @Override
    public boolean refresh(KVMStoragePool pool) {
        SP_LOG("StorPoolStorageAdaptor.refresh: pool=%s", pool);
        return true;
    }

    @Override
    public boolean createFolder(String uuid, String path) {
        SP_LOG("StorPoolStorageAdaptor.createFolder: uuid=%s, path=%s", uuid, path);
        throw new UnsupportedOperationException("A folder cannot be created in this configuration.");
    }

    @Override
    public KVMPhysicalDisk createDiskFromTemplateBacking(KVMPhysicalDisk template, String name,
                                                         PhysicalDiskFormat format, long size, KVMStoragePool destPool, int timeout, byte[] passphrase) {
        return null;
    }

    @Override
    public KVMPhysicalDisk createTemplateFromDirectDownloadFile(String templateFilePath, String destTemplatePath,
                                                                KVMStoragePool destPool, ImageFormat format, int timeout) {
        if (StringUtils.isEmpty(templateFilePath) || destPool == null) {
            throw new CloudRuntimeException(
                    "Unable to create template from direct download template file due to insufficient data");
        }

        File sourceFile = new File(templateFilePath);
        if (!sourceFile.exists()) {
            throw new CloudRuntimeException(
                    "Direct download template file " + templateFilePath + " does not exist on this host");
        }

        if (!StoragePoolType.StorPool.equals(destPool.getType())) {
            throw new CloudRuntimeException("Unsupported storage pool type: " + destPool.getType().toString());
        }

        if (!Storage.ImageFormat.QCOW2.equals(format)) {
            throw new CloudRuntimeException("Unsupported template format: " + format.toString());
        }

        String srcTemplateFilePath = templateFilePath;
        KVMPhysicalDisk destDisk = null;
        QemuImgFile srcFile = null;
        QemuImgFile destFile = null;
        String templateName = UUID.randomUUID().toString();
        String volume = null;
        try {

            srcTemplateFilePath = extractTemplate(templateFilePath, sourceFile, srcTemplateFilePath, templateName);

            QemuImg.PhysicalDiskFormat srcFileFormat = QemuImg.PhysicalDiskFormat.QCOW2;

            srcFile = new QemuImgFile(srcTemplateFilePath, srcFileFormat);

            String spTemplate = destPool.getUuid().split(";")[0];

            QemuImg qemu = new QemuImg(timeout);
            OutputInterpreter.AllLinesParser parser = createStorPoolVolume(destPool, srcFile, qemu, spTemplate);

            String response = parser.getLines();

            LOGGER.debug(response);
            volume = StorPoolUtil.devPath(getNameFromResponse(response, false, false));
            attachOrDetachVolume("attach", "volume", volume);
            destDisk = destPool.getPhysicalDisk(volume);
            if (destDisk == null) {
                throw new CloudRuntimeException(
                        "Failed to find the disk: " + volume + " of the storage pool: " + destPool.getUuid());
            }

            destFile = new QemuImgFile(destDisk.getPath(), QemuImg.PhysicalDiskFormat.RAW);

            qemu.convert(srcFile, destFile);
            parser = volumeSnapshot(StorPoolStorageAdaptor.getVolumeNameFromPath(volume, true), spTemplate);
            response = parser.getLines();
            LOGGER.debug(response);
            String newPath = StorPoolUtil.devPath(getNameFromResponse(response, false, true));
            destDisk = destPool.getPhysicalDisk(newPath);
        } catch (QemuImgException | LibvirtException e) {
            destDisk = null;
        } finally {
            if (volume != null) {
                attachOrDetachVolume("detach", "volume", volume);
                volumeDelete(StorPoolStorageAdaptor.getVolumeNameFromPath(volume, true));
            }
            Script.runSimpleBashScript("rm -f " + srcTemplateFilePath);
        }

        return destDisk;
    }

    @Override
    public boolean createFolder(String uuid, String path, String localPath) {
        return false;
    }

    @Override
    public KVMPhysicalDisk createPhysicalDisk(String name, KVMStoragePool pool, PhysicalDiskFormat format,
            ProvisioningType provisioningType, long size, byte[] passphrase) {
        return null;
    }

    @Override
    public KVMPhysicalDisk createDiskFromTemplate(KVMPhysicalDisk template, String name, PhysicalDiskFormat format,
            ProvisioningType provisioningType, long size, KVMStoragePool destPool, int timeout, byte[] passphrase) {
        return null;
    }

    private OutputInterpreter.AllLinesParser createStorPoolVolume(KVMStoragePool destPool, QemuImgFile srcFile,
                                                                  QemuImg qemu, String templateUuid) throws QemuImgException, LibvirtException {
        Map<String, String> info = qemu.info(srcFile);
        Map<String, Object> reqParams = new HashMap<>();
        reqParams.put("template", templateUuid);
        reqParams.put("size", info.get("virtual_size"));
        Map<String, String> tags = new HashMap<>();
        tags.put("cs", "template");
        reqParams.put("tags", tags);
        Gson gson = new Gson();
        String js = gson.toJson(reqParams);

        Script sc = createStorPoolRequest(js, "VolumeCreate", null,true);
        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();

        String res = sc.execute(parser);
        if (res != null) {
            throw new CloudRuntimeException("Could not create volume due to: " + res);
        }
        return parser;
    }

    private  OutputInterpreter.AllLinesParser volumeSnapshot(String volumeName, String templateUuid) {
        Map<String, String> reqParams = new HashMap<>();
        reqParams.put("template", templateUuid);
        Gson gson = new Gson();
        String js = gson.toJson(reqParams);

        Script sc = createStorPoolRequest(js, "VolumeSnapshot", volumeName,true);
        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();

        String res = sc.execute(parser);
        if (res != null) {
            throw new CloudRuntimeException("Could not snapshot volume due to: " + res);
        }
        return parser;
    }

    private OutputInterpreter.AllLinesParser volumeDelete(String volumeName) {
        Script sc = createStorPoolRequest(null, "VolumeDelete", volumeName, false);
        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();

        String res = sc.execute(parser);
        if (res != null) {
            throw new CloudRuntimeException("Could not delete volume due to: " + res);
        }
        return parser;
    }
    @NotNull
    private static Script createStorPoolRequest(String js, String apiCall, String param, boolean jsonRequired) {
        Script sc = new Script("storpool_req", 0, LOGGER);
        sc.add("-P");
        sc.add("-M");
        if (jsonRequired) {
            sc.add("--json");
            sc.add(js);
        }
        sc.add(apiCall);
        if (param != null) {
            sc.add(param);
        }
        return sc;
    }

    private String extractTemplate(String templateFilePath, File sourceFile, String srcTemplateFilePath,
                                   String templateName) {
        if (isTemplateExtractable(templateFilePath)) {
            srcTemplateFilePath = sourceFile.getParent() + "/" + templateName;
            String extractCommand = getExtractCommandForDownloadedFile(templateFilePath, srcTemplateFilePath);
            Script.runSimpleBashScript(extractCommand);
            Script.runSimpleBashScript("rm -f " + templateFilePath);
        }
        return srcTemplateFilePath;
    }

    private boolean isTemplateExtractable(String templatePath) {
        String type = Script.runSimpleBashScript("file " + templatePath + " | awk -F' ' '{print $2}'");
        return type.equalsIgnoreCase("bzip2") || type.equalsIgnoreCase("gzip") || type.equalsIgnoreCase("zip");
    }

    private String getExtractCommandForDownloadedFile(String downloadedTemplateFile, String templateFile) {
        if (downloadedTemplateFile.endsWith(".zip")) {
            return "unzip -p " + downloadedTemplateFile + " | cat > " + templateFile;
        } else if (downloadedTemplateFile.endsWith(".bz2")) {
            return "bunzip2 -c " + downloadedTemplateFile + " > " + templateFile;
        } else if (downloadedTemplateFile.endsWith(".gz")) {
            return "gunzip -c " + downloadedTemplateFile + " > " + templateFile;
        } else {
            throw new CloudRuntimeException("Unable to extract template " + downloadedTemplateFile);
        }
    }

    private String getNameFromResponse(String resp, boolean tildeNeeded, boolean isSnapshot) {
        JsonParser jsonParser = new JsonParser();
        JsonObject respObj = (JsonObject) jsonParser.parse(resp);
        JsonPrimitive data = isSnapshot ? respObj.getAsJsonPrimitive("snapshotGlobalId") : respObj.getAsJsonPrimitive("globalId");
        String name = data !=null ? data.getAsString() : null;
        name = name != null ? name.startsWith("~") && !tildeNeeded ? name.split("~")[1] : name : name;
        return name;
    }
}
