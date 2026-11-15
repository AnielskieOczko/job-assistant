import { Plus } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { PlanTopic, SkillProgressState } from '@/api/types'
import { canDeclareToProfile, formatSeniority, nextProgressState, progressLabel } from './format'

const PROGRESS_BADGE: Record<SkillProgressState, string> = {
  NOT_STARTED: 'border-foreground/20 text-foreground/60',
  LEARNING: 'border-foreground/50 text-foreground',
  READY_TO_DECLARE: 'border-transparent bg-foreground text-background',
}

export function PlanTopicCard({
  topic,
  heldSkillIds,
  updating,
  onToggleProgress,
  onDeclareSkill,
}: {
  topic: PlanTopic
  heldSkillIds: Set<number>
  updating: boolean
  onToggleProgress: (skillId: number, next: SkillProgressState) => void
  onDeclareSkill: (skillId: number) => void
}) {
  const next = nextProgressState(topic.progressState)
  const declarable = canDeclareToProfile(topic, heldSkillIds)

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
        <CardAction className="flex flex-wrap gap-2">
          {declarable ? (
            <Button size="sm" onClick={() => onDeclareSkill(topic.skillId)}>
              <Plus className="size-3.5" /> Add to Profile
            </Button>
          ) : null}
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
