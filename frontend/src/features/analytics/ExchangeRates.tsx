import { useState } from 'react'
import { Loading } from '../../components/Loading'
import { useResource } from '../../hooks/useResource'

interface DailyRates {
  date: string
  rates: { currencyCode: string; currencyName: string; units: number; rateByn: number }[]
}

const firstDate = '2026-01-01'
const lastDate = '2026-12-31'
const minskDate = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Minsk', dateStyle: 'short' })
const rateFormat = new Intl.NumberFormat('ru-BY', { maximumFractionDigits: 10 })

export function ExchangeRates() {
  const today = minskDate.format(new Date())
  const maxDate = today < lastDate ? today : lastDate
  const [date, setDate] = useState(maxDate)
  const [selectedDate, setSelectedDate] = useState(date)
  const rates = useResource<DailyRates>('/analytics/statistics/exchange-rates?date=' + encodeURIComponent(date))
  const day = rates.data?.date === date ? rates.data : null

  return <section id="nbrb-rates" tabIndex={-1} className="panel plan-parameters analysis-section statistics-section" aria-labelledby="nbrb-rates-heading">
    <div className="plan-parameters-heading"><h2 id="nbrb-rates-heading">Официальные курсы валют Нацбанка за 2026 год</h2></div>
    <form className="analysis-form" onSubmit={event => {
      event.preventDefault()
      if (selectedDate === date) void rates.refresh()
      else setDate(selectedDate)
    }}>
      <div className="analysis-actions">
        <label className="admin-field statistics-year">Дата курса
          <input type="date" min={firstDate} max={maxDate} value={selectedDate} required
            onChange={event => setSelectedDate(event.target.value)}/>
        </label>
        <button type="submit" className="button button-primary">Показать курсы</button>
      </div>
    </form>
    {rates.loading && <Loading label="Загружаем курсы Нацбанка…"/>}
    {rates.error && <div className="alert alert-error" role="alert">{rates.error}
      <button type="button" className="text-button" onClick={() => void rates.refresh()}>Повторить</button>
    </div>}
    {!rates.loading && !rates.error && day && (day.rates.length > 0 ? <>
      <p className="analysis-method-note analysis-inline-note">Курсы на {day.date.split('-').reverse().join('.')}</p>
      <div className="table-scroll">
        <table className="product-table plan-list-table">
          <thead><tr>
            <th scope="col">Код</th>
            <th scope="col">Валюта</th>
            <th scope="col" className="numeric">Количество единиц</th>
            <th scope="col" className="numeric">Курс за указанное количество, BYN</th>
          </tr></thead>
          <tbody>{day.rates.map(rate => <tr key={rate.currencyCode}>
            <th scope="row">{rate.currencyCode}</th>
            <td>{rate.currencyName}</td>
            <td className="numeric">{rateFormat.format(rate.units)}</td>
            <td className="numeric">{rateFormat.format(rate.rateByn)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </> : <p className="analysis-method-note analysis-inline-note">На выбранную дату курсы не опубликованы.</p>)}
  </section>
}
