import { NavLink, Outlet } from 'react-router'
import {
  BookOpen,
  FileStack,
  Layers,
  ScrollText,
  TrendingDown,
  UserRound,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { ProfileSwitcher } from '@/components/ProfileSwitcher'

const NAV = [
  { to: '/offers', label: 'Offers', icon: FileStack },
  { to: '/profile', label: 'Profile', icon: UserRound },
  { to: '/gaps', label: 'Gaps', icon: TrendingDown },
  { to: '/catalog', label: 'Catalog', icon: Layers },
  { to: '/llm', label: 'Model calls', icon: ScrollText },
]

export function AppShell() {
  return (
    <div className="flex min-h-screen bg-background text-foreground">
      <aside className="sticky top-0 flex h-screen w-56 shrink-0 flex-col border-r bg-sidebar">
        <div className="flex items-center gap-2 px-5 py-5">
          <BookOpen className="size-5 text-primary" />
          <span className="font-heading text-sm font-semibold tracking-tight">job-assistant</span>
        </div>
        <div className="px-3 pb-3">
          <ProfileSwitcher />
        </div>
        <nav className="flex flex-col gap-0.5 px-3">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
                    : 'text-muted-foreground hover:bg-sidebar-accent/60 hover:text-foreground',
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
        <p className="mt-auto px-5 py-4 text-[11px] leading-relaxed text-muted-foreground">
          Single user, no auth.
          <br />
          Bound to 127.0.0.1.
        </p>
      </aside>

      <main className="min-w-0 flex-1">
        <div className="mx-auto max-w-7xl px-8 py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
