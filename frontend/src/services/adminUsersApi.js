const jsonHeaders = { 'Content-Type': 'application/json', Accept: 'application/json' }

async function parseError(response) {
  const text = await response.text()
  try {
    const j = JSON.parse(text)
    // Spring Boot error JSON: { status, error, message, path }
    return j.message || j.error || text || response.statusText
  } catch {
    return text || response.statusText
  }
}

async function req(path, options = {}) {
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? jsonHeaders : {}),
      ...(options.headers || {}),
    },
  })
  if (!response.ok) {
    throw new Error(await parseError(response))
  }
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    throw new Error('Invalid JSON from server')
  }
}

export async function listUsers() {
  const data = await req('/api/admin/users')
  return Array.isArray(data) ? data : []
}

export function createUser(body) {
  return req('/api/admin/users', { method: 'POST', body: JSON.stringify(body) })
}

export function updateUser(id, body) {
  return req(`/api/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function setPassword(id, password) {
  return req(`/api/admin/users/${id}/password`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export function seedCash(id, amount) {
  return req(`/api/admin/users/${id}/seed-cash`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  })
}

export function resetAccount(id, amount) {
  // Omit body when no amount so backend uses default seed cash
  if (amount == null) {
    return req(`/api/admin/users/${id}/reset`, { method: 'POST' })
  }
  return req(`/api/admin/users/${id}/reset`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  })
}

export function setTradingEnabled(id, enabled) {
  return req(`/api/admin/users/${id}/trading`, {
    method: 'POST',
    body: JSON.stringify({ enabled }),
  })
}
