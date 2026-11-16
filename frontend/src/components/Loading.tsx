export function Loading({ label = 'Загрузка…' }: { label?: string }) { return <div className="loading" role="status"><span className="spinner"/>{label}</div> }
