export type User = { id: string; name: string; email: string }
export type Project = { id: string; name: string; description: string | null; createdAt: string; updatedAt: string }
export type ProjectPage = { content: Project[]; number: number; totalPages: number; totalElements: number }
export type ApiKey = { id: string; name: string; keyPrefix: string; createdAt: string; expiresAt: string | null; revokedAt: string | null; lastUsedAt: string | null }
export type CreatedApiKey = { apiKey: ApiKey; key: string }

export class ApiClientError extends Error {
  constructor(public status: number, message: string) { super(message) }
}

async function csrfToken(): Promise<string> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/csrf', { credentials: 'include', cache: 'no-store' })
  } catch {
    throw new ApiClientError(0, 'Não foi possível conectar à API.')
  }
  if (!response.ok) throw new ApiClientError(response.status, 'Não foi possível iniciar uma operação segura.')
  const body = await response.json() as { token: string }
  return body.token
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-CSRF-TOKEN'] = await csrfToken()
  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'include',
      cache: 'no-store',
    })
  } catch {
    throw new ApiClientError(0, 'Não foi possível conectar à API.')
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiClientError(response.status, error?.message || 'A operação não pôde ser concluída.')
  }
  if (response.status === 204) return undefined as T
  return await response.json() as T
}

export async function currentUser(): Promise<User | null> {
  try {
    return await api<User>('/api/v1/auth/me')
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 401) return null
    throw error
  }
}
