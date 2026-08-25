import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Upload, UserRound } from 'lucide-react'
import { getProfile } from '@/api/profile'
import { keys } from '@/api/keys'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
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

  // 204 No Content when there is no profile, so `data` is null rather than a 404 error.
  const profile = useQuery({ queryKey: keys.profile, queryFn: getProfile })

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
          title="No profile yet"
          description="Start one here, or import a document you already have. Either way the gap report needs it before it can analyse an offer."
          action={
            <div className="flex gap-2">
              <Button onClick={() => setStartOpen(true)}>Create profile</Button>
              <Button variant="outline" onClick={() => setImportOpen(true)}>
                <Upload /> Import JSON
              </Button>
            </div>
          }
        />
      ) : (
        <div className="space-y-6">
          <DetailsCard profile={profile.data} />
          <SkillsCard profile={profile.data} />
          <ExperienceCard profile={profile.data} />
          <div className="grid gap-6 md:grid-cols-2">
            <EducationCard profile={profile.data} />
            <LanguagesCard profile={profile.data} />
          </div>
        </div>
      )}

      <StartProfileDialog open={startOpen} onOpenChange={setStartOpen} />
      <ImportDialog open={importOpen} onOpenChange={setImportOpen} current={profile.data ?? null} />
    </>
  )
}
