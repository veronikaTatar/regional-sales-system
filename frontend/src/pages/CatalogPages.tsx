import { MapPinned, ShieldCheck } from 'lucide-react'
import type { Region } from '../types'
import { useResource } from '../hooks/useResource'
import { useAuth } from '../features/auth/AuthContext'
import { regionsByRegistrationCode } from '../features/regions/regionCodes'
import { PageHeading } from './WorkspacePages'
import { Loading } from '../components/Loading'
function ResourceError({ message, retry }: { message:string; retry:()=>void }) { return <div className="alert alert-error" role="alert">{message}<button className="text-button" onClick={retry}>Повторить</button></div> }
export function RegionsPage({ embedded = false }: { embedded?: boolean }) {
  const { user } = useAuth()
  const { data, loading, error, refresh } = useResource<Region[]>('/regions')
  const regions = regionsByRegistrationCode(data ?? [])

  return <>
    {!embedded && <PageHeading eyebrow="ПРОДАЖИ" title="Регионы"
      subtitle={user?.role === 'MANAGER' ? 'Регионы, закреплённые за вами на текущую дату.' : 'Шесть областей и город Минск — география сбыта предприятия.'}>
      <span className="outline-badge"><MapPinned size={14}/>Республика Беларусь</span>
    </PageHeading>}

    {loading ? <Loading/> : error ? <ResourceError message={error} retry={() => void refresh()}/> : regions.length ? (
      <>
        <div className="section-title"><h2>Доступные регионы</h2><span>{regions.length} доступно</span></div>
        <div className="region-grid">
          {regions.map(region => (
            <article className="region-card" key={region.id}>
              <div className="region-card-top">
                {region.registrationCode !== undefined && (
                  <span className="region-number" title="Код на автомобильных номерах">Код {region.registrationCode}</span>
                )}
                <MapPinned size={22} strokeWidth={1.5}/>
              </div>
              <h2>{region.name}</h2>
              <div className="region-card-bottom">
                <span className={`mini-status ${region.active ? '' : 'inactive'}`}>{region.active ? 'Активен' : 'Неактивен'}</span>
              </div>
            </article>
          ))}
        </div>
      </>
    ) : (
      <section className="empty-state panel">
        <MapPinned size={42} strokeWidth={1.3}/>
        <h2>Пока нет доступных регионов</h2>
        <p>Администратор назначит вам регионы.<br/>До назначения коммерческие данные регионов недоступны.</p>
      </section>
    )}

    <div className="stage-notice">
      <ShieldCheck size={21}/>
      <div>
        <strong>Региональные ограничения доступа</strong>
        <p>Администратор закрепляет регионы за менеджерами по продажам на выбранный период. Аналитикам доступны все активные регионы для сравнения показателей.</p>
      </div>
    </div>
  </>
}
