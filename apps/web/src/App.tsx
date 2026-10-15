import { useCallback, useEffect, useState } from 'react'
import AuthPage from './AuthPage'
import WorkspacePage from './WorkspacePage'
import { api, currentUser, type User } from './api'

export default function App() {
  const [route, setRoute] = useState(window.location.pathname)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const navigate = useCallback((path: string) => {
    window.history.pushState({}, '', path)
    setRoute(window.location.pathname)
    window.scrollTo(0, 0)
  }, [])

  const sessionExpired = useCallback(() => {
    setUser(null)
    navigate('/login')
  }, [navigate])

  const loadSession = useCallback(async () => {
    setLoading(true)
    setError('')
    try { setUser(await currentUser()) }
    catch { setError('Não foi possível consultar sua sessão. Verifique a conexão com a API.') }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void loadSession() }, [loadSession])
  useEffect(() => {
    const onPopState = () => setRoute(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])
  useEffect(() => {
    if (!loading && user && !route.startsWith('/projects')) navigate('/projects')
    if (!loading && !user && route.startsWith('/projects')) navigate('/login')
  }, [loading, user, route, navigate])

  async function logout() {
    await api<void>('/api/v1/auth/logout', 'POST')
    setUser(null)
    navigate('/login')
  }

  if (loading) return <div className="app-splash"><div className="brand-mark">H<span>●</span></div><p>Carregando sua sessão…</p></div>
  if (error) return <div className="app-splash"><div className="brand-mark">H<span>●</span></div><p role="alert">{error}</p><button className="primary" onClick={() => void loadSession()}>Tentar novamente</button></div>
  if (!user) return <AuthPage mode={route === '/register' ? 'register' : 'login'} onNavigate={navigate} onAuthenticated={loggedIn => { setUser(loggedIn); navigate('/projects') }} />
  return <WorkspacePage user={user} route={route} navigate={navigate} logout={logout} sessionExpired={sessionExpired} />
}
