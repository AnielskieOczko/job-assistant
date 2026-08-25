import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { ApplicationStatus, Importance, RequirementStatus } from '@/api/types'

const REQUIREMENT_STYLES: Record<RequirementStatus, string> = {
  MET: 'border-emerald-600/30 bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
  PARTIAL: 'border-amber-600/30 bg-amber-500/12 text-amber-700 dark:text-amber-300',
  MISSING: 'border-red-600/30 bg-red-500/12 text-red-700 dark:text-red-300',
  // Deliberately neutral: UNRESOLVED is a gap in the catalog, not a verdict on the candidate.
  UNRESOLVED: 'border-border bg-muted text-muted-foreground',
}

const REQUIREMENT_LABELS: Record<RequirementStatus, string> = {
  MET: 'Met',
  PARTIAL: 'Partial',
  MISSING: 'Missing',
  UNRESOLVED: 'Unresolved',
}

export function RequirementStatusBadge({
  status,
  className,
}: {
  status: RequirementStatus
  className?: string
}) {
  return (
    <Badge variant="outline" className={cn(REQUIREMENT_STYLES[status], 'font-medium', className)}>
      {REQUIREMENT_LABELS[status]}
    </Badge>
  )
}

export function ImportanceBadge({ importance }: { importance: Importance }) {
  return (
    <Badge variant="outline" className="text-muted-foreground">
      {importance === 'MUST_HAVE' ? 'Must have' : 'Nice to have'}
    </Badge>
  )
}

const APPLICATION_STYLES: Record<ApplicationStatus, string> = {
  SAVED: 'border-border bg-muted text-muted-foreground',
  ANALYZED: 'border-sky-600/30 bg-sky-500/12 text-sky-700 dark:text-sky-300',
  APPLIED: 'border-violet-600/30 bg-violet-500/12 text-violet-700 dark:text-violet-300',
  INTERVIEWING: 'border-amber-600/30 bg-amber-500/12 text-amber-700 dark:text-amber-300',
  REJECTED: 'border-red-600/30 bg-red-500/12 text-red-700 dark:text-red-300',
  OFFER: 'border-emerald-600/30 bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
}

const APPLICATION_LABELS: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  ANALYZED: 'Analyzed',
  APPLIED: 'Applied',
  INTERVIEWING: 'Interviewing',
  REJECTED: 'Rejected',
  OFFER: 'Offer',
}

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <Badge variant="outline" className={cn(APPLICATION_STYLES[status], 'font-medium')}>
      {APPLICATION_LABELS[status]}
    </Badge>
  )
}
