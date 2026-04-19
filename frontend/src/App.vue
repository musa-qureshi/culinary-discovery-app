<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

type UserRole = 'HOME_COOK' | 'VERIFIED_CHEF' | 'SUPPLIER' | 'ADMIN'
type RegisterRole = Exclude<UserRole, 'ADMIN'>
type ViewMode = 'login' | 'register' | 'dashboard'
type AdminTab = 'overview' | 'users' | 'pending'
type ChefTab = 'overview' | 'recipes' | 'create'

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

type RecipeAnalyticsItem = {
  recipeId: number
  title: string
  difficulty: string
  cookTimeMin: number
  cookedCount: number
  avgRating: number
  reviewCount: number
  royalty: number
}

type ChefDashboardData = {
  chefId: number
  fullName: string
  email: string
  bio: string | null
  totalRoyalty: number
  totalRecipes: number
  totalCooks: number
  overallAvgRating: number
  recipeAnalytics: RecipeAnalyticsItem[]
}

type RecipeSummaryItem = {
  recipeId: number
  title: string
  description: string | null
  baseServings: number
  cookTimeMin: number
  difficulty: string
  publishedAt: string | null
  tags: string[]
}

type IngredientItem = {
  ingredientId: number
  name: string
  calories: number
  carbs: number
  protein: number
  fat: number
  categoryId: number | null
  categoryName: string | null
}

type DietaryTag = {
  tagId: number
  name: string
}

type IngredientLine = {
  ingredientId: number
  ingredientName: string
  quantity: number
  unit: string
}

type MediaLine = {
  mediaType: string
  url: string
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const loginDefaults = { email: '', password: '' }
const registerDefaults = {
  fullName: '',
  email: '',
  password: '',
  role: 'HOME_COOK' as RegisterRole,
  chefBio: ''
}
const createFormDefaults = {
  title: '',
  description: '',
  baseServings: 2,
  cookTimeMin: 30,
  difficulty: 'Easy'
}

// —— Auth state ——
const activeMode = ref<ViewMode>('login')
const loginForm = reactive({ ...loginDefaults })
const registerForm = reactive({ ...registerDefaults })
const message = ref('')
const busy = ref(false)
const currentUser = ref<AuthUser | null>(null)

// —— Admin state ——
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

// —— Chef state ——
const chefTab = ref<ChefTab>('overview')
const chefNotice = ref('')
const chefDashboard = ref<ChefDashboardData | null>(null)
const chefRecipes = ref<RecipeSummaryItem[]>([])
const chefLoading = ref(false)
const chefRecipesLoading = ref(false)
const chefActionRecipeId = ref<number | null>(null)

// Ingredient / tag reference data for recipe creation
const availableIngredients = ref<IngredientItem[]>([])
const availableCategories = ref<Array<{ categoryId: number; name: string }>>([])
const availableDietaryTags = ref<DietaryTag[]>([])
const ingSearch = ref('')
const ingCategoryId = ref<number | null>(null)
const ingLoading = ref(false)

// Create recipe form state
const createForm = reactive({ ...createFormDefaults })
const selectedTagIds = ref<number[]>([])
const ingredientLines = ref<IngredientLine[]>([])
const stepLines = ref<Array<{ instructionText: string }>>([{ instructionText: '' }])
const mediaLines = ref<MediaLine[]>([])
const pickedIngredientId = ref<number | null>(null)
const pickedIngredientName = ref('')
const pickedQty = ref<number>(1)
const pickedUnit = ref('cup')
const createBusy = ref(false)

// —— Static labels ——
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
    description: 'Create and manage your recipes, and track your royalties.',
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

const difficultyOptions = ['Easy', 'Medium', 'Hard']
const unitOptions = ['cup', 'tbsp', 'tsp', 'g', 'kg', 'ml', 'L', 'oz', 'lb', 'piece', 'slice', 'pinch', 'clove', 'bunch']
const userRoleOptions: Array<'ALL' | UserRole> = ['ALL', 'HOME_COOK', 'SUPPLIER', 'VERIFIED_CHEF', 'ADMIN']

// —— Computed ——
const requiresChefReview = computed(() => registerForm.role === 'VERIFIED_CHEF')
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
const isChef = computed(() => currentUser.value?.role === 'VERIFIED_CHEF')
const isPendingChef = computed(() => isChef.value && currentUser.value?.accountStatus === 'PENDING_VERIFICATION')
const isActiveChef = computed(() => isChef.value && currentUser.value?.accountStatus === 'ACTIVE')

const filteredIngredients = computed(() => {
  let list = availableIngredients.value
  const q = ingSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(i => i.name.toLowerCase().includes(q))
  }
  if (ingCategoryId.value !== null) {
    list = list.filter(i => i.categoryId === ingCategoryId.value)
  }
  return list.slice(0, 60)
})

// —— Reset helpers ——
function resetLoginForm() { Object.assign(loginForm, loginDefaults) }
function resetRegisterForm() { Object.assign(registerForm, registerDefaults) }

function resetCreateForm() {
  Object.assign(createForm, createFormDefaults)
  selectedTagIds.value = []
  ingredientLines.value = []
  stepLines.value = [{ instructionText: '' }]
  mediaLines.value = []
  pickedIngredientId.value = null
  pickedIngredientName.value = ''
  pickedQty.value = 1
  pickedUnit.value = 'cup'
  ingSearch.value = ''
  ingCategoryId.value = null
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

function clearChefState() {
  chefTab.value = 'overview'
  chefNotice.value = ''
  chefDashboard.value = null
  chefRecipes.value = []
  chefLoading.value = false
  chefRecipesLoading.value = false
  chefActionRecipeId.value = null
  availableIngredients.value = []
  availableCategories.value = []
  availableDietaryTags.value = []
  ingLoading.value = false
  resetCreateForm()
}

function switchMode(nextMode: Exclude<ViewMode, 'dashboard'>) {
  activeMode.value = nextMode
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
  clearAdminState()
  clearChefState()
}

function logout() {
  currentUser.value = null
  activeMode.value = 'login'
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
  clearAdminState()
  clearChefState()
}

watch(activeMode, (nextMode, previousMode) => {
  if (nextMode !== previousMode) {
    message.value = ''
    resetLoginForm()
    resetRegisterForm()
  }
})

// —— Utility ——
function requireAdminUserId(): number {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') {
    throw new Error('Admin session required.')
  }
  return currentUser.value.userId
}

function requireChefUserId(): number {
  if (!currentUser.value || currentUser.value.role !== 'VERIFIED_CHEF') {
    throw new Error('Chef session required.')
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
  if (role === 'ALL') return 'All roles'
  return role.replace('_', ' ').toLowerCase().replace(/(^|\s)\w/g, (l) => l.toUpperCase())
}

function statusLabel(status: string): string {
  return status.replace('_', ' ').toLowerCase().replace(/(^|\s)\w/g, (l) => l.toUpperCase())
}

function formatDateTime(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString()
}

function roleBadgeClass(role: UserRole): string {
  switch (role) {
    case 'ADMIN': return 'badge-admin'
    case 'VERIFIED_CHEF': return 'badge-chef'
    case 'SUPPLIER': return 'badge-supplier'
    default: return 'badge-cook'
  }
}

function statusBadgeClass(status: string): string {
  if (status === 'ACTIVE') return 'badge-success'
  if (status === 'PENDING_VERIFICATION') return 'badge-warning'
  return 'badge-muted'
}

function difficultyBadgeClass(difficulty: string): string {
  if (difficulty === 'Easy') return 'badge-success'
  if (difficulty === 'Medium') return 'badge-warning'
  return 'badge-muted'
}

// —— HTTP ——
async function fetchJson(url: string, init?: RequestInit) {
  const response = await fetch(url, init)
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(parseApiError(payload, 'Request failed.'))
  }
  return payload
}

// —— Admin API ——
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
    if (userRoleFilter.value !== 'ALL') params.set('role', userRoleFilter.value)
    const cleanedSearch = userSearch.value.trim()
    if (cleanedSearch) params.set('search', cleanedSearch)
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

function switchAdminTab(nextTab: AdminTab) { adminTab.value = nextTab }

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
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  if (!window.confirm(`Delete user ${user.fullName} (${user.email})? This action cannot be undone.`)) return
  adminActionUserId.value = user.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/users/${user.userId}?adminUserId=${currentUser.value.userId}`, { method: 'DELETE' })
    adminNotice.value = `User ${user.fullName} deleted.`
    await Promise.all([loadAdminOverview(currentUser.value.userId), loadAdminUsers(currentUser.value.userId), loadPendingChefs(currentUser.value.userId), loadApprovedChefs(currentUser.value.userId)])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not delete user.'
  } finally {
    adminActionUserId.value = null
  }
}

function canToggleBlock(user: AdminSystemUser): boolean {
  if (user.role === 'ADMIN') return false
  if (user.role === 'VERIFIED_CHEF' && user.accountStatus === 'PENDING_VERIFICATION') return false
  return true
}

async function toggleBlock(user: AdminSystemUser) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN' || !canToggleBlock(user)) return
  const nextAction = user.accountStatus === 'BLOCKED' ? 'unblock' : 'block'
  if (!window.confirm(`Are you sure you want to ${nextAction} ${user.fullName}?`)) return
  adminActionUserId.value = user.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/users/${user.userId}/${nextAction}?adminUserId=${currentUser.value.userId}`, { method: 'POST' })
    adminNotice.value = `${user.fullName} ${nextAction}ed successfully.`
    await Promise.all([loadAdminOverview(currentUser.value.userId), loadAdminUsers(currentUser.value.userId), loadPendingChefs(currentUser.value.userId), loadApprovedChefs(currentUser.value.userId)])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not update user status.'
  } finally {
    adminActionUserId.value = null
  }
}

async function approvePendingChef(chef: PendingChef) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  adminActionUserId.value = chef.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/${chef.userId}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ adminUserId: currentUser.value.userId, reviewNote: 'Approved by admin' })
    })
    adminNotice.value = `${chef.fullName} is now verified.`
    await Promise.all([loadAdminOverview(currentUser.value.userId), loadAdminUsers(currentUser.value.userId), loadPendingChefs(currentUser.value.userId), loadApprovedChefs(currentUser.value.userId)])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not approve verified chef.'
  } finally {
    adminActionUserId.value = null
  }
}

async function requireApprovalAgain(chef: ApprovedChef) {
  if (!currentUser.value || currentUser.value.role !== 'ADMIN') return
  if (!window.confirm(`Move ${chef.fullName} back to pending verification?`)) return
  adminActionUserId.value = chef.userId
  try {
    await fetchJson(`${apiBaseUrl}/api/admin/verified-chefs/${chef.userId}/mark-pending?adminUserId=${currentUser.value.userId}`, { method: 'POST' })
    adminNotice.value = `${chef.fullName} now requires admin approval again.`
    await Promise.all([loadAdminOverview(currentUser.value.userId), loadAdminUsers(currentUser.value.userId), loadPendingChefs(currentUser.value.userId), loadApprovedChefs(currentUser.value.userId)])
  } catch (error) {
    adminNotice.value = error instanceof Error ? error.message : 'Could not move chef to pending.'
  } finally {
    adminActionUserId.value = null
  }
}

// —— Chef API ——
async function loadChefDashboard(userId?: number) {
  const id = userId ?? requireChefUserId()
  chefLoading.value = true
  try {
    chefDashboard.value = await fetchJson(`${apiBaseUrl}/api/chef/dashboard?userId=${id}`)
  } finally {
    chefLoading.value = false
  }
}

async function loadChefRecipes(userId?: number) {
  const id = userId ?? requireChefUserId()
  chefRecipesLoading.value = true
  try {
    chefRecipes.value = await fetchJson(`${apiBaseUrl}/api/chef/recipes?userId=${id}`)
  } finally {
    chefRecipesLoading.value = false
  }
}

async function loadIngredients() {
  ingLoading.value = true
  try {
    availableIngredients.value = await fetchJson(`${apiBaseUrl}/api/ingredients`)
  } finally {
    ingLoading.value = false
  }
}

async function loadIngredientCategories() {
  availableCategories.value = await fetchJson(`${apiBaseUrl}/api/ingredient-categories`)
}

async function loadDietaryTags() {
  availableDietaryTags.value = await fetchJson(`${apiBaseUrl}/api/dietary-tags`)
}

async function loadChefData(userId?: number) {
  const id = userId ?? requireChefUserId()
  chefNotice.value = ''
  try {
    await Promise.all([loadChefDashboard(id), loadChefRecipes(id)])
  } catch (error) {
    chefNotice.value = error instanceof Error ? error.message : 'Could not load chef data.'
  }
}

async function switchChefTab(tab: ChefTab) {
  chefTab.value = tab
  chefNotice.value = ''
  if (tab === 'create' && availableIngredients.value.length === 0) {
    try {
      await Promise.all([loadIngredients(), loadIngredientCategories(), loadDietaryTags()])
    } catch {
      chefNotice.value = 'Could not load ingredient data.'
    }
  }
}

function pickIngredient(ing: IngredientItem) {
  pickedIngredientId.value = ing.ingredientId
  pickedIngredientName.value = ing.name
}

function addIngredient() {
  if (!pickedIngredientId.value || pickedQty.value <= 0) return
  const alreadyAdded = ingredientLines.value.some(l => l.ingredientId === pickedIngredientId.value)
  if (alreadyAdded) {
    chefNotice.value = `${pickedIngredientName.value} is already in the ingredient list.`
    return
  }
  ingredientLines.value.push({
    ingredientId: pickedIngredientId.value,
    ingredientName: pickedIngredientName.value,
    quantity: pickedQty.value,
    unit: pickedUnit.value
  })
  pickedIngredientId.value = null
  pickedIngredientName.value = ''
  pickedQty.value = 1
  chefNotice.value = ''
}

function removeIngredient(index: number) {
  ingredientLines.value.splice(index, 1)
}

function addStep() {
  stepLines.value.push({ instructionText: '' })
}

function removeStep(index: number) {
  if (stepLines.value.length <= 1) return
  stepLines.value.splice(index, 1)
}

function addMedia() {
  mediaLines.value.push({ mediaType: 'image', url: '' })
}

function removeMedia(index: number) {
  mediaLines.value.splice(index, 1)
}

function toggleTag(tagId: number) {
  const idx = selectedTagIds.value.indexOf(tagId)
  if (idx === -1) {
    selectedTagIds.value.push(tagId)
  } else {
    selectedTagIds.value.splice(idx, 1)
  }
}

async function submitCreateRecipe() {
  chefNotice.value = ''
  if (!createForm.title.trim()) {
    chefNotice.value = 'Recipe title is required.'
    return
  }
  if (ingredientLines.value.length === 0) {
    chefNotice.value = 'At least one ingredient is required.'
    return
  }
  if (stepLines.value.every(s => !s.instructionText.trim())) {
    chefNotice.value = 'At least one step with instructions is required.'
    return
  }

  createBusy.value = true
  try {
    const userId = requireChefUserId()
    const body = {
      title: createForm.title.trim(),
      description: createForm.description.trim() || null,
      baseServings: createForm.baseServings,
      cookTimeMin: createForm.cookTimeMin,
      difficulty: createForm.difficulty,
      tagIds: selectedTagIds.value,
      ingredients: ingredientLines.value.map(l => ({
        ingredientId: l.ingredientId,
        quantity: l.quantity,
        unit: l.unit
      })),
      steps: stepLines.value
        .filter(s => s.instructionText.trim())
        .map((s, i) => ({ stepNo: i + 1, instructionText: s.instructionText.trim() })),
      media: mediaLines.value
        .filter(m => m.url.trim())
        .map((m, i) => ({ mediaNo: i + 1, mediaType: m.mediaType, url: m.url.trim() }))
    }

    await fetchJson(`${apiBaseUrl}/api/chef/recipes?userId=${userId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })

    chefNotice.value = `Recipe "${createForm.title.trim()}" created successfully.`
    resetCreateForm()
    await Promise.all([loadChefDashboard(userId), loadChefRecipes(userId)])
    chefTab.value = 'recipes'
  } catch (error) {
    chefNotice.value = error instanceof Error ? error.message : 'Could not create recipe.'
  } finally {
    createBusy.value = false
  }
}

async function deleteRecipe(recipe: RecipeSummaryItem) {
  if (!window.confirm(`Delete recipe "${recipe.title}"? This cannot be undone.`)) return
  const userId = requireChefUserId()
  chefActionRecipeId.value = recipe.recipeId
  try {
    await fetchJson(`${apiBaseUrl}/api/chef/recipes/${recipe.recipeId}?userId=${userId}`, { method: 'DELETE' })
    chefNotice.value = `Recipe "${recipe.title}" deleted.`
    await Promise.all([loadChefDashboard(userId), loadChefRecipes(userId)])
  } catch (error) {
    chefNotice.value = error instanceof Error ? error.message : 'Could not delete recipe.'
  } finally {
    chefActionRecipeId.value = null
  }
}

// —— Auth ——
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
      body: JSON.stringify({ email: loginForm.email, password: loginForm.password })
    }) as AuthUser

    currentUser.value = payload
    activeMode.value = 'dashboard'
    message.value = payload.message || `Welcome, ${payload.fullName}.`
    resetLoginForm()

    if (payload.role === 'ADMIN') {
      await loadAdminData(payload.userId)
    } else if (payload.role === 'VERIFIED_CHEF' && payload.accountStatus === 'ACTIVE') {
      await loadChefData(payload.userId)
    } else {
      clearAdminState()
      clearChefState()
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
  <!-- ── Auth shell ── -->
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
        <button :class="{ active: activeMode === 'login' }" type="button" @click="switchMode('login')">Login</button>
        <button :class="{ active: activeMode === 'register' }" type="button" @click="switchMode('register')">Register</button>
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
            <option v-for="(label, roleValue) in roleLabels" :key="roleValue" :value="roleValue">{{ label }}</option>
          </select>
        </label>
        <label v-if="requiresChefReview">
          Chef bio (required for verification)
          <textarea v-model="registerForm.chefBio" rows="3" placeholder="Share your culinary background" required />
        </label>
        <p v-if="requiresChefReview" class="hint">
          Verified Chef accounts remain blocked until approved by an admin.
        </p>
        <button class="primary" type="submit" :disabled="busy">{{ busy ? 'Creating...' : 'Create account' }}</button>
      </form>

      <p v-if="message" class="message">{{ message }}</p>
    </section>
  </main>

  <!-- ── Admin dashboard ── -->
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
        <article class="stat-card"><h3>Total users</h3><p>{{ adminStats.totalUsers }}</p></article>
        <article class="stat-card"><h3>Home cooks</h3><p>{{ adminStats.homeCookCount }}</p></article>
        <article class="stat-card"><h3>Suppliers</h3><p>{{ adminStats.supplierCount }}</p></article>
        <article class="stat-card"><h3>Verified chefs</h3><p>{{ adminStats.verifiedChefCount }}</p></article>
        <article class="stat-card"><h3>Pending chef approvals</h3><p>{{ adminStats.pendingChefVerifications }}</p></article>
        <article class="stat-card"><h3>Active users</h3><p>{{ adminStats.activeUsers }}</p></article>
        <article class="stat-card"><h3>Blocked users</h3><p>{{ adminStats.blockedUsers }}</p></article>
        <article class="stat-card"><h3>Admins</h3><p>{{ adminStats.adminCount }}</p></article>
      </div>
      <div class="dashboard-actions">
        <button class="secondary" type="button" @click="loadAdminData()" :disabled="statsLoading || usersLoading || pendingLoading">Refresh all admin data</button>
        <button class="primary" type="button" @click="logout">Log out</button>
      </div>
    </section>

    <section v-else-if="adminTab === 'users'" class="dashboard-card">
      <p class="dashboard-label">Users management</p>
      <div class="users-toolbar">
        <label>
          Search users
          <input v-model="userSearch" type="search" placeholder="Search by name or email" @keydown.enter.prevent="applyUsersFilter" />
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
              <th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in adminUsers" :key="user.userId">
              <td>{{ user.fullName }}</td>
              <td>{{ user.email }}</td>
              <td><span class="role-badge" :class="roleBadgeClass(user.role)">{{ roleLabel(user.role) }}</span></td>
              <td><span class="status-badge" :class="statusBadgeClass(user.accountStatus)">{{ statusLabel(user.accountStatus) }}</span></td>
              <td>{{ formatDateTime(user.createdAt) }}</td>
              <td>
                <div class="row-actions">
                  <button class="warning" type="button" :disabled="adminActionUserId === user.userId || !canToggleBlock(user)" @click="toggleBlock(user)">
                    {{ adminActionUserId === user.userId ? 'Updating...' : user.accountStatus === 'BLOCKED' ? 'Unblock' : 'Block' }}
                  </button>
                  <button class="danger" type="button" :disabled="adminActionUserId === user.userId || user.role === 'ADMIN'" @click="deleteUser(user)">
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
            <button class="primary" type="button" :disabled="adminActionUserId === chef.userId" @click="approvePendingChef(chef)">
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
            <button class="warning" type="button" :disabled="adminActionUserId === chef.userId" @click="requireApprovalAgain(chef)">
              {{ adminActionUserId === chef.userId ? 'Updating...' : 'Require Approval Again' }}
            </button>
          </article>
        </div>
      </div>
    </section>
  </main>

  <!-- ── Pending Chef: verification waiting screen ── -->
  <main v-else-if="isPendingChef" class="dashboard-shell">
    <section class="dashboard-hero">
      <p class="eyebrow">Verified Chef workspace</p>
      <h1>Awaiting Verification</h1>
      <p class="subtext">Your application is under review. You'll gain full access once an admin approves your account.</p>
    </section>

    <section class="dashboard-card pending-chef-card">
      <div class="pending-chef-icon">⏳</div>
      <h2>Your account is pending verification</h2>
      <p>
        Thank you for registering as a Verified Chef, <strong>{{ currentUser?.fullName }}</strong>.
        An admin will review your application and bio shortly.
      </p>
      <p class="hint">Once approved, you'll be able to create recipes, manage your chef profile, and track your royalties.</p>
      <div class="dashboard-actions">
        <button class="primary" type="button" @click="logout">Log out</button>
      </div>
    </section>
  </main>

  <!-- ── Active Chef Dashboard ── -->
  <main v-else-if="isActiveChef" class="dashboard-shell chef-shell">
    <section class="dashboard-hero">
      <p class="eyebrow">{{ dashboardCopy.VERIFIED_CHEF.accent }}</p>
      <h1>{{ dashboardCopy.VERIFIED_CHEF.title }}</h1>
      <p class="subtext">{{ dashboardCopy.VERIFIED_CHEF.description }}</p>
    </section>

    <section class="dashboard-card admin-nav-card">
      <div class="admin-tabs">
        <button type="button" :class="{ active: chefTab === 'overview' }" @click="switchChefTab('overview')">Overview</button>
        <button type="button" :class="{ active: chefTab === 'recipes' }" @click="switchChefTab('recipes')">My Recipes</button>
        <button type="button" :class="{ active: chefTab === 'create' }" @click="switchChefTab('create')">Create Recipe</button>
      </div>
      <p v-if="chefNotice" class="message admin-notice">{{ chefNotice }}</p>
    </section>

    <!-- Overview tab -->
    <section v-if="chefTab === 'overview'" class="dashboard-card">
      <p class="dashboard-label">Chef overview</p>

      <div v-if="chefLoading" class="empty-state">Loading dashboard...</div>

      <template v-else-if="chefDashboard">
        <div class="stats-grid">
          <article class="stat-card">
            <h3>Recipes</h3>
            <p>{{ chefDashboard.totalRecipes }}</p>
          </article>
          <article class="stat-card">
            <h3>Times Cooked</h3>
            <p>{{ chefDashboard.totalCooks }}</p>
          </article>
          <article class="stat-card">
            <h3>Avg Rating</h3>
            <p>{{ chefDashboard.overallAvgRating > 0 ? chefDashboard.overallAvgRating.toFixed(1) : '—' }}</p>
          </article>
          <article class="stat-card">
            <h3>Total Royalty</h3>
            <p>${{ chefDashboard.totalRoyalty.toFixed(2) }}</p>
          </article>
        </div>

        <div v-if="chefDashboard.recipeAnalytics.length > 0" style="margin-top: 20px;">
          <p class="dashboard-label">Recipe analytics</p>
          <div class="table-wrap">
            <table class="users-table">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Difficulty</th>
                  <th>Cook Time</th>
                  <th>Times Cooked</th>
                  <th>Avg Rating</th>
                  <th>Reviews</th>
                  <th>Royalty</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in chefDashboard.recipeAnalytics" :key="entry.recipeId">
                  <td>{{ entry.title }}</td>
                  <td><span class="status-badge" :class="difficultyBadgeClass(entry.difficulty)">{{ entry.difficulty }}</span></td>
                  <td>{{ entry.cookTimeMin }} min</td>
                  <td>{{ entry.cookedCount }}</td>
                  <td>{{ entry.avgRating > 0 ? entry.avgRating.toFixed(1) : '—' }}</td>
                  <td>{{ entry.reviewCount }}</td>
                  <td>${{ entry.royalty.toFixed(2) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-else class="empty-state" style="margin-top: 16px;">No recipes yet. Create your first recipe to see analytics here.</div>
      </template>

      <div class="dashboard-actions">
        <button class="secondary" type="button" @click="loadChefData()" :disabled="chefLoading">Refresh</button>
        <button class="primary" type="button" @click="logout">Log out</button>
      </div>
    </section>

    <!-- My Recipes tab -->
    <section v-else-if="chefTab === 'recipes'" class="dashboard-card">
      <p class="dashboard-label">My recipes</p>

      <div v-if="chefRecipesLoading" class="empty-state">Loading recipes...</div>

      <div v-else-if="chefRecipes.length === 0" class="empty-state">
        You haven't created any recipes yet.
        <button class="secondary" type="button" style="margin-top: 10px;" @click="switchChefTab('create')">Create your first recipe</button>
      </div>

      <div v-else class="recipe-list">
        <article class="recipe-card" v-for="recipe in chefRecipes" :key="recipe.recipeId">
          <div class="recipe-card-body">
            <div class="recipe-card-header">
              <h3>{{ recipe.title }}</h3>
              <span class="status-badge" :class="difficultyBadgeClass(recipe.difficulty)">{{ recipe.difficulty }}</span>
            </div>
            <p v-if="recipe.description" class="recipe-description">{{ recipe.description }}</p>
            <div class="recipe-meta-row">
              <span class="recipe-meta-item">{{ recipe.cookTimeMin }} min</span>
              <span class="recipe-meta-item">{{ recipe.baseServings }} servings</span>
              <span v-if="recipe.publishedAt" class="recipe-meta-item">{{ formatDateTime(recipe.publishedAt) }}</span>
            </div>
            <div v-if="recipe.tags.length > 0" class="recipe-tag-row">
              <span class="tag-chip" v-for="tag in recipe.tags" :key="tag">{{ tag }}</span>
            </div>
          </div>
          <button
            class="danger"
            type="button"
            :disabled="chefActionRecipeId === recipe.recipeId"
            @click="deleteRecipe(recipe)"
          >
            {{ chefActionRecipeId === recipe.recipeId ? 'Deleting...' : 'Delete' }}
          </button>
        </article>
      </div>

      <div class="dashboard-actions">
        <button class="secondary" type="button" @click="loadChefRecipes()" :disabled="chefRecipesLoading">Refresh</button>
        <button class="primary" type="button" @click="switchChefTab('create')">+ New Recipe</button>
        <button class="secondary" type="button" @click="logout" style="margin-left: auto;">Log out</button>
      </div>
    </section>

    <!-- Create Recipe tab -->
    <section v-else class="dashboard-card">
      <p class="dashboard-label">Create a new recipe</p>

      <!-- Basic info -->
      <div class="create-section">
        <h3 class="create-section-title">Basic information</h3>
        <div class="create-grid-2">
          <label style="grid-column: 1 / -1;">
            Recipe title *
            <input v-model="createForm.title" type="text" placeholder="e.g. Classic Carbonara" />
          </label>
          <label style="grid-column: 1 / -1;">
            Description
            <textarea v-model="createForm.description" rows="2" placeholder="Short description of the dish..." />
          </label>
          <label>
            Difficulty
            <select v-model="createForm.difficulty">
              <option v-for="d in difficultyOptions" :key="d" :value="d">{{ d }}</option>
            </select>
          </label>
          <label>
            Base servings
            <input v-model.number="createForm.baseServings" type="number" min="1" max="50" />
          </label>
          <label>
            Cook time (minutes)
            <input v-model.number="createForm.cookTimeMin" type="number" min="1" max="600" />
          </label>
        </div>
      </div>

      <!-- Dietary tags -->
      <div class="create-section">
        <h3 class="create-section-title">Dietary tags</h3>
        <div v-if="availableDietaryTags.length === 0" class="empty-state">Loading tags...</div>
        <div v-else class="tag-grid">
          <button
            v-for="tag in availableDietaryTags"
            :key="tag.tagId"
            type="button"
            class="tag-toggle"
            :class="{ 'tag-toggle--active': selectedTagIds.includes(tag.tagId) }"
            @click="toggleTag(tag.tagId)"
          >
            {{ tag.name }}
          </button>
        </div>
      </div>

      <!-- Ingredient picker -->
      <div class="create-section">
        <h3 class="create-section-title">Ingredients *</h3>

        <div class="ing-picker">
          <div class="ing-search-row">
            <label style="flex: 2;">
              Search ingredients
              <input v-model="ingSearch" type="search" placeholder="e.g. tomato, chicken..." />
            </label>
            <label style="flex: 1;">
              Category
              <select v-model="ingCategoryId">
                <option :value="null">All categories</option>
                <option v-for="cat in availableCategories" :key="cat.categoryId" :value="cat.categoryId">{{ cat.name }}</option>
              </select>
            </label>
          </div>

          <div v-if="ingLoading" class="empty-state">Loading ingredients...</div>

          <div v-else-if="filteredIngredients.length === 0" class="empty-state">No ingredients match. Try a different search.</div>

          <div v-else class="ing-grid">
            <button
              v-for="ing in filteredIngredients"
              :key="ing.ingredientId"
              type="button"
              class="ing-option"
              :class="{ 'ing-option--picked': pickedIngredientId === ing.ingredientId }"
              @click="pickIngredient(ing)"
              :title="`${ing.calories} kcal | ${ing.protein}g protein | ${ing.carbs}g carbs | ${ing.fat}g fat`"
            >
              <span class="ing-name">{{ ing.name }}</span>
              <span class="ing-category">{{ ing.categoryName }}</span>
            </button>
          </div>

          <div v-if="pickedIngredientId" class="ing-add-row">
            <span class="ing-picked-name">{{ pickedIngredientName }}</span>
            <label style="flex: 0 0 90px;">
              Qty
              <input v-model.number="pickedQty" type="number" min="0.01" step="0.01" />
            </label>
            <label style="flex: 0 0 110px;">
              Unit
              <select v-model="pickedUnit">
                <option v-for="u in unitOptions" :key="u" :value="u">{{ u }}</option>
              </select>
            </label>
            <button class="primary" type="button" @click="addIngredient" style="margin-top: 22px; flex: 0 0 auto;">Add</button>
          </div>
        </div>

        <div v-if="ingredientLines.length > 0" class="ing-selected">
          <p class="dashboard-label" style="margin-bottom: 8px;">Added ingredients</p>
          <div class="table-wrap">
            <table class="users-table">
              <thead>
                <tr><th>Ingredient</th><th>Quantity</th><th>Unit</th><th></th></tr>
              </thead>
              <tbody>
                <tr v-for="(line, idx) in ingredientLines" :key="idx">
                  <td>{{ line.ingredientName }}</td>
                  <td>{{ line.quantity }}</td>
                  <td>{{ line.unit }}</td>
                  <td><button class="danger" type="button" @click="removeIngredient(idx)">Remove</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Steps -->
      <div class="create-section">
        <h3 class="create-section-title">Steps *</h3>
        <div class="step-list">
          <div class="step-item" v-for="(step, idx) in stepLines" :key="idx">
            <span class="step-number">{{ idx + 1 }}</span>
            <textarea
              v-model="step.instructionText"
              rows="2"
              :placeholder="`Step ${idx + 1} instructions...`"
            />
            <button class="danger" type="button" :disabled="stepLines.length <= 1" @click="removeStep(idx)">✕</button>
          </div>
        </div>
        <button class="secondary" type="button" @click="addStep" style="margin-top: 10px;">+ Add step</button>
      </div>

      <!-- Media (optional) -->
      <div class="create-section">
        <h3 class="create-section-title">Media <span class="optional-label">(optional)</span></h3>
        <div class="media-list" v-if="mediaLines.length > 0">
          <div class="media-item" v-for="(m, idx) in mediaLines" :key="idx">
            <label style="flex: 0 0 110px;">
              Type
              <select v-model="m.mediaType">
                <option value="image">Image</option>
                <option value="video">Video</option>
              </select>
            </label>
            <label style="flex: 1;">
              URL
              <input v-model="m.url" type="url" placeholder="https://..." />
            </label>
            <button class="danger" type="button" @click="removeMedia(idx)" style="margin-top: 22px; flex: 0 0 auto;">✕</button>
          </div>
        </div>
        <button class="secondary" type="button" @click="addMedia" style="margin-top: 10px;">+ Add media URL</button>
      </div>

      <div class="dashboard-actions" style="margin-top: 6px;">
        <button class="primary" type="button" :disabled="createBusy" @click="submitCreateRecipe">
          {{ createBusy ? 'Creating...' : 'Create recipe' }}
        </button>
        <button class="secondary" type="button" @click="resetCreateForm">Reset form</button>
        <button class="secondary" type="button" @click="logout" style="margin-left: auto;">Log out</button>
      </div>
    </section>
  </main>

  <!-- ── Generic fallback (Home Cook, Supplier) ── -->
  <main v-else class="dashboard-shell">
    <section class="dashboard-hero">
      <p class="eyebrow">{{ dashboardCopy[currentUser?.role || 'HOME_COOK'].accent }}</p>
      <h1>{{ dashboardCopy[currentUser?.role || 'HOME_COOK'].title }}</h1>
      <p class="subtext">{{ dashboardCopy[currentUser?.role || 'HOME_COOK'].description }}</p>
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
