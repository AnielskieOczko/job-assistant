import { json, request } from './http'
import type { LinkedInDraft, LinkedInDraftRequest } from './types'

export const listLinkedInDrafts = (profileId: number) =>
  request<LinkedInDraft[]>(`/api/profiles/${profileId}/linkedin/drafts`)

export const draftLinkedInSection = (profileId: number, body: LinkedInDraftRequest) =>
  request<LinkedInDraft>(`/api/profiles/${profileId}/linkedin/draft`, {
    method: 'POST',
    ...json(body),
  })
