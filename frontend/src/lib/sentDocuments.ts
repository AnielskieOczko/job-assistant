import type { Application } from '@/api/types'

/**
 * What went out for an application, in one short label for a list row.
 *
 * Null rather than a dash or an empty string, so the caller decides how "nothing recorded" renders
 * — and so that nothing recorded stays visibly different from a deliberate "no documents". An
 * application made outside the tool has no document to name, and that is not the same fact as one
 * sent with neither a CV nor a letter.
 */
export function sentDocumentsLabel(application: Application): string | null {
  const cv = application.sentCvDocumentId !== null
  const letter = application.sentCoverLetterDocumentId !== null

  if (cv && letter) return 'CV + letter'
  if (cv) return 'CV'
  if (letter) return 'Letter'
  return null
}
