import { randomUUID } from 'node:crypto'
import { setTimeout as wait } from 'node:timers/promises'

const scenarios = Object.freeze([
  { type: 'payment.authorization', integration: 'payments', provider: 'example-pay' },
  { type: 'payment.capture', integration: 'payments', provider: 'example-pay' },
  { type: 'order.fulfillment', integration: 'commerce', provider: 'example-store' },
  { type: 'shipment.dispatch', integration: 'logistics', provider: 'example-logistics' },
])

function integer(value, name, { minimum, maximum }) {
  if (!/^\d+$/.test(value ?? '')) {
    throw new Error(`${name} deve ser um número inteiro.`)
  }

  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} deve estar entre ${minimum} e ${maximum}.`)
  }
  return parsed
}

function statusFor(random) {
  const roll = random()
  if (roll < 0.72) return 'SUCCESS'
  if (roll < 0.87) return 'ERROR'
  if (roll < 0.96) return 'TIMEOUT'
  return 'CANCELLED'
}

export function loadConfig(environment = process.env) {
  const apiKey = environment.HOOKSCOPE_API_KEY?.trim()
  if (!apiKey) {
    throw new Error('HOOKSCOPE_API_KEY é obrigatória.')
  }

  const configuredUrl = environment.HOOKSCOPE_API_URL?.trim() || 'http://localhost:8080'
  let apiUrl
  try {
    apiUrl = new URL(configuredUrl).toString().replace(/\/$/, '')
  } catch {
    throw new Error('HOOKSCOPE_API_URL deve ser uma URL válida.')
  }

  return {
    apiKey,
    apiUrl,
    intervalMs: integer(environment.SIMULATOR_INTERVAL_MS ?? '1000', 'SIMULATOR_INTERVAL_MS', {
      minimum: 100,
      maximum: 60_000,
    }),
    eventCount: integer(environment.SIMULATOR_EVENT_COUNT ?? '0', 'SIMULATOR_EVENT_COUNT', {
      minimum: 0,
      maximum: 100_000,
    }),
    requestTimeoutMs: integer(environment.SIMULATOR_REQUEST_TIMEOUT_MS ?? '5000', 'SIMULATOR_REQUEST_TIMEOUT_MS', {
      minimum: 100,
      maximum: 60_000,
    }),
  }
}

export function createEvent({
  random = Math.random,
  now = () => new Date(),
  id = randomUUID,
} = {}) {
  const scenario = scenarios[Math.floor(random() * scenarios.length)]
  const status = statusFor(random)
  const durationMs = status === 'TIMEOUT'
    ? 5_000 + Math.floor(random() * 5_001)
    : 40 + Math.floor(random() * 1_961)

  return {
    eventId: id(),
    type: scenario.type,
    status,
    durationMs,
    occurredAt: now().toISOString(),
    metadata: {
      integration: scenario.integration,
      provider: scenario.provider,
      attempt: 1 + Math.floor(random() * 3),
      simulator: true,
    },
  }
}

export async function sendEvent(config, event, {
  fetchImpl = fetch,
  signal,
} = {}) {
  const timeoutSignal = AbortSignal.timeout(config.requestTimeoutMs)
  const requestSignal = signal
    ? AbortSignal.any([signal, timeoutSignal])
    : timeoutSignal

  const response = await fetchImpl(`${config.apiUrl}/api/v1/events`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${config.apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(event),
    signal: requestSignal,
  })

  const responseText = await response.text()
  let responseBody = null
  if (responseText) {
    try {
      responseBody = JSON.parse(responseText)
    } catch {
      responseBody = responseText
    }
  }

  if (!response.ok) {
    const detail = typeof responseBody === 'object' && responseBody?.code
      ? ` (${responseBody.code})`
      : ''
    throw new Error(`HookScope respondeu HTTP ${response.status}${detail}.`)
  }

  return { status: response.status, body: responseBody }
}

export async function runSimulator(config, {
  fetchImpl = fetch,
  random = Math.random,
  now = () => new Date(),
  id = randomUUID,
  logger = console,
  sleeper = (milliseconds, signal) => wait(milliseconds, undefined, { signal }),
  signal,
} = {}) {
  let sent = 0

  while (!signal?.aborted && (config.eventCount === 0 || sent < config.eventCount)) {
    const event = createEvent({ random, now, id })
    const result = await sendEvent(config, event, { fetchImpl, signal })
    sent += 1
    logger.log(
      `[${sent}] ${event.type} ${event.status} -> HTTP ${result.status} (${event.eventId})`,
    )

    if (!signal?.aborted && (config.eventCount === 0 || sent < config.eventCount)) {
      await sleeper(config.intervalMs, signal)
    }
  }

  return sent
}
