import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { checkForUpdate, resetUpdateCheck, RELEASES_PAGE } from '../lib/update.js'

// The fork is signed with its own key, so the official APK would never install over it: the
// update check must only ever look at the fork's releases (specs/fork-watch RB-4, D-3).
describe('fork update check', () => {
  let originalFetch
  beforeEach(() => { originalFetch = globalThis.fetch; resetUpdateCheck() })
  afterEach(() => { globalThis.fetch = originalFetch })

  it('asks the fork, never the upstream repository', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ tag_name: 'v' + __APP_VERSION__, assets: [] }) }))
    await checkForUpdate()
    const url = globalThis.fetch.mock.calls[0][0]
    expect(url).toBe('https://api.github.com/repos/Cezarmvm/openGym/releases/latest')
    expect(url).not.toContain('DuarteSantos8')
    expect(RELEASES_PAGE).toBe('https://github.com/Cezarmvm/openGym/releases/latest')
  })

  it('reports no update while the fork has no published release', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) }))
    const result = await checkForUpdate()
    expect(result.hasUpdate).toBe(false)
    expect(result.apkUrl).toBeNull()
  })
})
