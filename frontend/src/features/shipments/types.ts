export interface Shipment {
  id: number
  shipmentDate: string
  referenceNumber: string | null
  regionId: number
  regionName: string
  productId: number
  sku: string
  productName: string
  unit: string
  quantity: number
  unitPrice: number
  totalAmount: string
}

export interface ShipmentPage {
  shipments: Shipment[]
  total: number
  page: number
  pageSize: number
}

export interface ShipmentOptions {
  canRecord: boolean
  regions: { id: number; name: string }[]
  products: { id: number; sku: string; name: string; unit: string; active: boolean }[]
}

export interface ShipmentImportResult {
  total: number
  created: number
  unchanged: number
  skipped: number
}
