package pgl22g.configs

import chipsalliance.rocketchip.config.Config
import freechips.rocketchip.subsystem._
import pgl22g._
import vexriscv.chipyard.{WithNVexRiscvCores, WithVexConfig}
import vexriscv.demo.VexOnChipConfig

class PGL22GVexRiscvBaseConfig extends Config(
  new WithUARTHarnessBinder ++
    // new WithDebugPeripherals ++
    // new WithJTAGHarnessBinder ++
    // new WithInternalJTAGIOCells ++
    // new WithInternalJTAGHarnessBinder ++
    // new WithInternalJTAGIOCells ++
    new WithNoDebug ++
    new WithSPIFlash ++
    new WithSPIFlashHarnessBinder ++
    new WithTestsBootROM ++
    new WithFPGAFrequency(5.0) ++
    new WithPGL22GTweaks ++
    new WithPGL22GAXIMem ++
    new WithNMemoryChannels(1) ++
    new WithBufferlessBroadcastHub ++
    new ModifiedAbstractConfig)

class PGL22GVexRiscvConfig extends Config(
  new WithNVexRiscvCores(1, onChipRAM = false) ++
    new WithVexConfig(VexOnChipConfig.default.copy(
      iCacheSize = 16384,
      dCacheSize = 16384,
      // iCacheSize = 4096,
      // dCacheSize = 4096,
      // iCacheSize = 0,
      // dCacheSize = 0,
      resetVector = 0x10040L,
      onChipRamSize = 0
    )) ++
    new PGL22GVexRiscvBaseConfig)

class PGL22GVexRiscv2Config extends Config(
  new WithNVexRiscvCores(2, onChipRAM = false) ++
    new WithVexConfig(VexOnChipConfig.default.copy(
      // iCacheSize = 16384,
      // dCacheSize = 16384,
      // iCacheSize = 4096,
      // dCacheSize = 4096,
      iCacheSize = 0,
      dCacheSize = 0,
      resetVector = 0x10040L,
      onChipRamSize = 0
    )) ++
    new PGL22GVexRiscvBaseConfig)

class SimPGL22GVexRiscvConfig extends Config(
  // new WithVexDefaultConfig ++
  //   new WithVexICacheSize(16384) ++
  //   new WithVexDCacheSize(16384) ++
  //   new WithVexResetVector(0x10040L) ++
  //   // new WithTestsBootROM ++
  //   new WithVexOnChipMemSize(0) ++
  new WithNVexRiscvCores(1, onChipRAM = false) ++
    new WithVexConfig(VexOnChipConfig.default.copy(
      // iCacheSize = 16384,
      // dCacheSize = 16384,
      // iCacheSize = 4096,
      // dCacheSize = 4096,
      iCacheSize = 0,
      dCacheSize = 0,
      resetVector = 0x10040L,
      onChipRamSize = 0
    )) ++
    new WithMemoryBusWidth(32) ++
    new WithPGL22GSimTinyTweaks ++
    // new WithVexRiscvBootROM ++
    new ModifiedAbstractConfig
)
