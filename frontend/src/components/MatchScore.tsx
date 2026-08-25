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

  if (report.matchScore === null) {
    return (
      <div className="flex items-center gap-4">
        <div className="flex size-24 shrink-0 items-center justify-center rounded-full border-4 border-dashed border-muted-foreground/30 text-center text-xs text-muted-foreground">
          Not
          <br />
          scoreable
        </div>
        <p className="text-sm text-muted-foreground">{explanation}</p>
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
      </div>
    </div>
  )
}

/** Only used if the computed getter is ever dropped from the wire format. */
function fallbackExplanation(report: AnalysisReport): string {
  const scored = mustHaves(report).filter((r) => r.status !== 'UNRESOLVED')
  if (scored.length === 0) return 'No resolvable must-have requirements were found.'
  const met = scored.filter((r) => r.status === 'MET').length
  const partial = scored.filter((r) => r.status === 'PARTIAL').length
  return `(${met} met + 0.5 x ${partial} partial) / ${scored.length} must-have requirements`
}
