# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

%define __os_install_post %{nil}
%global debug_package %{nil}
%global __requires_exclude libc\\.so\\..*
%define _binaries_in_noarch_packages_terminate_build   0

# DISABLE the post-percentinstall java repacking and line number stripping
# we need to find a way to just disable the java repacking and line number stripping, but not the autodeps

Name:      cloudstack
Summary:   CloudStack IaaS Platform
#http://fedoraproject.org/wiki/PackageNamingGuidelines#Pre-Release_packages
%define _maventag %{_fullver}
Release:   %{_rel}

Version:   %{_ver}
License:   ASL 2.0
Vendor:    Apache CloudStack <dev@cloudstack.apache.org>
Packager:  Apache CloudStack <dev@cloudstack.apache.org>
Group:     System Environment/Libraries
# FIXME do groups for every single one of the subpackages
Source0:   %{name}-%{_maventag}.tgz
BuildRoot: %{_tmppath}/%{name}-%{_maventag}-%{release}-build
BuildArch: noarch

BuildRequires: (java-11-openjdk-devel or java-17-openjdk-devel)
#BuildRequires: ws-commons-util
BuildRequires: jpackage-utils
BuildRequires: gcc
BuildRequires: glibc-devel
BuildRequires: /usr/bin/mkisofs
BuildRequires: python3-setuptools
BuildRequires: wget
BuildRequires: nodejs

%description
CloudStack is a highly-scalable elastic, open source,
intelligent IaaS cloud implementation.

%package management
Summary:   CloudStack management server UI
Requires: java-17-openjdk
Requires: (tzdata-java or timezone-java)
Requires: python3
Requires: bash
Requires: gawk
Requires: which
Requires: file
Requires: tar
Requires: bzip2
Requires: gzip
Requires: unzip
Requires: /sbin/mount.nfs
Requires: (openssh-clients or openssh)
Requires: (nfs-utils or nfs-client)
Requires: iproute
Requires: wget
Requires: (mysql or mariadb)
Requires: sudo
Requires: /sbin/service
Requires: /sbin/chkconfig
Requires: /usr/bin/ssh-keygen
Requires: (genisoimage or mkisofs)
Requires: ipmitool
Requires: %{name}-common = %{_ver}
Requires: (iptables-services or iptables)
Requires: rng-tools
Requires: (qemu-img or qemu-tools)
Requires: python3-pip
Requires: python3-setuptools
Requires: (libgcrypt > 1.8.3 or libgcrypt20)
Group:     System Environment/Libraries
%description management
The CloudStack management server is the central point of coordination,
management, and intelligence in CloudStack.

%package common
Summary: Apache CloudStack common files and scripts
Requires: python3
Group:   System Environment/Libraries
%description common
The Apache CloudStack files shared between agent and management server
%global __requires_exclude ^(libuuid\\.so\\.1|/usr/bin/python)$

%package agent
Summary: CloudStack Agent for KVM hypervisors
Requires: (openssh-clients or openssh)
Requires: java-17-openjdk
Requires: tzdata-java
Requires: %{name}-common = %{_ver}
Requires: libvirt
Requires: libvirt-daemon-driver-storage-rbd
Requires: ebtables
Requires: iptables
Requires: ethtool
Requires: (net-tools or net-tools-deprecated)
Requires: iproute
Requires: ipset
Requires: perl
Requires: rsync
Requires: cifs-utils
Requires: (python3-libvirt or python3-libvirt-python)
Requires: (qemu-img or qemu-tools)
Requires: qemu-kvm
Requires: cryptsetup
Requires: rng-tools
Requires: (libgcrypt > 1.8.3 or libgcrypt20)
Requires: (selinux-tools if selinux-tools)
Requires: sysstat
Provides: cloud-agent
Group: System Environment/Libraries
%description agent
The CloudStack agent for KVM hypervisors

%package baremetal-agent
Summary: CloudStack baremetal agent
Requires: tftp-server
Requires: xinetd
Requires: syslinux
Requires: chkconfig
Requires: dhcp
Requires: httpd
Group:     System Environment/Libraries
%description baremetal-agent
The CloudStack baremetal agent

%package usage
Summary: CloudStack Usage calculation server
Requires: java-17-openjdk
Requires: tzdata-java
Group: System Environment/Libraries
%description usage
The CloudStack usage calculation service

%package ui
Summary: CloudStack UI
Group: System Environment/Libraries
%description ui
The CloudStack UI

%package marvin
Summary: Apache CloudStack Marvin library
Requires: python3-pip
Requires: gcc
Requires: python3-devel
Requires: libffi-devel
Requires: openssl-devel
Group: System Environment/Libraries
%description marvin
Apache CloudStack Marvin library

%package integration-tests
Summary: Apache CloudStack Marvin integration tests
Requires: %{name}-marvin = %{_ver}
Group: System Environment/Libraries
%description integration-tests
Apache CloudStack Marvin integration tests

%if "%{_ossnoss}" == "noredist"
%package mysql-ha
Summary: Apache CloudStack Balancing Strategy for MySQL
Group: System Environmnet/Libraries
%description mysql-ha
Apache CloudStack Balancing Strategy for MySQL

%endif

%prep
echo Doing CloudStack build

%setup -q -n %{name}-%{_maventag}

%build

cp packaging/el8/replace.properties build/replace.properties
echo VERSION=%{_maventag} >> build/replace.properties
echo PACKAGE=%{name} >> build/replace.properties
touch build/gitrev.txt
echo $(git rev-parse HEAD) > build/gitrev.txt

if [ "%{_ossnoss}" == "NOREDIST" -o "%{_ossnoss}" == "noredist" ] ; then
   echo "Adding noredist flag to the maven build"
   FLAGS="$FLAGS -Dnoredist"
fi

if [ "%{_sim}" == "SIMULATOR" -o "%{_sim}" == "simulator" ] ; then
   echo "Adding simulator flag to the maven build"
   FLAGS="$FLAGS -Dsimulator"
fi

if [ \"%{_temp}\" != "" ]; then
    echo "Adding flags to package requested templates"
    FLAGS="$FLAGS `rpm --eval %{?_temp}`"
fi

mvn -Psystemvm,developer $FLAGS clean package
cd ui && npm install && npm run build && cd ..

%install
[ ${RPM_BUILD_ROOT} != "/" ] && rm -rf ${RPM_BUILD_ROOT}
# Common directories
mkdir -p ${RPM_BUILD_ROOT}%{_bindir}
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/agent
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/ipallocator
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/cache/%{name}/management/work
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/cache/%{name}/management/temp
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/%{name}/mnt
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/%{name}/management
mkdir -p ${RPM_BUILD_ROOT}%{_initrddir}
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/default
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/profile.d
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/sudoers.d

# Common
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/scripts
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/vms
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/python-site
mkdir -p ${RPM_BUILD_ROOT}/usr/bin
cp -r scripts/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/scripts
install -D systemvm/dist/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/vms/
install python/lib/cloud_utils.py ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/python-site/cloud_utils.py
cp -r python/lib/cloudutils ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/python-site/
python3 -m py_compile ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/python-site/cloud_utils.py
python3 -m compileall ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/python-site/cloudutils
cp build/gitrev.txt ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/scripts
cp packaging/el8/cloudstack-sccs ${RPM_BUILD_ROOT}/usr/bin

mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/scripts/network/cisco
cp -r plugins/network-elements/cisco-vnmc/src/main/scripts/network/cisco/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/scripts/network/cisco

# Management
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/lib
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/management
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/management
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/systemd/system/%{name}-management.service.d
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/run
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup/wheel

# Setup Jetty
ln -sf /etc/%{name}/management ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/conf
ln -sf /var/log/%{name}/management ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/logs

install -D client/target/utilities/bin/cloud-migrate-databases ${RPM_BUILD_ROOT}%{_bindir}/%{name}-migrate-databases
install -D client/target/utilities/bin/cloud-set-guest-password ${RPM_BUILD_ROOT}%{_bindir}/%{name}-set-guest-password
install -D client/target/utilities/bin/cloud-set-guest-sshkey ${RPM_BUILD_ROOT}%{_bindir}/%{name}-set-guest-sshkey
install -D client/target/utilities/bin/cloud-setup-databases ${RPM_BUILD_ROOT}%{_bindir}/%{name}-setup-databases
install -D client/target/utilities/bin/cloud-setup-encryption ${RPM_BUILD_ROOT}%{_bindir}/%{name}-setup-encryption
install -D client/target/utilities/bin/cloud-setup-management ${RPM_BUILD_ROOT}%{_bindir}/%{name}-setup-management
install -D client/target/utilities/bin/cloud-setup-baremetal ${RPM_BUILD_ROOT}%{_bindir}/%{name}-setup-baremetal
install -D client/target/utilities/bin/cloud-sysvmadm ${RPM_BUILD_ROOT}%{_bindir}/%{name}-sysvmadm
install -D client/target/utilities/bin/cloud-update-xenserver-licenses ${RPM_BUILD_ROOT}%{_bindir}/%{name}-update-xenserver-licenses
# Bundle cmk in cloudstack-management
CMK_REL=$(wget -O - "https://api.github.com/repos/apache/cloudstack-cloudmonkey/releases" 2>/dev/null | jq -r '.[0].tag_name')
wget https://github.com/apache/cloudstack-cloudmonkey/releases/download/$CMK_REL/cmk.linux.x86-64 -O ${RPM_BUILD_ROOT}%{_bindir}/cmk
chmod +x ${RPM_BUILD_ROOT}%{_bindir}/cmk

cp -r client/target/utilities/scripts/db/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup
cp -r plugins/integrations/kubernetes-service/src/main/resources/conf/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf
cp -r client/target/cloud-client-ui-%{_maventag}.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/
cp -r client/target/classes/META-INF/webapp ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapp
cp ui/dist/config.json ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/management/
cp -r ui/dist/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapp/
rm -f ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapp/config.json
ln -sf /etc/%{name}/management/config.json ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapp/config.json
mv ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cloud-client-ui-%{_maventag}.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/lib/cloudstack-%{_maventag}.jar
cp client/target/lib/*jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/lib/

# Don't package the scripts in the management webapp
rm -rf ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapps/client/WEB-INF/classes/scripts
rm -rf ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/webapps/client/WEB-INF/classes/vms

for name in db.properties server.properties log4j-cloud.xml environment.properties java.security.ciphers
do
  cp client/target/conf/$name ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/management/$name
done

ln -sf log4j-cloud.xml  ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/management/log4j2.xml

install python/bindir/cloud-external-ipallocator.py ${RPM_BUILD_ROOT}%{_bindir}/%{name}-external-ipallocator.py
install -D client/target/pythonlibs/jasypt-1.9.3.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/lib/jasypt-1.9.3.jar
install -D utils/target/cloud-utils-%{_maventag}-bundled.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-common/lib/%{name}-utils.jar

install -D packaging/el8/cloud-ipallocator.rc ${RPM_BUILD_ROOT}%{_initrddir}/%{name}-ipallocator
install -D packaging/el8/cloud.limits ${RPM_BUILD_ROOT}%{_sysconfdir}/security/limits.d/cloud
install -D packaging/el8/filelimit.conf ${RPM_BUILD_ROOT}%{_sysconfdir}/systemd/system/%{name}-management.service.d
install -D packaging/systemd/cloudstack-management.service ${RPM_BUILD_ROOT}%{_unitdir}/%{name}-management.service
install -D packaging/systemd/cloudstack-management.default ${RPM_BUILD_ROOT}%{_sysconfdir}/default/%{name}-management
install -D server/target/conf/cloudstack-sudoers ${RPM_BUILD_ROOT}%{_sysconfdir}/sudoers.d/%{name}-management
touch ${RPM_BUILD_ROOT}%{_localstatedir}/run/%{name}-management.pid
#install -D server/target/conf/cloudstack-catalina.logrotate ${RPM_BUILD_ROOT}%{_sysconfdir}/logrotate.d/%{name}-catalina
install -D server/target/conf/cloudstack-management.logrotate ${RPM_BUILD_ROOT}%{_sysconfdir}/logrotate.d/%{name}-management

install -D plugins/integrations/kubernetes-service/src/main/resources/conf/etcd-node.yml ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf/etcd-node.yml
install -D plugins/integrations/kubernetes-service/src/main/resources/conf/k8s-control-node.yml ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf/k8s-control-node.yml
install -D plugins/integrations/kubernetes-service/src/main/resources/conf/k8s-control-node-add.yml ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf/k8s-control-node-add.yml
install -D plugins/integrations/kubernetes-service/src/main/resources/conf/k8s-node.yml ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/cks/conf/k8s-node.yml

# SystemVM template
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/templates/systemvm
cp -r engine/schema/dist/systemvm-templates/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/templates/systemvm
rm -rf ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/templates/systemvm/md5sum.txt

# Sample Extensions
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/extensions
cp -r extensions/* ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/extensions
ln -sf %{_sysconfdir}/%{name}/extensions ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/extensions

# UI
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/ui
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-ui/
cp -r client/target/classes/META-INF/webapp/WEB-INF ${RPM_BUILD_ROOT}%{_datadir}/%{name}-ui
cp ui/dist/config.json ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/ui/
cp -r ui/dist/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-ui/
rm -f ${RPM_BUILD_ROOT}%{_datadir}/%{name}-ui/config.json
ln -sf /etc/%{name}/ui/config.json ${RPM_BUILD_ROOT}%{_datadir}/%{name}-ui/config.json

# Package mysql-connector-python
wget -P ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup/wheel https://files.pythonhosted.org/packages/ee/ff/48bde5c0f013094d729fe4b0316ba2a24774b3ff1c52d924a8a4cb04078a/six-1.15.0-py2.py3-none-any.whl
wget -P ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup/wheel https://files.pythonhosted.org/packages/e9/93/4860cebd5ad3ff2664ad3c966490ccb46e3b88458b2095145bca11727ca4/setuptools-47.3.1-py3-none-any.whl
wget -P ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup/wheel https://files.pythonhosted.org/packages/32/27/1141a8232723dcb10a595cc0ce4321dcbbd5215300bf4acfc142343205bf/protobuf-3.19.6-py2.py3-none-any.whl
wget -P ${RPM_BUILD_ROOT}%{_datadir}/%{name}-management/setup/wheel https://files.pythonhosted.org/packages/08/1f/42d74bae9dd6dcfec67c9ed0f3fa482b1ae5ac5f117ca82ab589ecb3ca19/mysql_connector_python-8.0.31-py2.py3-none-any.whl

chmod 440 ${RPM_BUILD_ROOT}%{_sysconfdir}/sudoers.d/%{name}-management
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/%{name}/mnt
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/%{name}/management
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/cache/%{name}/management/work
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/cache/%{name}/management/temp
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/management
chmod 770 ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/agent

# KVM Agent
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/agent
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/agent
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/plugins
install -D packaging/systemd/cloudstack-agent.service ${RPM_BUILD_ROOT}%{_unitdir}/%{name}-agent.service
install -D packaging/systemd/cloudstack-rolling-maintenance@.service ${RPM_BUILD_ROOT}%{_unitdir}/%{name}-rolling-maintenance@.service
install -D packaging/systemd/cloudstack-agent.default ${RPM_BUILD_ROOT}%{_sysconfdir}/default/%{name}-agent
install -D agent/target/transformed/agent.properties ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/agent/agent.properties
install -D agent/target/transformed/environment.properties ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/agent/environment.properties
install -D agent/target/transformed/log4j-cloud.xml ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/agent/log4j-cloud.xml
install -D agent/target/transformed/cloud-setup-agent ${RPM_BUILD_ROOT}%{_bindir}/%{name}-setup-agent
install -D agent/target/transformed/cloudstack-agent-upgrade ${RPM_BUILD_ROOT}%{_bindir}/%{name}-agent-upgrade
install -D agent/target/transformed/cloud-guest-tool ${RPM_BUILD_ROOT}%{_bindir}/%{name}-guest-tool
install -D agent/target/transformed/libvirtqemuhook ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib/libvirtqemuhook
install -D agent/target/transformed/rolling-maintenance ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib/rolling-maintenance
install -D agent/target/transformed/cloud-ssh ${RPM_BUILD_ROOT}%{_bindir}/%{name}-ssh
install -D agent/target/transformed/cloudstack-agent-profile.sh ${RPM_BUILD_ROOT}%{_sysconfdir}/profile.d/%{name}-agent-profile.sh
install -D agent/target/transformed/cloudstack-agent.logrotate ${RPM_BUILD_ROOT}%{_sysconfdir}/logrotate.d/%{name}-agent
install -D plugins/hypervisors/kvm/target/cloud-plugin-hypervisor-kvm-%{_maventag}.jar ${RPM_BUILD_ROOT}%{_datadir}/%name-agent/lib/cloud-plugin-hypervisor-kvm-%{_maventag}.jar
cp plugins/hypervisors/kvm/target/dependencies/*  ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib
cp plugins/storage/volume/storpool/target/*.jar  ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib
cp plugins/storage/volume/linstor/target/*.jar  ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib

# Usage server
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/usage
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-usage/lib
install -D usage/target/cloud-usage-%{_maventag}.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-usage/cloud-usage-%{_maventag}.jar
install -D usage/target/transformed/db.properties ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/usage/db.properties
install -D usage/target/transformed/log4j-cloud_usage.xml ${RPM_BUILD_ROOT}%{_sysconfdir}/%{name}/usage/log4j-cloud.xml
cp usage/target/dependencies/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-usage/lib/
cp client/target/lib/mysql*jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-usage/lib/
install -D packaging/systemd/cloudstack-usage.service ${RPM_BUILD_ROOT}%{_unitdir}/%{name}-usage.service
install -D packaging/systemd/cloudstack-usage.default ${RPM_BUILD_ROOT}%{_sysconfdir}/default/%{name}-usage
mkdir -p ${RPM_BUILD_ROOT}%{_localstatedir}/log/%{name}/usage/
install -D usage/target/transformed/cloudstack-usage.logrotate ${RPM_BUILD_ROOT}%{_sysconfdir}/logrotate.d/%{name}-usage

# Marvin
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-marvin
cp tools/marvin/dist/Marvin-*.tar.gz ${RPM_BUILD_ROOT}%{_datadir}/%{name}-marvin/

# integration-tests
mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-integration-tests
cp -r test/integration/* ${RPM_BUILD_ROOT}%{_datadir}/%{name}-integration-tests/

# MYSQL HA
if [ "x%{_ossnoss}" == "xnoredist" ] ; then
  mkdir -p ${RPM_BUILD_ROOT}%{_datadir}/%{name}-mysql-ha/lib
  cp -r plugins/database/mysql-ha/target/cloud-plugin-database-mysqlha-%{_maventag}.jar ${RPM_BUILD_ROOT}%{_datadir}/%{name}-mysql-ha/lib
fi

#License files from whisker
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-management-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-management-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-common-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-common-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-agent-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-agent-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-usage-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-usage-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-ui-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-ui-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-marvin-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-marvin-%{version}/LICENSE
install -D tools/whisker/NOTICE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-integration-tests-%{version}/NOTICE
install -D tools/whisker/LICENSE ${RPM_BUILD_ROOT}%{_defaultdocdir}/%{name}-integration-tests-%{version}/LICENSE

%clean
[ ${RPM_BUILD_ROOT} != "/" ] && rm -rf ${RPM_BUILD_ROOT}

%posttrans common

unalias cp
python_dir=$(python3 -c "from distutils.sysconfig import get_python_lib; print(get_python_lib(1))")
if [ ! -z $python_dir ];then
  cp -f -r /usr/share/cloudstack-common/python-site/* $python_dir/
fi

%preun management
/usr/bin/systemctl stop cloudstack-management || true
/usr/bin/systemctl disable cloudstack-management || true

%pre management
id cloud > /dev/null 2>&1 || /usr/sbin/useradd -M -U -c "CloudStack unprivileged user" \
     -r -s /bin/sh -d %{_localstatedir}/cloudstack/management cloud || true

rm -rf %{_localstatedir}/cache/cloudstack

# in case of upgrade to 4.9+ copy commands.properties if not exists in /etc/cloudstack/management/
if [ "$1" == "2" ] ; then
    if [ -f "%{_datadir}/%{name}-management/webapps/client/WEB-INF/classes/commands.properties" ] && [ ! -f "%{_sysconfdir}/%{name}/management/commands.properties" ] ; then
        cp -p %{_datadir}/%{name}-management/webapps/client/WEB-INF/classes/commands.properties %{_sysconfdir}/%{name}/management/commands.properties
    fi
fi

# Remove old tomcat symlinks and env config file
if [ -L "%{_datadir}/%{name}-management/lib" ]
then
    rm -f %{_datadir}/%{name}-management/bin
    rm -f %{_datadir}/%{name}-management/lib
    rm -f %{_datadir}/%{name}-management/temp
    rm -f %{_datadir}/%{name}-management/work
    rm -f %{_sysconfdir}/default/%{name}-management
fi

%post management
# Install mysql-connector-python
pip3 install %{_datadir}/%{name}-management/setup/wheel/six-1.15.0-py2.py3-none-any.whl %{_datadir}/%{name}-management/setup/wheel/setuptools-47.3.1-py3-none-any.whl %{_datadir}/%{name}-management/setup/wheel/protobuf-3.19.6-py2.py3-none-any.whl %{_datadir}/%{name}-management/setup/wheel/mysql_connector_python-8.0.31-py2.py3-none-any.whl

/usr/bin/systemctl enable cloudstack-management > /dev/null 2>&1 || true
/usr/bin/systemctl enable --now rngd > /dev/null 2>&1 || true

grep -s -q "db.cloud.driver=jdbc:mysql" "%{_sysconfdir}/%{name}/management/db.properties" || sed -i -e "\$adb.cloud.driver=jdbc:mysql" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "db.usage.driver=jdbc:mysql" "%{_sysconfdir}/%{name}/management/db.properties" || sed -i -e "\$adb.usage.driver=jdbc:mysql"  "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "db.simulator.driver=jdbc:mysql" "%{_sysconfdir}/%{name}/management/db.properties" || sed -i -e "\$adb.simulator.driver=jdbc:mysql" "%{_sysconfdir}/%{name}/management/db.properties"

# Update DB properties having master and slave(s), with source and replica(s) respectively (for inclusiveness)
grep -s -q "^db.cloud.slaves=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.cloud.slaves=/db.cloud.replicas=/g" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "^db.cloud.secondsBeforeRetryMaster=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.cloud.secondsBeforeRetryMaster=/db.cloud.secondsBeforeRetrySource=/g" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "^db.cloud.queriesBeforeRetryMaster=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.cloud.queriesBeforeRetryMaster=/db.cloud.queriesBeforeRetrySource=/g" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "^db.usage.slaves=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.usage.slaves=/db.usage.replicas=/g" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "^db.usage.secondsBeforeRetryMaster=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.usage.secondsBeforeRetryMaster=/db.usage.secondsBeforeRetrySource=/g" "%{_sysconfdir}/%{name}/management/db.properties"
grep -s -q "^db.usage.queriesBeforeRetryMaster=" "%{_sysconfdir}/%{name}/management/db.properties" && sed -i "s/^db.usage.queriesBeforeRetryMaster=/db.usage.queriesBeforeRetrySource=/g" "%{_sysconfdir}/%{name}/management/db.properties"

if [ ! -f %{_datadir}/cloudstack-common/scripts/vm/hypervisor/xenserver/vhd-util ] ; then
    echo Please download vhd-util from http://download.cloudstack.org/tools/vhd-util and put it in
    echo %{_datadir}/cloudstack-common/scripts/vm/hypervisor/xenserver/
fi

if [ -f %{_sysconfdir}/sysconfig/%{name}-management ] ; then
    rm -f %{_sysconfdir}/sysconfig/%{name}-management
fi

chown -R cloud:cloud /var/log/cloudstack/management
chown -R cloud:cloud /usr/share/cloudstack-management/templates
find /usr/share/cloudstack-management/templates -type d -exec chmod 0770 {} \;

systemctl daemon-reload

%posttrans management
# Print help message
if [ -f "/usr/share/cloudstack-common/scripts/installer/cloudstack-help-text" ];then
    sed -i "s,^ACS_VERSION=.*,ACS_VERSION=%{_maventag},g" /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text
    /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text management
fi

%preun agent
/sbin/service cloudstack-agent stop || true
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del cloudstack-agent > /dev/null 2>&1 || true
fi

%pre agent

# save old configs if they exist (for upgrade). Otherwise we may lose them
# when the old packages are erased. There are a lot of properties files here.
if [ -d "%{_sysconfdir}/cloud" ] ; then
    mv %{_sysconfdir}/cloud %{_sysconfdir}/cloud.rpmsave
fi

%posttrans agent

if [ "$1" == "2" ] ; then
    echo "Running %{_bindir}/%{name}-agent-upgrade to update bridge name for upgrade from CloudStack 4.0.x (and before) to CloudStack 4.1 (and later)"
    %{_bindir}/%{name}-agent-upgrade
fi
if [ ! -d %{_sysconfdir}/libvirt/hooks ] ; then
    mkdir %{_sysconfdir}/libvirt/hooks
fi
cp -a ${RPM_BUILD_ROOT}%{_datadir}/%{name}-agent/lib/libvirtqemuhook %{_sysconfdir}/libvirt/hooks/qemu
mkdir -m 0755 -p /usr/share/cloudstack-agent/tmp
/usr/bin/systemctl restart libvirtd
/usr/bin/systemctl enable cloudstack-agent > /dev/null 2>&1 || true
/usr/bin/systemctl enable cloudstack-rolling-maintenance@p > /dev/null 2>&1 || true
/usr/bin/systemctl enable --now rngd > /dev/null 2>&1 || true

# if saved configs from upgrade exist, copy them over
if [ -f "%{_sysconfdir}/cloud.rpmsave/agent/agent.properties" ]; then
    mv %{_sysconfdir}/%{name}/agent/agent.properties  %{_sysconfdir}/%{name}/agent/agent.properties.rpmnew
    cp -p %{_sysconfdir}/cloud.rpmsave/agent/agent.properties %{_sysconfdir}/%{name}/agent
    # make sure we only do this on the first install of this RPM, don't want to overwrite on a reinstall
    mv %{_sysconfdir}/cloud.rpmsave/agent/agent.properties %{_sysconfdir}/cloud.rpmsave/agent/agent.properties.rpmsave
fi

systemctl daemon-reload

# Print help message
if [ -f "/usr/share/cloudstack-common/scripts/installer/cloudstack-help-text" ];then
    sed -i "s,^ACS_VERSION=.*,ACS_VERSION=%{_maventag},g" /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text
    /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text agent
fi

%pre usage
id cloud > /dev/null 2>&1 || /usr/sbin/useradd -M -U -c "CloudStack unprivileged user" \
     -r -s /bin/sh -d %{_localstatedir}/cloudstack/management cloud|| true

%preun usage
/sbin/service cloudstack-usage stop || true
if [ "$1" == "0" ] ; then
    /sbin/chkconfig --del cloudstack-usage > /dev/null 2>&1 || true
fi

%post usage
if [ -f "%{_sysconfdir}/%{name}/management/db.properties" ]; then
    echo "Replacing usage server's db.properties with a link to the management server's db.properties"
    rm -f %{_sysconfdir}/%{name}/usage/db.properties
    ln -s %{_sysconfdir}/%{name}/management/db.properties %{_sysconfdir}/%{name}/usage/db.properties
    /usr/bin/systemctl enable cloudstack-usage > /dev/null 2>&1 || true
fi

if [ -f "%{_sysconfdir}/%{name}/management/key" ]; then
    echo "Replacing usage server's key with a link to the management server's key"
    rm -f %{_sysconfdir}/%{name}/usage/key
    ln -s %{_sysconfdir}/%{name}/management/key %{_sysconfdir}/%{name}/usage/key
fi

if [ ! -f "%{_sysconfdir}/%{name}/usage/key" ]; then
    ln -s %{_sysconfdir}/%{name}/management/key %{_sysconfdir}/%{name}/usage/key
fi

mkdir -p /usr/local/libexec
if [ ! -f "/usr/local/libexec/sanity-check-last-id" ]; then
    echo 1 > /usr/local/libexec/sanity-check-last-id
fi
chown cloud:cloud /usr/local/libexec/sanity-check-last-id

%posttrans usage
# Print help message
if [ -f "/usr/share/cloudstack-common/scripts/installer/cloudstack-help-text" ];then
    sed -i "s,^ACS_VERSION=.*,ACS_VERSION=%{_maventag},g" /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text
    /usr/share/cloudstack-common/scripts/installer/cloudstack-help-text usage
fi

%post marvin
pip3 install --upgrade https://files.pythonhosted.org/packages/08/1f/42d74bae9dd6dcfec67c9ed0f3fa482b1ae5ac5f117ca82ab589ecb3ca19/mysql_connector_python-8.0.31-py2.py3-none-any.whl
pip3 install --upgrade /usr/share/cloudstack-marvin/Marvin-*.tar.gz

#No default permission as the permission setup is complex
%files management
%defattr(-,root,root,-)
%dir %{_datadir}/%{name}-management
%dir %attr(0770,root,cloud) %{_localstatedir}/%{name}/mnt
%dir %attr(0770,cloud,cloud) %{_localstatedir}/%{name}/management
%dir %attr(0770,root,cloud) %{_localstatedir}/cache/%{name}/management
%dir %attr(0770,root,cloud) %{_localstatedir}/log/%{name}/management
%config(noreplace) %{_sysconfdir}/default/%{name}-management
%config(noreplace) %{_sysconfdir}/sudoers.d/%{name}-management
%config(noreplace) %{_sysconfdir}/security/limits.d/cloud
%config(noreplace) %{_sysconfdir}/systemd/system/%{name}-management.service.d
%config(noreplace) %attr(0640,root,cloud) %{_sysconfdir}/%{name}/management/db.properties
%config(noreplace) %attr(0640,root,cloud) %{_sysconfdir}/%{name}/management/server.properties
%config(noreplace) %attr(0640,root,cloud) %{_sysconfdir}/%{name}/management/config.json
%config(noreplace) %{_sysconfdir}/%{name}/management/log4j-cloud.xml
%config(noreplace) %{_sysconfdir}/%{name}/management/log4j2.xml
%config(noreplace) %{_sysconfdir}/%{name}/management/environment.properties
%config(noreplace) %{_sysconfdir}/%{name}/management/java.security.ciphers
%config(noreplace) %attr(0644,root,root) %{_sysconfdir}/logrotate.d/%{name}-management
%attr(0644,root,root) %{_unitdir}/%{name}-management.service
%attr(0755,cloud,cloud) %{_localstatedir}/run/%{name}-management.pid
%attr(0755,root,root) %{_bindir}/%{name}-setup-management
%attr(0755,root,root) %{_bindir}/%{name}-update-xenserver-licenses
%{_datadir}/%{name}-management/conf
%{_datadir}/%{name}-management/lib/*.jar
%{_datadir}/%{name}-management/logs
%{_datadir}/%{name}-management/templates
%{_datadir}/%{name}-management/extensions
%attr(0755,root,root) %{_bindir}/%{name}-setup-databases
%attr(0755,root,root) %{_bindir}/%{name}-migrate-databases
%attr(0755,root,root) %{_bindir}/%{name}-set-guest-password
%attr(0755,root,root) %{_bindir}/%{name}-set-guest-sshkey
%attr(0755,root,root) %{_bindir}/%{name}-sysvmadm
%attr(0755,root,root) %{_bindir}/%{name}-setup-encryption
%attr(0755,root,root) %{_bindir}/cmk
%{_datadir}/%{name}-management/cks/conf/*.yml
%{_datadir}/%{name}-management/setup/*.sql
%{_datadir}/%{name}-management/setup/*.sh
%{_datadir}/%{name}-management/setup/server-setup.xml
%{_datadir}/%{name}-management/webapp/*
%dir %attr(0770, cloud, cloud) %{_datadir}/%{name}-management/templates
%dir %attr(0770, cloud, cloud) %{_datadir}/%{name}-management/templates/systemvm
%attr(0644, cloud, cloud) %{_datadir}/%{name}-management/templates/systemvm/*
%attr(0755,root,root) %{_bindir}/%{name}-external-ipallocator.py
%attr(0755,root,root) %{_initrddir}/%{name}-ipallocator
%dir %attr(0770,root,root) %{_localstatedir}/log/%{name}/ipallocator
%{_defaultdocdir}/%{name}-management-%{version}/LICENSE
%{_defaultdocdir}/%{name}-management-%{version}/NOTICE
%{_datadir}/%{name}-management/setup/wheel/*.whl
%dir %attr(0755,cloud,cloud) %{_sysconfdir}/%{name}/extensions
%attr(0755,cloud,cloud) %{_sysconfdir}/%{name}/extensions/*

%files agent
%attr(0755,root,root) %{_bindir}/%{name}-setup-agent
%attr(0755,root,root) %{_bindir}/%{name}-agent-upgrade
%attr(0755,root,root) %{_bindir}/%{name}-guest-tool
%attr(0755,root,root) %{_bindir}/%{name}-ssh
%attr(0644,root,root) %{_unitdir}/%{name}-agent.service
%attr(0644,root,root) %{_unitdir}/%{name}-rolling-maintenance@.service
%config(noreplace) %{_sysconfdir}/default/%{name}-agent
%attr(0644,root,root) %{_sysconfdir}/profile.d/%{name}-agent-profile.sh
%config(noreplace) %attr(0644,root,root) %{_sysconfdir}/logrotate.d/%{name}-agent
%attr(0755,root,root) %{_datadir}/%{name}-common/scripts/network/cisco
%config(noreplace) %{_sysconfdir}/%{name}/agent
%dir %{_localstatedir}/log/%{name}/agent
%attr(0644,root,root) %{_datadir}/%{name}-agent/lib/*.jar
%attr(0755,root,root) %{_datadir}/%{name}-agent/lib/libvirtqemuhook
%attr(0755,root,root) %{_datadir}/%{name}-agent/lib/rolling-maintenance
%dir %{_datadir}/%{name}-agent/plugins
%{_defaultdocdir}/%{name}-agent-%{version}/LICENSE
%{_defaultdocdir}/%{name}-agent-%{version}/NOTICE

%files common
%dir %attr(0755,root,root) %{_datadir}/%{name}-common/python-site/cloudutils
%dir %attr(0755,root,root) %{_datadir}/%{name}-common/vms
%attr(0755,root,root) %{_datadir}/%{name}-common/scripts
%attr(0755,root,root) /usr/bin/cloudstack-sccs
%attr(0644, root, root) %{_datadir}/%{name}-common/vms/agent.zip
%attr(0644, root, root) %{_datadir}/%{name}-common/vms/cloud-scripts.tgz
%attr(0644, root, root) %{_datadir}/%{name}-common/vms/patch-sysvms.sh
%attr(0644,root,root) %{_datadir}/%{name}-common/python-site/cloud_utils.py
%attr(0644,root,root) %{_datadir}/%{name}-common/python-site/__pycache__/*
%attr(0644,root,root) %{_datadir}/%{name}-common/python-site/cloudutils/*
%attr(0644, root, root) %{_datadir}/%{name}-common/lib/jasypt-1.9.3.jar
%attr(0644, root, root) %{_datadir}/%{name}-common/lib/%{name}-utils.jar
%{_defaultdocdir}/%{name}-common-%{version}/LICENSE
%{_defaultdocdir}/%{name}-common-%{version}/NOTICE

%files ui
%config(noreplace) %attr(0640,root,cloud) %{_sysconfdir}/%{name}/ui/config.json
%{_datadir}/%{name}-ui/*
%{_defaultdocdir}/%{name}-ui-%{version}/LICENSE
%{_defaultdocdir}/%{name}-ui-%{version}/NOTICE

%files usage
%attr(0644,root,root) %{_unitdir}/%{name}-usage.service
%config(noreplace) %{_sysconfdir}/default/%{name}-usage
%config(noreplace) %attr(0644,root,root) %{_sysconfdir}/logrotate.d/%{name}-usage
%attr(0644,root,root) %{_datadir}/%{name}-usage/*.jar
%attr(0644,root,root) %{_datadir}/%{name}-usage/lib/*.jar
%dir %attr(0770,root,cloud) %{_localstatedir}/log/%{name}/usage
%attr(0644,root,root) %{_sysconfdir}/%{name}/usage/db.properties
%attr(0644,root,root) %{_sysconfdir}/%{name}/usage/log4j-cloud.xml
%{_defaultdocdir}/%{name}-usage-%{version}/LICENSE
%{_defaultdocdir}/%{name}-usage-%{version}/NOTICE

%files marvin
%attr(0644,root,root) %{_datadir}/%{name}-marvin/Marvin*.tar.gz
%{_defaultdocdir}/%{name}-marvin-%{version}/LICENSE
%{_defaultdocdir}/%{name}-marvin-%{version}/NOTICE

%files integration-tests
%attr(0755,root,root) %{_datadir}/%{name}-integration-tests/*
%{_defaultdocdir}/%{name}-integration-tests-%{version}/LICENSE
%{_defaultdocdir}/%{name}-integration-tests-%{version}/NOTICE

%if "%{_ossnoss}" == "noredist"
%files mysql-ha
%defattr(0644,cloud,cloud,0755)
%attr(0644,root,root) %{_datadir}/%{name}-mysql-ha/lib/*
%endif

%files baremetal-agent
%attr(0755,root,root) %{_bindir}/cloudstack-setup-baremetal

%changelog
* Thu Dec 22 2022 Rohit Yadav <rohit@apache.org> 4.18.0
- Add support for EL9

* Fri Oct 14 2022 Daan Hoogland <daan.hoogland@gmail.com> 4.18.0
- initialising sanity check pointer file

* Tue Jun 29 2021 David Jumani <dj.davidjumani1994@gmail.com> 4.16.0
- Adding SUSE 15 support

* Thu Apr 30 2015 Rohit Yadav <bhaisaab@apache.org> 4.6.0
- Remove awsapi package

* Wed Nov 19 2014 Hugo Trippaers <hugo@apache.org> 4.6.0
- Create a specific spec for CentOS 7

* Fri Jul 4 2014 Hugo Trippaers <hugo@apache.org> 4.5.0
- Add a package for the mysql ha module

* Fri Oct 5 2012 Hugo Trippaers <hugo@apache.org> 4.1.0
- new style spec file
