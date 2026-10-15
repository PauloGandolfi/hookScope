import { useEffect, useState, type FormEvent } from 'react'
import { api, type User } from './api'

type Props = {
  mode: 'login' | 'register'
  onAuthenticated: (user: User) => void
  onNavigate: (path: string) => void
}

export default function AuthPage({ mode, onAuthenticated, onNavigate }: Props) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [health, setHealth] = useState<'loading' | 'up' | 'down'>('loading')

  useEffect(() => {
    const controller = new AbortController()
    fetch('/actuator/health', { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) return setHealth('down')
        const body = await response.json() as { status?: string }
        setHealth(body.status === 'UP' ? 'up' : 'down')
      })
      .catch(() => { if (!controller.signal.aborted) setHealth('down') })
    return () => controller.abort()
  }, [])

  useEffect(() => { setError(''); setPassword('') }, [mode])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'register') {
        await api<User>('/api/v1/auth/register', 'POST', { name, email, password })
        onNavigate('/login?registered=1')
      } else {
        const user = await api<User>('/api/v1/auth/login', 'POST', { email, password })
        onAuthenticated(user)
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Não foi possível continuar.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-intro">
        <div className="brand-mark">H<span>●</span></div>
        <div className="eyebrow">MONITORAMENTO DE INTEGRAÇÕES</div>
        <h1>HookScope</h1>
        <p>Um lugar para organizar o monitoramento das suas APIs e integrações.</p>
        <div className={`health health--${health}`} role="status">
          <span className="health-dot" />
          {health === 'loading' ? 'Verificando API…' : health === 'up' ? 'API operacional' : 'API indisponível'}
        </div>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <div className="eyebrow">ACESSO À PLATAFORMA</div>
          <h2>{mode === 'register' ? 'Criar conta' : 'Bem-vindo de volta'}</h2>
          <p className="muted">{mode === 'register' ? 'Comece criando sua conta.' : 'Entre para acessar seus projetos.'}</p>
          {mode === 'login' && window.location.search.includes('registered=1') && <div className="notice success">Conta criada. Faça login para continuar.</div>}
          {error && <div className="notice error" role="alert">{error}</div>}
          <form onSubmit={event => void submit(event)}>
            {mode === 'register' && <label>Nome<input autoComplete="name" required maxLength={120} value={name} onChange={event => setName(event.target.value)} /></label>}
            <label>E-mail<input type="email" autoComplete="email" required maxLength={320} value={email} onChange={event => setEmail(event.target.value)} /></label>
            <label>Senha<input type="password" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} required minLength={mode === 'register' ? 12 : undefined} value={password} onChange={event => setPassword(event.target.value)} /></label>
            {mode === 'register' && <p className="hint">Use pelo menos 12 caracteres.</p>}
            <button className="primary full" disabled={busy}>{busy ? 'Aguarde…' : mode === 'register' ? 'Criar conta' : 'Entrar'}</button>
          </form>
          <p className="switch">{mode === 'register' ? 'Já tem uma conta?' : 'Ainda não tem uma conta?'}{' '}
            <button className="text-button" onClick={() => onNavigate(mode === 'register' ? '/login' : '/register')}>
              {mode === 'register' ? 'Fazer login' : 'Criar conta'}
            </button>
          </p>
        </div>
      </section>
    </main>
  )
}
