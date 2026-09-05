import { useState } from 'react'

export type SeedAction = 'seed' | 'reset' | 'none'

/**
 * What a seeded-form dialog should do this render, given the row it currently shows (`null` when
 * closed) and the key it last populated its fields from.
 *
 * Dialogs in this app seed local field state synchronously during render rather than in an effect
 * or via a remount key. That means a dialog closed without saving leaves its fields holding the
 * abandoned edit; reopening the *same* row looks already-seeded and skips repopulating, so the
 * abandoned edit reappears as if it had been saved. Resetting back to `null` on close is what
 * forces a reseed on the next open, even when it reuses the same key.
 */
export function decideSeed<K>(current: K | null, seeded: K | null): SeedAction {
  if (current !== null && seeded !== current) return 'seed'
  if (current === null && seeded !== null) return 'reset'
  return 'none'
}

/**
 * Owns the `seeded` tracking state for a row-seeded dialog and tells the caller whether to
 * (re)populate its fields this render. See `decideSeed` for why the reset matters.
 */
export function useSeededKey<K>(current: K | null): boolean {
  const [seeded, setSeeded] = useState<K | null>(null)
  const action = decideSeed(current, seeded)
  if (action === 'seed') setSeeded(current)
  else if (action === 'reset') setSeeded(null)
  return action === 'seed'
}
