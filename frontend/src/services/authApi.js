const jsonHeaders = { 'Content-Type': 'application/json', Accept: 'application/json' }

async function parseError(response) {
  const text = await response.text()
  try {
    const j = JSON.parse(text)
    return j.message || j.error || text || response.statusText
  } catch {
    return text || response.statusText
  }
}

export async function login(username, password) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders,
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) {
    throw new Error(await parseError(response))
  }
  const user = await response.json()
  if (!user || !user.username) {
    throw new Error('Login succeeded but user payload was empty')
  }
  return user
}

export async function logout() {
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: jsonHeaders,
  })
  if (!response.ok) {
    throw new Error(await parseError(response))
  }
  return response.json()
}

export async function fetchMe() {
  const response = await fetch('/api/auth/me', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new Error(await parseError(response))
  }
  return response.json()
}
