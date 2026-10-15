import { randomUUID } from 'node:crypto'
import { setTimeout as wait } from 'node:timers/promises'

const scenarios = Object.freeze([
  { type: 'payment.authorization', integration: 'payments', provider: 'example-pay' },
  { type: 'payment.capture', integration: 'payments', provider: 'example-pay' },
  { type: 'order.fulfillment', integration: 'commerce', provider: 'example-store' },
  { type: 'shipment.dispatch', integration: 'logistics', provider: 'example-logistics' },
])

export const loadProfiles = Object.freeze({
  demo: Object.freeze({ intervalMs: 1_000, batchSize: 1 }),
  steady: Object.freeze({ intervalMs: 250, batchSize: 1 }),
  burst: Object.freeze({ intervalMs: 1_000, batchSize: 10 }),
})

const failureModes = Object.freeze(['mixed', 'error', 'timeout', 'cancelled'])
const mixedFailureStatuses = Object.freeze(['ERROR', 'TIMEOUT', 'CANCELLED'])

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

function configuredProfile(value) {
  const name = (value || 'demo').trim().toLowerCase()
  const profile = loadProfiles[name]
  if (!profile) {
    throw new Error(`SIMULATOR_PROFILE deve ser um de: ${Object.keys(loadProfiles).join(', ')}.`)
  }
  return { name, ...profile }
}

function configuredFailureMode(value) {
  const mode = (value || 'mixed').trim().toLowerCase()
  if (!failureModes.includes(mode)) {
    throw new Error(`SIMULATOR_FAILURE_MODE deve ser um de: ${failureModes.join(', ')}.`)
  }
  return mode
}

function eventStatus(random, failureRate, failureMode) {
  if (random() * 100 >= failureRate) return { status: 'SUCCESS', simulatedFailure: false }

  const status = failureMode === 'mixed'
    ? mixedFailureStatuses[Math.floor(random() * mixedFailureStatuses.length)]
    : failureMode.toUpperCase()
  return { status, simulatedFailure: true }
}

export function loadConfig(environment = process.env) {
  const apiKey = environment.HOOKSCOPE_API_KEY?.trim()
  if (!apiKey) {
    throw new Error('HOOKSCOPE_API_KEY é obrigatória.')
  }

  const configuredUrl = environment.HOOKSCOPE_API_URL?.trim() || 'http://localhost:8080'
  let parsedUrl
  try {
    parsedUrl = new URL(configuredUrl)
  } catch {
    throw new Error('HOOKSCOPE_API_URL deve ser uma URL HTTP válida.')
  }
  if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
    throw new Error('HOOKSCOPE_API_URL deve ser uma URL HTTP válida.')
  }

  const profile = configuredProfile(environment.SIMULATOR_PROFILE)
  return {
    apiKey,
    apiUrl: parsedUrl.toString().replace(/\/$/, ''),
    profile: profile.name,
    batchSize: profile.batchSize,
    intervalMs: environment.SIMULATOR_INTERVAL_MS === undefined
      ? profile.intervalMs
      : integer(environment.SIMULATOR_INTERVAL_MS, 'SIMULATOR_INTERVAL_MS', {
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
    failureRate: integer(environment.SIMULATOR_FAILURE_RATE ?? '20', 'SIMULATOR_FAILURE_RATE', {
      minimum: 0,
      maximum: 100,
    }),
    failureMode: configuredFailureMode(environment.SIMULATOR_FAILURE_MODE),
  }
}

export function createEvent({
  random = Math.random,
  now = () => new Date(),
  id = randomUUID,
  failureRate = 20,
  failureMode = 'mixed',
  profile = 'demo',
} = {}) {
  const scenario = scenarios[Math.floor(random() * scenarios.length)]
  const { status, simulatedFailure } = eventStatus(random, failureRate, failureMode)
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
      loadProfile: profile,
      simulatedFailure,
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
    const remaining = config.eventCount === 0 ? config.batchSize : config.eventCount - sent
    const batchSize = Math.min(config.batchSize, remaining)
    const events = Array.from({ length: batchSize }, () => createEvent({
      random,
      now,
      id,
      failureRate: config.failureRate,
      failureMode: config.failureMode,
      profile: config.profile,
    }))
    const results = await Promise.all(
      events.map(event => sendEvent(config, event, { fetchImpl, signal })),
    )

    events.forEach((event, index) => {
      sent += 1
      logger.log(
        `[${sent}] ${event.type} ${event.status} -> HTTP ${results[index].status} (${event.eventId})`,
      )
    })

    if (!signal?.aborted && (config.eventCount === 0 || sent < config.eventCount)) {
      await sleeper(config.intervalMs, signal)
    }
  }

  return sent
}
