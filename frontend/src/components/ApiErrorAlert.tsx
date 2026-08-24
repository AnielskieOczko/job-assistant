import { AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { ApiError } from '@/api/http'

/**
 * Renders any failure from the API.
 *
 * Exception handlers in this backend are per-controller rather than a @ControllerAdvice, so the
 * catalog and llm controllers have none: an invalid id there surfaces as a bare 500 with no
 * ProblemDetail. This must degrade to the raw body instead of rendering nothing.
 */
export function ApiErrorAlert({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null

  const apiError = error instanceof ApiError ? error : null
  const problem = apiError?.problem
  const heading =
    title ??
    (typeof problem?.title === 'string' ? problem.title : null) ??
    (apiError ? `Request failed (HTTP ${apiError.status})` : 'Something went wrong')

  const detail =
    (typeof problem?.detail === 'string' ? problem.detail : null) ??
    apiError?.rawBody ??
    (error instanceof Error ? error.message : String(error))

  return (
    <Alert variant="destructive">
      <AlertTriangle />
      <AlertTitle>{heading}</AlertTitle>
      <AlertDescription>
        <p className="whitespace-pre-wrap">{detail}</p>
      </AlertDescription>
    </Alert>
  )
}
