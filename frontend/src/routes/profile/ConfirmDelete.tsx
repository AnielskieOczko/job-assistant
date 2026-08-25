import { ApiError } from '@/api/http'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * Deleting from the profile is deleting hand-authored ground truth, so it always asks first.
 *
 * A 409 is shown in place rather than closing the dialog: the most common one names the bullets
 * still citing a skill, which is exactly what the user needs in front of them to decide what to do.
 */
export function ConfirmDelete({
  open,
  onOpenChange,
  title,
  description,
  onConfirm,
  pending,
  error,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  onConfirm: () => void
  pending: boolean
  error: unknown
}) {
  const apiError = error instanceof ApiError ? error : null
  const blocking = apiError?.status === 409 ? apiError.blockingBullets : []

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>

        {apiError?.status === 409 ? (
          <Alert variant="destructive">
            <AlertTitle>Still in use</AlertTitle>
            <AlertDescription className="space-y-2">
              <p>{apiError.problem?.detail}</p>
              {blocking.length > 0 ? (
                <ul className="space-y-1">
                  {blocking.map((bullet) => (
                    <li key={bullet.id} className="flex gap-2 text-xs">
                      <Badge variant="outline" className="shrink-0">#{bullet.id}</Badge>
                      <span>{bullet.text}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </AlertDescription>
          </Alert>
        ) : apiError ? (
          <ApiErrorAlert error={apiError} />
        ) : null}

        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            className={cn(buttonVariants({ variant: 'destructive' }))}
            disabled={pending}
            onClick={(event) => {
              // Keep the dialog open so a 409 lands somewhere the user can read it.
              event.preventDefault()
              onConfirm()
            }}
          >
            {pending ? 'Deleting…' : 'Delete'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
