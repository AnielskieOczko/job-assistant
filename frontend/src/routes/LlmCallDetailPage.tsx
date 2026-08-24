import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ArrowLeft } from 'lucide-react'
import { getLlmCall } from '@/api/llm'
import { keys } from '@/api/keys'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { CopyButton } from '@/components/CopyButton'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime, formatDuration } from '@/lib/format'

/** Pretty-print when it parses, show it raw when it does not — a malformed body is the finding. */
function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

export function LlmCallDetailPage() {
  const { callId } = useParams()
  const id = Number(callId)
  const call = useQuery({ queryKey: keys.llmCall(id), queryFn: () => getLlmCall(id) })

  if (call.isPending) return <Skeleton className="h-96 w-full" />
  if (call.isError) return <ApiErrorAlert error={call.error} />

  const { call: meta, requestJson, responseText } = call.data

  return (
    <>
      <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2">
        <Link to="/llm"><ArrowLeft /> All model calls</Link>
      </Button>

      <PageHeader
        title={`Call ${meta.id}`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{meta.task}</Badge>
            <span className="font-mono text-xs">{meta.modelName ?? '—'}</span>
            <span>· {meta.modelProfile}</span>
            <span>· {formatDateTime(meta.createdAt)}</span>
            <span>· {formatDuration(meta.latencyMs)}</span>
            <span>· {meta.inputTokens ?? '—'} in / {meta.outputTokens ?? '—'} out</span>
          </span>
        }
      />

      {meta.error ? (
        <div className="mb-6"><ApiErrorAlert error={new Error(meta.error)} title="This call failed" /></div>
      ) : null}

      <div className="space-y-6">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">Request</CardTitle>
            <CopyButton value={requestJson} />
          </CardHeader>
          <CardContent>
            <pre className="max-h-[36rem] overflow-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-4 font-mono text-xs leading-relaxed">
              {prettyJson(requestJson)}
            </pre>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">Raw response</CardTitle>
            {responseText ? <CopyButton value={responseText} /> : null}
          </CardHeader>
          <CardContent>
            <pre className="max-h-[36rem] overflow-auto whitespace-pre-wrap rounded-md border bg-muted/40 p-4 font-mono text-xs leading-relaxed">
              {responseText ?? 'No response was recorded.'}
            </pre>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
