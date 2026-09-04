import type { PolishSuggestion } from '@/api/types'

/**
 * The rules behind the polish dialog, kept out of the component so they can be asserted directly.
 *
 * All of them exist to keep one promise: trying a suggestion costs nothing. The original is never
 * mutated, an accept is refused unless it would actually change something to something, and what a
 * reviewer sees marked is exactly what the server flagged.
 */

/** One run of text, marked or not, for rendering the suggestion with its flagged terms highlighted. */
export interface PolishSegment {
  text: string
  flagged: boolean
}

/**
 * The text an accept would write, or null when there is nothing to accept.
 *
 * Null for a draft the candidate has blanked (accepting would delete the field, which is a
 * deletion rather than a polish and belongs in the field's own editor) and for a draft that is the
 * original again (accepting would be a write with no change, and every profile write bumps the
 * revision, which is what marks stored CVs and analyses stale).
 */
export function acceptedText(original: string, draft: string): string | null {
  const next = draft.trim()
  if (next === '') return null
  if (next === original.trim()) return null
  return next
}

/** The draft a fresh suggestion starts from: the model's text, which the candidate may then edit. */
export function draftFor(suggestion: PolishSuggestion): string {
  return suggestion.suggestion
}

/**
 * Splits [text] into runs so the terms the server flagged can be marked in place.
 *
 * Whole-word, case-insensitive, longest term first so that "Apache Kafka" wins over a "Kafka" that
 * would otherwise cut it in half. This is presentation only — the authoritative reading is the
 * server's `unheldSkills`, and this never adds a term to it or drops one from it. A term the
 * matcher cannot locate in the text (the model wrote "K8s" and the catalog names it "Kubernetes")
 * simply goes unmarked; it is still named in the list beneath.
 */
export function markTerms(text: string, terms: string[]): PolishSegment[] {
  const wanted = terms.filter((term) => term.trim() !== '').sort((a, b) => b.length - a.length)
  if (wanted.length === 0 || text === '') return [{ text, flagged: false }]

  const pattern = new RegExp(`(?<![\\p{L}\\p{N}])(${wanted.map(escapeRegExp).join('|')})(?![\\p{L}\\p{N}])`, 'giu')
  const segments: PolishSegment[] = []
  let cursor = 0

  for (const match of text.matchAll(pattern)) {
    const start = match.index
    if (start > cursor) segments.push({ text: text.slice(cursor, start), flagged: false })
    segments.push({ text: match[0], flagged: true })
    cursor = start + match[0].length
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), flagged: false })

  return segments
}

/**
 * Which of [terms] the text still names.
 *
 * The server's `unheldSkills` describes the suggestion as the model wrote it. The moment the
 * candidate edits the draft, that list can be stale in one direction - a term they have just
 * deleted - so the warning is driven by this rather than by the raw list, and disappears when they
 * take the word out. It can never gain a term the server did not report: this narrows a list, it
 * does not re-run the scan, and a client-side reading is not the authority on what is claimed.
 */
export function flaggedIn(text: string, terms: string[]): string[] {
  return terms.filter((term) => markTerms(text, [term]).some((segment) => segment.flagged))
}

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
