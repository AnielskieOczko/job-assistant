import { NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/utils'

/**
 * Two readings of the same thing, side by side.
 *
 * The call list is per-call debugging evidence that ages out after thirty days; spend is a total
 * that has to still be right in a year. They are tabs rather than separate nav entries because the
 * path between them is the useful one — a figure on the dashboard is only trustworthy if the rows
 * behind it are one click away.
 */
const TABS = [
  { to: '.', label: 'Calls', end: true },
  { to: 'spend', label: 'Spend', end: false },
]

export function LlmLayout() {
  return (
    <>
      <nav className="mb-6 flex gap-1 border-b">
        {TABS.map((tab) => (
          <NavLink
            key={tab.label}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              cn(
                '-mb-px border-b-2 px-3 py-2 text-sm transition-colors',
                isActive
                  ? 'border-primary font-medium text-foreground'
                  : 'border-transparent text-muted-foreground hover:text-foreground',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  )
}
