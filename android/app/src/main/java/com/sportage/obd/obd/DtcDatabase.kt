package com.sportage.obd.obd

/**
 * Embedded diagnostic trouble code knowledge base.
 *
 * Covers the common SAE J2012 generic powertrain/chassis/body/network codes
 * that make up the vast majority of real-world check-engine events, plus the
 * Kia/Hyundai-specific codes most often seen on Theta/NU MPI engines.
 * Every entry: description, severity and the most probable causes.
 */
object DtcDatabase {

    data class Entry(
        val description: String,
        val severity: DtcSeverity,
        val probableCause: String
    )

    val CODES: Map<String, Entry> = mapOf(
        // --- Misfires ---
        "P0300" to Entry("Random/Multiple Cylinder Misfire Detected", DtcSeverity.CRITICAL, "Worn spark plugs, failing ignition coils, vacuum leak, low fuel pressure, contaminated fuel"),
        "P0301" to Entry("Cylinder 1 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 1 spark plug, ignition coil, injector or low compression"),
        "P0302" to Entry("Cylinder 2 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 2 spark plug, ignition coil, injector or low compression"),
        "P0303" to Entry("Cylinder 3 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 3 spark plug, ignition coil, injector or low compression"),
        "P0304" to Entry("Cylinder 4 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 4 spark plug, ignition coil, injector or low compression"),
        "P0305" to Entry("Cylinder 5 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 5 spark plug, ignition coil, injector or low compression"),
        "P0306" to Entry("Cylinder 6 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 6 spark plug, ignition coil, injector or low compression"),
        "P0316" to Entry("Misfire Detected on Startup (First 1000 Revolutions)", DtcSeverity.WARNING, "Cold-start misfire: fouled plugs, leaking injector, low compression"),
        "P0327" to Entry("Knock Sensor 1 Circuit Low (Bank 1)", DtcSeverity.WARNING, "Faulty knock sensor, wiring short, loose sensor bolt"),
        "P0328" to Entry("Knock Sensor 1 Circuit High (Bank 1)", DtcSeverity.WARNING, "Knock sensor failure, open circuit, PCM fault"),
        "P0335" to Entry("Crankshaft Position Sensor 'A' Circuit", DtcSeverity.CRITICAL, "Crank sensor failure, wiring damage, timing belt jumped"),
        "P0336" to Entry("Crankshaft Position Sensor 'A' Circuit Range/Performance", DtcSeverity.CRITICAL, "Crank sensor signal erratic: sensor, reluctor wheel, wiring"),
        "P0340" to Entry("Camshaft Position Sensor 'A' Circuit", DtcSeverity.WARNING, "Cam sensor failure, wiring, timing chain stretch"),
        "P0341" to Entry("Camshaft Position Sensor 'A' Circuit Range/Performance", DtcSeverity.WARNING, "Cam sensor alignment, timing chain wear, sensor failure"),

        // --- Fuel & air metering ---
        "P0100" to Entry("Mass Air Flow Sensor Circuit Malfunction", DtcSeverity.WARNING, "MAF sensor unplugged, damaged wiring, failed sensor"),
        "P0101" to Entry("Mass Air Flow Sensor Circuit Range/Performance", DtcSeverity.WARNING, "Dirty MAF sensing wire, intake boot leak, clogged air filter"),
        "P0102" to Entry("Mass Air Flow Sensor Circuit Low Input", DtcSeverity.WARNING, "Open circuit, failed MAF sensor, poor ground"),
        "P0103" to Entry("Mass Air Flow Sensor Circuit High Input", DtcSeverity.WARNING, "Short to voltage, failed MAF sensor"),
        "P0106" to Entry("Manifold Absolute Pressure Sensor Range/Performance", DtcSeverity.WARNING, "Vacuum leak, clogged MAP port, failed MAP sensor"),
        "P0107" to Entry("Manifold Absolute Pressure Sensor Circuit Low", DtcSeverity.WARNING, "Open circuit, failed MAP sensor"),
        "P0108" to Entry("Manifold Absolute Pressure Sensor Circuit High", DtcSeverity.WARNING, "Short to voltage, failed MAP sensor"),
        "P0112" to Entry("Intake Air Temperature Sensor 1 Circuit Low", DtcSeverity.ADVISORY, "IAT sensor shorted, wiring fault"),
        "P0113" to Entry("Intake Air Temperature Sensor 1 Circuit High", DtcSeverity.ADVISORY, "IAT sensor unplugged, open circuit"),
        "P0116" to Entry("Engine Coolant Temperature Sensor 1 Range/Performance", DtcSeverity.WARNING, "Coolant temp sensor drift, thermostat stuck, air pocket"),
        "P0117" to Entry("Engine Coolant Temperature Sensor 1 Circuit Low", DtcSeverity.WARNING, "ECT sensor short, wiring fault"),
        "P0118" to Entry("Engine Coolant Temperature Sensor 1 Circuit High", DtcSeverity.WARNING, "ECT sensor unplugged, open circuit"),
        "P0121" to Entry("Throttle Position Sensor 'A' Circuit Range/Performance", DtcSeverity.WARNING, "TPS drift, throttle body carbon build-up, APP sensor"),
        "P0122" to Entry("Throttle/Pedal Position Sensor 'A' Circuit Low", DtcSeverity.WARNING, "TPS short to ground, wiring fault"),
        "P0123" to Entry("Throttle/Pedal Position Sensor 'A' Circuit High", DtcSeverity.WARNING, "TPS short to voltage, open circuit"),
        "P0128" to Entry("Coolant Thermostat Malfunction (Temperature Below Regulating)", DtcSeverity.WARNING, "Thermostat stuck open, coolant temperature sensor, low coolant"),
        "P0130" to Entry("O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Upstream O2 sensor failure, wiring, exhaust leak"),
        "P0131" to Entry("O2 Sensor Circuit Low Voltage (Bank 1 Sensor 1)", DtcSeverity.WARNING, "O2 sensor short, lean condition, wiring"),
        "P0132" to Entry("O2 Sensor Circuit High Voltage (Bank 1 Sensor 1)", DtcSeverity.WARNING, "O2 sensor short to voltage, rich condition, leaking injector"),
        "P0133" to Entry("O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Aging O2 sensor, exhaust leak, contaminated sensor"),
        "P0134" to Entry("O2 Sensor Circuit No Activity Detected (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Dead O2 sensor heater, open circuit"),
        "P0135" to Entry("O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Blown heater fuse, damaged wiring, failed sensor"),
        "P0136" to Entry("O2 Sensor Circuit Malfunction (Bank 1 Sensor 2)", DtcSeverity.WARNING, "Downstream O2 sensor failure, wiring"),
        "P0137" to Entry("O2 Sensor Circuit Low Voltage (Bank 1 Sensor 2)", DtcSeverity.WARNING, "Downstream O2 sensor short, small exhaust leak"),
        "P0138" to Entry("O2 Sensor Circuit High Voltage (Bank 1 Sensor 2)", DtcSeverity.WARNING, "O2 sensor short to voltage"),
        "P0140" to Entry("O2 Sensor Circuit No Activity (Bank 1 Sensor 2)", DtcSeverity.WARNING, "Dead downstream O2 sensor, open circuit"),
        "P0141" to Entry("O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 2)", DtcSeverity.WARNING, "O2 heater fuse, wiring, sensor failure"),
        "P0171" to Entry("System Too Lean (Bank 1)", DtcSeverity.WARNING, "Vacuum leak, dirty MAF sensor, weak fuel pump, clogged injector, intake gasket"),
        "P0172" to Entry("System Too Rich (Bank 1)", DtcSeverity.WARNING, "Leaking injector, stuck fuel pressure regulator, failed MAF, bad O2 sensor"),
        "P0174" to Entry("System Too Lean (Bank 2)", DtcSeverity.WARNING, "Vacuum leak, dirty MAF sensor, low fuel pressure"),
        "P0175" to Entry("System Too Rich (Bank 2)", DtcSeverity.WARNING, "Leaking injector, high fuel pressure, failed O2 sensor"),
        "P0190" to Entry("Fuel Rail Pressure Sensor Circuit", DtcSeverity.WARNING, "Fuel pressure sensor failure, wiring"),
        "P0191" to Entry("Fuel Rail Pressure Sensor Range/Performance", DtcSeverity.WARNING, "Fuel pressure sensor drift, fuel pump wear"),
        "P0201" to Entry("Injector Circuit Malfunction - Cylinder 1", DtcSeverity.CRITICAL, "Injector coil open, wiring, PCM driver fault"),
        "P0202" to Entry("Injector Circuit Malfunction - Cylinder 2", DtcSeverity.CRITICAL, "Injector coil open, wiring, PCM driver fault"),
        "P0203" to Entry("Injector Circuit Malfunction - Cylinder 3", DtcSeverity.CRITICAL, "Injector coil open, wiring, PCM driver fault"),
        "P0204" to Entry("Injector Circuit Malfunction - Cylinder 4", DtcSeverity.CRITICAL, "Injector coil open, wiring, PCM driver fault"),
        "P0230" to Entry("Fuel Pump Primary Circuit Malfunction", DtcSeverity.CRITICAL, "Fuel pump relay, pump failure, wiring"),
        "P0231" to Entry("Fuel Pump Secondary Circuit Low", DtcSeverity.CRITICAL, "Fuel pump relay fault, wiring short"),
        "P0232" to Entry("Fuel Pump Secondary Circuit High", DtcSeverity.CRITICAL, "Fuel pump relay stuck, wiring short to voltage"),

        // --- Emissions & catalyst ---
        "P0401" to Entry("Exhaust Gas Recirculation Flow Insufficient Detected", DtcSeverity.WARNING, "Clogged EGR valve/passages, failed EGR vacuum regulator"),
        "P0402" to Entry("Exhaust Gas Recirculation Flow Excessive Detected", DtcSeverity.WARNING, "EGR valve stuck open, EGR sensor fault"),
        "P0403" to Entry("Exhaust Gas Recirculation Circuit Malfunction", DtcSeverity.WARNING, "EGR valve solenoid wiring, failed valve"),
        "P0404" to Entry("Exhaust Gas Recirculation Circuit Range/Performance", DtcSeverity.WARNING, "EGR valve carbon build-up, position sensor fault"),
        "P0420" to Entry("Catalyst System Efficiency Below Threshold (Bank 1)", DtcSeverity.WARNING, "Aging catalytic converter, exhaust leak, lazy downstream O2 sensor, engine misfire history"),
        "P0430" to Entry("Catalyst System Efficiency Below Threshold (Bank 2)", DtcSeverity.WARNING, "Aging catalytic converter, exhaust leak, O2 sensor"),
        "P0440" to Entry("Evaporative Emission Control System Malfunction", DtcSeverity.ADVISORY, "EVAP vent valve, purge valve, loose fuel cap"),
        "P0441" to Entry("Evaporative Emission Control System Incorrect Purge Flow", DtcSeverity.ADVISORY, "Purge valve stuck, EVAP pressure sensor, vacuum leak"),
        "P0442" to Entry("Evaporative Emission Control System Leak Detected (Small Leak)", DtcSeverity.ADVISORY, "Loose/damaged fuel cap, small EVAP hose crack"),
        "P0446" to Entry("Evaporative Emission Control System Vent Control Malfunction", DtcSeverity.ADVISORY, "Vent valve stuck, clogged vent filter, wiring"),
        "P0455" to Entry("Evaporative Emission Control System Leak Detected (Large Leak)", DtcSeverity.ADVISORY, "Fuel cap left loose, disconnected EVAP hose, failed vent valve"),
        "P0456" to Entry("Evaporative Emission Control System Leak Detected (Very Small Leak)", DtcSeverity.ADVISORY, "Tiny EVAP hose crack, fuel cap seal wear"),

        // --- Speed, idle & cruise ---
        "P0500" to Entry("Vehicle Speed Sensor 'A' Malfunction", DtcSeverity.WARNING, "VSS sensor failure, instrument cluster wiring, ABS wheel sensor"),
        "P0501" to Entry("Vehicle Speed Sensor 'A' Range/Performance", DtcSeverity.WARNING, "VSS sensor drift, wiring corrosion"),
        "P0505" to Entry("Idle Air Control System Malfunction", DtcSeverity.WARNING, "Dirty throttle body, failed idle actuator, vacuum leak"),
        "P0506" to Entry("Idle Air Control System RPM Lower Than Expected", DtcSeverity.WARNING, "Carbon-fouled throttle body, idle actuator sticking"),
        "P0507" to Entry("Idle Air Control System RPM Higher Than Expected", DtcSeverity.WARNING, "Vacuum leak, idle actuator fault, throttle plate stuck"),
        "P0560" to Entry("System Voltage Malfunction", DtcSeverity.WARNING, "Failing alternator, bad battery, corroded grounds"),
        "P0562" to Entry("System Voltage Low", DtcSeverity.WARNING, "Weak alternator output, slipping belt, bad battery, wiring resistance"),
        "P0563" to Entry("System Voltage High", DtcSeverity.WARNING, "Alternator regulator failure - overcharging"),

        // --- Transmission / drivetrain ---
        "P0700" to Entry("Transmission Control System (MIL Request)", DtcSeverity.WARNING, "TCM detected a fault: read transmission codes for details (P07xx/P27xx)"),
        "P0705" to Entry("Transmission Range Sensor Circuit Malfunction", DtcSeverity.WARNING, "Gear position sensor failure, wiring, shifter alignment"),
        "P0711" to Entry("Transmission Fluid Temperature Sensor 'A' Range/Performance", DtcSeverity.WARNING, "TFT sensor drift, wiring, degraded fluid"),
        "P0715" to Entry("Input/Turbine Speed Sensor 'A' Circuit", DtcSeverity.WARNING, "Input speed sensor failure, wiring"),
        "P0720" to Entry("Output Speed Sensor Circuit", DtcSeverity.WARNING, "Output speed sensor failure, wiring"),
        "P0730" to Entry("Incorrect Gear Ratio", DtcSeverity.CRITICAL, "Worn clutch packs, low fluid, valve body fault, failing solenoid"),
        "P0740" to Entry("Torque Converter Clutch Circuit Malfunction", DtcSeverity.WARNING, "TCC solenoid failure, wiring, low transmission fluid"),
        "P0741" to Entry("Torque Converter Clutch Circuit Performance/Stuck Off", DtcSeverity.WARNING, "TCC solenoid, torque converter clutch wear, valve body"),
        "P0751" to Entry("Shift Solenoid 'A' Performance/Stuck Off", DtcSeverity.WARNING, "Shift solenoid A failure, valve body, low fluid"),
        "P0755" to Entry("Shift Solenoid 'B' Malfunction", DtcSeverity.WARNING, "Shift solenoid B failure, wiring, valve body"),

        // --- Charging & electrical ---
        "P0620" to Entry("Generator Control Circuit Malfunction", DtcSeverity.WARNING, "Alternator control circuit fault, failed regulator"),
        "P062F" to Entry("Internal Control Module Keep Alive Memory (KAM) Error", DtcSeverity.ADVISORY, "PCM memory fault: battery was disconnected or PCM aging"),
        "P0645" to Entry("A/C Clutch Relay Control Circuit", DtcSeverity.ADVISORY, "A/C compressor relay failure, wiring"),
        "P064A" to Entry("Fuel Pump Control Module", DtcSeverity.WARNING, "Fuel pump control module failure, wiring"),

        // --- Network / body ---
        "U0001" to Entry("High Speed CAN Communication Bus", DtcSeverity.WARNING, "CAN bus wiring fault, failed module on bus"),
        "U0100" to Entry("Lost Communication With ECM/PCM 'A'", DtcSeverity.CRITICAL, "CAN bus fault, PCM power/ground, module failure"),
        "U0101" to Entry("Lost Communication With TCM", DtcSeverity.WARNING, "CAN bus fault to transmission module, TCM power"),
        "U0121" to Entry("Lost Communication With Anti-Lock Brake System Module", DtcSeverity.WARNING, "CAN fault to ABS module, module power/ground"),
        "U0140" to Entry("Lost Communication With Body Control Module", DtcSeverity.ADVISORY, "CAN fault to BCM, module power/ground"),
        "U0155" to Entry("Lost Communication With Instrument Panel Cluster", DtcSeverity.ADVISORY, "CAN fault to instrument cluster"),
        "C0035" to Entry("Left Front Wheel Speed Sensor Circuit", DtcSeverity.WARNING, "ABS wheel sensor failure, wiring, tone ring damage"),
        "C0040" to Entry("Right Front Wheel Speed Sensor Circuit", DtcSeverity.WARNING, "ABS wheel sensor failure, wiring"),
        "C0045" to Entry("Left Rear Wheel Speed Sensor Circuit", DtcSeverity.WARNING, "ABS wheel sensor failure, wiring"),
        "C0050" to Entry("Right Rear Wheel Speed Sensor Circuit", DtcSeverity.WARNING, "ABS wheel sensor failure, wiring"),
        "B1000" to Entry("ECU Malfunction (Body Domain)", DtcSeverity.ADVISORY, "Body control module internal fault"),
        "B1342" to Entry("ECU Malfunction (Manufacturing)", DtcSeverity.ADVISORY, "Module internal fault: reprogram or replace"),

        // --- Common Kia/Hyundai 2.0 MPI / 2.4 GDI patterns ---
        "P0011" to Entry("Intake Camshaft Position Timing Over-Advanced (Bank 1)", DtcSeverity.WARNING, "Oil control valve (OCV) stuck, dirty engine oil, stretched timing chain"),
        "P0012" to Entry("Intake Camshaft Position Timing Over-Retarded (Bank 1)", DtcSeverity.WARNING, "OCV stuck, low oil pressure, timing chain wear"),
        "P0016" to Entry("Crankshaft/Camshaft Position Correlation (Bank 1 Sensor A)", DtcSeverity.CRITICAL, "Stretched timing chain, failed OCV, cam/crank sensors"),
        "P0017" to Entry("Crankshaft/Camshaft Position Correlation (Bank 1 Sensor B)", DtcSeverity.CRITICAL, "Timing chain stretch, OCV failure, sensor fault"),
        "P0266" to Entry("Cylinder 2 Injector Circuit / Contribution/Balance", DtcSeverity.WARNING, "Injector imbalance, wiring, low compression cylinder 2"),
        "P0299" to Entry("Turbocharger/Supercharger Underboost", DtcSeverity.WARNING, "Boost leak, wastegate stuck, clogged air filter (turbo models)"),
        "P1326" to Entry("Kia/Hyundai Knock Sensor Detection System - Engine Damage Possible", DtcSeverity.CRITICAL, "Known Kia Theta II engine recall code: rod bearing wear. Stop driving and contact dealer"),
        "P2096" to Entry("Post Catalyst Fuel Trim System Too Lean (Bank 1)", DtcSeverity.WARNING, "Exhaust leak downstream, O2 sensor fault, fuel delivery"),
        "P2097" to Entry("Post Catalyst Fuel Trim System Too Rich (Bank 1)", DtcSeverity.WARNING, "Leaking injector, fuel pressure high, O2 sensor fault"),
        "P2122" to Entry("Throttle/Pedal Position Sensor 'D' Circuit Low", DtcSeverity.WARNING, "Accelerator pedal sensor fault, wiring"),
        "P2127" to Entry("Throttle/Pedal Position Sensor 'E' Circuit Low", DtcSeverity.WARNING, "Accelerator pedal sensor fault, wiring"),
        "P2191" to Entry("System Too Lean at Higher Load (Bank 1)", DtcSeverity.WARNING, "Fuel pump volume low, clogged filter, MAF under-reporting at load"),
        "P2413" to Entry("EGR Sensor 'A' Circuit Range/Performance", DtcSeverity.ADVISORY, "EGR differential pressure sensor fault, EGR carbon build-up"),
        "P2610" to Entry("ECM/PCM Internal Engine Off Timer Performance", DtcSeverity.ADVISORY, "PCM internal fault - often with battery/ground issues")
    )

    /** Returns the database entry or a generic fallback for unknown codes. */
    fun lookup(code: String): Entry = CODES[code] ?: Entry(
        description = "Diagnostic Trouble Code ($code)",
        severity = DtcSeverity.WARNING,
        probableCause = "Generic ECU fault flag. Inspect the affected system, sensors and connectors."
    )
}
