/**
 * SAE J1979 OBD2 & ELM327 Response Decoders
 * Exact mathematical conversions as specified by SAE / ISO standards.
 */

import { PidDefinition } from '../types/obd';

export const STANDARD_PIDS: Record<string, PidDefinition<any>> = {
  '010C': {
    mode: 0x01,
    pid: 0x0C,
    hexCode: '010C',
    name: 'Engine RPM',
    shortName: 'RPM',
    unit: 'rpm',
    min: 0,
    max: 8000,
    priority: 'P0',
    description: 'Revolutions per minute of crankshaft',
    decode: (bytes: number[]) => {
      if (bytes.length < 2) return 0;
      const a = bytes[0];
      const b = bytes[1];
      return Math.round(((a * 256) + b) / 4);
    }
  },
  '010D': {
    mode: 0x01,
    pid: 0x0D,
    hexCode: '010D',
    name: 'Vehicle Speed',
    shortName: 'Speed',
    unit: 'km/h',
    min: 0,
    max: 240,
    priority: 'P0',
    description: 'Vehicle speed reported by wheel speed sensors/transmission',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return bytes[0];
    }
  },
  '0105': {
    mode: 0x01,
    pid: 0x05,
    hexCode: '0105',
    name: 'Engine Coolant Temperature',
    shortName: 'Coolant',
    unit: '°C',
    min: -40,
    max: 130,
    priority: 'P0',
    description: 'Engine coolant temperature sensor',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return bytes[0] - 40;
    }
  },
  '010F': {
    mode: 0x01,
    pid: 0x0F,
    hexCode: '010F',
    name: 'Intake Air Temperature',
    shortName: 'IAT',
    unit: '°C',
    min: -40,
    max: 100,
    priority: 'P1',
    description: 'Temperature of air entering intake manifold',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return bytes[0] - 40;
    }
  },
  '0104': {
    mode: 0x01,
    pid: 0x04,
    hexCode: '0104',
    name: 'Calculated Engine Load',
    shortName: 'Load',
    unit: '%',
    min: 0,
    max: 100,
    priority: 'P1',
    description: 'Calculated engine load value',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number(((bytes[0] * 100) / 255).toFixed(1));
    }
  },
  '0111': {
    mode: 0x01,
    pid: 0x11,
    hexCode: '0111',
    name: 'Throttle Position',
    shortName: 'Throttle',
    unit: '%',
    min: 0,
    max: 100,
    priority: 'P0',
    description: 'Absolute throttle position sensor',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number(((bytes[0] * 100) / 255).toFixed(1));
    }
  },
  '0106': {
    mode: 0x01,
    pid: 0x06,
    hexCode: '0106',
    name: 'Short Term Fuel Trim - Bank 1',
    shortName: 'STFT 1',
    unit: '%',
    min: -100,
    max: 99.2,
    priority: 'P1',
    description: 'Short term fuel correction for closed loop stoichiometry',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number((((bytes[0] - 128) * 100) / 128).toFixed(1));
    }
  },
  '0107': {
    mode: 0x01,
    pid: 0x07,
    hexCode: '0107',
    name: 'Long Term Fuel Trim - Bank 1',
    shortName: 'LTFT 1',
    unit: '%',
    min: -100,
    max: 99.2,
    priority: 'P1',
    description: 'Long term fuel adaptive table correction',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number((((bytes[0] - 128) * 100) / 128).toFixed(1));
    }
  },
  '010B': {
    mode: 0x01,
    pid: 0x0B,
    hexCode: '010B',
    name: 'Intake Manifold Absolute Pressure',
    shortName: 'MAP',
    unit: 'kPa',
    min: 0,
    max: 255,
    priority: 'P1',
    description: 'Manifold absolute pressure',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return bytes[0];
    }
  },
  '0110': {
    mode: 0x01,
    pid: 0x10,
    hexCode: '0110',
    name: 'Mass Air Flow Rate',
    shortName: 'MAF',
    unit: 'g/s',
    min: 0,
    max: 655.35,
    priority: 'P1',
    description: 'Mass air flow sensor output rate',
    decode: (bytes: number[]) => {
      if (bytes.length < 2) return 0;
      return Number((((bytes[0] * 256) + bytes[1]) / 100).toFixed(2));
    }
  },
  '010E': {
    mode: 0x01,
    pid: 0x0E,
    hexCode: '010E',
    name: 'Ignition Timing Advance',
    shortName: 'Timing',
    unit: '°',
    min: -64,
    max: 63.5,
    priority: 'P1',
    description: 'Ignition timing advance for cylinder #1 before top dead center',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number(((bytes[0] / 2) - 64).toFixed(1));
    }
  },
  '0114': {
    mode: 0x01,
    pid: 0x14,
    hexCode: '0114',
    name: 'O2 Sensor 1 - Bank 1',
    shortName: 'O2 B1S1',
    unit: 'V',
    min: 0,
    max: 1.275,
    priority: 'P2',
    description: 'Pre-catalytic converter oxygen sensor voltage',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number((bytes[0] / 200).toFixed(3));
    }
  },
  '0115': {
    mode: 0x01,
    pid: 0x15,
    hexCode: '0115',
    name: 'O2 Sensor 2 - Bank 1',
    shortName: 'O2 B1S2',
    unit: 'V',
    min: 0,
    max: 1.275,
    priority: 'P2',
    description: 'Post-catalytic converter catalyst monitor oxygen sensor voltage',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number((bytes[0] / 200).toFixed(3));
    }
  },
  '012F': {
    mode: 0x01,
    pid: 0x2F,
    hexCode: '012F',
    name: 'Fuel Tank Level Input',
    shortName: 'Fuel Level',
    unit: '%',
    min: 0,
    max: 100,
    priority: 'P2',
    description: 'Vehicle fuel tank remaining volume level',
    decode: (bytes: number[]) => {
      if (bytes.length < 1) return 0;
      return Number(((bytes[0] * 100) / 255).toFixed(1));
    }
  },
  '0142': {
    mode: 0x01,
    pid: 0x42,
    hexCode: '0142',
    name: 'Control Module Voltage',
    shortName: 'ECU Volts',
    unit: 'V',
    min: 0,
    max: 20,
    priority: 'P0',
    description: 'Voltage supply measured by the engine control unit',
    decode: (bytes: number[]) => {
      if (bytes.length < 2) return 0;
      return Number((((bytes[0] * 256) + bytes[1]) / 1000).toFixed(2));
    }
  }
};

/**
 * Normalizes raw ELM327 response text
 */
export function normalizeElmResponse(raw: string): string {
  return raw
    .replace(/\r/g, '')
    .replace(/>/g, '')
    .trim();
}

/**
 * Extracts payload bytes from a successful OBD response line.
 * Example: '41 0C 1A F8' -> returns [0x1A, 0xF8]
 */
export function extractPidPayload(responseLine: string, expectedMode: number, expectedPid: number): number[] | null {
  const clean = responseLine.replace(/[^0-9A-Fa-f\s]/g, '').trim();
  const hexParts = clean.split(/\s+/).filter(p => p.length === 2);
  if (hexParts.length < 2) return null;

  const modeByte = parseInt(hexParts[0], 16);
  // Expected response mode for Mode 01 is 0x41 (mode + 0x40)
  if (modeByte !== (expectedMode + 0x40)) {
    return null;
  }

  const pidByte = parseInt(hexParts[1], 16);
  if (pidByte !== expectedPid) {
    return null;
  }

  // Remainder is payload
  return hexParts.slice(2).map(h => parseInt(h, 16));
}

/**
 * Parses DTC codes from Mode 03 / Mode 07 / Mode 0A response
 * Format: 2 bytes per DTC.
 * First 2 bits indicate type: 00=P (Powertrain), 01=C (Chassis), 10=B (Body), 11=U (Network)
 */
export function parseDtcBytes(bytes: number[]): string[] {
  const codes: string[] = [];
  const prefixes = ['P', 'C', 'B', 'U'];

  for (let i = 0; i < bytes.length; i += 2) {
    if (i + 1 >= bytes.length) break;
    const b1 = bytes[i];
    const b2 = bytes[i + 1];

    if (b1 === 0 && b2 === 0) continue; // Null padding

    const prefixIdx = (b1 >> 6) & 0x03;
    const digit1 = (b1 >> 4) & 0x03;
    const digit2 = b1 & 0x0F;
    const digit3 = (b2 >> 4) & 0x0F;
    const digit4 = b2 & 0x0F;

    const code = `${prefixes[prefixIdx]}${digit1}${digit2.toString(16).toUpperCase()}${digit3.toString(16).toUpperCase()}${digit4.toString(16).toUpperCase()}`;
    codes.push(code);
  }

  return codes;
}

/**
 * Parse PID support bitmap from Mode 01 PID 00 (PIDs 01-20)
 * 4 bytes = 32 bits, where bit 1 (MSB of byte 1) is PID 01.
 */
export function parseSupportedPidsBitmap(basePid: number, bytes: number[]): number[] {
  const supported: number[] = [];
  if (bytes.length < 4) return supported;

  let pidNum = basePid + 1;
  for (let byteIndex = 0; byteIndex < 4; byteIndex++) {
    const byte = bytes[byteIndex];
    for (let bitIndex = 7; bitIndex >= 0; bitIndex--) {
      if ((byte & (1 << bitIndex)) !== 0) {
        supported.push(pidNum);
      }
      pidNum++;
    }
  }

  return supported;
}
