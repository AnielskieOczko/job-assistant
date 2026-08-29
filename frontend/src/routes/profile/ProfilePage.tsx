import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Upload, UserRound } from 'lucide-react'
import { getProfile } from '@/api/profile'
import { keys } from '@/api/keys'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { CredentialsCard } from './CredentialsCard'
import { DetailsCard } from './DetailsCard'
import { EducationCard } from './EducationCard'
import { ExperienceCard } from './ExperienceCard'
import { ImportDialog } from './ImportDialog'
import { LanguagesCard } from './LanguagesCard'
import { SkillsCard } from './SkillsCard'
import { StartProfileDialog } from './StartProfileDialog'

export function ProfilePage() {
  const [importOpen, setImportOpen] = useState(false)
  const [startOpen, setStartOpen] = useState(false)
  const { profileId, isLoading: profilesLoading } = useSelectedProfile()

  // 204 No Content when this profile has no details yet, so `data` is null rather than a 404 error.
  const profile = useQuery({
    queryKey: keys.profile(profileId ?? -1),
    queryFn: () => getProfile(profileId!),
    enabled: profileId !== null,
  })

  if (profilesLoading) return <Skeleton className="h-64 w-full" />

  if (profileId === null) {
    return (
      <EmptyState
        icon={UserRound}
        title="No profile yet"
        description="Create a persona from the switcher in the sidebar to get started."
      />
    )
  }

  return (
    <>
      <PageHeader
        title="Profile"
        description="Hand-authored ground truth. No model writes to it, and every claim a generated CV makes must trace back to a record here."
        actions={
          profile.data ? (
            <Button variant="outline" onClick={() => setImportOpen(true)}>
              <Upload /> Re-import
            </Button>
          ) : null
        }
      />

      {profile.isError ? <ApiErrorAlert error={profile.error} /> : null}

      {profile.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : !profile.data ? (
        <EmptyState
          icon={UserRound}
          title="No details yet"
          description="Fill in the basics here, or import a document you already have. Either way the gap report needs it before it can analyse an offer."
          action={
            <div className="flex gap-2">
              <Button onClick={() => setStartOpen(true)}>Fill in details</Button>
              <Button variant="outline" onClick={() => setImportOpen(true)}>
                <Upload /> Import JSON
              </Button>
            </div>
          }
        />
      ) : (
        <div className="space-y-6">
          <DetailsCard profileId={profileId} profile={profile.data} />
          <SkillsCard profileId={profileId} profile={profile.data} />
          <ExperienceCard profileId={profileId} profile={profile.data} />
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            <EducationCard profileId={profileId} profile={profile.data} />
            <CredentialsCard profileId={profileId} profile={profile.data} />
            <LanguagesCard profileId={profileId} profile={profile.data} />
          </div>
        </div>
      )}

      <StartProfileDialog profileId={profileId} open={startOpen} onOpenChange={setStartOpen} />
      <ImportDialog profileId={profileId} open={importOpen} onOpenChange={setImportOpen} current={profile.data ?? null} />
    </>
  )
}
