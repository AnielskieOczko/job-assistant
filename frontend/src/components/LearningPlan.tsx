import type { LearningPlanItem } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'

export function LearningPlan({ items }: { items: LearningPlanItem[] }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">No learning plan was produced.</p>
  }

  const ordered = [...items].sort((a, b) => a.priority - b.priority)

  return (
    <div className="grid gap-3 md:grid-cols-2">
      {ordered.map((item, index) => (
        <Card key={`${item.skillName}-${index}`}>
          <CardContent className="space-y-2.5 pt-5">
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="font-mono text-xs">
                {item.priority}
              </Badge>
              <p className="font-medium">{item.skillName}</p>
              {item.effortEstimate ? (
                <Badge variant="secondary" className="ml-auto">{item.effortEstimate}</Badge>
              ) : null}
            </div>
            <p className="text-sm text-muted-foreground">{item.why}</p>
            {item.practiceProject ? (
              <div className="rounded-md border bg-muted/40 p-3">
                <p className="text-xs font-medium text-muted-foreground">Practice project</p>
                <p className="mt-1 text-sm">{item.practiceProject}</p>
              </div>
            ) : null}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
