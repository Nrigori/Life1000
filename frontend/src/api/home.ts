import { request } from './http'
export interface Quote { id: number; content: string; source: string | null; includeHome: boolean }
export interface QuoteInput { content: string; source: string | null; includeHome: boolean }
export interface Stats {
  writtenCount: number; completedCount: number; inProgressCount: number; blankCount: number
  completedThisYear: number; imageCount: number; documentCount: number; quoteCount: number
}
export const readStats = (signal?: AbortSignal) => request<Stats>('/stats', { signal })
export const randomBackground = (signal?: AbortSignal) => request<{ id: number; originalName: string } | undefined>('/home/background', { signal })
export const randomQuote = (signal?: AbortSignal) => request<Quote | undefined>('/quotes/random', { signal })
export const readQuotes = (keyword = '', signal?: AbortSignal) => request<Quote[]>('/quotes?'+new URLSearchParams({ keyword }), { signal })
export const saveQuote = (input: QuoteInput, id?: number) => request<Quote>(id ? '/quotes/'+id : '/quotes',
  { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) })
export const deleteQuote = (id: number) => request<void>('/quotes/'+id, { method: 'DELETE' })
