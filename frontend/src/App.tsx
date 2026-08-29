import { Navigate, Route, Routes } from 'react-router'
import { AppShell } from '@/components/AppShell'
import { OffersPage } from '@/routes/OffersPage'
import { OfferLayout } from '@/routes/OfferLayout'
import { OfferOverviewTab } from '@/routes/OfferOverviewTab'
import { AnalysisTab } from '@/routes/AnalysisTab'
import { DocumentsTab } from '@/routes/DocumentsTab'
import { ProfilePage } from '@/routes/profile/ProfilePage'
import { CatalogPage } from '@/routes/CatalogPage'
import { GapsPage } from '@/routes/GapsPage'
import { MarketPage } from '@/routes/market/MarketPage'
import { LlmCallsPage } from '@/routes/LlmCallsPage'
import { LlmCallDetailPage } from '@/routes/LlmCallDetailPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/offers" replace />} />
        <Route path="offers" element={<OffersPage />} />
        <Route path="offers/:offerId" element={<OfferLayout />}>
          <Route index element={<OfferOverviewTab />} />
          <Route path="analysis" element={<AnalysisTab />} />
          <Route path="documents" element={<DocumentsTab />} />
        </Route>
        <Route path="profile" element={<ProfilePage />} />
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="gaps" element={<GapsPage />} />
        <Route path="market" element={<MarketPage />} />
        <Route path="llm" element={<LlmCallsPage />} />
        <Route path="llm/:callId" element={<LlmCallDetailPage />} />
        <Route path="*" element={<Navigate to="/offers" replace />} />
      </Route>
    </Routes>
  )
}
