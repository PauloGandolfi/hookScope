import { loadConfig, runSimulator } from './simulator.js'

const controller = new AbortController()

process.once('SIGINT', () => {
  console.log('\nEncerrando o simulador...')
  controller.abort()
})

try {
  const config = loadConfig()
  const mode = config.eventCount === 0 ? 'contínuo' : `${config.eventCount} evento(s)`
  console.log(`HookScope Simulator iniciado: ${mode}, intervalo de ${config.intervalMs} ms.`)
  const sent = await runSimulator(config, { signal: controller.signal })
  console.log(`Simulador encerrado após ${sent} evento(s).`)
} catch (error) {
  if (error?.name !== 'AbortError') {
    console.error(error instanceof Error ? error.message : error)
    process.exitCode = 1
  }
}
