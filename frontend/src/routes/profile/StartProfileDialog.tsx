import { useState } from 'react'
import { putDetails } from '@/api/profile'
import type { DetailsRequest } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Field } from './Field'
import { blankToNull, useProfileEdit } from './mutations'

/**
 * Filling in the details of a profile that already exists as a persona (created via the switcher)
 * but has nothing recorded yet.
 *
 * `PUT /api/profiles/{id}/details` upserts this profile's one details row, so a name is all it
 * takes to have something to build on - which is the point: before this, a fresh persona left you
 * hand-writing a JSON document before you could record anything at all.
 */
export function StartProfileDialog({
  profileId,
  open,
  onOpenChange,
}: {
  profileId: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [fullName, setFullName] = useState('')
  const [headline, setHeadline] = useState('')
  const create = useProfileEdit(profileId, (body: DetailsRequest) => putDetails(profileId, body), 'Profile details saved')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Fill in details</DialogTitle>
          <DialogDescription>
            Just enough to get started — skills, roles and the rest are added from the profile page.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <Field label="Full name" value={fullName} onChange={setFullName} />
          <Field label="Headline" value={headline} onChange={setHeadline} placeholder="Backend Engineer" />
        </div>
        {create.isError ? <ApiErrorAlert error={create.error} /> : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            disabled={!fullName.trim() || create.isPending}
            onClick={() =>
              create.mutate(
                { fullName: fullName.trim(), headline: blankToNull(headline) },
                { onSuccess: () => onOpenChange(false) },
              )
            }
          >
            {create.isPending ? 'Saving…' : 'Save'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
