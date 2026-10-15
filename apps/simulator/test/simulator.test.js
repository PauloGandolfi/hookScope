import assert from 'node:assert/strict'
import test from 'node:test'
import { createEvent, loadConfig, runSimulator, sendEvent } from '../src/simulator.js'

const baseConfig = {
  apiKey: 'hs_example_secret',
  apiUrl: 'http://localhost:8080',
  profile: 'demo',
  batchSize: 1,
  intervalMs: 1_000,
  eventCount: 1,
  requestTimeoutMs: 5_000,
  failureRate: 20,
  failureMode: 'mixed',
}

test('loadConfig aplica o perfil demo e exige a API Key', () => {
  assert.throws(() => loadConfig({}), /HOOKSCOPE_API_KEY/)

  assert.deepEqual(loadConfig({ HOOKSCOPE_API_KEY: '  hs_example_secret  ' }), baseConfig)
})

test('loadConfig aplica perfis de carga e permite sobrescrever o intervalo', () => {
  const burst = loadConfig({
    HOOKSCOPE_API_KEY: 'key',
    SIMULATOR_PROFILE: 'BURST',
  })
  assert.equal(burst.profile, 'burst')
  assert.equal(burst.batchSize, 10)
  assert.equal(burst.intervalMs, 1_000)

  const steady = loadConfig({
    HOOKSCOPE_API_KEY: 'key',
    SIMULATOR_PROFILE: 'steady',
    SIMULATOR_INTERVAL_MS: '800',
  })
  assert.equal(steady.profile, 'steady')
  assert.equal(steady.batchSize, 1)
  assert.equal(steady.intervalMs, 800)
})

test('loadConfig valida perfil, taxa, modo de falha e URL', () => {
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', SIMULATOR_PROFILE: 'unknown' }),
    /SIMULATOR_PROFILE/,
  )
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', SIMULATOR_FAILURE_RATE: '101' }),
    /SIMULATOR_FAILURE_RATE/,
  )
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', SIMULATOR_FAILURE_MODE: 'random' }),
    /SIMULATOR_FAILURE_MODE/,
  )
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', HOOKSCOPE_API_URL: 'ftp://example.test' }),
    /HOOKSCOPE_API_URL/,
  )
})

test('createEvent produz uma falha controlada compatível com o contrato v1', () => {
  const values = [0, 0.1, 0, 0.5, 0.75]
  const event = createEvent({
    random: () => values.shift(),
    now: () => new Date('2026-10-15T12:00:00.000Z'),
    id: () => '018f4f9a-93f2-7f13-9d7d-7d7a3f58de21',
    failureRate: 100,
    failureMode: 'mixed',
    profile: 'burst',
  })

  assert.deepEqual(event, {
    eventId: '018f4f9a-93f2-7f13-9d7d-7d7a3f58de21',
    type: 'payment.authorization',
    status: 'ERROR',
    durationMs: 1_020,
    occurredAt: '2026-10-15T12:00:00.000Z',
    metadata: {
      integration: 'payments',
      provider: 'example-pay',
      attempt: 3,
      loadProfile: 'burst',
      simulatedFailure: true,
      simulator: true,
    },
  })
})

test('createEvent respeita taxa zero e modo fixo de falha', () => {
  const success = createEvent({ random: () => 0.5, failureRate: 0 })
  assert.equal(success.status, 'SUCCESS')
  assert.equal(success.metadata.simulatedFailure, false)

  const timeout = createEvent({
    random: () => 0,
    failureRate: 100,
    failureMode: 'timeout',
  })
  assert.equal(timeout.status, 'TIMEOUT')
  assert.equal(timeout.metadata.simulatedFailure, true)
  assert.ok(timeout.durationMs >= 5_000)
})

test('sendEvent autentica e envia JSON ao endpoint de ingestão', async () => {
  let request
  const fetchImpl = async (url, options) => {
    request = { url, options }
    return {
      ok: true,
      status: 201,
      text: async () => '{"duplicate":false}',
    }
  }
  const event = createEvent({ id: () => 'event-id' })

  const result = await sendEvent(baseConfig, event, { fetchImpl })

  assert.equal(request.url, 'http://localhost:8080/api/v1/events')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.headers.Authorization, 'Bearer hs_example_secret')
  assert.deepEqual(JSON.parse(request.options.body), event)
  assert.deepEqual(result, { status: 201, body: { duplicate: false } })
})

test('sendEvent expõe o código de erro retornado pela API', async () => {
  const fetchImpl = async () => ({
    ok: false,
    status: 401,
    text: async () => '{"code":"invalid_api_key","message":"API Key inválida."}',
  })

  await assert.rejects(
    sendEvent(baseConfig, createEvent(), { fetchImpl }),
    /HTTP 401 \(invalid_api_key\)/,
  )
})

test('runSimulator envia lotes concorrentes sem exceder a quantidade', async () => {
  const delivered = []
  let sleeps = 0
  let sequence = 0
  const config = {
    ...baseConfig,
    profile: 'burst',
    batchSize: 10,
    eventCount: 12,
    failureRate: 0,
  }

  const sent = await runSimulator(config, {
    fetchImpl: async (_url, options) => {
      delivered.push(JSON.parse(options.body))
      return { ok: true, status: 201, text: async () => '{}' }
    },
    id: () => `event-${++sequence}`,
    sleeper: async () => { sleeps += 1 },
    logger: { log: () => {} },
  })

  assert.equal(sent, 12)
  assert.equal(delivered.length, 12)
  assert.deepEqual(delivered.map(event => event.eventId), Array.from(
    { length: 12 },
    (_, index) => `event-${index + 1}`,
  ))
  assert.ok(delivered.every(event => event.metadata.loadProfile === 'burst'))
  assert.equal(sleeps, 1)
})
