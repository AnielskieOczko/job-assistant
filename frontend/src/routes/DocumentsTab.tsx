import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Download, FileText, RefreshCw, ShieldAlert, SquareArrowOutUpRight } from 'lucide-react'
import { toast } from 'sonner'
import {
  documentHtmlUrl, documentPdfUrl, generateDocument, getLatestDocument,
} from '@/api/documents'
import { ApiError } from '@/api/http'
import { keys } from '@/api/keys'
import type { DocumentType } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { StaleProfileNotice } from '@/components/StaleProfileNotice'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useOfferId } from '@/hooks/useOfferId'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'

const LANGUAGES = ['English', 'Polish', 'German', 'Spanish', 'French']

export function DocumentsTab() {
  const [compare, setCompare] = useState(false)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Generated from your profile and the completed analysis. A tailored CV may reorder and
          rephrase what you have — it can never introduce a technology you do not.
        </p>
        <Button variant="outline" size="sm" onClick={() => setCompare((v) => !v)}>
          {compare ? 'Stack' : 'Compare side by side'}
        </Button>
      </div>

      <div className={cn('grid gap-6', compare ? 'xl:grid-cols-2' : 'grid-cols-1')}>
        <DocumentPanel type="CV" title="CV" />
        <DocumentPanel type="COVER_LETTER" title="Cover letter" />
      </div>
    </div>
  )
}

function DocumentPanel({ type, title }: { type: DocumentType; title: string }) {
  const offerId = useOfferId()
  const queryClient = useQueryClient()
  const [language, setLanguage] = useState('English')
  const { profileId } = useSelectedProfile()

  const latest = useQuery({
    queryKey: keys.latestDocument(offerId, type, profileId ?? -1),
    queryFn: () => getLatestDocument(offerId, type, profileId!),
    enabled: profileId !== null,
  })

  const generate = useMutation({
    mutationFn: () => generateDocument(offerId, profileId!, type, language),
    onSuccess: () => {
      toast.success(`${title} generated`)
      queryClient.invalidateQueries({ queryKey: keys.latestDocument(offerId, type, profileId!) })
      queryClient.invalidateQueries({ queryKey: ['llm'] })
    },
  })

  const error = generate.error instanceof ApiError ? generate.error : null
  const doc = latest.data
  const busy = generate.isPending || profileId === null

  return (
    <Card className="min-w-0">
      <CardHeader className="flex-row items-center justify-between gap-3 space-y-0">
        <CardTitle className="text-base">{title}</CardTitle>
        <div className="flex items-center gap-2">
          <Label htmlFor={`lang-${type}`} className="text-xs text-muted-foreground">
            Language
          </Label>
          <Select value={language} onValueChange={setLanguage}>
            <SelectTrigger id={`lang-${type}`} size="sm" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LANGUAGES.map((l) => <SelectItem key={l} value={l}>{l}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button size="sm" onClick={() => generate.mutate()} disabled={busy}>
            {generate.isPending ? (
              <><RefreshCw className="animate-spin" /> Tailoring…</>
            ) : doc ? (
              <><RefreshCw /> Regenerate</>
            ) : (
              <><FileText /> Generate</>
            )}
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {error?.status === 422 ? <FabricatedClaims error={error} /> : null}
        {error?.status === 409 ? <NeedsAnalysis /> : null}
        {generate.isError && error?.status !== 422 && error?.status !== 409 ? (
          <ApiErrorAlert error={generate.error} />
        ) : null}

        {latest.isPending ? (
          <Skeleton className="h-[500px] w-full" />
        ) : !doc ? (
          <div className="rounded-lg border border-dashed py-14 text-center text-sm text-muted-foreground">
            No {title.toLowerCase()} generated yet.
          </div>
        ) : (
          <>
            <StaleProfileNotice
              profileId={profileId!}
              producedAt={doc.profileRevision}
              what="document"
              action={{
                label: 'Regenerate',
                onClick: () => generate.mutate(),
                disabled: busy,
              }}
            />
            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <Badge variant="secondary">{doc.language}</Badge>
              <span>Generated {formatDateTime(doc.createdAt)}</span>
              <span className="ml-auto flex gap-2">
                <Button asChild size="sm" variant="outline">
                  <a href={documentPdfUrl(doc.id)} target="_blank" rel="noreferrer">
                    <SquareArrowOutUpRight /> Open PDF
                  </a>
                </Button>
                <Button asChild size="sm" variant="outline">
                  <a href={documentPdfUrl(doc.id)} download>
                    <Download /> Download
                  </a>
                </Button>
              </span>
            </div>

            {/*
              Same-origin in dev (through the Vite proxy) and in production, so the iframe just
              works. Keyed on the document id so regenerating forces a reload.
            */}
            <iframe
              key={doc.id}
              src={documentHtmlUrl(doc.id)}
              title={`${title} preview`}
              className="h-[900px] w-full rounded-md border bg-white"
            />

            <p className="text-xs text-muted-foreground">
              The preview is byte-for-byte the markup Chromium turns into the PDF. The first PDF
              render downloads Chromium and can take several minutes; renders are also limited to
              two at a time.
            </p>
          </>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * The invariant that justifies the whole tool: the model tried to put a technology on the page
 * that the profile does not contain, so nothing was stored.
 */
function FabricatedClaims({ error }: { error: ApiError }) {
  const claims = error.fabricatedClaims
  const detail = typeof error.problem?.detail === 'string' ? error.problem.detail : null

  return (
    <Alert variant="destructive">
      <ShieldAlert />
      <AlertTitle>Generated document rejected</AlertTitle>
      <AlertDescription className="space-y-3">
        {detail ? <p>{detail}</p> : null}
        {claims.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {claims.map((claim) => (
              <Badge key={claim} variant="destructive">{claim}</Badge>
            ))}
          </div>
        ) : null}
        <p>
          Nothing was saved. Regenerate, or{' '}
          <Link to="/profile" className="underline underline-offset-2">add the skill to your profile</Link>{' '}
          if you genuinely have it.
        </p>
      </AlertDescription>
    </Alert>
  )
}

function NeedsAnalysis() {
  return (
    <Alert>
      <FileText />
      <AlertTitle>No completed analysis yet</AlertTitle>
      <AlertDescription>
        <p>A document is tailored to an analysis, so one has to have finished first.</p>
        <Button asChild size="sm" className="mt-2">
          <Link to="../analysis">Go to Analysis</Link>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
