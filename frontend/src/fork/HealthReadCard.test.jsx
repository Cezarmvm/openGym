// @vitest-environment happy-dom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { beforeEach, afterEach, describe, expect, it } from 'vitest'
import HealthReadCard from './HealthReadCard.jsx'

const flush = () => act(async () => { await new Promise(r => setTimeout(r, 0)) })

let host, root, calls, state, api
beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host)
  calls = []
  state = { on: false, kinds: { weight: true, bodyFat: true }, lastRead: 0, lastCount: 0, error: null }
  const h = {
    load: async () => state,
    onChange: () => () => {},
    enable: async kinds => { calls.push(['enable', kinds]); state = { ...state, on: true, kinds, lastRead: Date.UTC(2026, 9, 14, 9), lastCount: 3, history: true }; return { ok: true } },
    disable: async () => { calls.push(['disable']); state = { ...state, on: false }; return state },
    read: async () => { calls.push(['read']); return state },
  }
  api = async () => h
})
afterEach(() => { act(() => root.unmount()); host.remove() })

const render = async lang => { act(() => root.render(<HealthReadCard S={{ lang }} toast={() => {}} api={api} />)); await flush() }
const switches = () => [...host.querySelectorAll('input[type="checkbox"], [role="switch"]')]

describe('Read from Health Connect card', () => {
  it('starts off, in Portuguese on a pt-BR profile', async () => {
    await render('pt-BR')
    expect(host.textContent).toContain('Ler do Conexão Saúde')
    expect(host.textContent).not.toContain('Gordura corporal')
  })

  it('turning it on asks for both kinds and shows the last read', async () => {
    await render('en')
    await act(async () => { switches()[0].click() })
    await flush()
    expect(calls[0]).toEqual(['enable', { weight: true, bodyFat: true }])
    expect(host.textContent).toContain('Body fat')
    expect(host.textContent).toMatch(/Last read: .* · 3 new/)
  })

  it('turning it off keeps what came in and stops reading', async () => {
    state = { ...state, on: true }
    await render('en')
    await act(async () => { switches()[0].click() })
    await flush()
    expect(calls).toContainEqual(['disable'])
  })
})
