import { ArrowDown, ArrowUp, Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * Move / edit / delete for one row of a collection.
 *
 * Reordering is arrow buttons rather than drag-and-drop: the profile's collections are short, and
 * a drag library would be the only frontend dependency this feature needed.
 */
export function RowActions({
  onUp,
  onDown,
  onEdit,
  onDelete,
  disabled = false,
  label,
}: {
  onUp?: () => void
  onDown?: () => void
  onEdit?: () => void
  onDelete?: () => void
  disabled?: boolean
  label: string
}) {
  return (
    <div className="flex shrink-0 items-center gap-0.5">
      <Button
        variant="ghost"
        size="icon-xs"
        aria-label={`Move ${label} up`}
        disabled={disabled || !onUp}
        onClick={onUp}
      >
        <ArrowUp />
      </Button>
      <Button
        variant="ghost"
        size="icon-xs"
        aria-label={`Move ${label} down`}
        disabled={disabled || !onDown}
        onClick={onDown}
      >
        <ArrowDown />
      </Button>
      {onEdit ? (
        <Button variant="ghost" size="icon-xs" aria-label={`Edit ${label}`} disabled={disabled} onClick={onEdit}>
          <Pencil />
        </Button>
      ) : null}
      {onDelete ? (
        <Button
          variant="ghost"
          size="icon-xs"
          aria-label={`Delete ${label}`}
          disabled={disabled}
          onClick={onDelete}
          className="text-muted-foreground hover:text-destructive"
        >
          <Trash2 />
        </Button>
      ) : null}
    </div>
  )
}
