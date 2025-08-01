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
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        @finish="handleSubmit"
        layout="vertical"
       >
        <a-form-item name="name" ref="name">
          <template #label>
            <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
          </template>
          <a-input
            v-model:value="form.name"
            :placeholder="apiParams.name.description"
            v-focus="true" />
        </a-form-item>
        <a-form-item name="tags" ref="tags">
          <template #label>
            <tooltip-label :title="$t('label.tags')" :tooltip="apiParams.tags.description"/>
          </template>
          <a-input
            v-model:value="form.tags"
            :placeholder="apiParams.tags.description"
            v-focus="true" />
        </a-form-item>
        <a-form-item name="isTagARule" ref="isTagARule">
          <template #label>
            <tooltip-label :title="$t('label.istagarule')" :tooltip="apiParams.istagarule.description"/>
          </template>
          <a-switch v-model:checked="form.isTagARule" />
        </a-form-item>
        <a-form-item name="capacityBytes" ref="capacityBytes">
          <template #label>
            <tooltip-label :title="$t('label.capacitybytes')" :tooltip="apiParams.capacitybytes.description"/>
          </template>
          <a-input
            v-model:value="form.capacityBytes"
            :placeholder="apiParams.capacitybytes.description"
            v-focus="true" />
        </a-form-item>
        <a-form-item name="capacityIOPS" ref="capacityIOPS">
          <template #label>
            <tooltip-label :title="$t('label.capacityiops')" :tooltip="apiParams.capacityiops.description"/>
          </template>
          <a-input
            v-model:value="form.capacityIOPS"
            :placeholder="apiParams.capacityiops.description"
            v-focus="true" />
        </a-form-item>
        <br>
        <a-form-item name="nfsMountOpts" ref="nfsMountOpts" v-if="canUpdateNFSMountOpts">
          <template #label>
            <tooltip-label :title="$t('label.nfsmountopts')" :tooltip="$t('message.nfs.mount.options.description')"/>
          </template>
          <a-alert type="warning">
            <template #message>
              <span v-html="$t('message.action.edit.nfs.mount.options')"></span>
            </template>
          </a-alert>
          <br>
          <a-input
            v-model:value="form.nfsMountOpts"
            :placeholder="$t('message.nfs.mount.options.description')"
            v-focus="true" />
        </a-form-item>

        <a-form-item name="storageaccessgroups" ref="storageaccessgroups" v-if="resource.scope !== 'HOST'">
          <template #label>
            <tooltip-label :title="$t('label.storageaccessgroups')" :tooltip="apiParamsConfigureStorageAccess.storageaccessgroups.description"/>
          </template>
          <a-select
            mode="tags"
            v-model:value="form.storageaccessgroups"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.children?.[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }"
            :loading="storageAccessGroupsLoading"
            :placeholder="apiParamsConfigureStorageAccess.storageaccessgroups.description">
            <a-select-option v-for="(opt) in storageAccessGroups" :key="opt">
              {{ opt }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button :loading="loading" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { isAdmin } from '@/role'
import { mixinForm } from '@/utils/mixin'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'UpdateStoragePool',
  mixins: [mixinForm],
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      storageAccessGroups: [],
      storageAccessGroupsLoading: false
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('updateStoragePool')
    this.apiParamsConfigureStorageAccess = this.$getApiParams('configureStorageAccess')
  },
  created () {
    this.initForm()
    this.fetchStorageAccessGroupsData()
  },
  computed: {
    canUpdateNFSMountOpts () {
      if (isAdmin() === false) return false
      if (this.resource.type === 'NetworkFilesystem' && this.resource.state === 'Maintenance' &&
          (this.resource.hypervisor === 'KVM' || this.resource.hypervisor === 'Simulator')) {
        return true
      }
      return false
    }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        name: this.resource.name,
        tags: this.resource.tags,
        isTagARule: this.resource.istagarule,
        capacityBytes: this.resource.disksizetotal,
        capacityIOPS: this.resource.capacityiops,
        nfsMountOpts: this.resource.nfsmountopts,
        storageaccessgroups: this.resource.storageaccessgroups
          ? this.resource.storageaccessgroups.split(',')
          : []
      })
      this.rules = reactive({ })
    },
    isAdmin () {
      return isAdmin()
    },
    fetchStorageAccessGroupsData () {
      const params = {}
      this.storageAccessGroupsLoading = true
      getAPI('listStorageAccessGroups', params).then(json => {
        const sags = json.liststorageaccessgroupsresponse.storageaccessgroup || []
        for (const sag of sags) {
          if (!this.storageAccessGroups.includes(sag.name)) {
            this.storageAccessGroups.push(sag.name)
          }
        }
      }).finally(() => {
        this.storageAccessGroupsLoading = false
      })
      this.rules = reactive({})
    },
    handleSubmit (e) {
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const formRaw = toRaw(this.form)
        const values = this.handleRemoveFields(formRaw)

        var params = {
          id: this.resource.id,
          name: values.name,
          tags: values.tags,
          istagarule: values.isTagARule,
          capacitybytes: values.capacityBytes,
          capacityiops: values.capacityIOPS
        }
        if (values.nfsMountOpts) {
          params['details[0].nfsmountopts'] = values.nfsMountOpts
        }

        this.updateStoragePool(params, values)
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    closeAction () {
      this.$emit('close-action')
    },
    updateStoragePool (args, values) {
      postAPI('updateStoragePool', args).then(json => {
        this.$message.success(`${this.$t('message.success.edit.primary.storage')}: ${this.resource.name}`)

        if (values.storageaccessgroups != null && values.storageaccessgroups.length > 0) {
          args.storageaccessgroups = values.storageaccessgroups.join(',')
        } else {
          args.storageaccessgroups = ''
        }

        if (args.storageaccessgroups !== undefined && (this.resource.storageaccessgroups ? this.resource.storageaccessgroups.split(',').join(',') : '') !== args.storageaccessgroups) {
          postAPI('configureStorageAccess', {
            storageid: args.id,
            storageaccessgroups: args.storageaccessgroups
          }).then(response => {
            this.$pollJob({
              jobId: response.configurestorageaccessresponse.jobid,
              successMethod: () => {
                this.$message.success({
                  content: this.$t('label.action.configure.storage.access.group'),
                  duration: 2
                })
              },
              errorMessage: this.$t('message.configuring.storage.access.failed')
            })
          })
        }

        this.$emit('refresh-data')
        this.closeAction()
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.loading = false
      })
    }
  }
}
</script>

<style scoped lang="less">
  .form-layout {
    width: 60vw;

    @media (min-width: 500px) {
      width: 450px;
    }
  }

</style>
