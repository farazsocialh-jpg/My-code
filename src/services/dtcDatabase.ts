/**
 * Diagnostic Trouble Code (DTC) Knowledge Base
 * Sourced from SAE J2012, Kia Technical Service Information, and EPA OBD standards.
 */

import { DtcItem, ReadinessMonitor, Mode06TestResult } from '../types/obd';

export const DTC_DATABASE: Record<string, Omit<DtcItem, 'status' | 'firstSeen' | 'lastSeen'>> = {
  'P0420': {
    code: 'P0420',
    severity: 'WARNING',
    system: 'Powertrain',
    title: 'Catalyst System Efficiency Below Threshold (Bank 1)',
    description: 'The Engine Control Module (ECM) monitors the switching frequency ratio between the upstream heated oxygen sensor (B1S1) and downstream heated oxygen sensor (B1S2). The catalytic converter oxygen storage capacity is below the minimum threshold required for low emissions.',
    symptoms: [
      'Check Engine Light (MIL) illuminated',
      'Engine runs normally in most cases (no immediate drivability symptom)',
      'Slight reduction in highway fuel economy',
      'Failed annual emissions inspection'
    ],
    commonCauses: [
      'Exhaust leak at exhaust manifold, flex pipe or flange upstream of rear O2 sensor',
      'Degraded downstream oxygen sensor (B1S2) reporting erratic voltage oscillations',
      'Engine oil consumption or unburnt fuel contaminating the catalytic matrix',
      'Engine running rich or lean due to fuel trim drift (check STFT/LTFT)',
      'Substandard or aftermarket catalytic converter without sufficient precious metal washcoat',
      'Physical failure or melting of the catalytic converter core'
    ],
    diagnosticChecklist: [
      '1. Inspect exhaust system under car for black soot stains indicating pinhole leaks or cracked flex pipe before downstream O2 sensor.',
      '2. Inspect Bank 1 fuel trims: LTFT should be between -8% and +8%. High positive trims mean unmetered air / vacuum leak.',
      '3. Graph B1S1 (upstream) vs B1S2 (downstream) oxygen sensor voltages at warm idle and 2500 RPM. B1S1 should cycle rapidly between 0.1V and 0.9V; B1S2 should remain relatively flat around 0.5V to 0.7V.',
      '4. If B1S2 mimics the rapid cycling of B1S1 after warm-up, oxygen storage is depleted.',
      '5. Review Mode 06 Test ID $21 (Catalyst monitor) limit values to confirm test completion and margin.',
      '6. Verify engine has no misfire history (P0300-P0304) or oil-burning valve seals before replacing catalyst.'
    ],
    verifiedSportage: true
  },
  'P0171': {
    code: 'P0171',
    severity: 'WARNING',
    system: 'Powertrain',
    title: 'System Too Lean (Bank 1)',
    description: 'The ECM has added maximum positive fuel trim (STFT + LTFT > 25%) to compensate for an air-fuel ratio that is leaner than stoichiometric (excess air or lack of fuel).',
    symptoms: [
      'Rough idle or hesitation on gentle acceleration',
      'Engine stumble or mild surging at highway cruise',
      'Hard start when cold',
      'Loss of power under full throttle'
    ],
    commonCauses: [
      'Intake manifold gasket vacuum leak or cracked PCV hose',
      'Dirty or contaminated Mass Air Flow (MAF) or MAP sensor',
      'Low fuel pump delivery pressure or clogged fuel filter',
      'Clogged or sticky fuel injector on 2.0 MPI rail',
      'Exhaust leak upstream of the primary oxygen sensor introducing false air'
    ],
    diagnosticChecklist: [
      '1. Smoke-test the intake ducting, throttle body gasket, and PCV breather hose.',
      '2. Monitor STFT while spraying brake cleaner or carb cleaner carefully around intake seams.',
      '3. Inspect MAF sensor hot wire for dust/lint and clean with dedicated MAF spray.',
      '4. Verify fuel pressure at the rail (spec: 350-380 kPa on Nu 2.0 MPI).',
      '5. Check freeze frame data to see if code set at idle (vacuum leak) or under load (fuel starvation).'
    ],
    verifiedSportage: true
  },
  'P0300': {
    code: 'P0300',
    severity: 'CRITICAL',
    system: 'Powertrain',
    title: 'Random / Multiple Cylinder Misfire Detected',
    description: 'The ECM crankshaft position sensor detected abrupt drops in angular acceleration indicating combustion misfires occurring across random or multiple cylinders.',
    symptoms: [
      'Noticeable engine vibration or shaking at idle',
      'Hesitation, bucking, or loss of acceleration',
      'Flashing Check Engine Light (flashing indicates catalyst damage risk!)',
      'Strong smell of unburnt fuel from exhaust'
    ],
    commonCauses: [
      'Worn spark plugs (exceeded 60,000 km replacement interval on Nu 2.0 MPI)',
      'Failing ignition coils or oil intrusion into spark plug wells',
      'Low fuel pressure or contaminated fuel (water or low octane)',
      'Severe vacuum leak affecting entire intake manifold plenum',
      'Low cylinder compression or carbon build-up on intake valves'
    ],
    diagnosticChecklist: [
      '1. IMPORTANT: If MIL is FLASHING, stop driving immediately to avoid melting the catalytic converter.',
      '2. Remove ignition coils and inspect plug tubes for engine oil leakage.',
      '3. Inspect spark plug electrode condition and measure gap (0.9 - 1.0 mm).',
      '4. Query individual cylinder misfire counters in Mode 06 Test ID $A2-$A5.',
      '5. Swap suspect coil to another cylinder to test if misfire migrates.'
    ],
    verifiedSportage: true
  },
  'P0128': {
    code: 'P0128',
    severity: 'ADVISORY',
    system: 'Powertrain',
    title: 'Coolant Thermostat (Coolant Temp Below Regulating Temperature)',
    description: 'The engine coolant did not reach the target operating temperature (typically ~82°C-88°C) within the ECM expected time model after cold start.',
    symptoms: [
      'Engine takes abnormally long time to warm up',
      'Cabin heater blows only lukewarm air during winter/highway driving',
      'Slight increase in fuel consumption due to extended open-loop warmup'
    ],
    commonCauses: [
      'Thermostat stuck open or opening prematurely',
      'Faulty engine coolant temperature (ECT) sensor',
      'Low engine coolant level in expansion reservoir'
    ],
    diagnosticChecklist: [
      '1. Check coolant level in reservoir and radiator when cold.',
      '2. Log coolant temperature from cold start: observe if upper radiator hose gets warm immediately (indicates stuck-open thermostat).',
      '3. Replace engine thermostat (standard Nu 2.0 MPI OEM thermostat rated 82°C / 88°C).'
    ],
    verifiedSportage: true
  },
  'P0455': {
    code: 'P0455',
    severity: 'ADVISORY',
    system: 'Powertrain',
    title: 'Evaporative Emission System Leak Detected (Gross Leak)',
    description: 'The fuel vapor recovery (EVAP) system failed the vacuum decay diagnostic test, detecting a leak greater than 0.090 inches.',
    symptoms: [
      'Check Engine Light illuminated',
      'No noticeable engine running problems',
      'Possible faint fuel odor near rear fuel filler area'
    ],
    commonCauses: [
      'Loose, improperly tightened, or cracked fuel filler cap seal',
      'EVAP purge solenoid valve stuck open under intake plenum',
      'EVAP canister vent solenoid valve jammed with road dust/sand',
      'Cracked vapor line between fuel tank and charcoal canister'
    ],
    diagnosticChecklist: [
      '1. Remove, inspect, clean and firmly retighten the fuel gas cap until it clicks 3 times.',
      '2. Inspect rubber O-ring seal on gas cap for cracking or fuel residue.',
      '3. Clear code and perform standard EVAP drive cycle test.',
      '4. If recurring, smoke-test EVAP service port to pinpoint canister vent or line crack.'
    ],
    verifiedSportage: true
  },
  'P0011': {
    code: 'P0011',
    severity: 'WARNING',
    system: 'Powertrain',
    title: 'Camshaft Position "A" - Timing Over-Advanced (Bank 1)',
    description: 'The intake camshaft Continuous Variable Valve Timing (CVVT) phase angle is more advanced than commanded by the ECM target map.',
    symptoms: [
      'Rough idle or engine stalling on coming to a stop',
      'Engine rattle or chatter near CVVT phaser on cold start',
      'Sluggish low-end torque'
    ],
    commonCauses: [
      'Low engine oil level or severely degraded/dirty engine oil',
      'Oil Control Valve (OCV) solenoid sticking due to varnish or debris',
      'Clogged OCV oil feed micro-screen filter in cylinder head',
      'Worn CVVT intake camshaft sprocket phaser assembly'
    ],
    diagnosticChecklist: [
      '1. Check engine oil level and condition on dipstick immediately.',
      '2. Verify correct oil viscosity (5W-20 / 5W-30 API SN/SP as required for Kia Nu engine).',
      '3. Remove intake OCV solenoid; check for sludge and test electrical resistance (6.8-8.2 ohms).',
      '4. Clean OCV mesh filter in cylinder head passage with solvent.'
    ],
    verifiedSportage: true
  }
};

export const INITIAL_READINESS_MONITORS: ReadinessMonitor[] = [
  { id: 'misfire', name: 'Misfire Monitor', supported: true, ready: true, continuous: true, description: 'Continuously monitors crankshaft speed variations to detect engine misfires.' },
  { id: 'fuel_system', name: 'Fuel System', supported: true, ready: true, continuous: true, description: 'Continuously monitors closed-loop air-fuel feedback and STFT/LTFT boundaries.' },
  { id: 'comp_component', name: 'Comprehensive Components', supported: true, ready: true, continuous: true, description: 'Continuously tests electrical sensors and actuators for rational signals.' },
  { id: 'catalyst', name: 'Catalyst Monitor', supported: true, ready: true, continuous: false, description: 'Non-continuous test verifying oxygen storage capacity of catalytic converter.' },
  { id: 'evap', name: 'Evaporative System', supported: true, ready: true, continuous: false, description: 'Verifies fuel vapor containment and purge valve flow during steady cruise.' },
  { id: 'o2_sensor', name: 'Oxygen Sensor', supported: true, ready: true, continuous: false, description: 'Tests switching response time and voltage swing of upstream and downstream O2 sensors.' },
  { id: 'o2_heater', name: 'Oxygen Sensor Heater', supported: true, ready: true, continuous: false, description: 'Verifies electrical resistance and heating element operation of oxygen sensors.' },
  { id: 'egr_vvt', name: 'EGR / VVT System', supported: true, ready: true, continuous: false, description: 'Tests CVVT camshaft phase angle control response and oil control valve.' }
];

export const INITIAL_MODE06_RESULTS: Mode06TestResult[] = [
  { testId: '$01', componentId: '$01', name: 'Exhaust Gas Sensor Monitor Bank 1 - Sensor 1', currentValue: 0.42, minLimit: 0.15, maxLimit: 0.85, unit: 'V', passed: true },
  { testId: '$02', componentId: '$01', name: 'O2 Sensor Bank 1 Sensor 2 Switching Ratio', currentValue: 0.18, minLimit: 0.00, maxLimit: 0.55, unit: 'ratio', passed: true },
  { testId: '$21', componentId: '$01', name: 'Catalyst Monitor Bank 1 Conversion Efficiency', currentValue: 0.82, minLimit: 0.65, maxLimit: 1.00, unit: 'eff', passed: true },
  { testId: '$3B', componentId: '$02', name: 'EVAP System Gross Leak 0.090" Vacuum Decay', currentValue: 2.14, minLimit: 1.20, maxLimit: 4.50, unit: 'kPa', passed: true },
  { testId: '$A2', componentId: '$01', name: 'Cylinder 1 Misfire Rate (Last 1000 revs)', currentValue: 0, minLimit: 0, maxLimit: 15, unit: 'counts', passed: true },
  { testId: '$A3', componentId: '$02', name: 'Cylinder 2 Misfire Rate (Last 1000 revs)', currentValue: 0, minLimit: 0, maxLimit: 15, unit: 'counts', passed: true },
  { testId: '$A4', componentId: '$03', name: 'Cylinder 3 Misfire Rate (Last 1000 revs)', currentValue: 0, minLimit: 0, maxLimit: 15, unit: 'counts', passed: true },
  { testId: '$A5', componentId: '$04', name: 'Cylinder 4 Misfire Rate (Last 1000 revs)', currentValue: 0, minLimit: 0, maxLimit: 15, unit: 'counts', passed: true }
];
