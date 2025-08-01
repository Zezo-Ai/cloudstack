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

<template>
  <a-spin :spinning="fetchLoading">
    <a-list size="small">
      <a-list-item v-if="host.hypervisorversion || (host.details && host.details['Host.OS'])">
        <div>
          <strong>{{ $t('label.hypervisorversion') }}</strong>
          <div>
            {{ host.hypervisor }}
            <span v-if="host.hypervisorversion">
              {{ host.hypervisorversion }}
            </span>
            <span v-else-if="host.details && host.details['Host.OS']">
              {{ host.details['Host.OS'] + ' ' + host.details['Host.OS.Version'] }}
            </span>
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.details && host.details.secured">
        <div>
          <strong>{{ $t('label.secured') }}</strong>
          <div>
            {{ host.details.secured }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.encryptionsupported">
        <div>
          <strong>{{ $t('label.volume.encryption.support') }}</strong>
          <div>
            {{ host.encryptionsupported }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.instanceconversionsupported">
        <div>
          <strong>{{ $t('label.instance.conversion.support') }}</strong>
          <div>
            {{ host.instanceconversionsupported }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.details && host.details['host.virtv2v.version']">
        <div>
          <strong>{{ $t('label.host.virtv2v.version') }}</strong>
          <div>
            {{ host.details['host.virtv2v.version'] }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.details && host.details['host.ovftool.version']">
        <div>
          <strong>{{ $t('label.host.ovftool.version') }}</strong>
          <div>
            {{ host.details['host.ovftool.version'] }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.hosttags">
        <div>
          <strong>{{ $t('label.hosttags') }}</strong>
          <div v-for="hosttag in host.allhosttags" :key="hosttag.tag">
            {{ hosttag.tag }}
            <span v-if="hosttag.isexplicit">
              <a-tag color="blue">{{ $t('label.hosttags.explicit.abbr') }}</a-tag>
            </span>
            <span v-if="hosttag.isimplicit">
              <a-tag color="orange">{{ $t('label.hosttags.implicit.abbr') }}</a-tag>
            </span>
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.storageaccessgroups">
        <div>
          <strong>{{ $t('label.storageaccessgroups') }}</strong>
          <div>
            {{ host.storageaccessgroups }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.oscategoryid">
        <div>
          <strong>{{ $t('label.oscategoryid') }}</strong>
          <div>
            {{ host.oscategoryname }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.outofbandmanagement">
        <div>
          <strong>{{ $t('label.outofbandmanagement') }}</strong>
          <div>
            {{ host.outofbandmanagement.enabled }}
          </div>
        </div>
      </a-list-item>
      <span v-if="host?.outofbandmanagement?.enabled">
        <a-list-item>
          <div>
            <strong>{{ $t('label.oobm.username') }}</strong>
            <div>
              {{ host.outofbandmanagement.username }}
            </div>
          </div>
        </a-list-item>
        <a-list-item>
          <div>
            <strong>{{ $t('label.oobm.powerstate') }}</strong>
            <div>
              {{ host.outofbandmanagement.powerstate }}
            </div>
          </div>
        </a-list-item>
        <a-list-item>
          <div>
            <strong>{{ $t('label.oobm.driver') }}</strong>
            <div>
              {{ host.outofbandmanagement.driver }}
            </div>
          </div>
        </a-list-item>
        <a-list-item>
          <div>
            <strong>{{ $t('label.oobm.address') }}</strong>
            <div>
              {{ host.outofbandmanagement.address }}
            </div>
          </div>
        </a-list-item>
        <a-list-item>
          <div>
            <strong>{{ $t('label.oobm.port') }}</strong>
            <div>
              {{ host.outofbandmanagement.port }}
            </div>
          </div>
        </a-list-item>
      </span>
      <a-list-item v-if="host.hostha">
        <div>
          <strong>{{ $t('label.haenable') }}</strong>
          <div>
            {{ host.hostha.haenable }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.hostha && host.hostha.haenable">
        <div>
          <strong>{{ $t('label.hastate') }}</strong>
          <div>
            {{ host.hostha.hastate }}
          </div>
        </div>
      </a-list-item>
      <a-list-item v-if="host.hostha && host.hostha.haenable">
        <div>
          <strong>{{ $t('label.haprovider') }}</strong>
          <div>
            {{ host.hostha.haprovider }}
          </div>
        </div>
      </a-list-item>
      <a-list-item>
        <div>
          <strong>{{ $t('label.uefi.supported') }}</strong>
          <div>
            {{ host.ueficapability }}
          </div>
        </div>
      </a-list-item>
    </a-list>
  </a-spin>
</template>

<script>
import { getAPI } from '@/api'

export default {
  name: 'HostInfo',
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      host: {},
      fetchLoading: false
    }
  },
  created () {
    this.fetchData()
  },
  watch: {
    resource: {
      deep: true,
      handler (newItem, oldItem) {
        if (this.resource) {
          this.host = this.resource
          if (this.resource.id && newItem && newItem.id !== oldItem.id) {
            this.fetchData()
          }
        }
      }
    }
  },
  methods: {
    fetchData () {
      this.fetchLoading = true
      getAPI('listHosts', { id: this.resource.id }).then(json => {
        this.host = json.listhostsresponse.host[0]
        const hosttags = this.host.hosttags?.split(',') || []
        const explicithosttags = this.host.explicithosttags?.split(',') || []
        const implicithosttags = this.host.implicithosttags?.split(',') || []
        const allHostTags = []
        for (const hosttag of hosttags) {
          var isexplicit = false
          var isimplicit = false
          if (explicithosttags.includes(hosttag)) {
            isexplicit = true
          }
          if (implicithosttags.includes(hosttag)) {
            isimplicit = true
          }
          allHostTags.push({ tag: hosttag, isexplicit: isexplicit, isimplicit: isimplicit })
        }
        this.host.allhosttags = allHostTags
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.fetchLoading = false
      })
    }
  }
}
</script>

<style lang="less" scoped>

</style>
