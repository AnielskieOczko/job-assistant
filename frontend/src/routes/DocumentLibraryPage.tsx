import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Download, FileStack, SquareArrowOutUpRight } from 'lucide-react'
import { documentPdfUrl, listDocumentLibrary } from '@/api/documents'
import { keys } from '@/api/keys'
import type { DocumentType } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { formatDateTime } from '@/lib/format'
import { documentsById, reusedFromLabel } from '@/lib/documents'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'

const ALL = '__all__'
const TYPE_LABELS: Record<DocumentType, string> = { CV: 'CV', COVER_LETTER: 'Cover letter' }

/**
 * Every document generated for the selected profile, across every offer.
 *
 * The only way to see this today is opening each offer's Documents tab in turn — this is the
 * archaeology issue #82 exists to remove. Reuse itself starts from an offer's own Documents tab
 * ("reuse an existing CV"), not from here: this page is the read side, that is the write side.
 */
export function DocumentLibraryPage() {
  const { profileId } = useSelectedProfile()
  const [type, setType] = useState<string>(ALL)

  const library = useQuery({
    queryKey: keys.documentLibrary(profileId),
    queryFn: () => listDocumentLibrary(profileId!),
    enabled: profileId !== null,
  })

  const entries = library.data
  const byId = useMemo(() => documentsById(entries ?? []), [entries])
  const rows = useMemo(
    () => (entries ?? []).filter((entry) => type === ALL || entry.document.type === type),
    [entries, type],
  )

  return (
    <>
      <PageHeader
        title="Documents"
        description="Every CV and cover letter generated for this profile, across every offer."
      />

      {library.isError ? <ApiErrorAlert error={library.error} /> : null}

      {library.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
        </div>
      ) : (entries ?? []).length === 0 ? (
        <EmptyState
          icon={FileStack}
          title="No documents yet"
          description="Generate a CV or cover letter from an offer's Documents tab to see it here."
        />
      ) : (
        <>
          <div className="mb-4 flex items-center gap-2">
            <Select value={type} onValueChange={setType}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                <SelectItem value="CV">CV</SelectItem>
                <SelectItem value="COVER_LETTER">Cover letter</SelectItem>
              </SelectContent>
            </Select>
            <span className="ml-auto text-sm text-muted-foreground">
              {rows.length} of {(entries ?? []).length}
            </span>
          </div>

          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Offer</TableHead>
                  <TableHead className="w-32">Type</TableHead>
                  <TableHead className="w-28">Language</TableHead>
                  <TableHead>Provenance</TableHead>
                  <TableHead className="w-44 text-right">Generated</TableHead>
                  <TableHead className="w-24 text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((entry) => {
                  const reused = reusedFromLabel(entry, byId)
                  return (
                    <TableRow key={entry.document.id}>
                      <TableCell className="max-w-0">
                        <Link
                          to={`/offers/${entry.document.offerId}/documents`}
                          className="block truncate font-medium hover:underline"
                        >
                          {entry.offerTitle}
                        </Link>
                        {entry.offerCompany ? (
                          <p className="truncate text-xs text-muted-foreground">{entry.offerCompany}</p>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">{TYPE_LABELS[entry.document.type]}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{entry.document.language}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {reused ? (
                          <span title="Drop counts shown for a reused document are the source generation's, not fresh ones.">
                            {reused}
                          </span>
                        ) : (
                          '—'
                        )}
                      </TableCell>
                      <TableCell className="text-right text-sm text-muted-foreground">
                        {formatDateTime(entry.document.createdAt)}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1">
                          <Button asChild size="icon" variant="ghost" title="Open PDF">
                            <a href={documentPdfUrl(entry.document.id)} target="_blank" rel="noreferrer">
                              <SquareArrowOutUpRight />
                            </a>
                          </Button>
                          <Button asChild size="icon" variant="ghost" title="Download PDF">
                            <a href={documentPdfUrl(entry.document.id)} download>
                              <Download />
                            </a>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
                {rows.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                      No documents match that filter.
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </>
  )
}
