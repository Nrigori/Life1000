import { request } from './http'

export async function checkConnection(): Promise<void> {
  const body = await request<{ status: string }>('/health')
  if (body.status !== 'UP') throw new Error('Unexpected health response')
}
