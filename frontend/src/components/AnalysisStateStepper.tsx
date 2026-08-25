import { Check, Loader2 } from 'lucide-react'
import type { AnalysisState } from '@/api/types'
import { cn } from '@/lib/utils'

/**
 * The four working states, labelled so the wait is legible. Note that step 2 runs no model at
 * all - the diff is deterministic Kotlin - and saying so is the point of the label.
 */
const STEPS: { state: AnalysisState; label: string; hint: string }[] = [
  { state: 'PENDING', label: 'Queued', hint: 'Waiting for a slot on the analysis pool' },
  { state: 'EXTRACTING', label: 'Reading the offer', hint: 'Model call 1 of 2' },
  { state: 'MATCHING', label: 'Comparing against your profile', hint: 'Deterministic — no model' },
  { state: 'NARRATING', label: 'Writing the summary', hint: 'Model call 2 of 2' },
]

export function AnalysisStateStepper({ state }: { state: AnalysisState }) {
  const current = STEPS.findIndex((step) => step.state === state)

  return (
    <div className="rounded-lg border p-6">
      <ol className="space-y-4">
        {STEPS.map((step, index) => {
          const done = current > index
          const active = current === index
          return (
            <li key={step.state} className="flex items-start gap-3">
              <span
                className={cn(
                  'mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full border text-[10px]',
                  done && 'border-emerald-600/40 bg-emerald-500/15 text-emerald-600',
                  active && 'border-primary bg-primary/10 text-primary',
                  !done && !active && 'border-border text-muted-foreground',
                )}
              >
                {done ? (
                  <Check className="size-3" />
                ) : active ? (
                  <Loader2 className="size-3 animate-spin" />
                ) : (
                  index + 1
                )}
              </span>
              <div>
                <p className={cn('text-sm', active ? 'font-medium' : 'text-muted-foreground')}>
                  {step.label}
                </p>
                <p className="text-xs text-muted-foreground">{step.hint}</p>
              </div>
            </li>
          )
        })}
      </ol>
      <p className="mt-5 border-t pt-4 text-sm text-muted-foreground">
        Typically 30–90 seconds. Each job costs two model calls, and the pool runs two jobs at a
        time, so a queued job may wait.
      </p>
    </div>
  )
}
