import { Eye, EyeOff, Lock } from 'lucide-react'
import { usePrivacyManifest } from '@/hooks/usePrivacyManifest'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { PrivacyState } from '@/api/types'

/**
 * Icon shape carries the state, not color - this app is deliberately near-monochrome, and a
 * red/yellow/green privacy badge would be the one place that broke it while also being the
 * easiest one to misread as a severity rating rather than a description of a mechanism.
 */
const ICONS: Record<PrivacyState, typeof Lock> = {
  ENFORCED: Lock,
  OMITTED: EyeOff,
  SENT: Eye,
}

const STATE_LABELS: Record<PrivacyState, string> = {
  ENFORCED: 'Never sent — enforced',
  OMITTED: 'Never sent — by construction',
  SENT: 'Sent to the model',
}

/**
 * One field's privacy badge: an icon plus a popover naming the mechanism, backed by
 * `GET /api/privacy/manifest` rather than a hardcoded claim. If the manifest hasn't loaded or
 * names no such field, nothing renders — an absent badge is honest; a wrong one is not.
 */
export function PrivacyIndicator({ field }: { field: string }) {
  const manifest = usePrivacyManifest()
  const entry = manifest.byName.get(field)
  if (!entry) return null

  const Icon = ICONS[entry.state]

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex text-muted-foreground/70 hover:text-foreground"
          aria-label={`Privacy: ${entry.label}`}
        >
          <Icon className="size-3.5" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-64 text-sm">
        <p className="font-medium">{entry.label}</p>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {STATE_LABELS[entry.state]}
        </p>
        <p className="text-muted-foreground">{entry.mechanism}</p>
      </PopoverContent>
    </Popover>
  )
}

/** Several badges side by side, for a card whose title covers more than one manifest field. */
export function PrivacyIndicatorGroup({ fields }: { fields: string[] }) {
  return (
    <span className="inline-flex items-center gap-1">
      {fields.map((field) => <PrivacyIndicator key={field} field={field} />)}
    </span>
  )
}
