<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

type UserRole = 'HOME_COOK' | 'VERIFIED_CHEF' | 'SUPPLIER' | 'ADMIN'
type RegisterRole = Exclude<UserRole, 'ADMIN'>
type ViewMode = 'login' | 'register' | 'dashboard'

type AuthUser = {
  userId: number
  fullName: string
  email: string
  role: UserRole
  accountStatus: string
  message: string
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
    title: 'Admin Dashboard',
    description: 'Review chefs, manage platform access, and monitor the marketplace.',
    accent: 'Admin workspace'
  }
}

const requiresChefReview = computed(() => registerForm.role === 'VERIFIED_CHEF')

function resetLoginForm() {
  Object.assign(loginForm, loginDefaults)
}

function resetRegisterForm() {
  Object.assign(registerForm, registerDefaults)
}

function switchMode(nextMode: Exclude<ViewMode, 'dashboard'>) {
  activeMode.value = nextMode
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
}

function logout() {
  currentUser.value = null
  activeMode.value = 'login'
  message.value = ''
  resetLoginForm()
  resetRegisterForm()
}

watch(activeMode, (nextMode, previousMode) => {
  if (nextMode !== previousMode) {
    message.value = ''
    resetLoginForm()
    resetRegisterForm()
  }
})

async function submitLogin() {
  if (!loginForm.email || !loginForm.password) {
    message.value = 'Please enter email and password.'
    return
  }

  busy.value = true
  message.value = ''

  try {
    const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: loginForm.email,
        password: loginForm.password
      })
    })

    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.message || 'Login failed.')
    }

    currentUser.value = payload
    activeMode.value = 'dashboard'
    message.value = payload.message || `Welcome, ${payload.fullName}.`
    resetLoginForm()
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
    const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fullName: registerForm.fullName,
        email: registerForm.email,
        password: registerForm.password,
        role: registerForm.role,
        chefBio: registerForm.chefBio
      })
    })

    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.message || 'Registration failed.')
    }

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
