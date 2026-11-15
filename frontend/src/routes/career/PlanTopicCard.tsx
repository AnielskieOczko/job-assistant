import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { PlanTopic, SkillProgressState } from '@/api/types'
import { formatSeniority, nextProgressState, progressLabel } from './format'

const PROGRESS_BADGE: Record<SkillProgressState, string> = {
  NOT_STARTED: 'border-foreground/20 text-foreground/60',
  LEARNING: 'border-foreground/50 text-foreground',
  READY_TO_DECLARE: 'border-transparent bg-foreground text-background',
}

export function PlanTopicCard({
  topic,
  updating,
  onToggleProgress,
}: {
  topic: PlanTopic
  updating: boolean
  onToggleProgress: (skillId: number, next: SkillProgressState) => void
}) {
  const next = nextProgressState(topic.progressState)

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">#{topic.priority}</Badge>
          <CardTitle className="text-base">{topic.skillName}</CardTitle>
          <Badge variant="outline" className={PROGRESS_BADGE[topic.progressState]}>
            {progressLabel(topic.progressState)}
          </Badge>
        </div>
        <CardDescription>
          Asked in {topic.demandOffers} offers ({topic.demandRequiredOffers} required) ·{' '}
          {formatSeniority(topic.targetLevel)}
          {topic.effortEstimate ? ` · Est. ${topic.effortEstimate}` : ''}
        </CardDescription>
        <CardAction>
          <Button
            size="sm"
            variant="outline"
            disabled={updating}
            onClick={() => onToggleProgress(topic.skillId, next)}
          >
            Mark: {progressLabel(next)}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2 text-sm">
        <p>{topic.why}</p>
        {topic.practiceProject ? (
          <p className="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
            <span className="font-medium text-foreground">Practice project:</span>{' '}
            {topic.practiceProject}
          </p>
        ) : null}
      </CardContent>
    </Card>
  )
}
