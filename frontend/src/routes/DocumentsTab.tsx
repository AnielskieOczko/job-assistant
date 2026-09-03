import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  Check, Download, FileText, RefreshCw, Send, ShieldAlert, ShieldCheck, SquareArrowOutUpRight,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  documentHtmlUrl, documentPdfUrl, generateDocument, getLatestDocument, markDocumentSent,
  unmarkDocumentSent,
} from '@/api/documents'
import { ApiError } from '@/api/http'
import { keys } from '@/api/keys'
import { listOffers } from '@/api/offers'
import { getProfile } from '@/api/profile'
import type { Application, DocumentType, GeneratedDocument } from '@/api/types'
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

  // Which document was sent is a fact about the application, and the application record only comes
  // back on the list endpoint - the same reason OfferOverviewTab reads it from here.
  const offers = useQuery({ queryKey: keys.offers, queryFn: listOffers })
  const application = offers.data?.find((row) => row.offer.id === offerId)?.application

  // Only a CV carries a consent clause - see issue #52. Fetched here rather than lifted to
  // DocumentsTab because only this panel needs it, and ProfilePage already owns the cache entry.
  const profile = useQuery({
    queryKey: keys.profile(profileId ?? -1),
    queryFn: () => getProfile(profileId!),
    enabled: type === 'CV' && profileId !== null,
  })
  const hasConsentClauseForLanguage = profile.data?.consentClauses.some(
    (clause) => clause.language.toLowerCase() === language.toLowerCase(),
  )

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
        {type === 'CV' && profile.isSuccess && hasConsentClauseForLanguage === false ? (
          <p className="text-xs text-muted-foreground">
            No {language} consent clause on this profile — the CV will render without one.{' '}
            <Link to="/profile" className="underline underline-offset-2">Add one</Link>.
          </p>
        ) : null}
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
              <DiscardedChoices doc={doc} />
              {doc.type === 'CV' && doc.consentClauseLanguage === null ? (
                <Badge
                  variant="outline"
                  className="border-amber-500/40 text-amber-700 dark:text-amber-400"
                  title={`No ${doc.language} consent clause was found on the profile, so this CV rendered without one.`}
                >
                  No consent clause
                </Badge>
              ) : null}
              <span className="ml-auto flex gap-2">
                <SentControl doc={doc} application={application} />
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
 * Which document actually went to the employer.
 *
 * Deliberately manual and deliberately independent of the application's status: a document can be
 * generated and never sent, and an application can be sent by hand with no document to name. It is
 * recorded rather than derived because an application already sent cannot be reconstructed
 * afterwards — the link has to be captured at the time or it does not exist at all.
 */
function SentControl({
  doc,
  application,
}: {
  doc: GeneratedDocument
  application: Application | undefined
}) {
  const offerId = useOfferId()
  const queryClient = useQueryClient()

  const label = doc.type === 'CV' ? 'CV' : 'cover letter'
  const sentId = doc.type === 'CV'
    ? application?.sentCvDocumentId
    : application?.sentCoverLetterDocumentId
  const isThisOne = sentId === doc.id

  const mark = useMutation({
    mutationFn: () =>
      isThisOne ? unmarkDocumentSent(offerId, doc.type) : markDocumentSent(offerId, doc.id),
    onSuccess: () => {
      toast.success(isThisOne ? `No longer recorded as sent` : `Recorded as the ${label} you sent`)
      queryClient.invalidateQueries({ queryKey: keys.offers })
    },
  })

  if (!application) return null

  return (
    <>
      {/*
        An earlier document holds the slot. Said out loud rather than silently overwritten: the
        one on screen is not what the employer read.
      */}
      {sentId != null && !isThisOne ? (
        <span className="self-center text-amber-700 dark:text-amber-400">
          An earlier {label} is the one recorded as sent.
        </span>
      ) : null}
      <Button
        size="sm"
        variant={isThisOne ? 'secondary' : 'outline'}
        onClick={() => mark.mutate()}
        disabled={mark.isPending}
        title={
          isThisOne
            ? `Recorded as the ${label} you sent for this offer. Click to undo.`
            : `Record this as the ${label} you sent. It does not change the application's status.`
        }
      >
        {isThisOne ? <><Check /> Sent</> : <><Send /> Mark as sent</>}
      </Button>
    </>
  )
}

/**
 * How much of what the model asked for had nothing behind it.
 *
 * Deliberately not an error: these choices were discarded before rendering, so the document on
 * screen is fully backed by the profile. It is shown because the rate is worth watching — if it
 * starts climbing after a prompt or model change, tailoring has begun guessing, and this is the
 * only place that shows up on real offers rather than on test fixtures.
 */
function DiscardedChoices({ doc }: { doc: GeneratedDocument }) {
  const { droppedBulletCount: bullets, droppedSkillCount: skills } = doc
  if (bullets === 0 && skills === 0) return null

  const parts = [
    bullets > 0 ? `${bullets} bullet${bullets === 1 ? '' : 's'}` : null,
    skills > 0 ? `${skills} skill${skills === 1 ? '' : 's'}` : null,
  ].filter(Boolean)

  return (
    <Badge
      variant="outline"
      className="gap-1 border-amber-500/40 text-amber-700 dark:text-amber-400"
      title={
        'The model asked for these and the profile could not back them, so they were left out. ' +
        'The document is sound; a rising count means tailoring is guessing more than it used to.'
      }
    >
      <ShieldCheck className="size-3" />
      {parts.join(' and ')} discarded
    </Badge>
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
