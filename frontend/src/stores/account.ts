import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { GameAccount, GameServer, FarmProfile } from '@/types/account'
import { createDefaultFarm, createId } from '@/types/account'

const STORAGE_KEY = 'farm-helper-accounts'

function loadAccounts(): GameAccount[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as GameAccount[]) : []
  } catch {
    return []
  }
}

export const useAccountStore = defineStore('account', () => {
  const accounts = ref<GameAccount[]>(loadAccounts())

  watch(
    accounts,
    (val) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
    },
    { deep: true },
  )

  function addAccount(displayName: string) {
    accounts.value.push({
      id: createId(),
      displayName,
      platformType: '',
      regionCode: '',
      servers: [],
    })
  }

  function removeAccount(id: string) {
    accounts.value = accounts.value.filter((a) => a.id !== id)
  }

  function updateAccount(id: string, patch: Partial<GameAccount>) {
    const account = accounts.value.find((a) => a.id === id)
    if (account) Object.assign(account, patch)
  }

  function addServer(accountId: string, serverName: string) {
    const account = accounts.value.find((a) => a.id === accountId)
    if (!account) return
    const server: GameServer = {
      id: createId(),
      serverName,
      farm: createDefaultFarm(),
    }
    account.servers.push(server)
  }

  function removeServer(accountId: string, serverId: string) {
    const account = accounts.value.find((a) => a.id === accountId)
    if (!account) return
    account.servers = account.servers.filter((s) => s.id !== serverId)
  }

  function updateFarm(accountId: string, serverId: string, farm: FarmProfile) {
    const account = accounts.value.find((a) => a.id === accountId)
    const server = account?.servers.find((s) => s.id === serverId)
    if (server) server.farm = { ...farm }
  }

  return {
    accounts,
    addAccount,
    removeAccount,
    updateAccount,
    addServer,
    removeServer,
    updateFarm,
  }
})
