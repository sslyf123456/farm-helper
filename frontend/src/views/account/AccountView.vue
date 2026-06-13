<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAccountStore } from '@/stores/account'
import {
  PLATFORM_LABELS,
  type PlatformType,
  type FarmProfile,
} from '@/types/account'

const store = useAccountStore()

const newAccountName = ref('')
const newServerName = ref('')
const activeAccountId = ref<string | null>(null)
const activeServerId = ref<string | null>(null)

const platformOptions = Object.entries(PLATFORM_LABELS).map(([value, label]) => ({
  value: value as PlatformType,
  label,
}))

function addAccount() {
  const name = newAccountName.value.trim()
  if (!name) {
    ElMessage.warning('请输入王者账号名称')
    return
  }
  store.addAccount(name)
  newAccountName.value = ''
  ElMessage.success('已添加王者账号')
}

async function removeAccount(id: string, name: string) {
  await ElMessageBox.confirm(`确定删除王者账号「${name}」及其所有区服？`, '确认删除', {
    type: 'warning',
  })
  store.removeAccount(id)
  if (activeAccountId.value === id) {
    activeAccountId.value = null
    activeServerId.value = null
  }
}

function addServer(accountId: string) {
  const name = newServerName.value.trim()
  if (!name) {
    ElMessage.warning('请输入区服名称')
    return
  }
  store.addServer(accountId, name)
  newServerName.value = ''
  ElMessage.success('已添加区服')
}

async function removeServer(accountId: string, serverId: string, name: string) {
  await ElMessageBox.confirm(`确定删除区服「${name}」？`, '确认删除', { type: 'warning' })
  store.removeServer(accountId, serverId)
  if (activeServerId.value === serverId) activeServerId.value = null
}

function selectServer(accountId: string, serverId: string) {
  activeAccountId.value = accountId
  activeServerId.value = serverId
}

function saveFarm(farm: FarmProfile) {
  if (!activeAccountId.value || !activeServerId.value) return
  store.updateFarm(activeAccountId.value, activeServerId.value, farm)
  ElMessage.success('农场档案已保存')
}

const activeFarm = () => {
  if (!activeAccountId.value || !activeServerId.value) return null
  const account = store.accounts.find((a) => a.id === activeAccountId.value)
  const server = account?.servers.find((s) => s.id === activeServerId.value)
  return server?.farm ?? null
}

const editingFarm = ref<FarmProfile | null>(null)

function startEdit() {
  const farm = activeFarm()
  if (farm) editingFarm.value = { ...farm }
}

function onSelectServer(accountId: string, serverId: string) {
  selectServer(accountId, serverId)
  startEdit()
}
</script>

<template>
  <div>
    <el-alert
      title="一个网站账号可管理多个王者荣耀账号；每个王者账号下可有多个区服，每个区服对应一个农场。"
      type="info"
      show-icon
      :closable="false"
      class="mb"
    />

    <div class="page-card header-card">
      <h1 class="page-title">我的账号</h1>
      <p class="page-desc">
        网站账号与王者账号分开管理。当前数据暂存于浏览器本地，登录功能将在后续版本提供。
      </p>
    </div>

    <div class="account-layout">
      <div class="page-card account-list">
        <h2>王者荣耀账号</h2>
        <div class="add-row">
          <el-input v-model="newAccountName" placeholder="账号备注名" />
          <el-button type="primary" @click="addAccount">添加</el-button>
        </div>

        <el-collapse v-if="store.accounts.length">
          <el-collapse-item
            v-for="acc in store.accounts"
            :key="acc.id"
            :title="acc.displayName"
            :name="acc.id"
          >
            <div class="account-meta">
              <el-select
                v-model="acc.platformType"
                placeholder="平台（可选）"
                clearable
                style="width: 100%; margin-bottom: 8px"
                @change="store.updateAccount(acc.id, { platformType: acc.platformType })"
              >
                <el-option
                  v-for="opt in platformOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <el-input
                v-model="acc.regionCode"
                placeholder="区号信息（可选）"
                @change="store.updateAccount(acc.id, { regionCode: acc.regionCode })"
              />
              <el-button
                type="danger"
                link
                style="margin-top: 8px"
                @click="removeAccount(acc.id, acc.displayName)"
              >
                删除账号
              </el-button>
            </div>

            <div class="server-section">
              <h4>区服列表</h4>
              <div class="add-row">
                <el-input v-model="newServerName" placeholder="区服名称" size="small" />
                <el-button size="small" @click="addServer(acc.id)">添加区服</el-button>
              </div>
              <div
                v-for="srv in acc.servers"
                :key="srv.id"
                class="server-item"
                :class="{ active: activeServerId === srv.id }"
                @click="onSelectServer(acc.id, srv.id)"
              >
                <span>{{ srv.serverName }}</span>
                <span class="farm-lv">Lv.{{ srv.farm.farmLevel }}</span>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click.stop="removeServer(acc.id, srv.id, srv.serverName)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-empty v-else description="暂无王者账号，请先添加" />
      </div>

      <div class="page-card farm-panel">
        <h2>农场档案</h2>
        <template v-if="editingFarm">
          <el-form label-width="110px">
            <el-form-item label="农场等级">
              <el-input-number v-model="editingFarm.farmLevel" :min="1" :max="999" />
            </el-form-item>
            <el-form-item label="当前经验">
              <el-input-number v-model="editingFarm.currentExp" :min="0" />
            </el-form-item>
            <el-form-item label="农场币">
              <el-input-number v-model="editingFarm.farmCoins" :min="0" />
            </el-form-item>
            <el-form-item label="小摊等级">
              <el-input-number v-model="editingFarm.stallLevel" :min="0" :max="99" />
            </el-form-item>
            <el-form-item label="已开垦农田">
              <el-input-number v-model="editingFarm.farmlandCount" :min="1" :max="24" />
            </el-form-item>
            <el-form-item label="二级农田数">
              <el-input-number v-model="editingFarm.upgradedFarmlandCount" :min="0" :max="16" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="editingFarm.note" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveFarm(editingFarm)">保存档案</el-button>
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else description="请从左侧选择一个区服" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.mb {
  margin-bottom: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.account-list,
.farm-panel {
  background: #ffffff;
}

.account-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;
}

.account-list h2,
.farm-panel h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.server-section {
  margin-top: 12px;
}

.server-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.server-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 6px;
  background: #f8faf9;
}

.server-item.active {
  background: #d8f3dc;
  font-weight: 600;
}

.farm-lv {
  margin-left: auto;
  font-size: 12px;
  color: var(--fh-muted);
}

@media (max-width: 900px) {
  .account-layout {
    grid-template-columns: 1fr;
  }
}
</style>
