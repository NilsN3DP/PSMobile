package de.psmobile.ui

/** The workspace is usable only with an installed, selected printer preset. */
object PrinterReadyPolicy {
    fun isReady(selectedPrinter: String, installedPrinters: List<String>): Boolean =
        selectedPrinter.isNotBlank() && selectedPrinter in installedPrinters
}
