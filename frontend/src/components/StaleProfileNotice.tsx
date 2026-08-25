import { useQuery } from '@tanstack/react-query'
import { History } from 'lucide-react'
import { getProfile } from '@/api/profile'
import { keys } from '@/api/keys'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

/**
 * "Your profile has moved on since this was produced."
 *
 * An analysis and a generated document both record the profile revision they were built from. The
 * output is not wrong — it was true when it was produced — but a gap report telling you to learn
 * something you have since added is actively misleading, so it is worth saying.
 *
 * Renders nothing when the revisions agree, when the stored one predates the counter (null), or
 * while the profile is still loading.
 */
export function StaleProfileNotice({
  producedAt,
  what,
  action,
}: {
  producedAt: number | null
  what: string
  action?: { label: string; onClick: () => void; disabled?: boolean }
}) {
  const profile = useQuery({ queryKey: keys.profile, queryFn: getProfile })
  const current = profile.data?.revision

  if (producedAt === null || current === undefined || producedAt >= current) return null

  return (
    <Alert>
      <History />
      <AlertTitle>Your profile changed since this {what} ran</AlertTitle>
      <AlertDescription className="flex flex-wrap items-center gap-3">
        <span>
          It reflects the profile as it was {current - producedAt} edit(s) ago.
        </span>
        {action ? (
          <Button size="sm" variant="outline" onClick={action.onClick} disabled={action.disabled}>
            {action.label}
          </Button>
        ) : null}
      </AlertDescription>
    </Alert>
  )
}
