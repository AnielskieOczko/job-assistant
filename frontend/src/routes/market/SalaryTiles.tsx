import type { SalaryGroup, SalaryReport } from '@/api/types'
import { StatTile } from './StatTile'
import { byOffers, employmentLabel, formatBand, percent, plural } from './format'

/** Contract types shown as tiles of their own. The rest are named as a residual, never pooled. */
const TILED_GROUPS = 2

/**
 * The salary shape of the scope, split so that nothing incomparable is pooled.
 *
 * 21,800 B2B and 21,800 UoP are different money — one is gross-of-everything on a contract, the
 * other is a salary with tax and social contributions already behind it — so there is one tile per
 * contract type and never a figure spanning them. The types too small for a tile are named with
 * their counts rather than dropped, because dropping them would make a "B2B versus employment"
 * split quietly wrong about what the remainder was.
 *
 * Two floors apply, and both render as words rather than as absence:
 *   - fewer than 30 offers in a group and it reports its count instead of a band;
 *   - salary stated on under 80% of the scope and the whole row says so above itself.
 */
export function SalaryTiles({ salary }: { salary: SalaryReport }) {
  const groups = [...salary.groups].sort(byOffers)
  const tiled = groups.slice(0, TILED_GROUPS)
  const residual = groups.slice(TILED_GROUPS)

  return (
    <div className="space-y-2">
      {!salary.meetsCoverageFloor ? (
        <p className="text-xs text-muted-foreground">
          Salary is stated on {salary.offersWithSalary.toLocaleString()} of{' '}
          {salary.offersInScope.toLocaleString()} in-scope offers ({percent(salary.coverage)}).
          Below the 80% coverage the bands below need to describe the scope rather than the subset
          that happened to publish a number.
        </p>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {tiled.map((group) => (
          <SalaryGroupTile
            key={`${group.employmentType}-${group.currency}-${group.period}`}
            group={group}
            offersInScope={salary.offersInScope}
          />
        ))}

        {residual.length > 0 ? <ResidualTile groups={residual} /> : null}

        <StatTile
          label="Salary stated"
          value={percent(salary.coverage)}
          caption={`${salary.offersWithSalary.toLocaleString()} of ${salary.offersInScope.toLocaleString()} in-scope offers publish a figure at all.`}
        />
      </div>
    </div>
  )
}

function SalaryGroupTile({
  group,
  offersInScope,
}: {
  group: SalaryGroup
  offersInScope: number
}) {
  const label = `${employmentLabel(group.employmentType)} band`
  const band = formatBand(group)
  const share = offersInScope === 0 ? 0 : group.offers / offersInScope

  /*
    Below the sample floor the tile reports what it has — the count — rather than a band. It keeps
    its label and its caption, so the reader learns "there were twenty of these" instead of finding
    an empty box and assuming the request failed.
  */
  if (!group.meetsSampleFloor || !band) {
    return (
      <StatTile
        label={label}
        value={plural(group.offers, 'offer')}
        caption={
          group.meetsSampleFloor
            ? 'These offers state only one end of their range, so there is no band to report.'
            : `Too few for a median — the floor is 30. ${percent(share)} of the scope.`
        }
        belowFloor
      />
    )
  }

  return (
    <StatTile
      label={label}
      value={band}
      caption={`Median of stated floors to median of stated ceilings, over ${plural(group.offers, 'offer')} — ${percent(share)} of the scope. Quartiles ${formatQuartiles(group)}.`}
    />
  )
}

/** p25 of the floor to p75 of the ceiling: the spread the median band hides. */
function formatQuartiles(group: SalaryGroup): string {
  const band = formatBand({
    medianFrom: group.p25From,
    medianTo: group.p75To,
    currency: null,
    period: null,
  })
  return band ?? 'unavailable'
}

/**
 * The contract types too small to tile, named individually.
 *
 * Their salaries are deliberately not combined into a figure. A median across UZ, UoD and Staż
 * would be a number describing nobody, and the point of the split is that these are different
 * money — a residual tile that pooled them would reintroduce the error one level down.
 */
function ResidualTile({ groups }: { groups: SalaryGroup[] }) {
  const total = groups.reduce((sum, group) => sum + group.offers, 0)
  const named = groups
    .map((group) => `${employmentLabel(group.employmentType)} ${group.offers}`)
    .join(' · ')

  return (
    <StatTile
      label="Other contract types"
      value={plural(total, 'offer')}
      caption={`${named}. Each is different money from the others, so no band is shown across them.`}
      belowFloor
    />
  )
}
