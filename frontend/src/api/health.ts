export async function checkConnection(): Promise<void> {
  const response = await fetch('/api/health', { signal: AbortSignal.timeout(5000) })
  if (!response.ok) throw new Error('Connection failed')
  const body: unknown = await response.json()
  if (!body || typeof body !== 'object' || !('status' in body) || body.status !== 'UP') {
    throw new Error('Unexpected health response')
  }
}
