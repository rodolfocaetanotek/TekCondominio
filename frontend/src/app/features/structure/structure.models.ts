export type UnitType = 'APARTMENT' | 'HOUSE' | 'ROOM' | 'STORE' | 'PARKING' | 'OTHER';

export const UNIT_TYPE_LABELS: Record<UnitType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE: 'Casa',
  ROOM: 'Sala',
  STORE: 'Loja',
  PARKING: 'Vaga',
  OTHER: 'Outro',
};

export interface Building {
  id: string;
  name: string;
  description?: string;
  active: boolean;
  activeUnits: number;
}

export interface Unit {
  id: string;
  buildingId?: string;
  buildingName?: string;
  identifier: string;
  label: string;
  type: UnitType;
  floor?: string;
  area?: number;
  idealFraction: number;
  notes?: string;
  active: boolean;
  version: number;
  ownerName?: string;
  financialResponsibleName?: string;
}

export interface UnitRequest {
  buildingId: string | null;
  identifier: string;
  type: UnitType;
  floor: string | null;
  area: number | null;
  idealFraction: number | null;
  notes: string | null;
  version?: number;
}

export interface FractionSummary {
  total: number;
  activeUnits: number;
  balanced: boolean;
  difference: number;
}

export interface ImportResult {
  dryRun: boolean;
  lines: number;
  buildingsCreated: number;
  unitsCreated: number;
  peopleCreated: number;
  peopleReused: number;
  errors: { line: number; message: string }[];
}

export type OccupancyRole = 'OWNER' | 'TENANT' | 'RESIDENT';

export const OCCUPANCY_ROLE_LABELS: Record<OccupancyRole, string> = {
  OWNER: 'Proprietário',
  TENANT: 'Inquilino',
  RESIDENT: 'Morador',
};

export interface Occupancy {
  id: string;
  condominiumId: string;
  unitId: string;
  unitLabel: string;
  personId: string;
  personName: string;
  role: OccupancyRole;
  financialResponsible: boolean;
  startDate: string;
  endDate?: string;
  endReason?: string;
  current: boolean;
}

export type PersonKind = 'INDIVIDUAL' | 'COMPANY';

export interface PersonSummary {
  id: string;
  name: string;
  kind: PersonKind;
  document?: string;
  email?: string;
  phone?: string;
  active: boolean;
  units: string[];
}

export interface PersonDetail {
  id: string;
  name: string;
  kind: PersonKind;
  document?: string;
  email?: string;
  phone?: string;
  secondaryPhone?: string;
  notes?: string;
  active: boolean;
  version: number;
  portalUser: boolean;
  occupancies: Occupancy[];
}

export interface PersonRequest {
  name: string;
  kind: PersonKind;
  document: string | null;
  email: string | null;
  phone: string | null;
  secondaryPhone: string | null;
  notes: string | null;
  version?: number;
}
