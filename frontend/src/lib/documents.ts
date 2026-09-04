import type { DocumentLibraryEntry } from '@/api/types'

/** Only the CVs — reuse is scoped to `DocumentType.CV`, never cover letters (issue #82). */
export function cvEntries(entries: DocumentLibraryEntry[]): DocumentLibraryEntry[] {
  return entries.filter((entry) => entry.document.type === 'CV')
}

/**
 * A document's own entry keyed by its id, so a reused row can name the offer its source came from
 * without a second request. Built once per render of the library rather than searched per row.
 */
export function documentsById(entries: DocumentLibraryEntry[]): Map<number, DocumentLibraryEntry> {
  return new Map(entries.map((entry) => [entry.document.id, entry]))
}

/**
 * "Reused from &lt;offer&gt;", or null for a document that was freshly tailored.
 *
 * Falls back to naming the id when the source has since fallen out of the same query's results
 * (a different profile, or one deleted from a store that has no deletion path today but might) —
 * a reused document must never render as though it were an ordinary generation just because its
 * source is momentarily unresolvable.
 */
export function reusedFromLabel(entry: DocumentLibraryEntry, byId: Map<number, DocumentLibraryEntry>): string | null {
  const sourceId = entry.document.sourceDocumentId
  if (sourceId == null) return null
  const source = byId.get(sourceId)
  return source ? `Reused from ${source.offerTitle}` : `Reused from document #${sourceId}`
}
