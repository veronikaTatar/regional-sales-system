import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

interface Props {
  title: string
  description?: string
  busy: boolean
  onClose: () => void
  children: ReactNode
}

export function AdminDialog({ title, description, busy, onClose, children }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  return (
    <dialog
      ref={dialog}
      className="admin-dialog"
      aria-labelledby={titleId}
      onCancel={event => {
        event.preventDefault()
        if (!busy) onClose()
      }}
    >
      <header className="admin-dialog-heading">
        <div>
          <h2 id={titleId}>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        <button type="button" className="icon-button" aria-label="Закрыть" onClick={onClose} disabled={busy}>
          <X size={19}/>
        </button>
      </header>
      {children}
    </dialog>
  )
}
