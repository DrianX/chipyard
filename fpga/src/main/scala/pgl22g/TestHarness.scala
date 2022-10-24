package chipyard.fpga.pgl22g

import chipyard._
import chipyard.harness.ApplyHarnessBinders
import chipyard.iobinders.HasIOBinders
import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters}
import freechips.rocketchip.tilelink._
import shell.pango.{DDRDesignInputSysClk, DDROverlayKeySysClk}
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.ip.pango._
import sifive.fpgashells.ip.pango.ddr3.PGL22GMIGIODDRBase
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.pango.{ChipLinkPGL22GPlacedOverlay, PGL22GPerfShell, PGL22GShellBasicOverlays, ShellPangoUARTPortIO, UARTPGL22GShellPlacer, UARTPangoPlacedOverlay}

class PGL22GFPGATestHarness(override implicit val p: Parameters) extends PGL22GShellBasicOverlays {

  def dp = designParameters

  // val pmod_is_sdio  = p(PGL22GShellPMOD) == "SDIO"
  // val jtag_location = Some(if (pmod_is_sdio) "FMC_J2" else "PMOD_J52")

  // Order matters; ddr depends on sys_clock
  // val uart      = Overlay(UARTOverlayKey, new UARTPGL22GShellPlacer(this, UARTShellInput()))
  // val sdio      = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOPGL22GShellPlacer(this, SPIShellInput()))) else None
  // val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugPGL22GShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  // val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugPGL22GShellPlacer(this, cJTAGDebugShellInput()))
  // val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanPGL22GShellPlacer(this, JTAGDebugBScanShellInput()))
  // val fmc       = Overlay(PCIeOverlayKey, new PCIePGL22GFMCShellPlacer(this, PCIeShellInput()))
  // val edge      = Overlay(PCIeOverlayKey, new PCIePGL22GEdgeShellPlacer(this, PCIeShellInput()))
  // val sys_clock2 = Overlay(ClockInputOverlayKey, new SysClock2PGL22GShellPlacer(this, ClockInputShellInput()))
  // val ddr2       = Overlay(DDROverlayKey, new DDR2PGL22GShellPlacer(this, DDRShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

  /** * Connect/Generate clocks ** */

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  println(s"PGL22G FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
  // DOC include end: ClockOverlay

  /** * UART ** */

  // DOC include start: UartOverlay
  // 1st UART goes to the PGL22G dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // DOC include end: UartOverlay

  // /*** SPI ***/
  //
  // // 1st SPI goes to the PGL22G SDIO port
  //
  // val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).head)))
  // dp(SPIOverlayKey).head.place(SPIDesignInput(dp(PeripherySPIKey).head, io_spi_bb))

  /** * DDR ** */

  // val ddrNode = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput.ddr
  val ddrNode = dp(DDROverlayKeySysClk).head.place(DDRDesignInputSysClk(dp(ExtTLMem).get.master.base, dutWrangler.node, sysClkNode)).overlayOutput.mig.node

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match {
    case td: ChipTop =>
      td.lazySystem match {
        case lsys: CanHaveMasterTLMemPort =>
          lsys.memTLNode.edges.in(0)
      }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrNode := ddrClient

  // module implementation
  override lazy val module = new PGL22GFPGATestHarnessImp(this)
}

class PGL22GAXIFPGATestHarness(override implicit val p: Parameters) extends PGL22GShellBasicOverlays {
  def dp = designParameters

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")
  // DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node
  /** * Connect/Generate clocks ** */
  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode
  // create and connect to the dutClock
  println(s"PGL22G FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // module implementation
  override lazy val module = new PGL22GAXIFPGATestHarnessImp(this)
}

class PGL22GAXIFPGATestHarnessImp(_outer: PGL22GAXIFPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val pgl22gOuter = _outer

  // is resetN
  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")

  val resetIBUF = Module(new GTP_INBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkPGL22GPlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := ((!resetIBUF.io.O) || powerOnReset || ereset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = WireInit(false.B)

  childClock := buildtopClock
  childReset := buildtopReset

  // harness binders are non-lazy
  _outer.topDesign match {
    case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  // check the top-level reference clock is equal to the default
  // non-exhaustive since you need all ChipTop clocks to equal the default
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}

class PGL22GFPGATestHarnessImp(_outer: PGL22GFPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val pgl22gOuter = _outer

  // is resetN
  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")

  val resetIBUF = Module(new GTP_INBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkPGL22GPlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := ((!resetIBUF.io.O) || powerOnReset || ereset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = WireInit(false.B)

  childClock := buildtopClock
  childReset := buildtopReset

  // harness binders are non-lazy
  _outer.topDesign match {
    case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  // check the top-level reference clock is equal to the default
  // non-exhaustive since you need all ChipTop clocks to equal the default
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}

class PGL22GSimShell(implicit p: Parameters) extends IOShell {
  val sdc = new SDC("shell.sdc")
}

class PGL22GSimTestHarness(implicit p: Parameters) extends PGL22GSimShell {
  def dp = designParameters

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).head))
  // dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  override lazy val module = new PGL22GSimTestHarnessImpl(this)
}

class PGL22GSimTestHarnessImpl(_outer: PGL22GSimTestHarness)
  extends LazyRawModuleImp(_outer)
    with HasHarnessSignalReferences {
  val io_success = IO(Output(Bool()))
  io_success := false.B

  val reset = IO(Input(Bool()))
  val clock = IO(Input(Clock()))

  val resetInner = WireInit(false.B.asTypeOf(reset.cloneType))
  val clockInner = WireInit(false.B.asClock)

  override def buildtopClock = clockInner

  override def buildtopReset = resetInner

  override def dutReset = resetInner

  override def success = io_success

  val refClkBundle = p(HarnessClockInstantiatorKey).requestClockBundle("buildtop_reference_clock", getRefClockFreq * (1000 * 1000))
  val implicitHarnessClockBundle = Wire(new ClockBundle(ClockBundleParameters()))
  implicitHarnessClockBundle.clock := clock
  implicitHarnessClockBundle.reset := reset
  p(HarnessClockInstantiatorKey).instantiateHarnessDividerPLL(implicitHarnessClockBundle)

  // buildtopClock := refClkBundle.clock
  // buildtopReset := WireInit(refClkBundle.reset)

  withClockAndReset(buildtopClock, buildtopReset) {
    _outer.topDesign match {
      case d: HasIOBinders =>
        ApplyHarnessBinders(this, d.lazySystem, d.portMap)
    }
  }

  buildtopClock := refClkBundle.clock
  buildtopReset := WireInit(refClkBundle.reset)
}

class PGL22GBareTestHarness(override implicit val p: Parameters) extends PGL22GShellBasicOverlays {
  def dp = designParameters

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node
  val migUIClock = PLLNode(feedback = false)
  // val harnessSysPLL = dp(PLLFactoryKey)()
  // harnessSysPLL := sysClkNode
  migUIClock := sysClkNode
  println(s"PGL22G FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  // dutClock := dutWrangler.node := dutGroup := harnessSysPLL
  dutClock := dutWrangler.node := dutGroup := migUIClock
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // val innerDDRIO = new PGL22GMIGIODDRBase
  override lazy val module = new PGL22GBareTestHarnessImp(this)
}

trait PGL22GTestHarnessDDRImp {
  val ddr: PGL22GMIGIODDRBase
  val ddrphy_rst_done: Bool
  val ddrc_init_done: Bool
  val pll_lock: Bool
  val pll_clk_bus: Clock
  val sysclk: Clock
  val hardResetN: Bool
}

class PGL22GBareTestHarnessImp(_outer: PGL22GBareTestHarness)
  extends LazyRawModuleImp(_outer)
    with HasHarnessSignalReferences
    with PGL22GTestHarnessDDRImp {
  val pgl22gOuter = _outer
  // is resetN
  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")
  val resetIBUF = Module(new GTP_INBUF)
  resetIBUF.io.I := reset
  val hardResetN = (resetIBUF.io.O).asBool
  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock
  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))
  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkPGL22GPlacedOverlay) => !x.ereset_n
    case _ => false.B
  }
  // used for
  _outer.pllReset := (!hardResetN || powerOnReset || ereset)
  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset
  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = WireInit(false.B)
  childClock := buildtopClock
  childReset := buildtopReset

  val ddr = IO(new PGL22GMIGIODDRBase)

  val ddrphy_rst_done = WireInit(false.B)
  val ddrc_init_done = WireInit(false.B)
  val pll_lock = WireInit(false.B)
  val pll_clk_bus = WireInit(sysclk)
  _outer.migUIClock.out.head._1.member.head.reset := (!(pll_lock & ddrc_init_done & ddrphy_rst_done)) || _outer.pllReset
  _outer.migUIClock.out.head._1.member.head.clock := pll_clk_bus
  // harness binders are non-lazy
  _outer.topDesign match {
    case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}

class PGL22GTestHarness(override implicit val p: Parameters) extends PGL22GShellBasicOverlays {

  def dp = designParameters

  // val pmod_is_sdio  = p(PGL22GShellPMOD) == "SDIO"
  // val jtag_location = Some(if (pmod_is_sdio) "FMC_J2" else "PMOD_J52")

  // Order matters; ddr depends on sys_clock
  // val uart      = Overlay(UARTOverlayKey, new UARTPGL22GShellPlacer(this, UARTShellInput()))
  // val sdio      = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOPGL22GShellPlacer(this, SPIShellInput()))) else None
  // val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugPGL22GShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  // val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugPGL22GShellPlacer(this, cJTAGDebugShellInput()))
  // val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanPGL22GShellPlacer(this, JTAGDebugBScanShellInput()))
  // val fmc       = Overlay(PCIeOverlayKey, new PCIePGL22GFMCShellPlacer(this, PCIeShellInput()))
  // val edge      = Overlay(PCIeOverlayKey, new PCIePGL22GEdgeShellPlacer(this, PCIeShellInput()))
  // val sys_clock2 = Overlay(ClockInputOverlayKey, new SysClock2PGL22GShellPlacer(this, ClockInputShellInput()))
  // val ddr2       = Overlay(DDROverlayKey, new DDR2PGL22GShellPlacer(this, DDRShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

  val dutWrangler = LazyModule(new ResetWrangler)

  /** * DDR ** */

  val mig = dp(DDROverlayKeySysClk).head.place(DDRDesignInputSysClk(dp(ExtTLMem).get.master.base, dutWrangler.node, sysClkNode)).overlayOutput.mig
  val ddrNode = mig.node

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match {
    case td: ChipTop =>
      td.lazySystem match {
        case lsys: CanHaveMasterTLMemPort =>
          lsys.memTLNode.edges.in(0)
      }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrNode := ddrClient

  val migUIClock = mig.pllNode
  migUIClock := sysClkNode
  // sysClkNode := migUIClock

  /** * Connect/Generate clocks ** */

  // connect to the PLL that will generate multiple clocks
  // val harnessSysPLL = dp(PLLFactoryKey)()
  // harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  println(s"PGL22G FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutGroup = ClockGroup()
  // dutClock := dutWrangler.node := dutGroup := harnessSysPLL
  dutClock := dutWrangler.node := dutGroup := migUIClock
  // DOC include end: ClockOverlay

  /** * UART ** */

  // DOC include start: UartOverlay
  // 1st UART goes to the PGL22G dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // DOC include end: UartOverlay

  // /*** SPI ***/
  //
  // // 1st SPI goes to the PGL22G SDIO port
  //
  // val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).head)))
  // dp(SPIOverlayKey).head.place(SPIDesignInput(dp(PeripherySPIKey).head, io_spi_bb))

  // module implementation
  override lazy val module = new PGL22GTestHarnessImp(this)
}

class PGL22GTestHarnessImp(_outer: PGL22GTestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {
  val pgl22gOuter = _outer
  // is resetN
  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")
  val resetIBUF = Module(new GTP_INBUF)
  resetIBUF.io.I := reset
  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock
  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))
  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkPGL22GPlacedOverlay) => !x.ereset_n
    case _ => false.B
  }
  // used for
  _outer.pllReset := ((!resetIBUF.io.O) || powerOnReset || ereset)
  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset
  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = WireInit(false.B)
  childClock := buildtopClock
  childReset := buildtopReset
  // harness binders are non-lazy
  _outer.topDesign match {
    case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
  // check the top-level reference clock is equal to the default
  // non-exhaustive since you need all ChipTop clocks to equal the default
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}

class PGL22GPerfTestHarness(override implicit val p: Parameters)
  extends PGL22GPerfShell
    with HasHarnessSignalReferences
    with PGL22GTestHarnessDDRImp {
  val lazyDut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")

  val sysclk = Wire(Clock())
  sysclk <> sys_clock
  val hardResetN = WireInit(!reset)
  val ddrphy_rst_done = WireInit(false.B)
  val ddrc_init_done = WireInit(false.B)
  val pll_lock = WireInit(false.B)
  val pll_clk_bus = Wire(Clock())

  // Convert harness resets from Bool to Reset type.
  val hReset = Wire(Reset())
  hReset := ~hardResetN

  // val dReset = Wire(AsyncReset())
  // dReset := reset_core.asAsyncReset

  withClockAndReset(pll_clk_bus, hReset) {
    val dut = Module(lazyDut.module)
  }

  val ddr: PGL22GMIGIODDRBase = IO(new PGL22GMIGIODDRBase)
  val buildtopClock = pll_clk_bus
  val buildtopReset = hReset

  val success = WireInit(false.B)

  // val dutReset = dReset
  val dutReset = hReset

  // must be after HasHarnessSignalReferences assignments
  lazyDut match {
    case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}