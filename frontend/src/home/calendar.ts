/** Calendar-day arithmetic uses local Y/M/D but UTC ordinals to avoid DST-length days. */
export function calendar(date: Date) {
  const year = date.getFullYear()
  const start = Date.UTC(year, 0, 1)
  const days = (Date.UTC(year + 1, 0, 1) - start) / 86400000
  const elapsed = (Date.UTC(year, date.getMonth(), date.getDate()) - start) / 86400000
  return { year, days, percent: elapsed / days * 100,
    date: `${year}.${String(date.getMonth()+1).padStart(2,'0')}.${String(date.getDate()).padStart(2,'0')}` }
}
