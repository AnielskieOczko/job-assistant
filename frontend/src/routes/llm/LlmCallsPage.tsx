import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { RefreshCw } from 'lucide-react'
import { listLlmCalls } from '@/api/llm'
import { keys } from '@/api/keys'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { formatCallCost, formatDateTime, formatDuration } from '@/lib/format'

export function LlmCallsPage() {
  const [limit, setLimit] = useState(50)
  const calls = useQuery({
    queryKey: keys.llmCalls(limit),
    queryFn: () => listLlmCalls(limit),
    // Cheap, read-only, and the interesting rows appear while an analysis is running.
    refetchInterval: 10_000,
  })

  const rows = calls.data ?? []

  return (
    <>
      <PageHeader
        title="Model calls"
        description="Every call is recorded with its prompt, raw response, token usage, cost and latency. When a generated artifact reads badly, this is how you tell whether the prompt, the model or the profile is at fault."
        actions={
          <div className="flex items-center gap-2">
            <Select value={String(limit)} onValueChange={(v) => setLimit(Number(v))}>
              <SelectTrigger className="w-28"><SelectValue /></SelectTrigger>
              <SelectContent>
                {[20, 50, 200].map((n) => (
                  <SelectItem key={n} value={String(n)}>Last {n}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" onClick={() => calls.refetch()}>
              <RefreshCw className={calls.isFetching ? 'animate-spin' : ''} /> Refresh
            </Button>
          </div>
        }
      />

      {calls.isError ? <ApiErrorAlert error={calls.error} /> : null}
      {calls.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-44">When</TableHead>
                <TableHead className="w-32">Task</TableHead>
                <TableHead>Model</TableHead>
                <TableHead className="w-32 text-right">Tokens in/out</TableHead>
                <TableHead className="w-24 text-right">Cost</TableHead>
                <TableHead className="w-24 text-right">Latency</TableHead>
                <TableHead className="w-20"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((call) => (
                <TableRow key={call.id} className={call.error ? 'bg-red-500/5' : undefined}>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatDateTime(call.createdAt)}
                  </TableCell>
                  <TableCell><Badge variant="outline">{call.task}</Badge></TableCell>
                  <TableCell className="max-w-0 truncate">
                    <span className="font-mono text-xs">{call.modelName ?? '—'}</span>
                    <span className="ml-2 text-xs text-muted-foreground">{call.modelProfile}</span>
                    {/* A router serves one model slug from providers with different capabilities,
                        so this is what makes two identical-looking rows tell different stories. */}
                    {call.servingProvider ? (
                      <span className="ml-2 text-xs text-muted-foreground">
                        via {call.servingProvider}
                      </span>
                    ) : null}
                    {call.error ? (
                      <span className="ml-2 text-xs text-red-600">failed</span>
                    ) : null}
                    {/* STOP is the ordinary ending and saying so on every row would be noise.
                        LENGTH is the one worth seeing: a truncated answer you were charged for. */}
                    {call.finishReason && call.finishReason !== 'STOP' ? (
                      <span className="ml-2 text-xs text-amber-600">{call.finishReason}</span>
                    ) : null}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums text-muted-foreground">
                    {call.inputTokens ?? '—'} / {call.outputTokens ?? '—'}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums">
                    {formatCallCost(call.costUsd)}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums text-muted-foreground">
                    {formatDuration(call.latencyMs)}
                  </TableCell>
                  <TableCell className="text-right">
                    <Link
                      to={`/llm/calls/${call.id}`}
                      className="text-sm underline underline-offset-2 hover:text-foreground"
                    >
                      Open
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="py-10 text-center text-muted-foreground">
                    No model calls recorded yet.
                  </TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </div>
      )}
    </>
  )
}
