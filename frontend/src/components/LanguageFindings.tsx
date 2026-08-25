import type { LanguageFinding } from '@/api/types'
import { RequirementStatusBadge } from '@/components/StatusBadges'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'

/** Levels are compared by CEFR ordinal in Kotlin, never by a model's opinion. */
export function LanguageFindings({ items }: { items: LanguageFinding[] }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">The offer stated no language requirement.</p>
  }

  return (
    <div className="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Language</TableHead>
            <TableHead className="w-32">Required</TableHead>
            <TableHead className="w-32">You hold</TableHead>
            <TableHead className="w-28">Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((item) => (
            <TableRow key={item.language}>
              <TableCell className="font-medium">{item.language}</TableCell>
              <TableCell className="font-mono text-sm">{item.requiredLevel}</TableCell>
              <TableCell className="font-mono text-sm">
                {item.heldLevel ?? (
                  <span className="font-sans text-muted-foreground">not in profile</span>
                )}
              </TableCell>
              <TableCell><RequirementStatusBadge status={item.status} /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
