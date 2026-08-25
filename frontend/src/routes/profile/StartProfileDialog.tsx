import { useState } from 'react'
import { putDetails } from '@/api/profile'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Field } from './Field'
import { blankToNull, useProfileEdit } from './mutations'

/**
 * Creating a profile from nothing.
 *
 * `PUT /api/profile/details` upserts the singleton, so a name is all it takes to bring a profile
 * into existence — which is the point: before this, a fresh database left you hand-writing a JSON
 * document before you could record anything at all.
 */
export function StartProfileDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [fullName, setFullName] = useState('')
  const [headline, setHeadline] = useState('')
  const create = useProfileEdit(putDetails, 'Profile created')

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create profile</DialogTitle>
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
            {create.isPending ? 'Creating…' : 'Create profile'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
