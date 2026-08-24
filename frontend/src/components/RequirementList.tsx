import { Link } from 'react-router'
import { ChevronDown } from 'lucide-react'
import type { RequirementFinding, RequirementStatus } from '@/api/types'
import { RequirementStatusBadge } from '@/components/StatusBadges'
import {
  Collapsible, CollapsibleContent, CollapsibleTrigger,
} from '@/components/ui/collapsible'

export function RequirementSummaryStrip({ items }: { items: RequirementFinding[] }) {
  const count = (status: RequirementStatus) => items.filter((r) => r.status === status).length
  return (
    <p className="text-sm text-muted-foreground">
      {items.length} requirement{items.length === 1 ? '' : 's'} · {count('MET')} met ·{' '}
      {count('PARTIAL')} partial · {count('MISSING')} missing · {count('UNRESOLVED')} unresolved
    </p>
  )
}

export function RequirementList({ items }: { items: RequirementFinding[] }) {
  if (items.length === 0) {
    return <p className="py-4 text-sm text-muted-foreground">None.</p>
  }

  return (
    <ul className="divide-y rounded-lg border">
      {items.map((item) => (
        <RequirementRow key={item.id} item={item} />
      ))}
    </ul>
  )
}

function RequirementRow({ item }: { item: RequirementFinding }) {
  const hasDetail = Boolean(item.evidence || item.rationale || item.skillName)

  return (
    <li>
      <Collapsible>
        <div className="flex items-start gap-3 px-4 py-3">
          <RequirementStatusBadge status={item.status} className="mt-0.5 shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="font-medium">{item.skillName ?? item.rawText}</p>
            {item.skillName && item.skillName !== item.rawText ? (
              <p className="mt-0.5 truncate text-sm text-muted-foreground">“{item.rawText}”</p>
            ) : null}
            {item.status === 'UNRESOLVED' ? (
              <p className="mt-1.5 text-sm text-muted-foreground">
                The catalog could not place this phrase — a gap in the catalog, not necessarily in
                you.{' '}
                <Link to="/catalog" className="underline underline-offset-2 hover:text-foreground">
                  Review the queue
                </Link>
                .
              </p>
            ) : null}
          </div>
          {hasDetail ? (
            <CollapsibleTrigger className="group shrink-0 rounded p-1 text-muted-foreground hover:text-foreground">
              <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" />
            </CollapsibleTrigger>
          ) : null}
        </div>

        <CollapsibleContent>
          <dl className="space-y-2 border-t bg-muted/30 px-4 py-3 text-sm">
            {item.evidence ? (
              <div>
                <dt className="font-medium">Evidence</dt>
                <dd className="text-muted-foreground">{item.evidence}</dd>
              </div>
            ) : null}
            {item.rationale ? (
              <div>
                <dt className="font-medium">Rationale</dt>
                <dd className="text-muted-foreground">{item.rationale}</dd>
              </div>
            ) : null}
            <div>
              <dt className="font-medium">As written in the offer</dt>
              <dd className="text-muted-foreground">“{item.rawText}”</dd>
            </div>
          </dl>
        </CollapsibleContent>
      </Collapsible>
    </li>
  )
}
