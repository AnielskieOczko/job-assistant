import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Radar } from 'lucide-react'
import { fetchMarketDemand, fetchMarketSalary } from '@/api/market'
import { keys } from '@/api/keys'
import type { DemandEntry, DemandRanking } from '@/api/types'
import { ApiErrorAlert } from '@/components/ApiErrorAlert'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useSelectedProfile } from '@/hooks/useSelectedProfile'
import { CorpusOffers } from './CorpusOffers'
import { DemandTable } from './DemandTable'
import { IngestionControls } from './IngestionControls'
import { SalaryTiles } from './SalaryTiles'
import { ScopeLine } from './ScopeLine'
import { UnmetDemandChart } from './UnmetDemandChart'
import { bandLabel, byOffers, plural } from './format'

/** Skills fetched per request. The response reports how many it left out. */
const DEMAND_LIMIT = 100

/**
 * What this job hunt's slice of the board asks for, and what of it the profile covers.
 *
 * Model-free throughout, and read-only apart from one control: every number here is an aggregate
 * over rows already ingested, so a page load costs a few queries and cannot spend a token. The
 * exception is `IngestionControls`, the one thing in the application that reaches solid.jobs — a
 * button, never something a page load does.
 *
 * The layout is fixed by the shape of the argument it makes, top to bottom: the scope line first
 * (the denominator everything else hangs off), then one hero figure, then the KPI row, then
 * exactly one chart, then the demand table as the primary surface. A reader who meets a median
 * before they meet its population has already been misled.
 */
export function MarketPage() {
  const { profileId, isLoading: profileLoading } = useSelectedProfile()
  const [ranking, setRanking] = useState<DemandRanking>('UNMET')

  const demand = useQuery({
    queryKey: keys.marketDemand(profileId, ranking, DEMAND_LIMIT),
    queryFn: () => fetchMarketDemand(ranking, profileId ?? undefined, DEMAND_LIMIT),
    enabled: !profileLoading,
  })
  const salary = useQuery({ queryKey: keys.marketSalary, queryFn: fetchMarketSalary })

  const header = (
    <PageHeader
      title="Market"
      description="Ingested job offers, counted rather than applied to. No model reads any of this: the source states skills, salary and seniority as structured data, so there is nothing to extract and nothing to invent."
    />
  )

  if (profileLoading || demand.isPending) {
    return (
      <>
        {header}
        <Skeleton className="h-96 w-full" />
      </>
    )
  }
  if (demand.isError) return <>{header}<ApiErrorAlert error={demand.error} /></>

  const report = demand.data
  const { scope } = report

  if (scope.scopeSkills.length === 0) {
    return (
      <>
        {header}
        <div className="space-y-6">
          <ScopeLine scope={scope} />
          <IngestionControls />
          <EmptyState
            icon={Radar}
            title="No scope configured"
            description="Set job-assistant.market.scope-skills to the skills that define the roles you are hunting. Without a scope there is no population to measure, and a median over every offer on the board would describe nobody."
          />
        </div>
      </>
    )
  }

  if (scope.offersInScope === 0) {
    return (
      <>
        {header}
        <div className="space-y-6">
          <ScopeLine scope={scope} />
          <IngestionControls />
          <EmptyState
            icon={Radar}
            title="Nothing in scope yet"
            description={`The corpus holds ${plural(scope.corpusOffers, 'offer')}, none of them currently valid and asking for a scope skill. Poll the source, or widen the scope.`}
          />
        </div>
      </>
    )
  }

  // Whichever ranking is on screen, the most-asked skill the profile does not cover is the first
  // MISSING row: UNMET puts it first outright, and TOTAL sorts by count, so the first MISSING it
  // reaches is the same skill. The hero does not move when the table's ranking does.
  const hero = report.entries.find((entry) => entry.status === 'MISSING') ?? null
  const dominantEmploymentType =
    [...(salary.data?.groups ?? [])].sort(byOffers)[0]?.employmentType ?? null

  return (
    <>
      {header}

      <div className="space-y-8">
        <ScopeLine scope={scope} />

        <IngestionControls />

        <Hero entry={hero} offersInScope={scope.offersInScope} hasProfile={profileId !== null} />

        {salary.isError ? (
          <ApiErrorAlert error={salary.error} title="Salary statistics failed to load" />
        ) : salary.data ? (
          <SalaryTiles salary={salary.data} />
        ) : (
          <Skeleton className="h-28 w-full" />
        )}

        <UnmetDemandChart entries={report.entries} offersInScope={scope.offersInScope} />

        <Tabs defaultValue="demand">
          <TabsList className="mb-4">
            <TabsTrigger value="demand">Demand</TabsTrigger>
            <TabsTrigger value="offers">Offers behind the numbers</TabsTrigger>
          </TabsList>
          <TabsContent value="demand">
            <DemandTable
              report={report}
              ranking={ranking}
              onRankingChange={setRanking}
              dominantEmploymentType={dominantEmploymentType}
            />
          </TabsContent>
          <TabsContent value="offers">
            <CorpusOffers profileId={profileId} />
          </TabsContent>
        </Tabs>
      </div>
    </>
  )
}

/**
 * The one figure the page leads with.
 *
 * It states an **observation**, never a counterfactual: "50 offers ask for this and you do not
 * have it", not "50 offers you would win". The difference is not pedantry — the three candidate
 * rules for offers-gained-if-learned were measured against this corpus and all three collapsed to
 * five to seven offers, resting on a coverage claim the catalog blind spot cannot yet support.
 * Issue #47, decision 1, records the measurement.
 */
function Hero({
  entry,
  offersInScope,
  hasProfile,
}: {
  entry: DemandEntry | null
  offersInScope: number
  hasProfile: boolean
}) {
  if (!entry) {
    return (
      <p className="text-sm text-muted-foreground">
        {hasProfile
          ? 'Nothing in scope asks for a skill your profile lacks — as far as the catalog can place what these offers say. The unplaced share above is the size of that qualifier.'
          : 'No profile is selected, so nothing here is compared against anything. Pick a persona in the sidebar to see which of this demand you already cover.'}
      </p>
    )
  }

  const salary = entry.salary ? bandLabel(entry.salary) : null

  return (
    <div>
      <p className="text-sm text-muted-foreground">Biggest unmet demand in scope</p>
      {/* Proportional figures, not tabular: at display size a tabular `1` sits in a `0`'s box. */}
      <p className="mt-1 font-heading text-5xl font-semibold tracking-tight">
        {entry.offers.toLocaleString()}
      </p>
      <p className="mt-2 max-w-2xl text-sm">
        of {offersInScope.toLocaleString()} in-scope offers ask for{' '}
        <span className="font-medium">{entry.skillName}</span> and your profile does not cover it.{' '}
        {entry.requiredOffers.toLocaleString()} of them ask for it as a requirement rather than a
        nice-to-have.
        {salary ? ` Those offers pay ${salary}.` : ''}
      </p>
    </div>
  )
}
