import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiClientError, type EventPage, type HookEvent } from './api'

type Props = {
  projectId: string
  sessionExpired: () => void
}

type Filters = {
  type: string
  status: string
  from: string
  to: string
}

type Density = 'comfortable' | 'compact'

const emptyFilters: Filters = { type: '', status: '', from: '', to: '' }
const refreshOptions = [0, 5, 15, 30] as const
const pageSizeOptions = [10, 20, 50] as const
const storageKeys = {
  refresh: 'hookscope.dashboard.refresh',
  pageSize: 'hookscope.dashboard.page-size',
  density: 'hookscope.dashboard.density',
}

const statusLabels: Record<HookEvent['status'], string> = {
  SUCCESS: 'Sucesso',
  ERROR: 'Erro',
  TIMEOUT: 'Timeout',
  CANCELLED: 'Cancelado',
}

function storedNumber(key: string, options: readonly number[], fallback: number) {
  try {
    const value = Number(window.localStorage.getItem(key))
    return options.includes(value) ? value : fallback
  } catch {
    return fallback
  }
}

function storedDensity(): Density {
  try {
    return window.localStorage.getItem(storageKeys.density) === 'compact' ? 'compact' : 'comfortable'
  } catch {
    return 'comfortable'
  }
}

function storePreference(key: string, value: string | number) {
  try {
    window.localStorage.setItem(key, String(value))
  } catch {
    // A preferência continua válida durante a sessão quando o armazenamento está indisponível.
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function formatUpdateTime(value: Date | null) {
  if (!value) return 'Aguardando primeira atualização'
  return `Atualizado às ${new Intl.DateTimeFormat('pt-BR', { timeStyle: 'medium' }).format(value)}`
}

function buildQuery(page: number, pageSize: number, filters: Filters) {
  const query = new URLSearchParams({ page: String(page), size: String(pageSize) })
  if (filters.type) query.set('type', filters.type)
  if (filters.status) query.set('status', filters.status)
  if (filters.from) query.set('from', new Date(filters.from).toISOString())
  if (filters.to) query.set('to', new Date(filters.to).toISOString())
  return query.toString()
}

function messageFor(failure: unknown, sessionExpired: () => void) {
  if (failure instanceof ApiClientError && failure.status === 401) sessionExpired()
  return failure instanceof Error ? failure.message : 'Não foi possível carregar os eventos.'
}

export default function EventDashboard({ projectId, sessionExpired }: Props) {
  const [draft, setDraft] = useState<Filters>(emptyFilters)
  const [filters, setFilters] = useState<Filters>(emptyFilters)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(() => storedNumber(storageKeys.pageSize, pageSizeOptions, 20))
  const [autoRefreshSeconds, setAutoRefreshSeconds] = useState(() => storedNumber(storageKeys.refresh, refreshOptions, 0))
  const [density, setDensity] = useState<Density>(storedDensity)
  const [data, setData] = useState<EventPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    api<EventPage>(`/api/v1/projects/${projectId}/events?${buildQuery(page, pageSize, filters)}`)
      .then(result => {
        if (!controller.signal.aborted) {
          setData(result)
          setLastUpdated(new Date())
          setError('')
          setLoading(false)
        }
      })
      .catch(failure => {
        if (!controller.signal.aborted) {
          setError(messageFor(failure, sessionExpired))
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [projectId, page, pageSize, filters, refresh, sessionExpired])

  useEffect(() => {
    if (autoRefreshSeconds === 0) return
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') setRefresh(value => value + 1)
    }, autoRefreshSeconds * 1_000)
    return () => window.clearInterval(interval)
  }, [autoRefreshSeconds])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(0)
    setFilters({
      type: draft.type.trim(),
      status: draft.status,
      from: draft.from,
      to: draft.to,
    })
  }

  function clearFilters() {
    setDraft(emptyFilters)
    setFilters(emptyFilters)
    setPage(0)
  }

  function changeRefresh(value: string) {
    const seconds = Number(value)
    setAutoRefreshSeconds(seconds)
    storePreference(storageKeys.refresh, seconds)
  }

  function changePageSize(value: string) {
    const size = Number(value)
    setPageSize(size)
    setPage(0)
    storePreference(storageKeys.pageSize, size)
  }

  function changeDensity(value: Density) {
    setDensity(value)
    storePreference(storageKeys.density, value)
  }

  const events = data?.content ?? []
  const failureCount = events.filter(event => event.status !== 'SUCCESS').length
  const averageDuration = events.length
    ? Math.round(events.reduce((total, event) => total + event.durationMs, 0) / events.length)
    : 0

  return (
    <section className="event-dashboard" aria-labelledby="events-title">
      <div className="dashboard-heading">
        <div>
          <div className="eyebrow">MONITORAMENTO</div>
          <h2 id="events-title">Eventos recentes</h2>
          <p>Acompanhe as integrações deste projeto e investigue ocorrências específicas.</p>
        </div>
        <div className="dashboard-actions">
          <span className="refresh-state" aria-live="polite">
            {autoRefreshSeconds > 0 && <i aria-hidden="true" />}
            {formatUpdateTime(lastUpdated)}
          </span>
          <button className="secondary" disabled={loading} onClick={() => setRefresh(value => value + 1)}>
            {loading ? 'Atualizando…' : 'Atualizar agora'}
          </button>
        </div>
      </div>

      <div className="dashboard-preferences panel" aria-label="Preferências do dashboard">
        <label>
          Atualização automática
          <select value={autoRefreshSeconds} onChange={event => changeRefresh(event.target.value)}>
            <option value={0}>Desativada</option>
            <option value={5}>A cada 5 segundos</option>
            <option value={15}>A cada 15 segundos</option>
            <option value={30}>A cada 30 segundos</option>
          </select>
        </label>
        <label>
          Eventos por página
          <select value={pageSize} onChange={event => changePageSize(event.target.value)}>
            {pageSizeOptions.map(size => <option key={size} value={size}>{size} eventos</option>)}
          </select>
        </label>
        <label>
          Densidade
          <select value={density} onChange={event => changeDensity(event.target.value as Density)}>
            <option value="comfortable">Confortável</option>
            <option value="compact">Compacta</option>
          </select>
        </label>
        <small>As preferências ficam salvas neste navegador.</small>
      </div>

      <div className="metric-grid">
        <article className="metric-card">
          <span>Eventos encontrados</span>
          <strong>{data?.totalElements ?? 0}</strong>
          <small>Com os filtros atuais</small>
        </article>
        <article className="metric-card">
          <span>Falhas na página</span>
          <strong className={failureCount ? 'metric-alert' : ''}>{failureCount}</strong>
          <small>Erro, timeout ou cancelamento</small>
        </article>
        <article className="metric-card">
          <span>Duração média</span>
          <strong>{averageDuration}<em> ms</em></strong>
          <small>Nos {events.length} eventos exibidos</small>
        </article>
      </div>

      <form className="event-filters panel" onSubmit={applyFilters}>
        <label>
          Tipo
          <input
            placeholder="payment.authorization"
            value={draft.type}
            onChange={event => setDraft(current => ({ ...current, type: event.target.value }))}
          />
        </label>
        <label>
          Status
          <select
            value={draft.status}
            onChange={event => setDraft(current => ({ ...current, status: event.target.value }))}
          >
            <option value="">Todos</option>
            <option value="SUCCESS">Sucesso</option>
            <option value="ERROR">Erro</option>
            <option value="TIMEOUT">Timeout</option>
            <option value="CANCELLED">Cancelado</option>
          </select>
        </label>
        <label>
          A partir de
          <input
            type="datetime-local"
            step="1"
            value={draft.from}
            onChange={event => setDraft(current => ({ ...current, from: event.target.value }))}
          />
        </label>
        <label>
          Até
          <input
            type="datetime-local"
            step="1"
            value={draft.to}
            onChange={event => setDraft(current => ({ ...current, to: event.target.value }))}
          />
        </label>
        <div className="filter-actions">
          <button className="primary">Filtrar</button>
          <button type="button" className="text-button" onClick={clearFilters}>Limpar</button>
        </div>
      </form>

      {error && <div className="notice error" role="alert">{error}</div>}

      <div className={`panel event-panel event-panel--${density}`}>
        {loading && !data ? <p className="muted">Carregando eventos…</p> :
          events.length === 0 ? <div className="empty-state">
            <div className="empty-symbol">⌁</div>
            <h3>Nenhum evento encontrado</h3>
            <p>Execute o simulador ou ajuste os filtros para visualizar eventos.</p>
          </div> : <>
            <div className="event-table-wrap">
              <table className="event-table">
                <thead>
                  <tr>
                    <th>Ocorrido em</th>
                    <th>Tipo</th>
                    <th>Status</th>
                    <th>Duração</th>
                    <th>Identificador</th>
                    <th>Dados</th>
                  </tr>
                </thead>
                <tbody>
                  {events.map(event => <EventRow key={event.eventId} event={event} />)}
                </tbody>
              </table>
            </div>
            <div className="pagination">
              <button disabled={page === 0 || loading} onClick={() => setPage(value => value - 1)}>Anterior</button>
              <span>Página {page + 1} de {Math.max(data?.totalPages ?? 1, 1)}</span>
              <button
                disabled={loading || page + 1 >= (data?.totalPages ?? 0)}
                onClick={() => setPage(value => value + 1)}
              >
                Próxima
              </button>
            </div>
          </>}
      </div>
    </section>
  )
}

function EventRow({ event }: { event: HookEvent }) {
  return (
    <tr>
      <td><time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time></td>
      <td><strong className="event-type">{event.type}</strong></td>
      <td><span className={`event-status event-status--${event.status.toLowerCase()}`}>{statusLabels[event.status]}</span></td>
      <td>{event.durationMs.toLocaleString('pt-BR')} ms</td>
      <td><code title={event.eventId}>{event.eventId.slice(0, 8)}…</code></td>
      <td>
        <details className="metadata">
          <summary>Ver JSON</summary>
          <pre>{JSON.stringify(event.metadata, null, 2)}</pre>
        </details>
      </td>
    </tr>
  )
}
