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
import { formatCallCost, formatDateTime, formatDuration } from '@/lib/format'

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
            {/* A router serves one model slug from providers with different capabilities, so this
                is what makes two identical-looking calls tell different stories. */}
            {meta.servingProvider ? <span>· via {meta.servingProvider}</span> : null}
            <span>· {formatDateTime(meta.createdAt)}</span>
            <span>· {formatDuration(meta.latencyMs)}</span>
            <span>· {meta.inputTokens ?? '—'} in / {meta.outputTokens ?? '—'} out</span>
            {/* Already inside the output count and already paid for, but nowhere in the text
                below — so it is only visible if it is said. */}
            {meta.reasoningOutputTokens ? (
              <span>· {meta.reasoningOutputTokens.toLocaleString()} reasoning</span>
            ) : null}
            {meta.cachedInputTokens ? (
              <span>· {meta.cachedInputTokens.toLocaleString()} cached</span>
            ) : null}
            <span>· {formatCallCost(meta.costUsd)}</span>
            {meta.finishReason && meta.finishReason !== 'STOP' ? (
              <Badge variant="outline" className="text-amber-600">{meta.finishReason}</Badge>
            ) : null}
            {/* The join key to the provider's own billing dashboard, so this row can be matched
                against a line on the invoice rather than inferred from a timestamp. */}
            {meta.providerCallId ? (
              <span className="font-mono text-xs select-all">· {meta.providerCallId}</span>
            ) : null}
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
