import { useEffect, useState } from 'react'

type HealthState = 'loading' | 'up' | 'down' | 'error'

function App() {
  const [health, setHealth] = useState<HealthState>('loading')

  useEffect(() => {
    const controller = new AbortController()

    async function checkHealth() {
      try {
        const response = await fetch('/actuator/health', {
          cache: 'no-store',
          signal: controller.signal,
        })
        if (!response.ok) {
          setHealth('down')
          return
        }
        const payload: unknown = await response.json()
        setHealth(
          typeof payload === 'object' && payload !== null && 'status' in payload && payload.status === 'UP'
            ? 'up'
            : 'down',
        )
      } catch {
        if (!controller.signal.aborted) setHealth('error')
      }
    }

    void checkHealth()
    return () => controller.abort()
  }, [])

  const label = {
    loading: 'Verificando conexão…',
    up: 'API operacional',
    down: 'API indisponível',
    error: 'Não foi possível consultar a API',
  }[health]

  return (
    <main className="page">
      <section className="card">
        <span className="eyebrow">FUNDAÇÃO · FASE 00</span>
        <h1>HookScope</h1>
        <p>Base técnica para monitoramento de APIs e integrações.</p>
        <div className={`status status--${health}`} role="status" aria-live="polite">
          <span className="status__dot" aria-hidden="true" />
          <span>{label}</span>
        </div>
      </section>
    </main>
  )
}

export default App
