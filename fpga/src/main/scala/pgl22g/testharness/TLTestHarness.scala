package pgl22g.testharness

import chipyard._
import chipyard.harness.ApplyHarnessBinders
import chipyard.iobinders.HasIOBinders
import chisel3._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import shell.pango.{DDRDesignInputSysClk, DDROverlayKeySysClk}
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.ip.pango.{GTP_INBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.pango.{ChipLinkPGL22GPlacedOverlay, PGL22GShellDDROverlays, PGL22GShellTLDDROverlays, SPIFlashIO}

class PGL22GTLTestHarness(override implicit val p: Parameters) extends PGL22GShellTLDDROverlays {

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
  require(dp(ClockInputOverlayKey).nonEmpty)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node

  val dutWrangler = LazyModule(new ResetWrangler)

  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  /** * DDR ** */

  val mig = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput
  val ddrNode = mig.ddr

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match {
    case td: ChipTop =>
      td.lazySystem match {
        case lsys: CanHaveMasterTLMemPort =>
          lsys.memTLNode.edges.in.head
      }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrNode := ddrClient

  // val migUIClock = mig
  // migUIClock := sysClkNode
  // sysClkNode := migUIClock

  /** * Connect/Generate clocks ** */

  // create and connect to the dutClock
  println(s"PGL22G FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
  // dutClock := dutWrangler.node := dutGroup := migUIClock
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
  override lazy val module = new PGL22GTLTestHarnessImp(this)
}

class PGL22GTLTestHarnessImp(_outer: PGL22GTLTestHarness)
  extends LazyRawModuleImp(_outer)
    with HasHarnessSignalReferences
    with PGL22GTestHarnessUartImp
    with PGL22GTestHarnessSPIFlashImpl {
  val pgl22gOuter = _outer
  override val uart = _outer.io_uart_bb.bundle
  override val qspi = IO(new SPIFlashIO)
  // is resetN
  val reset = IO(Input(Bool()))
  _outer.fdc.addPackagePin(reset, "L19")
  _outer.fdc.addIOStandard(reset, "LVCMOS12")
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