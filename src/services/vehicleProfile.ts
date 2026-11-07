/**
 * Kia Sportage 2.0 MPI AWD - Vehicle Profile & Maintenance Schedules
 * Tailored for GCC Market conditions (extreme summer ambient, severe service intervals)
 */

import { VehicleProfile, MaintenanceItem, ServiceLogEntry } from '../types/obd';

export const DEFAULT_SPORTAGE_PROFILE: VehicleProfile = {
  id: 'kia_sportage_2019_gcc_20mpi_awd',
  make: 'Kia',
  model: 'Sportage (QL)',
  year: 2019,
  market: 'GCC (Gulf Cooperation Council)',
  engine: '2.0L MPI Nu (G4NA) 4-Cylinder DOHC Dual CVVT',
  displacementCc: 1999,
  transmission: '6-Speed Automatic (Hyundai/Kia A6MF1)',
  drivetrain: 'Dynamax Intelligent AWD with 50:50 Lock',
  vin: 'KNDPM3AC7K7****** (Redacted for privacy)',
  fuelType: 'Petrol (Gasoline)',
  curbWeightKg: 1540,
  fuelCapacityLiters: 62,
  verifiedPidsCount: 28
};

export const DEFAULT_MAINTENANCE_SCHEDULE: MaintenanceItem[] = [
  {
    id: 'maint_engine_oil',
    category: 'Fluids',
    title: 'Engine Oil & OEM Filter (Nu 2.0 MPI)',
    intervalKm: 10000,
    intervalMonths: 6,
    lastServicedKm: 72400,
    lastServicedDate: '2026-06-15',
    nextDueKm: 82400,
    nextDueDate: '2026-12-15',
    status: 'OK',
    notes: 'Severe GCC climate schedule recommended. Capacity: 4.0 L with filter change.',
    specificationNotes: 'SAE 5W-20 or 5W-30 Full Synthetic, API SN PLUS / SP, ILSAC GF-6'
  },
  {
    id: 'maint_engine_air_filter',
    category: 'Filters',
    title: 'Engine Intake Air Filter & Cabin Filter',
    intervalKm: 20000,
    intervalMonths: 12,
    lastServicedKm: 65000,
    lastServicedDate: '2026-01-20',
    nextDueKm: 85000,
    nextDueDate: '2027-01-20',
    status: 'OK',
    notes: 'Crucial in dusty GCC conditions to prevent MAF sensor contamination.',
    specificationNotes: 'OEM Hyundai/Kia 28113-D3300'
  },
  {
    id: 'maint_spark_plugs',
    category: 'Ignition',
    title: 'Spark Plugs (Iridium Long-Life)',
    intervalKm: 60000,
    intervalMonths: 48,
    lastServicedKm: 60000,
    lastServicedDate: '2025-08-10',
    nextDueKm: 120000,
    nextDueDate: '2029-08-10',
    status: 'OK',
    notes: 'Nu 2.0 MPI engine requires strict 0.9-1.0mm electrode gap to protect ignition coils.',
    specificationNotes: 'NGK SILZKR6B10E or DENSO VXUH20I Iridium'
  },
  {
    id: 'maint_brake_fluid',
    category: 'Brakes',
    title: 'Brake Fluid Flush & Bleed',
    intervalKm: 40000,
    intervalMonths: 24,
    lastServicedKm: 40000,
    lastServicedDate: '2024-09-05',
    nextDueKm: 80000,
    nextDueDate: '2026-09-05',
    status: 'DUE_SOON',
    notes: 'Hygroscopic fluid absorbs atmospheric moisture; causes vapor lock in high summer heat.',
    specificationNotes: 'FMVSS 116 DOT-4'
  },
  {
    id: 'maint_awd_fluids',
    category: 'Drivetrain',
    title: 'AWD Rear Differential & Transfer Case Fluid',
    intervalKm: 60000,
    intervalMonths: 36,
    lastServicedKm: 60000,
    lastServicedDate: '2025-08-10',
    nextDueKm: 120000,
    nextDueDate: '2028-08-10',
    status: 'OK',
    notes: 'Dynamax AWD electro-hydraulic coupling and hypoid bevel gear case.',
    specificationNotes: 'Hypoid Gear Oil SAE 75W-90 API GL-5 (Rear: ~0.65L, Transfer: ~0.7L)'
  },
  {
    id: 'maint_coolant_flush',
    category: 'Fluids',
    title: 'Engine Long-Life Coolant / Antifreeze',
    intervalKm: 100000,
    intervalMonths: 60,
    lastServicedKm: 0,
    lastServicedDate: '2019-11-01',
    nextDueKm: 100000,
    nextDueDate: '2027-11-01',
    status: 'OK',
    notes: 'Phosphate-organic acid technology (P-OAT) aluminum protection. Prevents boiling at 115°C.',
    specificationNotes: 'Kia Long Life Coolant (Green or Pink P-OAT) 50/50 deionized premix'
  },
  {
    id: 'maint_transmission_fluid',
    category: 'Fluids',
    title: 'Automatic Transmission Fluid (ATF)',
    intervalKm: 80000,
    intervalMonths: 48,
    lastServicedKm: 0,
    lastServicedDate: '2019-11-01',
    nextDueKm: 80000,
    nextDueDate: '2026-11-30',
    status: 'DUE_SOON',
    notes: 'Severe GCC climate inspection & drain/refill for 6-speed A6MF1 gearbox.',
    specificationNotes: 'Genuine Hyundai/Kia ATF SP-IV or Michang SP-IV'
  }
];

export const INITIAL_SERVICE_LOGS: ServiceLogEntry[] = [
  {
    id: 'log_3',
    date: '2026-06-15',
    odometerKm: 72400,
    title: 'Engine Oil & Filter Service (5W-30)',
    serviceCenter: 'Al-Attiya Motors & Trading Co. (Kia Authorized Service)',
    cost: 380,
    currency: 'QAR',
    notes: 'Synthetic 5W-30 oil change, OEM oil filter, 21-point multi-inspection completed. All tire pressures adjusted to 35 PSI.'
  },
  {
    id: 'log_2',
    date: '2026-01-20',
    odometerKm: 65000,
    title: 'Air Filter, Cabin Filter & AC Sanitization',
    serviceCenter: 'Doha Express Auto Care',
    cost: 260,
    currency: 'QAR',
    notes: 'Replaced OEM engine intake filter and cabin pollen filter after sandstorm season. AC condenser blown clean.'
  },
  {
    id: 'log_1',
    date: '2025-08-10',
    odometerKm: 60000,
    title: 'Major 60,000 km Service (Spark Plugs + AWD Diff Gear Oil)',
    serviceCenter: 'Kia Quick Service Center',
    cost: 1150,
    currency: 'QAR',
    notes: 'Installed 4x NGK Iridium spark plugs, replaced rear differential hypoid oil and front transfer case gear oil. Inspected brake pads (6.5mm remaining).'
  }
];
