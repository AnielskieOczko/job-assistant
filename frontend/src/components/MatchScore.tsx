import type { AnalysisReport } from '@/api/types'
import { mustHaves } from '@/api/types'

/**
 * `matchScore` is a nullable 0.0-1.0 over must-haves only. Null means nothing was scoreable,
 * which is not the same as scoring zero, so it renders as text rather than an empty ring.
 *
 * `scoreExplanation` is printed verbatim: it exists on the Kotlin side specifically so the number
 * is never a black box, and paraphrasing it would defeat that.
 */
export function MatchScore({ report }: { report: AnalysisReport }) {
  const explanation =
    report.scoreExplanation ?? fallbackExplanation(report)

  /*
    Reports scored before soft skills were excluded counted them in the denominator. Their score is
    stored and deliberately not recomputed — rewriting it would change a number past decisions were
    made on, and could not rewrite the model-written summary that already narrates it. So the report
    says which rule it used, and an older one is labelled rather than quietly reinterpreted.
  */
  const note =
    report.scoringRule === 'V1_ALL_CATEGORIES'
      ? 'Scored before soft skills were excluded, so they count toward this number.'
      : null

  if (report.matchScore === null) {
    return (
      <div className="flex items-center gap-4">
        <div className="flex size-24 shrink-0 items-center justify-center rounded-full border-4 border-dashed border-muted-foreground/30 text-center text-xs text-muted-foreground">
          Not
          <br />
          scoreable
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{explanation}</p>
          {note ? <p className="mt-1 text-xs text-muted-foreground/80">{note}</p> : null}
        </div>
      </div>
    )
  }

  const percent = Math.round(report.matchScore * 100)
  const hue = percent >= 75 ? 'text-emerald-600' : percent >= 45 ? 'text-amber-600' : 'text-red-600'

  return (
    <div className="flex items-center gap-4">
      <div
        className="relative flex size-24 shrink-0 items-center justify-center rounded-full"
        style={{
          background: `conic-gradient(currentColor ${percent}%, var(--muted) 0)`,
        }}
      >
        <span className={hue} aria-hidden />
        <div className="absolute inset-[6px] flex items-center justify-center rounded-full bg-card">
          <span className={`font-heading text-2xl font-semibold ${hue}`}>{percent}%</span>
        </div>
      </div>
      <div className="min-w-0">
        <p className="text-sm font-medium">Must-have coverage</p>
        <p className="mt-0.5 text-sm text-muted-foreground">{explanation}</p>
        {note ? <p className="mt-1 text-xs text-muted-foreground/80">{note}</p> : null}
      </div>
    </div>
  )
}

/** Only used if the computed getter is ever dropped from the wire format. */
function fallbackExplanation(report: AnalysisReport): string {
  const softExcluded = report.scoringRule === 'V2_SOFT_EXCLUDED'
  const scored = mustHaves(report)
    .filter((r) => r.status !== 'UNRESOLVED')
    .filter((r) => !softExcluded || r.category !== 'SOFT')
  if (scored.length === 0) return 'No resolvable must-have requirements were found.'
  const met = scored.filter((r) => r.status === 'MET').length
  const partial = scored.filter((r) => r.status === 'PARTIAL').length
  const noun = softExcluded ? 'technical must-have requirements' : 'must-have requirements'
  return `(${met} met + 0.5 x ${partial} partial) / ${scored.length} ${noun}`
}
