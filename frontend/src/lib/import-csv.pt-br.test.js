import { describe, it, expect } from 'vitest'
import { parseWhen, parseWorkoutCSV } from './import-csv.js'

// FORK: Hevy exported in Portuguese (or Spanish) writes dates as "4 de mar. de 2026, 18:36". The
// day-month-year parser only knew "4 mar 2026", so a whole Hevy CSV imported as zero workouts.
describe('parseWhen with "de" between the parts (Hevy in Portuguese/Spanish)', () => {
  it('reads the Portuguese export, with and without the time', () => {
    expect(parseWhen('4 de mar. de 2026, 18:36')).toEqual(parseWhen('4 mar 2026, 18:36'))
    expect(parseWhen('25 de set. de 2025')).toEqual(parseWhen('25 Sep 2025'))
    expect(parseWhen('4 de mar. de 2026, 18:36').d).toBe('2026-03-04')
  })

  it('reads the Spanish one too', () => {
    expect(parseWhen('12 de ene. de 2026, 07:05').d).toBe('2026-01-12')
  })

  it('still reads the plain form', () => {
    expect(parseWhen('22 Dec 2025, 08:00').d).toBe('2025-12-22')
  })
})

describe('a Hevy CSV exported in Portuguese', () => {
  const csv = [
    '"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"',
    '"A – Peito","4 de mar. de 2026, 18:36","4 de mar. de 2026, 19:31","","Bench Press (Dumbbell)",,"",0,"normal",32,14,,,',
    '"A – Peito","4 de mar. de 2026, 18:36","4 de mar. de 2026, 19:31","","Bench Press (Dumbbell)",,"",1,"normal",32,10,,,',
    '"B – Costas","6 de mar. de 2026, 18:10","6 de mar. de 2026, 19:02","","Lat Pulldown (Cable)",,"",0,"normal",50,12,,,',
  ].join('\n')

  it('imports every workout instead of none', () => {
    const parsed = parseWorkoutCSV(csv, { unit: 'kg' })
    expect(parsed.error).toBeFalsy()
    expect(parsed.workouts.map(w => w.d)).toEqual(['2026-03-04', '2026-03-06'])
    expect(parsed.sets).toBe(3)
  })
})
