import { request, fetchApi } from './http'
export interface BackgroundImage { attachmentId:number; slotNo:number; originalName:string; filePath:string; allowHomeBackground:boolean; fixed:boolean }
export interface Settings { mode:'RANDOM'|'FIXED'; fixedPath:string|null; fixedImage:BackgroundImage|null; files:{imageCount:number;documentCount:number;totalBytes:number} }
export const readSettings=(signal?:AbortSignal)=>request<Settings>('/settings',{signal})
export const readImages=(signal?:AbortSignal)=>request<BackgroundImage[]>('/settings/images',{signal})
export const updateSettings=(values:Record<string,string|null>)=>request<Settings>('/settings',{method:'PUT',body:JSON.stringify(values)})
export async function exportBackup(signal:AbortSignal) {
  const response=await fetchApi('/backup/export',{signal})
  const blob=await response.blob()
  const name=/filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition')||'')?.[1] || 'Life1000_Backup.zip'
  const url=URL.createObjectURL(blob)
  try { const link=document.createElement('a');link.href=url;link.download=name;document.body.append(link);link.click();link.remove() }
  finally { window.setTimeout(()=>URL.revokeObjectURL(url),1000) }
}
