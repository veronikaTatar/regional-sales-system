import { ChartNoAxesCombined } from 'lucide-react'
export function Brand({ inverse = false }: { inverse?: boolean }) {
  return <div className={`brand ${inverse ? 'brand-inverse' : ''}`}><span className="brand-icon"><ChartNoAxesCombined size={25} strokeWidth={2.2}/></span><div><strong>регион<span>·</span></strong><small>УПРАВЛЕНИЕ СБЫТОМ</small></div></div>
}
