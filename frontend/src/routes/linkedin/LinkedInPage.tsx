import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, Copy, Share2, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import { keys } from '@/api/keys'
import { draftLinkedInSection, listLinkedInDrafts } from '@/api/linkedin'
import { getProfile } from '@/api/profile'
import type { LinkedInDraft, LinkedInSection } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import {
  LINKEDIN_SECTIONS,
  formatCharCount,
  sectionLabel,
  sectionNeedsSubject,
} from './format'

export function LinkedInPage() {
  const qc = useQueryClient()
  const { profileId, isLoading: profileLoading } = useSelectedProfile()

  const [section, setSection] = useState<LinkedInSection>('ABOUT')
  const [subjectId, setSubjectId] = useState<string>('')
  const [notes, setNotes] = useState('')
  const [copiedId, setCopiedId] = useState<number | null>(null)

  const profile = useQuery({
    queryKey: keys.profile(profileId ?? -1),
    queryFn: () => getProfile(profileId!),
    enabled: profileId !== null,
  })

  const drafts = useQuery({
    queryKey: keys.linkedinDrafts(profileId ?? -1),
    queryFn: () => listLinkedInDrafts(profileId!),
    enabled: profileId !== null,
  })

  const generate = useMutation({
    mutationFn: () =>
      draftLinkedInSection(profileId!, {
        section,
        subjectId: sectionNeedsSubject(section) && subjectId ? Number(subjectId) : null,
        notes: notes.trim() || null,
      }),
    onSuccess: () => {
      toast.success('LinkedIn draft generated')
      if (profileId !== null) {
        void qc.invalidateQueries({ queryKey: keys.linkedinDrafts(profileId) })
      }
    },
  })

  const copyDraft = async (draft: LinkedInDraft) => {
    await navigator.clipboard.writeText(draft.content)
    setCopiedId(draft.id)
    toast.success('Copied to clipboard')
    setTimeout(() => setCopiedId((prev) => (prev === draft.id ? null : prev)), 2000)
  }

  if (profileLoading) return <Skeleton className="h-64 w-full" />

  if (profileId === null) {
    return (
      <EmptyState
        icon={Share2}
        title="No profile selected"
        description="Select a profile in the sidebar to draft LinkedIn updates."
      />
    )
  }

  const subjects =
    section === 'EXPERIENCE_ENTRY'
      ? (profile.data?.experiences ?? []).map((e) => ({
          id: e.id,
          label: `${e.roleTitle} @ ${e.company}`,
        }))
      : section === 'PROJECT_ENTRY'
        ? (profile.data?.projects ?? []).map((p) => ({
            id: p.id,
            label: p.name,
          }))
        : []

  return (
    <div className="space-y-6">
      <PageHeader
        title="LinkedIn Updates"
        description="Copy-ready drafts grounded in your profile. No LinkedIn API integration — review, copy, and paste directly."
      />

      <Card>
        <CardHeader>
          <CardTitle>Draft a section</CardTitle>
          <CardDescription>
            Choose a LinkedIn profile section and optional guidance notes.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {generate.isError ? <ApiErrorAlert error={generate.error} /> : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Section</Label>
              <Select
                value={section}
                onValueChange={(v) => {
                  setSection(v as LinkedInSection)
                  setSubjectId('')
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {LINKEDIN_SECTIONS.map((s) => (
                    <SelectItem key={s} value={s}>
                      {sectionLabel(s)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {sectionNeedsSubject(section) ? (
              <div className="space-y-1.5">
                <Label>{section === 'EXPERIENCE_ENTRY' ? 'Experience' : 'Project'}</Label>
                <Select value={subjectId} onValueChange={setSubjectId}>
                  <SelectTrigger>
                    <SelectValue placeholder="All / general" />
                  </SelectTrigger>
                  <SelectContent>
                    {subjects.map((sub) => (
                      <SelectItem key={sub.id} value={String(sub.id)}>
                        {sub.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="linkedin-notes">Notes / emphasis (optional)</Label>
            <Textarea
              id="linkedin-notes"
              rows={2}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. Emphasize distributed systems and Kafka reliability"
            />
          </div>

          <Button disabled={generate.isPending} onClick={() => generate.mutate()}>
            <Sparkles className="size-4" />
            {generate.isPending ? 'Drafting…' : 'Generate draft'}
          </Button>
        </CardContent>
      </Card>

      {drafts.isError ? <ApiErrorAlert error={drafts.error} /> : null}

      {drafts.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : (drafts.data ?? []).length === 0 ? (
        <EmptyState
          icon={Share2}
          title="No drafts yet"
          description="Generate your first LinkedIn section draft above."
        />
      ) : (
        <div className="space-y-4">
          {(drafts.data ?? []).map((draft) => (
            <Card key={draft.id}>
              <CardHeader>
                <div className="flex flex-wrap items-center gap-2">
                  <CardTitle className="text-base">{sectionLabel(draft.section)}</CardTitle>
                  <Badge variant="outline">{formatCharCount(draft.content, draft.section)}</Badge>
                  {draft.stale ? (
                    <Badge variant="destructive">Stale (profile updated)</Badge>
                  ) : null}
                </div>
                <CardDescription>
                  Profile rev {draft.profileRevision}
                  {draft.modelProfile ? ` · ${draft.modelProfile}` : ''} ·{' '}
                  {new Date(draft.createdAt).toLocaleString()}
                </CardDescription>
                <CardAction>
                  <Button size="sm" variant="outline" onClick={() => void copyDraft(draft)}>
                    {copiedId === draft.id ? (
                      <>
                        <Check className="size-3.5" /> Copied
                      </>
                    ) : (
                      <>
                        <Copy className="size-3.5" /> Copy
                      </>
                    )}
                  </Button>
                </CardAction>
              </CardHeader>
              <CardContent className="space-y-3">
                <pre className="whitespace-pre-wrap rounded-md bg-muted/50 p-3 font-sans text-sm leading-relaxed">
                  {draft.content}
                </pre>
                {draft.unheldSkills.length > 0 ? (
                  <div className="flex flex-wrap items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400">
                    <AlertTriangle className="size-3.5 shrink-0" />
                    <span>Mentions skills not declared on profile:</span>
                    {draft.unheldSkills.map((name) => (
                      <Badge key={name} variant="outline">
                        {name}
                      </Badge>
                    ))}
                  </div>
                ) : null}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
