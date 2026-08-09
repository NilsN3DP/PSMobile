package de.psmobile.net

/** Dedupliziert lokale Geräte anhand ihrer bestehenden Geräte-ID und ergänzt erreichbare Hosts. */
object LocalPrinterMerge {
    fun withHost(printer: PrusaLink.Printer, host: String): PrusaLink.Printer =
        printer.copy(
            host = host,
            localExperimental = true,
            localHosts = (printer.localHosts + host).distinct(),
        )
}
