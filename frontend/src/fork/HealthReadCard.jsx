// FORK (specs/fork-health HR-3): Settings → Data & backup → "Read from Health Connect". Sits under
// upstream's HealthConnectCard; one FORK line in views/Settings.jsx renders it on Android.
import { useEffect, useState } from 'react'
import { Section, Row, Switch } from '../components/ui.jsx'
import { healthRead } from './health-read-sync.js'
import { ft } from './strings.js'

const when = (t, lang) => new Date(t).toLocaleString(lang || undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

export default function HealthReadCard({ S, toast, api = healthRead }) {
  const [hr, setHr] = useState(null)
  const [st, setSt] = useState(null)
  const [busy, setBusy] = useState(false)
  const lang = S?.lang
  useEffect(() => {
    let gone = false, off = () => {}
    api().then(async h => {
      if (gone) return
      setHr(h)
      setSt(await h.load())
      off = h.onChange(s => { if (!gone) setSt(s) })
    }).catch(() => {})
    return () => { gone = true; off() }
  }, [api])
  if (!hr || !st) return null

  const turnOn = async kinds => {
    setBusy(true)
    try {
      const r = await hr.enable(kinds)
      setSt(await hr.load())
      if (r.ok) toast?.(ft('Reading from Health Connect', lang))
      else toast?.(r.reason === 'denied' ? ft('Health Connect permission not granted', lang) : ft('Could not read from Health Connect', lang))
    } catch { toast?.(ft('Could not read from Health Connect', lang)) }
    setBusy(false)
  }
  const turnOff = async () => { setBusy(true); try { setSt(await hr.disable()) } finally { setBusy(false) } }
  const readNow = async () => { setBusy(true); try { setSt(await hr.read()) } finally { setBusy(false) } }
  const setKind = (k, v) => turnOn({ ...st.kinds, [k]: v })

  return (
    <Section title={ft('Read from Health Connect', lang)}
      footer={st.on ? ft('Weight and body fat from Samsung Health and your scale come into openGym. What you type here always wins.', lang) : null}>
      <Row icon="heart" iconTint="var(--teal)" title={ft('Read from Health Connect', lang)}
        subtitle={st.on ? null : ft('Weight and body fat from Samsung Health and your scale come into openGym. What you type here always wins.', lang)}>
        <Switch checked={!!st.on} disabled={busy} onChange={v => (v ? turnOn(st.kinds) : turnOff())} />
      </Row>
      {st.on && <Row title={ft('Weight', lang)}>
        <Switch checked={!!st.kinds.weight} disabled={busy} onChange={v => setKind('weight', v)} />
      </Row>}
      {st.on && <Row title={ft('Body fat', lang)}>
        <Switch checked={!!st.kinds.bodyFat} disabled={busy} onChange={v => setKind('bodyFat', v)} />
      </Row>}
      {st.on && st.error === 'permission' && <Row icon="warning" iconTint="var(--orange)"
        title={ft('Permission withdrawn. Tap to allow again', lang)} accessory="chevron" onClick={() => turnOn(st.kinds)} />}
      {st.on && st.history === false && <Row icon="info" iconTint="var(--grey)"
        title={ft('Only the last 30 days: Health Connect did not allow the history.', lang)} />}
      {st.on && <Row icon="download" iconTint="var(--blue)" title={ft('Read now', lang)}
        subtitle={st.lastRead ? ft('Last read: {0} · {1} new', lang, when(st.lastRead, lang), st.lastCount) : ft('Not read yet', lang)}
        accessory="chevron" onClick={busy ? undefined : readNow} />}
    </Section>
  )
}
