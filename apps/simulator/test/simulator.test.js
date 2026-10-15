import assert from 'node:assert/strict'
import test from 'node:test'
import { createEvent, loadConfig, runSimulator, sendEvent } from '../src/simulator.js'

const baseConfig = {
  apiKey: 'hs_example_secret',
  apiUrl: 'http://localhost:8080',
  intervalMs: 100,
  eventCount: 1,
  requestTimeoutMs: 5_000,
}

test('loadConfig aplica padrões e exige a API Key', () => {
  assert.throws(() => loadConfig({}), /HOOKSCOPE_API_KEY/)

  assert.deepEqual(loadConfig({ HOOKSCOPE_API_KEY: '  hs_example_secret  ' }), {
    apiKey: 'hs_example_secret',
    apiUrl: 'http://localhost:8080',
    intervalMs: 1_000,
    eventCount: 0,
    requestTimeoutMs: 5_000,
  })
})

test('loadConfig rejeita limites inválidos', () => {
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', SIMULATOR_INTERVAL_MS: '99' }),
    /SIMULATOR_INTERVAL_MS/,
  )
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', SIMULATOR_EVENT_COUNT: '-1' }),
    /SIMULATOR_EVENT_COUNT/,
  )
  assert.throws(
    () => loadConfig({ HOOKSCOPE_API_KEY: 'key', HOOKSCOPE_API_URL: 'not a url' }),
    /HOOKSCOPE_API_URL/,
  )
})

test('createEvent produz um evento compatível com o contrato v1', () => {
  const values = [0, 0.8, 0.5, 0.75]
  const event = createEvent({
    random: () => values.shift(),
    now: () => new Date('2026-10-15T12:00:00.000Z'),
    id: () => '018f4f9a-93f2-7f13-9d7d-7d7a3f58de21',
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
      simulator: true,
    },
  })
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

test('runSimulator respeita a quantidade configurada', async () => {
  const delivered = []
  let sleeps = 0
  let sequence = 0
  const config = { ...baseConfig, eventCount: 2 }

  const sent = await runSimulator(config, {
    fetchImpl: async (_url, options) => {
      delivered.push(JSON.parse(options.body))
      return { ok: true, status: 201, text: async () => '{}' }
    },
    id: () => `event-${++sequence}`,
    sleeper: async () => { sleeps += 1 },
    logger: { log: () => {} },
  })

  assert.equal(sent, 2)
  assert.deepEqual(delivered.map(event => event.eventId), ['event-1', 'event-2'])
  assert.equal(sleeps, 1)
})
