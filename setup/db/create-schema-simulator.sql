-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
use simulator;

DROP TABLE IF EXISTS `simulator`.`mockhost`;
DROP TABLE IF EXISTS `simulator`.`mocksecstorage`;
DROP TABLE IF EXISTS `simulator`.`mockstoragepool`;
DROP TABLE IF EXISTS `simulator`.`mockvm`;
DROP TABLE IF EXISTS `simulator`.`mockvolume`;
DROP TABLE IF EXISTS `simulator`.`mocksecurityrules`;
DROP TABLE IF EXISTS `simulator`.`mockgpudevice`;

CREATE TABLE  `simulator`.`mockhost` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  `private_ip_address` char(40),
  `private_mac_address` varchar(17),
  `private_netmask` varchar(15),
  `storage_ip_address` char(40),
  `storage_netmask` varchar(15),
  `storage_mac_address` varchar(17),
  `public_ip_address` char(40),
  `public_netmask` varchar(15),
  `public_mac_address` varchar(17),
  `guid` varchar(255) UNIQUE,
  `version` varchar(40) NOT NULL,
  `data_center_id` bigint unsigned NOT NULL,
  `pod_id` bigint unsigned,
  `cluster_id` bigint unsigned COMMENT 'foreign key to cluster',
  `cpus` int(10) unsigned,
  `speed` int(10) unsigned,
  `ram` bigint unsigned,
  `arch` varchar(8) DEFAULT "x86_64" COMMENT "the CPU architecture of the host",
  `capabilities` varchar(255) COMMENT 'host capabilities in comma separated list',
  `vm_id` bigint unsigned,
  `resource` varchar(255) DEFAULT NULL COMMENT 'If it is a local resource, this is the class name',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `simulator`.`mocksecstorage` (
  `id` bigint unsigned NOT NULL auto_increment,
  `url` varchar(255),
  `capacity` bigint unsigned,
  `mount_point` varchar(255),
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `simulator`.`mockstoragepool` (
  `id` bigint unsigned NOT NULL auto_increment,
  `guid` varchar(255),
  `mount_point` varchar(255),
  `capacity` bigint,
  `pool_type` varchar(40),
  `hostguid` varchar(255) UNIQUE,
  PRIMARY KEY  (`id`),
  INDEX `i_mockstoragepool__guid`(`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `simulator`.`mockvm` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255),
  `host_id` bigint unsigned,
  `type` varchar(40),
  `power_state` varchar(40),
  `vnc_port` bigint unsigned,
  `memory` bigint unsigned,
  `cpu` bigint unsigned,
  `bootargs` varchar(255),
  PRIMARY KEY  (`id`),
  INDEX `i_mockvm__host_id`(`host_id`),
  INDEX `i_mockvm__power_state`(`power_state`),
  INDEX `i_mockvm__type`(`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `simulator`.`mockvolume` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255),
  `size` bigint unsigned,
  `path` varchar(255),
  `pool_id` bigint unsigned,
  `type` varchar(40),
  `status` varchar(40),
  PRIMARY KEY  (`id`),
  INDEX `i_mockvolume__pool_id`(`pool_id`),
  INDEX `i_mockvolume__status`(`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE `simulator`.`mockconfiguration` (
  `id` bigint unsigned NOT NULL auto_increment,
  `data_center_id` bigint unsigned,
  `pod_id` bigint unsigned,
  `cluster_id` bigint unsigned,
  `host_id` bigint unsigned,
  `name` varchar(255),
  `values` varchar(4096),
  `count` int,
  `json_response` varchar(4096),
  `removed` datetime,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `simulator`.`mocksecurityrules` (
  `id` bigint unsigned NOT NULL auto_increment,
  `vmid` bigint unsigned,
  `signature` varchar(255),
  `ruleset` varchar(4095),
  `hostid` varchar(255),
  `seqnum` bigint unsigned,
  `vmname` varchar(255),
  PRIMARY KEY (`id`),
  INDEX `i_mocksecurityrules__vmid`(`vmid`),
  INDEX `i_mocksecurityrules__hostid`(`hostid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- Mock GPU Devices for Simulator
CREATE TABLE IF NOT EXISTS `simulator`.`mockgpudevice` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `bus_address` varchar(64) NOT NULL,
  `vendor_id` varchar(32) NOT NULL,
  `device_id` varchar(32) NOT NULL,
  `numa_node` int unsigned NOT NULL,
  `pci_root` varchar(64) NOT NULL,
  `vendor_name` varchar(128) NOT NULL,
  `device_name` varchar(128) NOT NULL,
  `host_id` bigint unsigned DEFAULT NULL,
  `vm_id` bigint unsigned DEFAULT NULL,
  `max_vgpu_per_pgpu` bigint unsigned NOT NULL DEFAULT 1,
  `video_ram` bigint unsigned NOT NULL DEFAULT 0,
  `max_resolution_x` bigint unsigned NOT NULL DEFAULT 0,
  `max_resolution_y` bigint unsigned NOT NULL DEFAULT 0,
  `max_heads` bigint unsigned NOT NULL DEFAULT 0,
  `state` varchar(32) DEFAULT 'Available',
  `device_type` varchar(32) DEFAULT 'PCI',
  `parent_device_id` bigint unsigned DEFAULT NULL,
  `profile_name` varchar(128) DEFAULT NULL,
  `passthrough_enabled` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mockgpudevice__bus_address` (`bus_address`, `host_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
