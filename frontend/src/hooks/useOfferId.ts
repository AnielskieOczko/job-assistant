import { useParams } from 'react-router'

/** The `:offerId` path param, as a number. Shared by the offer layout and all three tabs. */
export function useOfferId(): number {
  const { offerId } = useParams()
  return Number(offerId)
}
