import { Link } from 'react-router-dom'

export function WorkspaceTabs({ label, active, items }: {
  label: string
  active: string
  items: { id: string; title: string; to: string }[]
}) {
  return <nav className="workspace-tabs" aria-label={label}>
    {items.map(item => <Link key={item.id} to={item.to} aria-current={active === item.id ? 'page' : undefined}>{item.title}</Link>)}
  </nav>
}
