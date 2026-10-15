import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiClientError, type ApiKey, type CreatedApiKey, type Project, type ProjectPage, type User } from './api'
import EventDashboard from './EventDashboard'

type Props = {
  user: User
  route: string
  navigate: (path: string) => void
  logout: () => Promise<void>
  sessionExpired: () => void
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(new Date(value))
}

function errorMessage(failure: unknown, sessionExpired: () => void) {
  if (failure instanceof ApiClientError && failure.status === 401) sessionExpired()
  return failure instanceof Error ? failure.message : 'Não foi possível concluir a operação.'
}

export default function WorkspacePage({ user, route, navigate, logout, sessionExpired }: Props) {
  const [logoutError, setLogoutError] = useState('')
  const projectId = route.match(/^\/projects\/([0-9a-f-]{36})$/)?.[1]

  return (
    <div className="workspace">
      <aside className="sidebar">
        <button className="sidebar-brand" onClick={() => navigate('/projects')}><span className="brand-mark small">H<span>●</span></span> HookScope</button>
        <nav aria-label="Principal"><button className="nav-item active" onClick={() => navigate('/projects')}>◫ <span>Projetos</span></button></nav>
        <div className="sidebar-bottom">
          <div className="account"><div className="avatar">{user.name.charAt(0).toUpperCase()}</div><div><strong>{user.name}</strong><small>{user.email}</small></div></div>
          {logoutError && <p className="sidebar-error" role="alert">{logoutError}</p>}
          <button className="logout-button" onClick={() => void logout().catch(failure => setLogoutError(errorMessage(failure, sessionExpired)))}>Sair da conta</button>
        </div>
      </aside>
      <div className="workspace-main">
        <header className="topbar"><span>Workspace pessoal</span><span className="topbar-user">{user.name}</span></header>
        {projectId ? <ProjectDetail key={projectId} projectId={projectId} navigate={navigate} sessionExpired={sessionExpired} /> :
          <ProjectList navigate={navigate} sessionExpired={sessionExpired} />}
      </div>
    </div>
  )
}

function ProjectList({ navigate, sessionExpired }: Pick<Props, 'navigate' | 'sessionExpired'>) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<ProjectPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    let active = true
    api<ProjectPage>(`/api/v1/projects?page=${page}&size=20`)
      .then(result => { if (active) { setData(result); setError(''); setLoading(false) } })
      .catch(failure => { if (active) { setError(errorMessage(failure, sessionExpired)); setLoading(false) } })
    return () => { active = false }
  }, [page, sessionExpired])

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreating(true)
    setError('')
    try {
      const project = await api<Project>('/api/v1/projects', 'POST', { name, description })
      navigate(`/projects/${project.id}`)
    } catch (failure) {
      setError(errorMessage(failure, sessionExpired))
    } finally {
      setCreating(false)
    }
  }

  return <main className="content">
    <div className="page-heading"><div><div className="eyebrow">ORGANIZAÇÃO</div><h1>Seus projetos</h1><p>Organize as integrações que você vai monitorar.</p></div><span className="count-pill">{data?.totalElements ?? 0} projetos</span></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    <div className="content-grid">
      <section className="panel">
        <div className="panel-heading"><h2>Projetos</h2><span className="muted">Página {page + 1}</span></div>
        {loading ? <p className="muted">Carregando projetos…</p> : data?.content.length ? <div className="project-list">{data.content.map(project =>
          <button className="project-row" key={project.id} onClick={() => navigate(`/projects/${project.id}`)}>
            <span className="project-icon">⌁</span><span><strong>{project.name}</strong><small>{project.description || 'Sem descrição'}</small></span><span className="arrow">→</span>
          </button>)}</div> : <div className="empty-state"><div className="empty-symbol">◇</div><h3>Nenhum projeto ainda</h3><p>Crie o primeiro projeto para começar.</p></div>}
        {data && data.totalPages > 1 && <div className="pagination"><button disabled={page === 0} onClick={() => { setLoading(true); setPage(page - 1) }}>Anterior</button><span>{page + 1} / {data.totalPages}</span><button disabled={page + 1 >= data.totalPages} onClick={() => { setLoading(true); setPage(page + 1) }}>Próxima</button></div>}
      </section>
      <section className="panel create-panel"><div className="panel-heading"><h2>Novo projeto</h2></div><p className="muted">Dê um nome ao ambiente que deseja acompanhar.</p>
        <form onSubmit={event => void create(event)}><label>Nome<input required maxLength={120} placeholder="Ex.: API de pagamentos" value={name} onChange={event => setName(event.target.value)} /></label>
          <label>Descrição<textarea maxLength={1000} rows={4} placeholder="O que este projeto acompanha?" value={description} onChange={event => setDescription(event.target.value)} /></label>
          <button className="primary" disabled={creating}>{creating ? 'Criando…' : 'Criar projeto'}</button></form>
      </section>
    </div>
  </main>
}

function ProjectDetail({ projectId, navigate, sessionExpired }: { projectId: string; navigate: Props['navigate']; sessionExpired: Props['sessionExpired'] }) {
  const [project, setProject] = useState<Project | null>(null)
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [keyName, setKeyName] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([api<Project>(`/api/v1/projects/${projectId}`), api<ApiKey[]>(`/api/v1/projects/${projectId}/api-keys`)])
      .then(([loadedProject, loadedKeys]) => {
        if (active) { setProject(loadedProject); setName(loadedProject.name); setDescription(loadedProject.description || ''); setKeys(loadedKeys); setLoading(false) }
      })
      .catch(failure => { if (active) { setError(errorMessage(failure, sessionExpired)); setLoading(false) } })
    return () => { active = false }
  }, [projectId, sessionExpired])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try { setProject(await api<Project>(`/api/v1/projects/${projectId}`, 'PATCH', { name, description })) }
    catch (failure) { setError(errorMessage(failure, sessionExpired)) }
    finally { setBusy(false) }
  }

  async function createKey(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setCreatedKey(null); setCopied(false)
    try {
      const result = await api<CreatedApiKey>(`/api/v1/projects/${projectId}/api-keys`, 'POST', { name: keyName })
      setCreatedKey(result.key)
      setKeys(current => [result.apiKey, ...current])
      setKeyName('')
    } catch (failure) { setError(errorMessage(failure, sessionExpired)) }
    finally { setBusy(false) }
  }

  async function revoke(keyId: string) {
    if (!window.confirm('Revogar esta API Key? Ela deixará de ser válida imediatamente.')) return
    setBusy(true); setError('')
    try {
      await api<void>(`/api/v1/projects/${projectId}/api-keys/${keyId}`, 'DELETE')
      setKeys(current => current.map(key => key.id === keyId ? { ...key, revokedAt: new Date().toISOString() } : key))
    } catch (failure) { setError(errorMessage(failure, sessionExpired)) }
    finally { setBusy(false) }
  }

  async function copyKey() {
    if (!createdKey) return
    try { await navigator.clipboard.writeText(createdKey); setCopied(true) }
    catch { setError('Não foi possível copiar automaticamente. Selecione a chave e copie manualmente.') }
  }

  return <main className="content">
    <button className="back-link" onClick={() => navigate('/projects')}>← Todos os projetos</button>
    {loading ? <div className="panel"><p className="muted">Carregando projeto…</p></div> : !project ? <div className="notice error" role="alert">{error || 'Projeto não encontrado.'}</div> : <>
      <div className="page-heading"><div><div className="eyebrow">PROJETO</div><h1>{project.name}</h1><p>{project.description || 'Sem descrição'}</p></div><span className="count-pill">Criado em {formatDate(project.createdAt)}</span></div>
      {error && <div className="notice error" role="alert">{error}</div>}
      <EventDashboard projectId={projectId} sessionExpired={sessionExpired} />
      <div className="content-grid detail-grid">
        <section className="panel"><div className="panel-heading"><h2>Informações</h2></div><form onSubmit={event => void save(event)}>
          <label>Nome<input required maxLength={120} value={name} onChange={event => setName(event.target.value)} /></label>
          <label>Descrição<textarea rows={4} maxLength={1000} value={description} onChange={event => setDescription(event.target.value)} /></label>
          <button className="primary" disabled={busy}>{busy ? 'Salvando…' : 'Salvar alterações'}</button></form></section>
        <section className="panel"><div className="panel-heading"><h2>Nova API Key</h2></div><p className="muted">Cada chave pertence somente a este projeto.</p>
          <form onSubmit={event => void createKey(event)}><label>Nome da chave<input required maxLength={120} placeholder="Ex.: Produção" value={keyName} onChange={event => setKeyName(event.target.value)} /></label>
            <button className="primary" disabled={busy}>{busy ? 'Criando…' : 'Gerar API Key'}</button></form></section>
      </div>
      {createdKey && <section className="secret-panel" role="status"><div><strong>Copie sua nova API Key agora</strong><p>Ela será exibida somente neste momento. Guarde-a em um local seguro.</p></div><div className="secret-row"><code>{createdKey}</code><button onClick={() => void copyKey()}>{copied ? 'Copiada' : 'Copiar'}</button></div><button className="text-button" onClick={() => setCreatedKey(null)}>Já salvei a chave</button></section>}
      <section className="panel keys-panel"><div className="panel-heading"><h2>API Keys</h2><span className="muted">{keys.length} chaves</span></div>
        {keys.length === 0 ? <div className="empty-state"><div className="empty-symbol">⚿</div><h3>Nenhuma chave criada</h3><p>Gere uma chave para preparar a integração deste projeto.</p></div> :
          <div className="keys-list">{keys.map(key => <div className="key-row" key={key.id}><div><strong>{key.name}</strong><small><code>{key.keyPrefix}</code> · Criada em {formatDate(key.createdAt)}</small></div><div className="key-actions"><span className={`badge ${key.revokedAt ? 'revoked' : key.expiresAt && new Date(key.expiresAt) <= new Date() ? 'expired' : 'active'}`}>{key.revokedAt ? 'Revogada' : key.expiresAt && new Date(key.expiresAt) <= new Date() ? 'Expirada' : 'Ativa'}</span>{!key.revokedAt && <button className="danger-link" disabled={busy} onClick={() => void revoke(key.id)}>Revogar</button>}</div></div>)}</div>}
      </section>
    </>}
  </main>
}
