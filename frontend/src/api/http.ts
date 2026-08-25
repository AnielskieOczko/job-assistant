/**
 * The single fetch wrapper.
 *
 * Every path is relative (`/api/...`) and there is deliberately no base URL and no
 * `VITE_API_BASE`: in dev the Vite proxy forwards `/api` to 127.0.0.1:8080, and in production
 * Spring serves the built SPA from the same origin. That is why the backend needs no CORS
 * configuration at all, and adding a base URL would break both halves of that.
 */

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** RFC-7807 extensions: fabricatedClaims, unresolvedSkills, undeclaredBulletSkills. */
  [extension: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }

  get problem(): ProblemDetail | null {
    return this.body !== null && typeof this.body === 'object'
      ? (this.body as ProblemDetail)
      : null
  }

  /** Raw body text, for the controllers that have no exception handler and return a bare 500. */
  get rawBody(): string | null {
    return typeof this.body === 'string' ? this.body : null
  }

  private strings(key: string): string[] {
    const value = this.problem?.[key]
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
  }

  /** 422 from document generation: skills the model tried to claim but the profile lacks. */
  get fabricatedClaims(): string[] {
    return this.strings('fabricatedClaims')
  }

  /** 400 from profile import: names the catalog could not resolve. */
  get unresolvedSkills(): string[] {
    return this.strings('unresolvedSkills')
  }

  /** 400 from profile import: bullet tags not declared in `skills[]`. */
  get undeclaredBulletSkills(): string[] {
    return this.strings('undeclaredBulletSkills')
  }

  /** 409 from deleting a profile skill: the bullets still citing it, so the UI can name them. */
  get blockingBullets(): { id: number; text: string }[] {
    const value = this.problem?.['blockingBullets']
    if (!Array.isArray(value)) return []
    return value.filter(
      (v): v is { id: number; text: string } =>
        typeof v === 'object' && v !== null && typeof (v as { text?: unknown }).text === 'string',
    )
  }

  /** 400 from a per-entity edit: field name to message, for inline form errors. */
  get fieldErrors(): Record<string, string> {
    const value = this.problem?.['fieldErrors']
    if (typeof value !== 'object' || value === null) return {}
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).filter(
        (entry): entry is [string, string] => typeof entry[1] === 'string',
      ),
    )
  }
}

async function send(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  // 204 (GET /api/profile before an import) and the empty 200 from rejecting an unmatched term
  // have no body at all; calling res.json() on those throws.
  const text = response.status === 204 ? '' : await response.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }

  if (!response.ok) {
    const problem = body as ProblemDetail | null
    const adHoc = body as { error?: string } | null
    const detail =
      (typeof problem?.detail === 'string' ? problem.detail : null) ??
      // The analysis/offer/document controllers use a bare {"error": "..."} shape.
      (typeof adHoc?.error === 'string' ? adHoc.error : null) ??
      (typeof body === 'string' && body.length > 0 ? body : null) ??
      response.statusText
    throw new ApiError(response.status, body, `HTTP ${response.status}: ${detail}`)
  }

  return body
}

export function request<T>(path: string, init?: RequestInit): Promise<T> {
  return send(path, init) as Promise<T>
}

/**
 * For endpoints where "not found" is a normal empty state rather than an error: the latest
 * analysis for an offer, the latest document of a type, an unknown offer id.
 */
export async function requestOrNull<T>(path: string, init?: RequestInit): Promise<T | null> {
  try {
    return await request<T>(path, init)
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

export const json = (body: unknown): RequestInit => ({ body: JSON.stringify(body) })

export const query = (params: Record<string, string | number | undefined | null>): string => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) search.set(key, String(value))
  }
  const rendered = search.toString()
  return rendered ? `?${rendered}` : ''
}
