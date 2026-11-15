import { History } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { CareerPlanSummary } from '@/api/types'
import { formatSeniority } from './format'

export function PlanHistory({
  history,
  activePlanId,
  onSelectPlan,
}: {
  history: CareerPlanSummary[]
  activePlanId: number | null
  onSelectPlan: (planId: number) => void
}) {
  if (history.length === 0) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <History className="size-4" />
          Plan History
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {history.map((item) => (
          <div
            key={item.id}
            className="flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2 text-sm"
          >
            <div className="flex flex-wrap items-center gap-2">
              {item.targetLevel ? (
                <Badge variant="outline">{formatSeniority(item.targetLevel)}</Badge>
              ) : null}
              <span className="font-medium">
                {item.targetSkills.length > 0 ? item.targetSkills.join(', ') : 'Default scope'}
              </span>
              <span className="text-xs text-muted-foreground">
                {item.matchedOfferCount}/{item.corpusOfferCount} offers ·{' '}
                {new Date(item.createdAt).toLocaleDateString()}
              </span>
            </div>
            <Button
              size="sm"
              variant={activePlanId === item.id ? 'secondary' : 'ghost'}
              onClick={() => onSelectPlan(item.id)}
            >
              {activePlanId === item.id ? 'Viewing' : 'View'}
            </Button>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
