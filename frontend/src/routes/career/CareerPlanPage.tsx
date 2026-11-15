import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Compass, Sparkles } from 'lucide-react'
import { toast } from 'sonner'
import {
  fetchCareerPlanById,
  fetchCareerPlanHistory,
  fetchCurrentCareerPlan,
  generateCareerPlan,
  updateSkillProgress,
} from '@/api/career'
import { keys } from '@/api/keys'
import type { SkillProgressState } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import { PlanHistory } from './PlanHistory'
import { PlanTopicCard } from './PlanTopicCard'
import { TargetRoleCard } from './TargetRoleCard'
import { countReadyTopics, formatSeniority } from './format'

export function CareerPlanPage() {
  const qc = useQueryClient()
  const { profileId, isLoading: profileLoading } = useSelectedProfile()
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)

  const currentPlan = useQuery({
    queryKey: keys.careerPlan(profileId ?? -1),
    queryFn: () => fetchCurrentCareerPlan(profileId ?? undefined),
    enabled: profileId !== null,
  })

  const selectedPlan = useQuery({
    queryKey: keys.careerPlanDetail(selectedPlanId ?? -1),
    queryFn: () => fetchCareerPlanById(selectedPlanId!),
    enabled: selectedPlanId !== null,
  })

  const history = useQuery({
    queryKey: keys.careerHistory(profileId ?? -1),
    queryFn: () => fetchCareerPlanHistory(profileId ?? undefined),
    enabled: profileId !== null,
  })

  const generate = useMutation({
    mutationFn: () => generateCareerPlan(profileId ?? undefined),
    onSuccess: (created) => {
      toast.success('Career plan generated')
      setSelectedPlanId(created.id)
      if (profileId !== null) {
        void qc.invalidateQueries({ queryKey: keys.careerPlan(profileId) })
        void qc.invalidateQueries({ queryKey: keys.careerHistory(profileId) })
      }
    },
  })

  const toggleProgress = useMutation({
    mutationFn: ({ skillId, state }: { skillId: number; state: SkillProgressState }) =>
      updateSkillProgress(skillId, { state }, profileId ?? undefined),
    onSuccess: () => {
      if (profileId !== null) {
        void qc.invalidateQueries({ queryKey: keys.careerPlan(profileId) })
      }
      if (selectedPlanId !== null) {
        void qc.invalidateQueries({ queryKey: keys.careerPlanDetail(selectedPlanId) })
      }
    },
  })

  if (profileLoading) return <Skeleton className="h-64 w-full" />

  if (profileId === null) {
    return (
      <EmptyState
        icon={Compass}
        title="No profile selected"
        description="Create or select a profile in the sidebar to plan your target role."
      />
    )
  }

  const activePlan = selectedPlanId !== null ? selectedPlan.data : currentPlan.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Career Plan"
        description="Evidence-bounded learning plan grounded in unmet demand for your target role. Progress is tracked separately and never writes to your profile."
        actions={
          <Button disabled={generate.isPending} onClick={() => generate.mutate()}>
            <Sparkles className="size-4" />
            {generate.isPending
              ? 'Generating…'
              : currentPlan.data
                ? 'Regenerate plan'
                : 'Generate plan'}
          </Button>
        }
      />

      <TargetRoleCard profileId={profileId} />

      {generate.isError ? <ApiErrorAlert error={generate.error} /> : null}
      {currentPlan.isError ? <ApiErrorAlert error={currentPlan.error} /> : null}
      {toggleProgress.isError ? <ApiErrorAlert error={toggleProgress.error} /> : null}

      {currentPlan.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : activePlan ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              {activePlan.targetLevel ? (
                <Badge variant="outline">{formatSeniority(activePlan.targetLevel)}</Badge>
              ) : null}
              <span className="font-medium">
                {activePlan.targetSkills.length > 0
                  ? activePlan.targetSkills.join(', ')
                  : 'Default scope'}
              </span>
              <span className="text-muted-foreground">
                {activePlan.matchedOfferCount}/{activePlan.corpusOfferCount} offers ·{' '}
                {countReadyTopics(activePlan)}/{activePlan.topics.length} ready to declare
                {activePlan.modelProfile ? ` · model: ${activePlan.modelProfile}` : ''}
              </span>
            </div>
          </div>

          <div className="space-y-3">
            {activePlan.topics.map((topic) => (
              <PlanTopicCard
                key={topic.skillId}
                topic={topic}
                updating={toggleProgress.isPending}
                onToggleProgress={(skillId, state) => toggleProgress.mutate({ skillId, state })}
              />
            ))}
          </div>
        </div>
      ) : (
        <EmptyState
          icon={Compass}
          title="No career plan yet"
          description="Set your target role above and generate a prioritized learning plan from unmet market demand."
        />
      )}

      <PlanHistory
        history={history.data ?? []}
        activePlanId={activePlan?.id ?? null}
        onSelectPlan={(id) => setSelectedPlanId(id)}
      />
    </div>
  )
}
