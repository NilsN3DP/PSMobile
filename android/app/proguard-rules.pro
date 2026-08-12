# R8-Regeln fuer den Release-Build.
#
# Diese Datei war in build.gradle.kts schon eingetragen, existierte aber
# nicht - was niemandem auffiel, weil isMinifyEnabled auf false stand und
# R8 sie nie gelesen hat.
#
# Alles, was hier NICHT steht, darf R8 umbenennen oder wegwerfen.

# --- Die JNI-Bruecke -------------------------------------------------------
# android/jni/psm_jni.cpp exportiert 143 Symbole nach dem festen Schema
# Java_de_psmobile_core_PsmCore_* und Java_de_psmobile_core_PsmViewport_*.
# Es gibt kein RegisterNatives - die Verbindung entsteht allein ueber den
# Namen. Benennt R8 die Klasse oder eine native Methode um, findet
# System.loadLibrary die Funktion zur Laufzeit nicht mehr, und die App
# stirbt beim ersten Slice mit UnsatisfiedLinkError.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class de.psmobile.core.PsmCore { *; }
-keep class de.psmobile.core.PsmViewport { *; }

# --- Was aus dem Kern zurueckkommt ----------------------------------------
# Die nativen Methoden liefern nur primitive Typen, String, String[] und
# IntArray. Kaeme spaeter eine eigene Klasse als Rueckgabewert dazu,
# muesste sie hier stehen - includedescriptorclasses oben deckt die
# Signaturen ab, nicht aber Felder, die C++ selbst setzt.

# --- Kotlin ----------------------------------------------------------------
# Zeilennummern erhalten, damit ein Absturzbericht aus dem Feld noch
# lesbar ist. Ohne SourceFile-Umbenennung waere der Stacktrace nutzlos.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coroutines, Compose, kotlinx.serialization und ML Kit bringen ihre
# eigenen Regeln als consumer-rules in den Artefakten mit. Hier steht
# bewusst nichts davon doppelt.

# --- Serialisierung --------------------------------------------------------
# Die App benutzt ausschliesslich die untypisierte JsonElement-Schnittstelle
# (Json.parseToJsonElement, JsonObject, jsonPrimitive) - kein @Serializable,
# keine generierten Serializer, also auch keine Reflexion, die R8 stoeren
# koennte. Sollte spaeter eine @Serializable-Klasse dazukommen, braucht sie
# eine eigene Keep-Regel.
