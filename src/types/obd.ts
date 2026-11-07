/**
 * Sportage OBD Intelligence & Car Launcher - Core Type Definitions
 * Real Bluetooth ELM327 & USB Serial Telemetry.
 * Zero demo data.
 */

export type DataSource = 
  | 'OBD_LIVE' 
  | 'GPS' 
  | 'DEVICE_SENSOR';

export type ReadingState<T> =
  | { status: 'LIVE'; value: T; unit: string; timestamp: number }
  | { status: 'DISCONNECTED' }
  | { status: 'NO_DATA' }
  | { status: 'ERROR'; reason: string };

export type ConnectionState =
  | 'DISCONNECTED'
  | 'PAIRING'
  | 'CONNECTING'
  | 'INITIALIZING_ADAPTER'
  | 'NEGOTIATING_PROTOCOL'
  | 'CONNECTED'
  | 'RECONNECTING'
  | 'ERROR';

export type TransportType = 'BLUETOOTH_BLE' | 'USB_SERIAL';

export interface AdapterInfo {
  id: string;
  name: string;
  transportType: TransportType;
  elmVersion: string;
  protocol: string;
  voltage: number;
  lastPacketTime: number;
  packetsReceived: number;
}

export interface LiveTelemetry {
  rpm: ReadingState<number>;
  speed: ReadingState<number>;
  coolantTemp: ReadingState<number>;
  intakeTemp: ReadingState<number>;
  engineLoad: ReadingState<number>;
  throttlePosition: ReadingState<number>;
  stftBank1: ReadingState<number>;
  ltftBank1: ReadingState<number>;
  map: ReadingState<number>;
  maf: ReadingState<number>;
  timingAdvance: ReadingState<number>;
  batteryVoltage: ReadingState<number>;
  fuelLevel: ReadingState<number>;
  gear: number;
}

export interface LauncherApp {
  id: string;
  name: string;
  category: 'Navigation' | 'Media' | 'Communication' | 'Vehicle' | 'Utility';
  icon: string;
  urlScheme: string;
  webFallback: string;
  color: string;
  description: string;
}

export interface DtcFault {
  code: string;
  status: 'STORED' | 'PENDING' | 'PERMANENT';
  description: string;
  timestamp: number;
}

export interface RawLogPacket {
  id: string;
  timestamp: string;
  direction: 'TX' | 'RX' | 'INFO' | 'ERR';
  data: string;
}

export interface LauncherSettings {
  speedUnit: 'km/h' | 'mph';
  tempUnit: '°C' | '°F';
  keepScreenOn: boolean;
  pollingIntervalMs: number;
  defaultNavApp: 'waze' | 'google_maps';
  themeAccent: 'cyan' | 'teal' | 'red' | 'amber';
}

export interface PidDefinition<T = number> {
  mode: number;
  pid: number;
  hexCode: string;
  name: string;
  shortName: string;
  unit: string;
  min: number;
  max: number;
  priority: 'P0' | 'P1' | 'P2' | 'P3';
  description: string;
  decode: (bytes: number[]) => T;
}

export interface DtcItem {
  code: string;
  status: 'STORED' | 'PENDING' | 'PERMANENT';
  severity: 'CRITICAL' | 'WARNING' | 'ADVISORY';
  system: 'Powertrain' | 'Chassis' | 'Body' | 'Network' | 'Transmission' | 'AWD';
  title: string;
  description: string;
  symptoms: string[];
  commonCauses: string[];
  diagnosticChecklist: string[];
  verifiedSportage: boolean;
}

export interface ReadinessMonitor {
  id: string;
  name: string;
  supported: boolean;
  ready: boolean;
  continuous: boolean;
  description: string;
}

export interface Mode06TestResult {
  testId: string;
  componentId: string;
  name: string;
  currentValue: number;
  minLimit: number;
  maxLimit: number;
  unit: string;
  passed: boolean;
}

export interface VehicleProfile {
  id: string;
  make: string;
  model: string;
  year: number;
  market: string;
  engine: string;
  displacementCc: number;
  transmission: string;
  drivetrain: string;
  vin?: string;
  fuelType: 'Petrol (Gasoline)' | 'Diesel' | 'Hybrid';
  curbWeightKg: number;
  fuelCapacityLiters: number;
  verifiedPidsCount: number;
}

export interface MaintenanceItem {
  id: string;
  category: 'Fluids' | 'Filters' | 'Ignition' | 'Brakes' | 'Drivetrain' | 'Inspection';
  title: string;
  intervalKm: number;
  intervalMonths: number;
  lastServicedKm: number;
  lastServicedDate: string;
  nextDueKm: number;
  nextDueDate: string;
  status: 'OK' | 'DUE_SOON' | 'OVERDUE';
  notes: string;
  specificationNotes: string;
}

export interface ServiceLogEntry {
  id: string;
  date: string;
  odometerKm: number;
  title: string;
  serviceCenter: string;
  cost: number;
  currency: string;
  notes: string;
}
