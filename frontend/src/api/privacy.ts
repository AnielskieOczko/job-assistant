import { request } from './http'
import type { PrivacyManifest } from './types'

/**
 * What leaves this machine, and what does not. Profile-independent - the same manifest answers
 * whether or not a persona exists yet - so callers don't need a profile id to ask.
 */
export const getPrivacyManifest = () => request<PrivacyManifest>('/api/privacy/manifest')
