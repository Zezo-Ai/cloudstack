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
  <div class="form-layout">
    <a-form
      ref="formRef"
      :model="form"
      :rules="rules"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit"
      class="form"
      layout="vertical"
    >
      <a-alert type="warning">
        <template #message>
          <span v-html="resourcestate === 'Disabled' ? $t('message.confirm.enable.host') : $t('message.confirm.disable.host') " />
        </template>
      </a-alert>
      <div v-show="kvmAutoEnableDisableSetting" class="reason">
        <a-form-item
          class="form__item"
          name="reason"
          ref="reason"
          :label="'The Auto Enable/Disable KVM Hosts functionality is enabled, ' +
            ' can specify a reason for ' + (resourcestate === 'Enabled' ? 'disabling' : 'enabling') + ' this host'">
          <a-textarea
            v-model:value="form.reason"
            :placeholder="'(Optional) Reason to ' + (resourcestate === 'Enabled' ? 'disable' : 'enable') + ' this host'"
            rows="3"
          />
        </a-form-item>
      </div>
      <div :span="24" class="action-button">
        <a-button @click="$emit('close-action')">{{ $t('label.cancel') }}</a-button>
        <a-button type="primary" @click="handleSubmit" ref="submit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-form>
  </div>
</template>

<script>
import { reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'

export default {
  name: 'HostEnableDisable',
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      resourcestate: '',
      allocationstate: '',
      kvmAutoEnableDisableSetting: false
    }
  },
  created () {
    this.initForm()
    this.fetchAutoEnableDisableKVMSetting()
    this.resourcestate = this.resource.resourcestate
    this.allocationstate = this.resourcestate === 'Enabled' ? 'Disable' : 'Enable'
  },
  methods: {
    initForm () {
      this.form = reactive({})
      this.rules = reactive({})
    },
    fetchAutoEnableDisableKVMSetting () {
      if (this.resource.hypervisor !== 'KVM') {
        return
      }
      getAPI('listConfigurations', { name: 'enable.kvm.host.auto.enable.disable', clusterid: this.resource.clusterid }).then(json => {
        if (json.listconfigurationsresponse.configuration?.[0]) {
          this.kvmAutoEnableDisableSetting = json?.listconfigurationsresponse?.configuration?.[0]?.value || false
        }
      })
    },
    handleSubmit (e) {
      this.$refs.formRef.validate().then(() => {
        const values = toRaw(this.form)
        const data = {
          allocationstate: this.allocationstate,
          id: this.resource.id
        }
        if (values.reason) {
          data.annotation = values.reason
        }
        postAPI('updateHost', data).then(_ => {
          this.$emit('close-action')
          this.$emit('refresh-data')
        }).catch(err => {
          this.$message.error(err.message || 'Failed to update host status')
        })
      }).catch(() => {
        this.$message.error('Validation failed. Please check the inputs.')
      })
    }
  }
}
</script>

<style scoped>
.reason {
  padding-top: 20px;
}

.form-layout {
  width: 30vw;
  @media (min-width: 500px) {
    width: 450px;
  }
}
</style>
