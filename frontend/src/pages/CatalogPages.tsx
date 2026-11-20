import { useState } from 'react'
import { ArrowUpRight, MapPinned, Package, Search, ShieldCheck } from 'lucide-react'
import type { Region, Product } from '../types'
import { useResource } from '../hooks/useResource'
import { useAuth } from '../features/auth/AuthContext'
import { regionsByRegistrationCode } from '../features/regions/regionCodes'
import { PageHeading } from './WorkspacePages'
import { Loading } from '../components/Loading'
function ResourceError({ message, retry }: { message:string; retry:()=>void }) { return <div className="alert alert-error" role="alert">{message}<button className="text-button" onClick={retry}>Повторить</button></div> }
export function RegionsPage() {
  const { user } = useAuth()
  const { data, loading, error, refresh } = useResource<Region[]>('/regions')
  const regions = regionsByRegistrationCode(data ?? [])

  return <>
    <PageHeading eyebrow="ПРОДАЖИ" title="Регионы"
      subtitle={user?.role === 'MANAGER' ? 'Регионы, закреплённые за вами на текущую дату.' : 'Шесть областей и город Минск — география сбыта предприятия.'}>
      <span className="outline-badge"><MapPinned size={14}/>Республика Беларусь</span>
    </PageHeading>

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
export function ProductsPage() {
  const {data,loading,error,refresh}=useResource<Product[]>('/products')
  const [query,setQuery]=useState('')
  const filtered=data?.filter(product=>`${product.name} ${product.sku} ${product.category??''}`.toLocaleLowerCase('ru').includes(query.toLocaleLowerCase('ru')))
  return <><PageHeading eyebrow="ПРОДАЖИ" title="Продукция" subtitle="Каталог продукции предприятия. Цены указаны в белорусских рублях."/><section className="panel catalog-panel"><div className="table-toolbar"><h2>Каталог продукции<span className="count-pill">{data?.length??'—'}</span></h2><label className="search-input"><Search size={17}/><input aria-label="Поиск продукции" placeholder="Название, артикул, категория" value={query} onChange={event=>setQuery(event.target.value)}/></label></div>{loading ? <Loading/> : error ? <ResourceError message={error} retry={()=>void refresh()}/> : data?.length ? <div className="table-scroll"><table><thead><tr><th>Продукция</th><th>Артикул</th><th>Категория</th><th>Ед. изм.</th><th className="numeric">Цена, BYN</th><th>Статус</th></tr></thead><tbody>{filtered?.map(product=><tr key={product.id}><td><strong>{product.name}</strong></td><td className="monospace">{product.sku}</td><td>{product.category??'—'}</td><td>{product.unit}</td><td className="numeric">{new Intl.NumberFormat('ru-BY',{minimumFractionDigits:2,maximumFractionDigits:2}).format(product.currentPrice)}</td><td><span className={`mini-status ${product.active?'':'inactive'}`}>{product.active?'Активен':'Неактивен'}</span></td></tr>)}</tbody></table>{!filtered?.length && <div className="table-no-results">По вашему запросу ничего не найдено.</div>}</div> : <div className="empty-state"><Package size={42} strokeWidth={1.3}/><h2>Каталог пока пуст</h2><p>Справочник подключён к базе данных.<br/>Добавление и редактирование товаров появятся на следующем этапе.</p></div>}</section><div className="subtle-note"><ArrowUpRight size={16}/>Сейчас доступен просмотр каталога. Демонстрационные товары не создаются автоматически.</div></>
}
