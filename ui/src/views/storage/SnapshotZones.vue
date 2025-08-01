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
  <div>
    <a-button
      v-if="((deleteApi in $store.getters.apis) && this.selectedRowKeys.length > 0)"
      type="primary"
      danger
      style="width: 100%; margin-bottom: 15px"
      @click="bulkActionConfirmation()">
      <template #icon><delete-outlined /></template>
      {{ $t('label.action.bulk.delete.snapshots') }}
    </a-button>
    <a-table
      size="small"
      style="overflow-y: auto"
      :loading="loading || fetchLoading"
      :columns="columns"
      :dataSource="dataSource"
      :pagination="false"
      :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
      :rowKey="record => record.zoneid">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'zonename'">
          <span v-if="record.datastoreid">
            <router-link :to="{ path: (record.datastoretype === 'Primary' ? '/storagepool/' : '/imagestore/') + record.datastoreid }">
              <span v-if="fetchZoneIcon(record.zoneid)">
                <resource-icon :image="zoneIcon" size="1x" style="margin-right: 5px"/>
              </span>
              <global-outlined v-else style="margin-right: 5px" />
              <span> {{ record.zonename }} </span>
            </router-link>
          </span>
          <span v-else>
            <span v-if="fetchZoneIcon(record.zoneid)">
              <resource-icon :image="zoneIcon" size="1x" style="margin-right: 5px"/>
            </span>
            <global-outlined v-else style="margin-right: 5px" />
            <span> {{ record.zonename }} </span>
          </span>
        </template>
        <template v-if="column.key === 'isready'">
          <span v-if="record.datastorestate==='Ready'">{{ $t('label.yes') }}</span>
          <span v-else>{{ $t('label.no') }}</span>
        </template>
        <template v-if="column.key === 'actions'">
          <tooltip-button
            v-if="record.state==='BackedUp'"
            style="margin-right: 5px"
            :disabled="!(copyApi in $store.getters.apis)"
            :title="$t('label.action.copy.snapshot')"
            icon="copy-outlined"
            :loading="copyLoading"
            @onClick="showCopySnapshot(record)" />
          <tooltip-button
            v-if="record.state==='BackedUp'"
            style="margin-right: 5px"
            :disabled="!(deleteApi in $store.getters.apis)"
            :title="$t('label.action.delete.snapshot')"
            type="primary"
            :danger="true"
            icon="delete-outlined"
            @onClick="onShowDeleteModal(record)"/>
        </template>
      </template>
    </a-table>
    <a-pagination
      class="row-element"
      size="small"
      :current="page"
      :pageSize="pageSize"
      :total="itemCount"
      :showTotal="total => `${$t('label.total')} ${total} ${$t('label.items')}`"
      :pageSizeOptions="['10', '20', '40', '80', '100']"
      @change="handleChangePage"
      @showSizeChange="handleChangePageSize"
      showSizeChanger>
      <template #buildOptionText="props">
        <span>{{ props.value }} / {{ $t('label.page') }}</span>
      </template>
    </a-pagination>

    <a-modal
      v-if="copyApi in $store.getters.apis"
      style="top: 20px;"
      :title="$t('label.action.copy.snapshot')"
      :visible="showCopyActionForm"
      :closable="true"
      :maskClosable="false"
      :footer="null"
      :confirmLoading="copyLoading"
      @cancel="onCloseModal"
      centered>
      <a-spin :spinning="copyLoading" v-ctrl-enter="handleCopySnapshotSubmit">
        <a-form
          :ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          @finish="handleCopySnapshotSubmit">
          <a-form-item ref="zoneid" name="zoneid" :label="$t('label.zoneid')">
            <a-select
              id="zone-selection"
              mode="multiple"
              :placeholder="$t('label.select.zones')"
              v-model:value="form.zoneid"
              showSearch
              optionFilterProp="label"
              :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }"
              :loading="zoneLoading"
              v-focus="true">
              <a-select-option v-for="zone in zones" :key="zone.id" :label="zone.name">
                <div>
                  <span v-if="zone.icon && zone.icon.base64image">
                    <resource-icon :image="zone.icon.base64image" size="1x" style="margin-right: 5px"/>
                  </span>
                  <global-outlined v-else style="margin-right: 5px" />
                  {{ zone.name }}
                </div>
              </a-select-option>
            </a-select>
          </a-form-item>

          <div :span="24" class="action-button">
            <a-button @click="onCloseModal">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" ref="submit" @click="handleCopySnapshotSubmit">{{ $t('label.ok') }}</a-button>
          </div>
        </a-form>
      </a-spin>
    </a-modal>

    <a-modal
      :title="selectedItems.length > 0 && showTable ? $t(message.title) : $t('label.action.delete.snapshot')"
      :visible="showDeleteSnapshot"
      :closable="true"
      :maskClosable="false"
      :footer="null"
      :width="showTable ? modalWidth : '30vw'"
      @ok="selectedItems.length > 0 ? deleteSnapshots() : deleteSnapshot(currentRecord)"
      @cancel="onCloseModal"
      :ok-button-props="getOkProps()"
      :cancel-button-props="getCancelProps()"
      :confirmLoading="deleteLoading"
      centered>
      <div v-ctrl-enter="deleteSnapshot">
        <div v-if="selectedRowKeys.length > 0">
          <a-alert type="error">
            <template #message>
              <exclamation-circle-outlined style="color: red; fontSize: 30px; display: inline-flex" />
              <span style="padding-left: 5px" v-html="`<b>${selectedRowKeys.length} ` + $t('label.items.selected') + `. </b>`" />
              <span v-html="$t(message.confirmMessage)" />
            </template>
          </a-alert>
        </div>
        <a-alert v-else :message="$t('message.action.delete.snapshot')" type="warning" />
        <br />
        <a-table
          v-if="selectedRowKeys.length > 0 && showTable"
          size="middle"
          :columns="selectedColumns"
          :dataSource="selectedItems"
          :rowKey="record => record.zoneid || record.name"
          :pagination="true"
          style="overflow-y: auto">
        </a-table>
        <a-spin :spinning="deleteLoading">
          <div :span="24" class="action-button">
            <a-button @click="onCloseModal">{{ $t('label.cancel') }}</a-button>
            <a-button type="primary" ref="submit" @click="deleteSnapshot">{{ $t('label.ok') }}</a-button>
          </div>
        </a-spin>
      </div>
    </a-modal>
    <bulk-action-progress
      :showGroupActionModal="showGroupActionModal"
      :selectedItems="selectedItems"
      :selectedColumns="selectedColumns"
      :message="message"
      @handle-cancel="handleCancel" />
  </div>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import OsLogo from '@/components/widgets/OsLogo'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipButton from '@/components/widgets/TooltipButton'
import BulkActionProgress from '@/components/view/BulkActionProgress'
import Status from '@/components/widgets/Status'
import eventBus from '@/config/eventBus'

export default {
  name: 'SnapshotZones',
  components: {
    TooltipButton,
    OsLogo,
    ResourceIcon,
    BulkActionProgress,
    Status
  },
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
      columns: [],
      dataSource: [],
      page: 1,
      pageSize: 10,
      itemCount: 0,
      fetchLoading: false,
      showCopyActionForm: false,
      currentRecord: {},
      zones: [],
      zoneLoading: false,
      copyLoading: false,
      deleteLoading: false,
      showDeleteSnapshot: false,
      forcedDelete: false,
      selectedRowKeys: [],
      showGroupActionModal: false,
      selectedItems: [],
      selectedColumns: [],
      filterColumns: ['Status', 'Ready'],
      showConfirmationAction: false,
      message: {
        title: this.$t('label.action.bulk.delete.snapshots'),
        confirmMessage: this.$t('label.confirm.delete.snapshot.zones')
      },
      modalWidth: '30vw',
      showTable: false
    }
  },
  beforeCreate () {
    this.deleteApi = 'deleteSnapshot'
    this.copyApi = 'copySnapshot'
    this.apiParams = this.$getApiParams(this.copyApi)
  },
  created () {
    this.columns = [
      {
        key: 'zonename',
        title: this.$t('label.zonename'),
        dataIndex: 'zonename'
      },
      {
        title: this.$t('label.status'),
        dataIndex: 'status'
      },
      {
        key: 'isready',
        title: this.$t('label.isready'),
        dataIndex: 'isready'
      }
    ]
    if (this.isActionPermitted()) {
      this.columns.push({
        key: 'actions',
        title: '',
        dataIndex: 'actions',
        width: 100
      })
    }

    this.initForm()
    this.fetchData()
  },
  watch: {
    loading (newData, oldData) {
      if (!newData && !this.showGroupActionModal) {
        this.fetchData()
      }
    }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        zoneid: [{ type: 'array', required: true, message: this.$t('message.error.select') }]
      })
    },
    fetchData () {
      const params = {}
      params.id = this.resource.id
      params.showunique = false
      params.locationtype = 'Secondary'
      params.listall = true
      params.page = this.page
      params.pagesize = this.pageSize

      this.dataSource = []
      this.itemCount = 0
      this.fetchLoading = true
      getAPI('listSnapshots', params).then(json => {
        this.dataSource = json.listsnapshotsresponse.snapshot || []
        this.itemCount = json.listsnapshotsresponse.count || 0
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.fetchLoading = false
      })
      this.fetchZoneData()
    },
    fetchZoneIcon (zoneid) {
      const zoneItem = this.zones.filter(zone => zone.id === zoneid)
      if (zoneItem?.[0]?.icon?.base64image) {
        this.zoneIcon = zoneItem[0].icon.base64image
        return true
      }
      return false
    },
    handleChangePage (page, pageSize) {
      this.page = page
      this.pageSize = pageSize
      this.fetchData()
    },
    handleChangePageSize (currentPage, pageSize) {
      this.page = currentPage
      this.pageSize = pageSize
      this.fetchData()
    },
    isActionPermitted () {
      return this.resource.state && this.resource.state === 'BackedUp'
    },
    setSelection (selection) {
      this.selectedRowKeys = selection
      if (selection?.length > 0) {
        this.modalWidth = '50vw'
        this.$emit('selection-change', this.selectedRowKeys)
        this.selectedItems = (this.dataSource.filter(function (item) {
          return selection.indexOf(item.zoneid) !== -1
        }))
      } else {
        this.modalWidth = '30vw'
        this.selectedItems = []
      }
    },
    resetSelection () {
      this.setSelection([])
    },
    onSelectChange (selectedRowKeys, selectedRows) {
      this.setSelection(selectedRowKeys)
    },
    bulkActionConfirmation () {
      this.showConfirmationAction = true
      this.selectedColumns = this.columns.filter(column => {
        return !this.filterColumns.includes(column.title)
      })
      this.selectedItems = this.selectedItems.map(v => ({ ...v, status: 'InProgress' }))
      this.onShowDeleteModal(this.selectedItems[0])
    },
    handleCancel () {
      eventBus.emit('update-bulk-job-status', { items: this.selectedItems, action: false })
      this.showGroupActionModal = false
      this.selectedItems = []
      this.selectedColumns = []
      this.selectedRowKeys = []
      this.showTable = false
      this.fetchData()
      if (this.dataSource.length === 0) {
        this.$router.go(-1)
      }
    },
    getOkProps () {
      if (this.selectedRowKeys.length > 0) {
        return { props: { type: 'default' } }
      } else {
        return { props: { type: 'primary' } }
      }
    },
    getCancelProps () {
      if (this.selectedRowKeys.length > 0) {
        return { props: { type: 'primary' } }
      } else {
        return { props: { type: 'default' } }
      }
    },
    deleteSnapshots (e) {
      this.showConfirmationAction = false
      this.selectedColumns.splice(0, 0, {
        key: 'status',
        dataIndex: 'status',
        title: this.$t('label.operation.status'),
        filters: [
          { text: 'In Progress', value: 'InProgress' },
          { text: 'Success', value: 'success' },
          { text: 'Failed', value: 'failed' }
        ]
      })
      if (this.selectedRowKeys.length > 0 && this.showTable) {
        this.showGroupActionModal = true
      }
      for (const snapshot of this.selectedItems) {
        this.deleteSnapshot(snapshot)
      }
    },
    deleteSnapshot (snapshot) {
      if (!snapshot.id) {
        snapshot = this.currentRecord
      }
      const params = {
        id: snapshot.id,
        zoneid: snapshot.zoneid
      }
      this.deleteLoading = true
      postAPI(this.deleteApi, params).then(json => {
        const jobId = json.deletesnapshotresponse.jobid
        eventBus.emit('update-job-details', { jobId, resourceId: null })
        const singleZone = (this.dataSource.length === 1)
        this.$pollJob({
          jobId,
          title: this.$t('label.action.delete.snapshot'),
          description: this.resource.name,
          successMethod: result => {
            if (singleZone) {
              const isResourcePage = (this.$route.params && this.$route.params.id)
              const isSameResource = isResourcePage && this.$route.params.id === result.jobinstanceid
              if (isResourcePage && isSameResource && this.selectedItems.length === 0 && !this.showGroupActionModal) {
                this.$router.push({ path: '/snapshot' })
              }
            } else {
              if (this.selectedItems.length === 0) {
                this.fetchData()
              }
            }
            if (this.selectedItems.length > 0) {
              eventBus.emit('update-resource-state', { selectedItems: this.selectedItems, resource: snapshot.zoneid, state: 'success' })
            }
          },
          errorMethod: () => {
            if (this.selectedItems.length === 0) {
              this.fetchData()
            }
            if (this.selectedItems.length > 0) {
              eventBus.emit('update-resource-state', { selectedItems: this.selectedItems, resource: snapshot.zoneid, state: 'failed' })
            }
          },
          showLoading: !(this.selectedItems.length > 0 && this.showGroupActionModal),
          loadingMessage: `${this.$t('label.deleting.snapshot')} ${this.resource.name} ${this.$t('label.in.progress')}`,
          catchMessage: this.$t('error.fetching.async.job.result'),
          bulkAction: this.selectedItems.length > 0 && this.showGroupActionModal
        })
        this.onCloseModal()
        if (this.selectedItems.length === 0) {
          this.fetchData()
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.deleteLoading = false
      })
    },
    fetchZoneData () {
      this.zones = []
      this.zoneLoading = true
      getAPI('listZones', { showicon: true }).then(json => {
        const zones = json.listzonesresponse.zone || []
        this.zones = [...zones.filter((zone) => this.currentRecord.zoneid !== zone.id)]
      }).finally(() => {
        this.zoneLoading = false
      })
    },
    showCopySnapshot (record) {
      this.currentRecord = record
      this.form.zoneid = []
      this.fetchZoneData()
      this.showCopyActionForm = true
    },
    onShowDeleteModal (record) {
      this.forcedDelete = false
      this.currentRecord = record
      this.showDeleteSnapshot = true
      if (this.showConfirmationAction) {
        this.showTable = true
      } else {
        this.selectedItems = []
      }
    },
    onCloseModal () {
      this.currentRecord = {}
      this.showCopyActionForm = false
      this.showDeleteSnapshot = false
      this.showConfirmationAction = false
      this.showTable = false
      this.selectedRowKeys = []
    },
    handleCopySnapshotSubmit (e) {
      e.preventDefault()
      if (this.copyLoading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        const params = {
          id: this.currentRecord.id,
          sourcezoneid: this.currentRecord.zoneid,
          destzoneids: values.zoneid.join()
        }
        this.copyLoading = true
        postAPI(this.copyApi, params).then(json => {
          const jobId = json.copysnapshotresponse.jobid
          eventBus.emit('update-job-details', { jobId, resourceId: null })
          this.$pollJob({
            jobId,
            title: this.$t('label.action.copy.snapshot'),
            description: this.resource.name,
            successMethod: result => {
              this.fetchData()
            },
            errorMethod: () => this.fetchData(),
            loadingMessage: `${this.$t('label.action.copy.snapshot')} ${this.resource.name} ${this.$t('label.in.progress')}`,
            catchMessage: this.$t('error.fetching.async.job.result')
          })
        }).catch(error => {
          this.$notification.error({
            message: this.$t('message.request.failed'),
            description: (error.response && error.response.headers && error.response.headers['x-description']) || error.message
          })
        }).finally(() => {
          this.copyLoading = false
          this.$emit('refresh-data')
          this.onCloseModal()
          this.fetchData()
        })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    }
  }
}
</script>

<style lang="less" scoped>
.row-element {
  margin-top: 15px;
  margin-bottom: 15px;
}
</style>
