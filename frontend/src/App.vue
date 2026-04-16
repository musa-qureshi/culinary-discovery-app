<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

type UserRole = 'HOME_COOK' | 'VERIFIED_CHEF' | 'SUPPLIER' | 'ADMIN'
type RegisterRole = Exclude<UserRole, 'ADMIN'>
type ViewMode = 'login' | 'register' | 'dashboard'
type AdminTab = 'overview' | 'users' | 'pending'

type AuthUser = {
  userId: number
  fullName: string
  email: string
  role: UserRole
  accountStatus: string
  message: string
}

type AdminDashboardStats = {
  totalUsers: number
  homeCookCount: number
  supplierCount: number
  verifiedChefCount: number
  adminCount: number
  pendingChefVerifications: number
  activeUsers: number
  blockedUsers: number
}

type AdminSystemUser = {
  userId: number
  fullName: string
  email: string
  role: UserRole
  accountStatus: string
  createdAt: string
}

type PendingChef = {
  userId: number
  fullName: string
  email: string
  bio: string | null
  requestedAt: string
}

type ApprovedChef = {
  userId: number
  fullName: string
  email: string
  bio: string | null
  requestedAt: string
  reviewedAt: string | null
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const loginDefaults = {
  email: '',
  password: ''
}

const registerDefaults = {
  fullName: '',
  email: '',
  password: '',
  role: 'HOME_COOK' as RegisterRole,
  chefBio: ''
}

const activeMode = ref<ViewMode>('login')
const loginForm = reactive({ ...loginDefaults })
const registerForm = reactive({ ...registerDefaults })
const message = ref('')
const busy = ref(false)
const currentUser = ref<AuthUser | null>(null)

const adminTab = ref<AdminTab>('overview')
const adminNotice = ref('')

const adminStats = ref<AdminDashboardStats | null>(null)
const adminUsers = ref<AdminSystemUser[]>([])
const pendingChefs = ref<PendingChef[]>([])
const approvedChefs = ref<ApprovedChef[]>([])

const statsLoading = ref(false)
const usersLoading = ref(false)
const pendingLoading = ref(false)
const approvedLoading = ref(false)
const adminActionUserId = ref<number | null>(null)

const userRoleFilter = ref<'ALL' | UserRole>('ALL')
const userSearch = ref('')

const roleLabels: Record<RegisterRole, string> = {
  HOME_COOK: 'Home Cook',
  VERIFIED_CHEF: 'Verified Chef',
  SUPPLIER: 'Supplier'
}

const dashboardCopy: Record<UserRole, { title: string; description: string; accent: string }> = {
  HOME_COOK: {
    title: 'Home Cook Dashboard',
    description: 'Plan meals, save recipes, and build your weekly kitchen routine.',
    accent: 'Home Cook workspace'
  },
  VERIFIED_CHEF: {
    title: 'Verified Chef Dashboard',
    description: 'Manage recipes, update your profile, and track your approved status.',
    accent: 'Chef workspace'
  },
  SUPPLIER: {
    title: 'Supplier Dashboard',
    description: 'Manage ingredients, product availability, and buyer connections.',
    accent: 'Supplier workspace'
  },
  ADMIN: {
    title: 'Admin Control Center',
    description: 'Monitor key system metrics, manage users, and approve verified chefs.',
    accent: 'Admin workspace'
  }
}

const userRoleOptions: Array<'ALL' | UserRole> = ['ALL', 'HOME_COOK', 'SUPPLIER', 'VERIFIED_CHEF', 'ADMIN']

const requiresChefReview = computed(() => registerForm.role === 'VERIFIED_CHEF')
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

function resetLoginForm() {
  Object.assign(loginForm, loginDefaults)
}

function resetRegisterForm() {
  Object.assign(registerForm, registerDefaults)
}

function clearAdminState() {
  adminTab.value = 'overview'
  adminNotice.value = ''
  adminStats.value = null
  adminUsers.value = []
  pendingChefs.value = []
  approvedChefs.value = []
  userRoleFilter.value = 'ALL'
  userSearch.value = ''
  statsLoading.value = false
  usersLoading.value = false
  pendingLoading.value = false
  approvedLoading.value = false
  adminActionUserId.value = null
}

function switchMode(nextMode: Exclude<ViewMode, 'dashboard'>) {
  activeMode.value = nextMode
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
  clearAdminState()
}

function logout() {
  currentUser.value = null
  activeMode.value = 'login'
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
  clearAdminState()
}

watch(activeMode, (nextMode, previousMode) => {
  if (nextMode !== previousMode) {
    message.value = ''
    resetLoginForm()
    resetRegisterForm()
  }
})

function requireAdminUserId(): number {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') {
    throw new Error('Admin session required.')
  }
  return currentUser.value.userId
}

function parseApiError(payload: unknown, fallback: string): string {
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const candidate = (payload as { message?: unknown }).message
    if (typeof candidate === 'string' && candidate.trim()) {
      return candidate
    }
  }
  return fallback
}

function roleLabel(role: 'ALL' | UserRole): string {
  if (role === 'ALL') {
    return 'All roles'
  }
  return role.replace('_', ' ').toLowerCase().replace(/(^|\s)\w/g, (letter) => letter.toUpperCase())
}

function statusLabel(status: string): string {
  return status.replace('_', ' ').toLowerCase().replace(/(^|\s)\w/g, (letter) => letter.toUpperCase())
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString()
}

function roleBadgeClass(role: UserRole): string {
  switch (role) {
    case 'ADMIN':
      return 'badge-admin'
    case 'VERIFIED_CHEF':
      return 'badge-chef'
    case 'SUPPLIER':
      return 'badge-supplier'
    default:
      return 'badge-cook'
  }
}

function statusBadgeClass(status: string): string {
  if (status === 'ACTIVE') {
    return 'badge-success'
  }
  if (status === 'PENDING_VERIFICATION') {
    return 'badge-warning'
  }
  return 'badge-muted'
}

async function fetchJson(url: string, init?: RequestInit) {
  const response = await fetch(url, init)
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(parseApiError(payload, 'Request failed.'))
  }
  return payload
}

async function loadAdminOverview(adminUserId?: number) {
  const id = adminUserId ?? requireAdminUserId()
  statsLoading.value = true
  try {
    adminStats.value = await fetchJson(`${apiBaseUrl}/api/admin/dashboard-stats?adminUserId=${id}`)
  } finally {
    statsLoading.value = false
  }
}

async function loadAdminUsers(adminUserId?: number) {
  const id = adminUserId ?? requireAdminUserId()
  usersLoading.value = true
  try {
    const params = new URLSearchParams({ adminUserId: String(id) })
    if (userRoleFilter.value !== 'ALL') {
      params.set('role', userRoleFilter.value)
    }
    const cleanedSearch = userSearch.value.trim()
    if (cleanedSearch) {
      params.set('search', cleanedSearch)
    }

    adminUsers.value = await fetchJson(`${apiBaseUrl}/api/admin/users?${params.toString()}`)
  } finally {
    usersLoading.value = false
  }
}

async function loadPendingChefs(adminUserId?: number) {
  const id = adminUserId ?? requireAdminUserId()
  pendingLoading.value = true
  try {
    pendingChefs.value = await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/pending?adminUserId=${id}`)
  } finally {
    pendingLoading.value = false
  }
}

async function loadApprovedChefs(adminUserId?: number) {
  const id = adminUserId ?? requireAdminUserId()
  approvedLoading.value = true
  try {
    approvedChefs.value = await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/approved?adminUserId=${id}`)
  } finally {
    approvedLoading.value = false
  }
}

async function loadAdminData(adminUserId?: number) {
  const id = adminUserId ?? requireAdminUserId()
  adminNotice.value = ''
  try {
    await Promise.all([loadAdminOverview(id), loadAdminUsers(id), loadPendingChefs(id), loadApprovedChefs(id)])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not load admin data.'
  }
}

function switchAdminTab(nextTab: AdminTab) {
  adminTab.value = nextTab
}

async function applyUsersFilter() {
  try {
    await loadAdminUsers()
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not load users.'
  }
}

async function resetUsersFilter() {
  userRoleFilter.value = 'ALL'
  userSearch.value = ''
  await applyUsersFilter()
}

async function deleteUser(user: AdminSystemUser) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') {
    return
  }

  if (!window.confirm(`Delete user ${user.fullName} (${user.email})? This action cannot be undone.`)) {
    return
  }

  adminActionUserId.value = user.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/users/${user.userId}?adminUserId=${currentUser.value.userId}`, {
      method: 'DELETE'
    })

    adminNotice.value = `User ${user.fullName} deleted.`
    await Promise.all([
      loadAdminOverview(currentUser.value.userId),
      loadAdminUsers(currentUser.value.userId),
      loadPendingChefs(currentUser.value.userId),
      loadApprovedChefs(currentUser.value.userId)
    ])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not delete user.'
  } finally {
    adminActionUserId.value = null
  }
}

function canToggleBlock(user: AdminSystemUser): boolean {
  if (user.role === 'ADMIN') {
    return false
  }
  if (user.role === 'VERIFIED_CHEF' && user.accountStatus === 'PENDING_VERIFICATION') {
    return false
  }
  return true
}

async function toggleBlock(user: AdminSystemUser) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN' || !canToggleBlock(user)) {
    return
  }

  const nextAction = user.accountStatus === 'BLOCKED' ? 'unblock' : 'block'
  const nextVerb = user.accountStatus === 'BLOCKED' ? 'unblock' : 'block'
  if (!window.confirm(`Are you sure you want to ${nextVerb} ${user.fullName}?`)) {
    return
  }

  adminActionUserId.value = user.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/users/${user.userId}/${nextAction}?adminUserId=${currentUser.value.userId}`, {
      method: 'POST'
    })

    adminNotice.value = `${user.fullName} ${nextAction}ed successfully.`
    await Promise.all([
      loadAdminOverview(currentUser.value.userId),
      loadAdminUsers(currentUser.value.userId),
      loadPendingChefs(currentUser.value.userId),
      loadApprovedChefs(currentUser.value.userId)
    ])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not update user status.'
  } finally {
    adminActionUserId.value = null
  }
}

async function approvePendingChef(chef: PendingChef) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') {
    return
  }

  adminActionUserId.value = chef.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/${chef.userId}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        adminUserId: currentUser.value.userId,
        reviewNote: 'Approved by admin'
      })
    })

    adminNotice.value = `${chef.fullName} is now verified.`
    await Promise.all([
      loadAdminOverview(currentUser.value.userId),
      loadAdminUsers(currentUser.value.userId),
      loadPendingChefs(currentUser.value.userId),
      loadApprovedChefs(currentUser.value.userId)
    ])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not approve verified chef.'
  } finally {
    adminActionUserId.value = null
  }
}

async function requireApprovalAgain(chef: ApprovedChef) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') {
    return
  }

  if (!window.confirm(`Move ${chef.fullName} back to pending verification?`)) {
    return
  }

  adminActionUserId.value = chef.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/${chef.userId}/mark-pending?adminUserId=${currentUser.value.userId}`, {
      method: 'POST'
    })

    adminNotice.value = `${chef.fullName} now requires admin approval again.`
    await Promise.all([
      loadAdminOverview(currentUser.value.userId),
      loadAdminUsers(currentUser.value.userId),
      loadPendingChefs(currentUser.value.userId),
      loadApprovedChefs(currentUser.value.userId)
    ])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not move chef to pending.'
  } finally {
    adminActionUserId.value = null
  }
}

async function submitLogin() {
  if (!loginForm.email || !loginForm.password) {
    message.value = 'Please enter email and password.'
    return
  }

  busy.value = true
  message.value = ''

  try {
    const payload = await fetchJson(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: loginForm.email,
        password: loginForm.password
      })
    }) as AuthUser

    currentUser.value = payload
    activeMode.value = 'dashboard'
    message.value = payload.message || `Welcome, ${payload.fullName}.`
    resetLoginForm()

    if (payload.role === 'ADMIN') {
      await loadAdminData(payload.userId)
    } else {
      clearAdminState()
    }
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Login failed.'
  } finally {
    busy.value = false
  }
}

async function submitRegister() {
  if (!registerForm.fullName || !registerForm.email || !registerForm.password) {
    message.value = 'Please fill all required registration fields.'
    return
  }

  if (requiresChefReview.value && !registerForm.chefBio.trim()) {
    message.value = 'Verified chefs must provide a short bio for admin review.'
    return
  }

  busy.value = true
  message.value = ''

  try {
    const payload = await fetchJson(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fullName: registerForm.fullName,
        email: registerForm.email,
        password: registerForm.password,
        role: registerForm.role,
        chefBio: registerForm.chefBio
      })
    }) as { message?: string }

    activeMode.value = 'login'
    resetRegisterForm()
    message.value = payload.message || 'Registration successful.'
  } catch (error) {
    message.value = error instanceof Error ? error.message : 'Registration failed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main v-if="activeMode !== 'dashboard'" class="auth-shell">
    <section class="brand-panel">
      <p class="eyebrow">Culinary Discovery</p>
      <h1>Cook smarter.<br />Source local.<br />Grow together.</h1>
      <p class="subtext">
        Step 1 focuses on authentication UX. Role rules are already enforced in the form behavior,
        matching your project decisions.
      </p>
    </section>

    <section class="card">
      <div class="mode-switch" role="tablist" aria-label="Auth mode selector">
        <button :class="{ active: activeMode === 'login' }" type="button" @click="switchMode('login')">
          Login
        </button>
        <button :class="{ active: activeMode === 'register' }" type="button" @click="switchMode('register')">
          Register
        </button>
      </div>

      <form v-if="activeMode === 'login'" class="form" @submit.prevent="submitLogin">
        <label>
          Email
          <input v-model="loginForm.email" type="email" placeholder="you@example.com" required />
        </label>

        <label>
          Password
          <input v-model="loginForm.password" type="password" placeholder="********" required />
        </label>

        <button class="primary" type="submit" :disabled="busy">{{ busy ? 'Signing in...' : 'Sign in' }}</button>
      </form>

      <form v-else class="form" @submit.prevent="submitRegister">
        <label>
          Full name
          <input v-model="registerForm.fullName" type="text" placeholder="Alex Morgan" required />
        </label>

        <label>
          Email
          <input v-model="registerForm.email" type="email" placeholder="you@example.com" required />
        </label>

        <label>
          Password
          <input v-model="registerForm.password" type="password" placeholder="Choose a strong password" required />
        </label>

        <label>
          Role (exactly one)
          <select v-model="registerForm.role">
            <option
              v-for="(label, roleValue) in roleLabels"
              :key="roleValue"
              :value="roleValue"
            >
              {{ label }}
            </option>
          </select>
        </label>

        <label v-if="requiresChefReview">
          Chef bio (required for verification)
          <textarea
            v-model="registerForm.chefBio"
            rows="3"
            placeholder="Share your culinary background"
            required
          />
        </label>

        <p v-if="requiresChefReview" class="hint">
          Verified Chef accounts remain blocked until approved by an admin.
        </p>

        <button class="primary" type="submit" :disabled="busy">{{ busy ? 'Creating...' : 'Create account' }}</button>
      </form>

      <p v-if="message" class="message">{{ message }}</p>
    </section>
  </main>

  <main v-else-if="isAdmin" class="dashboard-shell admin-shell">
    <section class="dashboard-hero">
      <p class="eyebrow">{{ dashboardCopy.ADMIN.accent }}</p>
      <h1>{{ dashboardCopy.ADMIN.title }}</h1>
      <p class="subtext">{{ dashboardCopy.ADMIN.description }}</p>
    </section>

    <section class="dashboard-card admin-nav-card">
      <div class="admin-tabs">
        <button type="button" :class="{ active: adminTab === 'overview' }" @click="switchAdminTab('overview')">Overview</button>
        <button type="button" :class="{ active: adminTab === 'users' }" @click="switchAdminTab('users')">Users</button>
        <button type="button" :class="{ active: adminTab === 'pending' }" @click="switchAdminTab('pending')">Pending Verified Chefs</button>
      </div>

      <p v-if="adminNotice" class="message admin-notice">{{ adminNotice }}</p>
    </section>

    <section v-if="adminTab === 'overview'" class="dashboard-card">
      <p class="dashboard-label">Platform snapshot</p>

      <div v-if="statsLoading" class="empty-state">Loading dashboard stats...</div>

      <div v-else-if="adminStats" class="stats-grid">
        <article class="stat-card">
          <h3>Total users</h3>
          <p>{{ adminStats.totalUsers }}</p>
        </article>
        <article class="stat-card">
          <h3>Home cooks</h3>
          <p>{{ adminStats.homeCookCount }}</p>
        </article>
        <article class="stat-card">
          <h3>Suppliers</h3>
          <p>{{ adminStats.supplierCount }}</p>
        </article>
        <article class="stat-card">
          <h3>Verified chefs</h3>
          <p>{{ adminStats.verifiedChefCount }}</p>
        </article>
        <article class="stat-card">
          <h3>Pending chef approvals</h3>
          <p>{{ adminStats.pendingChefVerifications }}</p>
        </article>
        <article class="stat-card">
          <h3>Active users</h3>
          <p>{{ adminStats.activeUsers }}</p>
        </article>
        <article class="stat-card">
          <h3>Blocked users</h3>
          <p>{{ adminStats.blockedUsers }}</p>
        </article>
        <article class="stat-card">
          <h3>Admins</h3>
          <p>{{ adminStats.adminCount }}</p>
        </article>
      </div>

      <div class="dashboard-actions">
        <button class="secondary" type="button" @click="loadAdminData()" :disabled="statsLoading || usersLoading || pendingLoading">
          Refresh all admin data
        </button>
        <button class="primary" type="button" @click="logout">Log out</button>
      </div>
    </section>

    <section v-else-if="adminTab === 'users'" class="dashboard-card">
      <p class="dashboard-label">Users management</p>

      <div class="users-toolbar">
        <label>
          Search users
          <input
            v-model="userSearch"
            type="search"
            placeholder="Search by name or email"
            @keydown.enter.prevent="applyUsersFilter"
          />
        </label>

        <label>
          Role filter
          <select v-model="userRoleFilter">
            <option v-for="role in userRoleOptions" :key="role" :value="role">{{ roleLabel(role) }}</option>
          </select>
        </label>

        <div class="users-toolbar-actions">
          <button class="secondary" type="button" @click="applyUsersFilter" :disabled="usersLoading">Apply</button>
          <button class="secondary" type="button" @click="resetUsersFilter" :disabled="usersLoading">Reset</button>
        </div>
      </div>

      <div v-if="usersLoading" class="empty-state">Loading users...</div>

      <div v-else-if="adminUsers.length === 0" class="empty-state">No users match the current filter.</div>

      <div v-else class="table-wrap">
        <table class="users-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Joined</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in adminUsers" :key="user.userId">
              <td>{{ user.fullName }}</td>
              <td>{{ user.email }}</td>
              <td>
                <span class="role-badge" :class="roleBadgeClass(user.role)">{{ roleLabel(user.role) }}</span>
              </td>
              <td>
                <span class="status-badge" :class="statusBadgeClass(user.accountStatus)">{{ statusLabel(user.accountStatus) }}</span>
              </td>
              <td>{{ formatDateTime(user.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    class="warning"
                    type="button"
                    :disabled="adminActionUserId === user.userId || !canToggleBlock(user)"
                    @click="toggleBlock(user)"
                  >
                    {{ adminActionUserId === user.userId ? 'Updating...' : user.accountStatus === 'BLOCKED' ? 'Unblock' : 'Block' }}
                  </button>
                  <button
                    class="danger"
                    type="button"
                    :disabled="adminActionUserId === user.userId || user.role === 'ADMIN'"
                    @click="deleteUser(user)"
                  >
                    {{ adminActionUserId === user.userId ? 'Deleting...' : 'Delete' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else class="dashboard-card">
      <p class="dashboard-label">Verified chef approvals</p>

      <div class="pending-section">
        <h3>Pending chef approvals</h3>

        <div v-if="pendingLoading" class="empty-state">Loading pending chefs...</div>

        <div v-else-if="pendingChefs.length === 0" class="empty-state">No chefs are pending verification.</div>

        <div v-else class="pending-list">
          <article class="pending-card" v-for="chef in pendingChefs" :key="chef.userId">
            <div>
              <h3>{{ chef.fullName }}</h3>
              <p class="pending-email">{{ chef.email }}</p>
              <p class="pending-meta">Requested: {{ formatDateTime(chef.requestedAt) }}</p>
              <p class="pending-bio">{{ chef.bio || 'No bio provided.' }}</p>
            </div>

            <button
              class="primary"
              type="button"
              :disabled="adminActionUserId === chef.userId"
              @click="approvePendingChef(chef)"
            >
              {{ adminActionUserId === chef.userId ? 'Verifying...' : 'Verify' }}
            </button>
          </article>
        </div>
      </div>

      <div class="pending-section">
        <h3>Approved verified chefs</h3>

        <div v-if="approvedLoading" class="empty-state">Loading approved chefs...</div>

        <div v-else-if="approvedChefs.length === 0" class="empty-state">No approved verified chefs found.</div>

        <div v-else class="pending-list">
          <article class="pending-card" v-for="chef in approvedChefs" :key="chef.userId">
            <div>
              <h3>{{ chef.fullName }}</h3>
              <p class="pending-email">{{ chef.email }}</p>
              <p class="pending-meta">Requested: {{ formatDateTime(chef.requestedAt) }}</p>
              <p class="pending-meta">Approved: {{ chef.reviewedAt ? formatDateTime(chef.reviewedAt) : '-' }}</p>
              <p class="pending-bio">{{ chef.bio || 'No bio provided.' }}</p>
            </div>

            <button
              class="warning"
              type="button"
              :disabled="adminActionUserId === chef.userId"
              @click="requireApprovalAgain(chef)"
            >
              {{ adminActionUserId === chef.userId ? 'Updating...' : 'Require Approval Again' }}
            </button>
          </article>
        </div>
      </div>
    </section>
  </main>

  <main v-else class="dashboard-shell">
    <section class="dashboard-hero">
      <p class="eyebrow">{{ dashboardCopy[currentUser?.role || 'HOME_COOK'].accent }}</p>
      <h1>{{ dashboardCopy[currentUser?.role || 'HOME_COOK'].title }}</h1>
      <p class="subtext">
        {{ dashboardCopy[currentUser?.role || 'HOME_COOK'].description }}
      </p>
    </section>

    <section class="dashboard-card">
      <p class="dashboard-label">Signed in as</p>
      <h2>{{ currentUser?.fullName }}</h2>
      <p>{{ currentUser?.email }}</p>
      <p class="hint">Role: {{ currentUser?.role }} | Status: {{ currentUser?.accountStatus }}</p>

      <div class="dashboard-actions">
        <button class="primary" type="button" @click="logout">Log out</button>
      </div>
    </section>
  </main>
</template>
