/*
 * psmobile_core.h - die einzige Grenze zwischen App und Slicer-Kern.
 *
 * Regeln fuer diese Datei:
 *   1. Reines C. Keine C++-Typen, keine Ausnahmen, keine STL ueber die
 *      Grenze. C++-Symbole tragen weder ueber JNI noch ueber Swift sauber.
 *   2. Der Aufrufer besitzt nie Speicher, den der Kern angelegt hat.
 *      Alles, was der Kern zurueckgibt, wird mit der passenden
 *      psm_*_free-Funktion wieder freigegeben.
 *   3. Der Viewport laeuft NICHT ueber dieses ABI. Pro-Frame-Aufrufe
 *      ueber JNI waeren ein Performancefehler. Siehe docs/entscheidungen.md, E-03.
 *   4. Alle Funktionen sind threadsicher bezogen auf verschiedene
 *      Sessions. Eine einzelne Session darf nur aus einem Thread
 *      gleichzeitig veraendert werden - ausgenommen psm_slice_cancel.
 *
 * Copyright (c) 2026 PSMobile-Projekt
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#ifndef PSMOBILE_CORE_H
#define PSMOBILE_CORE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
#  define PSM_API __declspec(dllexport)
#else
#  define PSM_API __attribute__((visibility("default")))
#endif

/* ------------------------------------------------------------------ */
/* Version                                                             */
/* ------------------------------------------------------------------ */

#define PSM_ABI_VERSION 4

/** Gibt die ABI-Version zurueck. Die App prueft sie beim Start gegen
 *  PSM_ABI_VERSION und verweigert den Dienst bei Abweichung. */
PSM_API int psm_abi_version(void);

/** Versionsstring des zugrundeliegenden PrusaSlicer-Kerns, z. B. "2.9.6".
 *  Zeigt auf statischen Speicher, nicht freigeben. */
PSM_API const char *psm_core_version(void);

/* ------------------------------------------------------------------ */
/* Fehlercodes                                                         */
/* ------------------------------------------------------------------ */

typedef enum {
    PSM_OK                 =  0,
    PSM_ERR_GENERIC        = -1,
    PSM_ERR_INVALID_ARG    = -2,
    PSM_ERR_IO             = -3,   /* Datei nicht lesbar/schreibbar      */
    PSM_ERR_PARSE          = -4,   /* Datei kaputt oder unbekanntes Format */
    PSM_ERR_NOT_FOUND      = -5,   /* Objekt-/Preset-ID unbekannt        */
    PSM_ERR_BUSY           = -6,   /* Slice-Job laeuft bereits           */
    PSM_ERR_CANCELLED      = -7,
    PSM_ERR_OUT_OF_MEMORY  = -8,
    PSM_ERR_SLICING        = -9,   /* Slicer meldet fachlichen Fehler    */
    PSM_ERR_UNSUPPORTED    = -10,
    PSM_ERR_STALE_RESULT   = -11   /* Ergebnis passt nicht mehr zum Projekt */
} psm_result;

/** Letzte Fehlermeldung dieser Session im Klartext, UTF-8.
 *  Gueltig bis zum naechsten Aufruf auf derselben Session. Nicht freigeben.
 *  Bei NULL-Session: der letzte globale Fehler. */
PSM_API const char *psm_last_error(void *session);

/* ------------------------------------------------------------------ */
/* Session                                                             */
/* ------------------------------------------------------------------ */

typedef struct psm_session psm_session;

/** Legt eine Session an.
 *  @param datadir  Beschreibbares Verzeichnis fuer Profile und Zwischenstaende.
 *                  Android: context.getFilesDir()
 *                  iOS:     Application Support
 *  @param resdir   Verzeichnis mit den mitgelieferten PrusaSlicer-Ressourcen
 *                  (profiles/, shaders/, icons/).
 *  @return NULL bei Fehler, dann psm_last_error(NULL) abfragen. */
PSM_API psm_session *psm_session_create(const char *datadir, const char *resdir);

PSM_API void psm_session_destroy(psm_session *s);

/** Setzt die Session auf leeres Bett zurueck. Presets bleiben erhalten. */
PSM_API psm_result psm_session_clear(psm_session *s);

/* ------------------------------------------------------------------ */
/* Protokoll                                                           */
/* ------------------------------------------------------------------ */

typedef enum {
    PSM_LOG_ERROR = 0,
    PSM_LOG_WARN  = 1,
    PSM_LOG_INFO  = 2,
    PSM_LOG_DEBUG = 3
} psm_log_level;

typedef void (*psm_log_cb)(psm_log_level level, const char *msg, void *user);

/** Global, nicht pro Session. Vor psm_session_create aufrufen.
 *  Android leitet das an __android_log_write weiter, iOS an os_log. */
PSM_API void psm_set_log_callback(psm_log_cb cb, void *user);

/* ------------------------------------------------------------------ */
/* Modelle                                                             */
/* ------------------------------------------------------------------ */

typedef int32_t psm_object_id;
#define PSM_INVALID_ID ((psm_object_id) -1)

/** Laedt STL, 3MF, OBJ oder AMF und legt die enthaltenen Objekte aufs Bett.
 *  @param out_ids     Puffer fuer die erzeugten Objekt-IDs, darf NULL sein.
 *  @param out_ids_cap Kapazitaet von out_ids.
 *  @param out_count   erhaelt die Zahl der tatsaechlich erzeugten Objekte,
 *                     auch wenn sie groesser als out_ids_cap ist.
 *
 *  Hinweis Speicher: Bei grossen Meshes wird fuer die Anzeige eine
 *  dezimierte Fassung erzeugt; das Original bleibt fuer das Slicing
 *  erhalten. Siehe docs/02-architektur.md, Speicherstrategie. */
PSM_API psm_result psm_model_load(psm_session *s,
                                  const char *path,
                                  psm_object_id *out_ids,
                                  size_t out_ids_cap,
                                  size_t *out_count);

/**
 * Ergebnis eines 3MF-Projektimports.
 *
 * requested_* sind die im Projekt gespeicherten Profilnamen. selected_*
 * sind die Profile, die der Kern tatsaechlich aktiviert hat. Stimmen sie
 * ueberein, war das installierte Profil unveraendert vorhanden; andernfalls
 * hat PrusaSlicer ein projektlokales externes Profil angelegt.
 */
typedef struct {
    int32_t config_loaded;
    int32_t post_process_removed;
    int32_t object_count;
    int32_t bed_count;
    char    requested_printer[256];
    char    selected_printer[256];
    char    requested_print[256];
    char    selected_print[256];
} psm_project_import_info;

/**
 * Oeffnet eine 3MF als vollstaendiges Projekt.
 *
 * Anders als psm_model_load ersetzt dieser Aufruf das aktuelle Bett,
 * behaelt die Positionen aus der 3MF bei und uebernimmt Drucker-, Druck-
 * und Filamentkonfiguration. Das passende installierte Profil wird
 * aktiviert; gibt es kein exaktes Profil, bleibt die eingebettete
 * Konfiguration als projektlokales externes Profil erhalten.
 *
 * Eingebettete Post-Processing-Skripte werden aus Sicherheitsgruenden
 * nicht uebernommen und ueber post_process_removed gemeldet.
 */
PSM_API psm_result psm_project_load_3mf(psm_session *s,
                                        const char *path,
                                        psm_project_import_info *out_info);

/**
 * Speichert alle mobilen Betten als PrusaSlicer-kompatibles 3MF-Projekt.
 *
 * Die getrennten mobilen Bettkoordinaten werden dabei in PrusaSlicers
 * virtuelle Mehrbett-Anordnung zurückübersetzt. Druck-, Filament- und
 * Druckerkonfiguration werden eingebettet; Zugangsdaten und
 * Post-Processing-Skripte werden niemals exportiert.
 */
PSM_API psm_result psm_project_save_3mf(psm_session *s, const char *path);

/* ------------------------------------------------------------------ */
/* Undo / Redo                                                         */
/* ------------------------------------------------------------------ */

/** Fasst mehrere Core-Aufrufe zu genau einem Undo-Schritt zusammen. */
PSM_API psm_result psm_history_begin(psm_session *s, const char *label);
PSM_API psm_result psm_history_end(psm_session *s);

/** Zahl und Beschriftung der verfügbaren Schritte. */
PSM_API size_t psm_history_undo_count(psm_session *s);
PSM_API size_t psm_history_redo_count(psm_session *s);
PSM_API psm_result psm_history_undo_label(psm_session *s, char *out, size_t out_cap);
PSM_API psm_result psm_history_redo_label(psm_session *s, char *out, size_t out_cap);

PSM_API psm_result psm_history_undo(psm_session *s);
PSM_API psm_result psm_history_redo(psm_session *s);
PSM_API psm_result psm_history_clear(psm_session *s);

/* ------------------------------------------------------------------ */
/* Mehrbett                                                            */
/* ------------------------------------------------------------------ */

#define PSM_MAX_BEDS 36

/** Zahl der Betten im aktuellen Projekt. Mindestens eins. */
PSM_API size_t psm_bed_count(psm_session *s);

/** Nullbasierter Index des in UI und Viewport sichtbaren Betts. */
PSM_API size_t psm_bed_active(psm_session *s);

/**
 * Waehlt ein Bett direkt aus. Die Ansicht springt auf dieses Bett; es
 * gibt bewusst keine Desktop-artige, seitlich scrollbare Bettlandschaft.
 */
PSM_API psm_result psm_bed_select(psm_session *s, size_t index);

/** Legt ein leeres Bett an, waehlt es aus und liefert seinen Index. */
PSM_API psm_result psm_bed_add(psm_session *s, size_t *out_index);

/** Entfernt ein Bett. Das letzte verbleibende Bett kann nicht entfernt werden. */
PSM_API psm_result psm_bed_remove(psm_session *s, size_t index);

/** Leert nur das aktive Bett; Profile und andere Betten bleiben erhalten. */
PSM_API psm_result psm_bed_clear(psm_session *s);

/** Zahl der Objekte auf einem Bett. */
PSM_API size_t psm_bed_object_count(psm_session *s, size_t index);

/** Verschiebt ein Objekt vom aktiven Bett auf ein anderes Bett. */
PSM_API psm_result psm_bed_move_object(psm_session *s,
                                       psm_object_id id,
                                       size_t target_bed,
                                       psm_object_id *out_new_id);

PSM_API psm_result psm_model_remove(psm_session *s, psm_object_id id);

PSM_API size_t psm_model_count(psm_session *s);

/** Fuellt out_ids mit allen aktuellen Objekt-IDs. */
PSM_API psm_result psm_model_list(psm_session *s,
                                  psm_object_id *out_ids,
                                  size_t cap,
                                  size_t *out_count);

typedef struct {
    psm_object_id id;
    char          name[256];      /* UTF-8, abgeschnitten falls laenger */
    float         position[3];    /* mm, Bettkoordinaten                */
    float         rotation[3];    /* Radiant, XYZ-Euler                 */
    float         scale[3];       /* Faktor, 1.0 = Original             */
    float         bbox_min[3];    /* mm, nach Transformation            */
    float         bbox_max[3];
    uint32_t      triangle_count;
    int32_t       instance_count;
    int32_t       outside_bed;    /* 1 = ragt ueber das Druckbett hinaus */
} psm_object_info;

PSM_API psm_result psm_model_info(psm_session *s, psm_object_id id, psm_object_info *out);

/** Lage eines Objekts zum Druckraum. Reihenfolge wie BuildVolume::ObjectState. */
typedef enum {
    PSM_BED_INSIDE    = 0,  /**< vollstaendig im Druckraum, druckbar */
    PSM_BED_COLLIDING = 1,  /**< schneidet den Rand - nicht druckbar */
    PSM_BED_OUTSIDE   = 2,  /**< ganz daneben */
    PSM_BED_BELOW     = 3,  /**< vollstaendig unter dem Bett */
    PSM_BED_UNKNOWN   = 4   /**< Bettgeometrie fehlt oder Objekt unbekannt */
} psm_bed_state;

/**
 * Wo das Objekt relativ zum Druckraum liegt.
 *
 * Die Auskunft stammt aus PrusaSlicers eigener Pruefung; hier wird
 * nichts nachgerechnet. Hat ein Objekt mehrere Instanzen, gilt die
 * schlechteste - eine einzige kollidierende Instanz macht den Druck
 * unmoeglich.
 */
PSM_API psm_bed_state psm_model_bed_state(psm_session *s, psm_object_id id);

typedef enum {
    PSM_VOLUME_MODEL_PART       = 0,
    PSM_VOLUME_NEGATIVE         = 1,
    PSM_VOLUME_MODIFIER         = 2,
    PSM_VOLUME_SUPPORT_BLOCKER  = 3,
    PSM_VOLUME_SUPPORT_ENFORCER = 4
} psm_volume_type;

typedef struct {
    int32_t         index;
    psm_volume_type type;
    char            name[256];
    uint32_t        triangle_count;
    int32_t         extruder;          /* effektiv, 0 = Standard */
    int32_t         explicit_extruder; /* 0 = vom Objekt erben */
} psm_volume_info;

/** Objekt-/Volumenbaum und Extruderzuweisung wie in der Desktop-Sidebar. */
PSM_API int32_t psm_model_extruder_get(psm_session *s, psm_object_id id);
PSM_API psm_result psm_model_extruder_set(psm_session *s, psm_object_id id,
                                          int32_t extruder);
PSM_API size_t psm_model_volume_count(psm_session *s, psm_object_id id);
PSM_API psm_result psm_model_volume_info(psm_session *s, psm_object_id id,
                                         size_t volume_index,
                                         psm_volume_info *out);
PSM_API psm_result psm_model_volume_extruder_set(psm_session *s,
                                                  psm_object_id id,
                                                  size_t volume_index,
                                                  int32_t extruder);

PSM_API psm_result psm_model_set_position(psm_session *s, psm_object_id id, float x, float y, float z);
PSM_API psm_result psm_model_set_rotation(psm_session *s, psm_object_id id, float rx, float ry, float rz);
PSM_API psm_result psm_model_set_scale(psm_session *s, psm_object_id id, float sx, float sy, float sz);

/**
 * Pfad zum Druckbett fuer den gewaehlten Drucker.
 *
 * Prusa liefert zu jedem Druckermodell ein Bettmodell als STL und eine
 * Textur als SVG mit; die Namen stehen im Abschnitt [printer_model:...]
 * des Herstellerbuendels. Ohne sie zeichnen wir nur ein flaches Vieleck
 * aus bed_shape.
 *
 * Geliefert wird der reine Dateiname, ohne Pfad - wo die Dateien liegen,
 * weiss die App. Leer, wenn das Modell keines angibt.
 */
PSM_API psm_result psm_bed_model_file(psm_session *s, char *out, size_t out_cap);
PSM_API psm_result psm_bed_texture_file(psm_session *s, char *out, size_t out_cap);

/** Spiegelt an einer Achse: 0 = X, 1 = Y, 2 = Z. */
PSM_API psm_result psm_model_mirror(psm_session *s, psm_object_id id, int32_t axis);

/**
 * Setzt die Zahl der Kopien auf dem Bett. PrusaSlicer nennt das
 * Instanzen: dieselbe Geometrie, mehrfach platziert, ohne den Speicher
 * zu vervielfachen.
 */
PSM_API psm_result psm_model_set_instances(psm_session *s, psm_object_id id, int32_t count);

/** Legt das Objekt flach auf das Bett (kleinster Z-Punkt auf 0). */
PSM_API psm_result psm_model_drop_to_bed(psm_session *s, psm_object_id id);

/**
 * Legt das Objekt auf seine groesste ebene Flaeche.
 *
 * Sucht die Richtung, in die der groesste Flaecheninhalt zeigt, und
 * dreht sie nach unten. Das ist, was ein Mensch beim Hinlegen auch tut.
 */
PSM_API psm_result psm_model_lay_flat_auto(psm_session *s, psm_object_id id);

/** Skaliert so, dass die groesste Kante genau size_mm betraegt. */
PSM_API psm_result psm_model_scale_to_fit(psm_session *s, psm_object_id id, float size_mm);

/**
 * Skaliert und zentriert ein Objekt gleichmaessig auf das aktuelle Bett.
 * fill_ratio ist der genutzte Anteil von Breite und Tiefe, (0, 1].
 */
PSM_API psm_result psm_model_fit_to_bed(psm_session *s,
                                        psm_object_id id,
                                        float fill_ratio);

PSM_API psm_result psm_model_duplicate(psm_session *s, psm_object_id id, psm_object_id *out_new_id);

/** Auto-Arrange ueber libnest2d. Blockierend, aber typisch unter 1 s. */
PSM_API psm_result psm_arrange(psm_session *s, float gap_mm);
/**
 * Ordnet ein bestimmtes mobiles Bett an, ohne die sichtbare Bettauswahl
 * zu wechseln. Jedes mobile Bett bleibt dabei ein eigenes lokales Modell.
 */
PSM_API psm_result psm_arrange_bed(psm_session *s, size_t bed_index,
                                   float gap_mm);

/* ------------------------------------------------------------------ */
/* Erweiterte Modellwerkzeuge                                         */
/* ------------------------------------------------------------------ */

/**
 * Zerlegt alle getrennten Koerper eines Objekts in eigene Objekte.
 * Modifier und vorhandene Bemalung werden wie im Desktop beim
 * Neuaufbau der Meshes verworfen.
 */
PSM_API psm_result psm_model_split_objects(psm_session *s,
                                            psm_object_id id,
                                            psm_object_id *out_ids,
                                            size_t out_ids_cap,
                                            size_t *out_count);

/**
 * Zerlegt getrennte Koerper innerhalb der druckbaren Volumen in
 * einzelne Volumen. out_count ist die neue Volumenzahl.
 */
PSM_API psm_result psm_model_split_volumes(psm_session *s,
                                            psm_object_id id,
                                            size_t *out_count);

/**
 * Schneidet an einer horizontalen Ebene in Bettkoordinaten.
 * keep_upper / keep_lower waehlen die Ergebnisse, keep_as_parts legt
 * beide Haelften als Volumen eines Objekts statt als getrennte Objekte an.
 */
PSM_API psm_result psm_model_cut_z(psm_session *s,
                                   psm_object_id id,
                                   float z_mm,
                                   int32_t keep_upper,
                                   int32_t keep_lower,
                                   int32_t keep_as_parts,
                                   psm_object_id *out_ids,
                                   size_t out_ids_cap,
                                   size_t *out_count);

/**
 * Reduziert die Dreieckszahl aller druckbaren Volumen mit PrusaSlicers
 * Quadric-Edge-Collapse. ratio liegt in [0.01, 1.0].
 */
PSM_API psm_result psm_model_simplify(psm_session *s,
                                      psm_object_id id,
                                      float ratio,
                                      uint32_t *out_before,
                                      uint32_t *out_after);

typedef enum {
    PSM_PRIMITIVE_BOX      = 0,
    PSM_PRIMITIVE_CYLINDER = 1,
    PSM_PRIMITIVE_SPHERE   = 2
} psm_primitive_shape;

/**
 * Erzeugt ein neues Volumen im Zentrum des Objekts. Damit entstehen
 * Modifier, Negativvolumen sowie Support-Blocker/-Enforcer ohne Dateiimport.
 */
PSM_API psm_result psm_model_add_primitive_volume(psm_session *s,
                                                   psm_object_id id,
                                                   psm_volume_type type,
                                                   psm_primitive_shape shape,
                                                   float size_x,
                                                   float size_y,
                                                   float size_z,
                                                   size_t *out_volume_index);

PSM_API psm_result psm_model_remove_volume(psm_session *s,
                                           psm_object_id id,
                                           size_t volume_index);

/**
 * Erzeugt editierbare Text- beziehungsweise SVG-Geometrie als Volumen.
 * font_path ist auf Android typischerweise /system/fonts/Roboto-Regular.ttf.
 */
PSM_API psm_result psm_model_add_text_volume(psm_session *s,
                                             psm_object_id id,
                                             const char *utf8_text,
                                             const char *font_path,
                                             float size_mm,
                                             float depth_mm,
                                             psm_volume_type type,
                                             size_t *out_volume_index);

PSM_API psm_result psm_model_add_svg_volume(psm_session *s,
                                            psm_object_id id,
                                            const char *svg_path,
                                            float depth_mm,
                                            psm_volume_type type,
                                            size_t *out_volume_index);

/**
 * Richtet die angetippte Mesh-Flaeche nach unten aus und legt das Objekt
 * anschliessend auf Z=0.
 */
PSM_API psm_result psm_model_lay_on_facet(psm_session *s,
                                          psm_object_id id,
                                          size_t volume_index,
                                          size_t facet_index);

typedef enum {
    PSM_PAINT_SUPPORT = 0,
    PSM_PAINT_SEAM    = 1,
    PSM_PAINT_FUZZY   = 2,
    PSM_PAINT_MMU     = 3
} psm_paint_tool;

/**
 * Markiert ein Originaldreieck. state: 0 loeschen; Support/Naht nutzen
 * 1=enforce, 2=block; Fuzzy nutzt 1; MMU nutzt die 1-basierte Extrudernummer.
 */
PSM_API psm_result psm_model_paint_facet(psm_session *s,
                                         psm_object_id id,
                                         size_t volume_index,
                                         size_t facet_index,
                                         psm_paint_tool tool,
                                         int32_t state);

/**
 * Touch-Pinsel um ein Startdreieck. Es werden nur kantenverbundene,
 * ähnlich ausgerichtete Facetten innerhalb radius_mm markiert.
 */
PSM_API psm_result psm_model_paint_brush(psm_session *s,
                                         psm_object_id id,
                                         size_t volume_index,
                                         size_t facet_index,
                                         psm_paint_tool tool,
                                         int32_t state,
                                         float radius_mm);

PSM_API psm_result psm_model_clear_paint(psm_session *s,
                                         psm_object_id id,
                                         psm_paint_tool tool);

/** Zahl der mit diesem Werkzeug markierten Original-/Teilfacetten. */
PSM_API size_t psm_model_paint_count(psm_session *s,
                                     psm_object_id id,
                                     psm_paint_tool tool);

/**
 * Setzt die variablen Schichthoehen als Paare z_mm, height_mm.
 * Mindestens zwei Paare, streng steigende Z-Werte. count=0 setzt zurueck.
 */
PSM_API psm_result psm_model_layer_profile_set(psm_session *s,
                                                psm_object_id id,
                                                const double *z_height_pairs,
                                                size_t pair_count);

PSM_API size_t psm_model_layer_profile_count(psm_session *s,
                                              psm_object_id id);
PSM_API psm_result psm_model_layer_profile_at(psm_session *s,
                                               psm_object_id id,
                                               size_t index,
                                               double *out_z,
                                               double *out_height);

/** Objektfarbe und Purge-Optionen aus dem Desktop-Objektbaum. */
PSM_API psm_result psm_model_colour_get(psm_session *s, psm_object_id id,
                                        char *out, size_t out_cap);
PSM_API psm_result psm_model_colour_set(psm_session *s, psm_object_id id,
                                        const char *rgb);
PSM_API psm_result psm_model_wipe_get(psm_session *s, psm_object_id id,
                                      int32_t *out_infill, int32_t *out_objects);
PSM_API psm_result psm_model_wipe_set(psm_session *s, psm_object_id id,
                                      int32_t into_infill, int32_t into_objects);

/* ------------------------------------------------------------------ */
/* Projektbezogener Custom-G-Code und Wipe-Tower                       */
/* ------------------------------------------------------------------ */

typedef enum {
    PSM_CUSTOM_COLOR_CHANGE = 0,
    PSM_CUSTOM_PAUSE        = 1,
    PSM_CUSTOM_TOOL_CHANGE  = 2,
    PSM_CUSTOM_TEMPLATE     = 3,
    PSM_CUSTOM_CODE         = 4
} psm_custom_gcode_type;

typedef struct {
    double                print_z;
    psm_custom_gcode_type type;
    int32_t               extruder;
    char                  color[32];
    char                  extra[1024];
} psm_custom_gcode;

PSM_API size_t psm_custom_gcode_count(psm_session *s);
PSM_API psm_result psm_custom_gcode_at(psm_session *s, size_t index,
                                       psm_custom_gcode *out);
PSM_API psm_result psm_custom_gcode_add(psm_session *s,
                                        const psm_custom_gcode *item);
PSM_API psm_result psm_custom_gcode_update(psm_session *s, size_t index,
                                           const psm_custom_gcode *item);
PSM_API psm_result psm_custom_gcode_remove(psm_session *s, size_t index);
PSM_API psm_result psm_custom_gcode_clear(psm_session *s);

PSM_API psm_result psm_wipe_tower_get(psm_session *s,
                                      float *out_x, float *out_y,
                                      float *out_rotation_deg);
PSM_API psm_result psm_wipe_tower_set(psm_session *s,
                                      float x, float y, float rotation_deg);

/* ------------------------------------------------------------------ */
/* Presets                                                             */
/* ------------------------------------------------------------------ */

typedef enum {
    PSM_PRESET_PRINT    = 0,
    PSM_PRESET_FILAMENT = 1,
    PSM_PRESET_PRINTER  = 2
} psm_preset_type;

/*
 * Ersteinrichtung: erst die verfuegbaren Druckermodelle ansehen, dann
 * gezielt installieren.
 *
 * Warum nicht einfach alles laden: Die mitgelieferten Prusa-Bundles
 * ergeben 221 Drucker, 520 Druckprofile und 5762 Filamente. Das kostet
 * beim Start rund 14 Sekunden und ueberschwemmt jede Auswahlliste.
 * Der Desktop loest das ueber den Konfigurationsassistenten - hier
 * genauso: einmal die eigenen Drucker waehlen, danach ist nur noch
 * relevant, was dazu passt.
 */

typedef struct {
    char    vendor_id[64];
    char    model_id[64];
    char    name[128];
    char    family[64];
    int32_t technology;      /* 0 = FFF, 1 = SLA */
    int32_t variant_count;   /* Duesengroessen bzw. Varianten */
} psm_printer_model;

/** Liest die Vendor-Bundles, ohne Presets zu materialisieren. Schnell. */
PSM_API psm_result psm_printer_models_scan(psm_session *s, size_t *out_count);

PSM_API psm_result psm_printer_model_at(psm_session *s, size_t index, psm_printer_model *out);

/** Variante (z. B. Duesendurchmesser) eines Modells. */
PSM_API psm_result psm_printer_variant_at(psm_session *s, size_t model_index, size_t variant_index,
                                          char *out, size_t out_cap);

/**
 * Installiert genau die angegebenen Modelle und laedt die dazu passenden
 * Profile.
 *
 * @param model_keys Schluessel im Format "vendor_id:model_id".
 * @param count      Anzahl. 0 bedeutet: alles installieren.
 */
PSM_API psm_result psm_presets_install(psm_session *s,
                                       const char *const *model_keys,
                                       size_t count);

/** Kurzform fuer psm_presets_install(s, NULL, 0) - installiert alles. */
PSM_API psm_result psm_presets_load_bundled(psm_session *s);

PSM_API size_t psm_preset_count(psm_session *s, psm_preset_type type);

/** Schreibt den Namen des n-ten Presets nach out (UTF-8, nullterminiert). */
PSM_API psm_result psm_preset_name_at(psm_session *s, psm_preset_type type,
                                      size_t index, char *out, size_t out_cap);

/**
 * Auch Profile auflisten, die zum gewaehlten Drucker nicht passen -
 * PrusaSlicers "Show incompatible print and filament presets".
 *
 * Standardmaessig aus. Mobil war die Liste anfangs immer gefiltert, weil
 * ohne Suche niemand durch tausende Filamente scrollt; mit Suche ist die
 * vollstaendige Liste wieder handhabbar.
 */
PSM_API psm_result psm_preset_show_incompatible(psm_session *s, int32_t on);
PSM_API int32_t    psm_preset_shows_incompatible(psm_session *s);

/**
 * Ob der n-te Eintrag zum gewaehlten Drucker passt (1) oder nicht (0).
 * Damit kann die Oberflaeche unpassende Eintraege kennzeichnen, statt
 * sie entweder zu verstecken oder ununterscheidbar mitzulisten.
 */
PSM_API psm_result psm_preset_compatible_at(psm_session *s, psm_preset_type type,
                                            size_t index, int32_t *out);

/** Waehlt ein Preset. Inkompatible Kombinationen werden abgelehnt. */
PSM_API psm_result psm_preset_select(psm_session *s, psm_preset_type type, const char *name);

/** Aktuell gewaehltes Preset. */
PSM_API psm_result psm_preset_selected(psm_session *s, psm_preset_type type,
                                       char *out, size_t out_cap);

/* ------------------------------------------------------------------ */
/* Geaenderte Werte gegenueber dem gewaehlten Preset                    */
/* ------------------------------------------------------------------ */

/*
 * PrusaSlicer haelt Aenderungen nicht in einer eigenen Kopie, sondern im
 * "edited preset" jeder Sammlung. Der Unterschied zum gewaehlten Preset
 * ist die Liste der geaenderten Werte, und genau die zeigt der Desktop
 * beim Profilwechsel im Dialog "Unsaved Changes" an.
 *
 * Wir uebernehmen den Mechanismus unveraendert - psm_config_set schreibt
 * ins edited preset, nicht in eine losgeloeste Konfiguration.
 */

/** Zahl der gegenueber dem gewaehlten Preset geaenderten Werte. */
PSM_API size_t psm_preset_dirty_count(psm_session *s, psm_preset_type type);

/**
 * Ein geaenderter Wert. Alle out-Zeiger duerfen null sein.
 *
 * @param out_key   Parametername
 * @param out_old   Wert im Preset
 * @param out_new   aktuell eingestellter Wert
 */
PSM_API psm_result psm_preset_dirty_at(psm_session *s, psm_preset_type type, size_t index,
                                       char *out_key, size_t key_cap,
                                       char *out_old, size_t old_cap,
                                       char *out_new, size_t new_cap);

/** Verwirft alle Aenderungen und stellt das gewaehlte Preset wieder her. */
PSM_API psm_result psm_preset_discard(psm_session *s, psm_preset_type type);

/** Speichert den aktuellen Stand als eigenes Preset unter neuem Namen. */
PSM_API psm_result psm_preset_save_as(psm_session *s, psm_preset_type type, const char *name);

/**
 * Waehlt ein Preset und traegt die uebergebenen Werte danach wieder ein -
 * das "Transfer" aus PrusaSlicers Dialog. Schluessel und Werte kommen
 * paarweise aus psm_preset_dirty_at.
 */
PSM_API psm_result psm_preset_select_keeping(psm_session *s, psm_preset_type type,
                                             const char *name,
                                             const char *const *keys,
                                             const char *const *values,
                                             size_t count);

/**
 * Liest oder schreibt einen Wert ausdrücklich im bearbeiteten Preset einer
 * Sammlung. Das ist für compatible_printers nötig, weil derselbe Schlüssel
 * sowohl im Druck- als auch im Filamentprofil vorkommt.
 */
PSM_API psm_result psm_preset_config_get(psm_session *s, psm_preset_type type,
                                         const char *key,
                                         char *out, size_t out_cap);
PSM_API psm_result psm_preset_config_set(psm_session *s, psm_preset_type type,
                                         const char *key, const char *value);

/**
 * Liest einen Wert aus einem benannten Preset, ohne es auszuwaehlen.
 *
 * Gedacht fuer Uebersichten: die Materialauswahl braucht von jedem
 * Filamentprofil Typ und Farbe, und zwar von allen gleichzeitig. Ueber
 * die Auswahl zu gehen hiesse, fuer jede Zeile die ganze Konfiguration
 * umzubauen und das Slice-Ergebnis zu verwerfen.
 */
PSM_API psm_result psm_preset_option_at(psm_session *s, psm_preset_type type,
                                        const char *preset_name, const char *key,
                                        char *out, size_t out_cap);

/**
 * Ist ein Parameter im aktuellen Zustand ueberhaupt wirksam?
 *
 * PrusaSlicer graut aus, was gerade nichts bewirkt - alle Stuetzenwerte
 * bei ausgeschalteten Stuetzen, Ironing-Werte ohne Ironing,
 * Reinigungsturm-Werte bei einem Extruder. Die Regeln stammen woertlich
 * aus ConfigManipulation::toggle_print_fff_options; siehe
 * build/scripts/extract-toggles.py.
 *
 * Der Desktop sagt nicht, WARUM gesperrt ist. Auf einem Tablet, wo kein
 * Handbuch danebenliegt, ist das die wichtigere Haelfte - deshalb nennt
 * out_reason den Parameter, der die Sperre ausloest, oder bleibt leer.
 *
 * @return 1 = bedienbar, 0 = wirkungslos
 */
PSM_API int32_t psm_config_enabled(psm_session *s, const char *key,
                                   char *out_reason, size_t reason_cap);

/*
 * Werte, die je Extruder einen Eintrag haben.
 *
 * retract_length, nozzle_diameter, extruder_offset und die uebrigen
 * Parameter der Extruderseite sind Vektoren. psm_config_get liefert
 * dafuer die ganze Reihe ("0.8,0.8,0.8"), was sich nicht bearbeiten
 * laesst. Diese beiden greifen einen einzelnen Eintrag heraus - genau
 * wie append_single_option_line(key, "", extruder_idx) am Desktop.
 *
 * Bei Parametern ohne Vektor verhalten sie sich wie psm_config_get/set.
 */
PSM_API psm_result psm_config_get_at(psm_session *s, const char *key, int32_t index,
                                     char *out, size_t out_cap);

PSM_API psm_result psm_config_set_at(psm_session *s, const char *key, int32_t index,
                                     const char *value);

/* ------------------------------------------------------------------ */
/* Extruder: Filament und Farbe je Kopf                                */
/* ------------------------------------------------------------------ */

/*
 * Ein MMU3 hat fuenf Filamentwege, ein XL bis zu fuenf Werkzeugkoepfe.
 * Beide brauchen je Extruder ein eigenes Filament und eine eigene Farbe,
 * sonst ist der Drucker zwar gewaehlt, druckt aber einfarbig.
 *
 * Das Filament je Extruder liegt in PresetBundle::extruders_filaments,
 * die Farbe in der Druckeroption extruder_colour. Beides ist
 * PrusaSlicers eigener Weg.
 */

/** Zahl der Extruder = Laenge von nozzle_diameter. */
PSM_API int32_t psm_extruder_count(psm_session *s);

/** Filament des n-ten Extruders. */
PSM_API psm_result psm_extruder_filament_get(psm_session *s, int32_t extruder,
                                             char *out, size_t out_cap);

/** Setzt das Filament des n-ten Extruders. */
PSM_API psm_result psm_extruder_filament_set(psm_session *s, int32_t extruder,
                                             const char *name);

/**
 * Farbe des n-ten Extruders als "#RRGGBB". Leer, wenn keine gesetzt ist -
 * dann gilt die Farbe des Filaments.
 */
PSM_API psm_result psm_extruder_color_get(psm_session *s, int32_t extruder,
                                          char *out, size_t out_cap);

/** Setzt die Farbe. Leerer String loescht sie wieder. */
PSM_API psm_result psm_extruder_color_set(psm_session *s, int32_t extruder,
                                          const char *rgb);

/* ------------------------------------------------------------------ */
/* ColorMix / virtuelle Extruder                                      */
/* ------------------------------------------------------------------ */

/*
 * PrusaSlicer speichert ColorMix als JSON im 3MF und expandiert einen
 * virtuellen Extruder beim Slicen zu seinen physischen Komponenten.
 * Diese Grenze übernimmt bewusst dieses Format, damit Blend- und
 * Höhenverlauf-Rezepte ohne paralleles Datenmodell erhalten bleiben.
 * IDs und physische Extruder sind 1-basiert wie im Desktop.
 */
PSM_API psm_result psm_colormix_get_json(psm_session *s,
                                         char *out, size_t out_cap);
PSM_API psm_result psm_colormix_set_json(psm_session *s,
                                         const char *json);

/* ------------------------------------------------------------------ */
/* Filamenthersteller                                                  */
/* ------------------------------------------------------------------ */

/*
 * Zum gewaehlten Drucker passen schnell mehrere hundert Filamente. Der
 * Desktop laesst deshalb im Assistenten nach Hersteller waehlen
 * (PageMaterials, Spalte "vendor"); das Feld dafuer ist filament_vendor
 * im Profil selbst.
 */

/** Zahl der Hersteller unter den zum Drucker passenden Filamenten. */
PSM_API size_t psm_filament_vendor_count(psm_session *s);

/**
 * Ein Hersteller. out_filaments erhaelt die Zahl seiner passenden
 * Filamente, out_enabled ob er gerade eingeblendet ist. Beide duerfen
 * null sein.
 */
PSM_API psm_result psm_filament_vendor_at(psm_session *s, size_t index,
                                          char *out, size_t out_cap,
                                          int32_t *out_filaments,
                                          int32_t *out_enabled);

/**
 * Blendet genau die genannten Hersteller ein. Eine leere Liste zeigt
 * wieder alle.
 */
PSM_API psm_result psm_filament_vendors_set(psm_session *s,
                                            const char *const *names, size_t count);

/* ------------------------------------------------------------------ */
/* Einzelparameter                                                     */
/* ------------------------------------------------------------------ */

/*
 * Die Experten-UI wird aus PrintConfig generiert statt handgebaut -
 * siehe docs/05-ui-konzept-touch-stift.md. Dafuer braucht die App
 * Zugriff auf die Metadaten jedes Parameters.
 */

typedef enum {
    PSM_CFG_BOOL    = 0,
    PSM_CFG_INT     = 1,
    PSM_CFG_FLOAT   = 2,
    PSM_CFG_STRING  = 3,
    PSM_CFG_ENUM    = 4,
    PSM_CFG_PERCENT = 5,
    PSM_CFG_POINT   = 6,
    PSM_CFG_OTHER   = 99
} psm_config_type;

/*
 * Sichtbarkeitsstufe eines Parameters.
 *
 * Steht bereits an jeder Option in PrintConfig (comSimple/comAdvanced/
 * comExpert). Welche Einstellung auf welcher Stufe erscheint, ist damit
 * uebernommene Information und keine Entwurfsentscheidung.
 */
typedef enum {
    PSM_MODE_SIMPLE   = 0,
    PSM_MODE_ADVANCED = 1,
    PSM_MODE_EXPERT   = 2
} psm_config_mode;

typedef struct {
    char            key[64];
    char            label[128];
    char            category[64];
    char            tooltip[1024];
    char            unit[16];
    psm_config_type type;
    psm_config_mode mode;
    float           min;
    float           max;
    int32_t         has_min;
    int32_t         has_max;
    int32_t         enum_count;   /* nur bei PSM_CFG_ENUM */
} psm_config_meta;

PSM_API size_t psm_config_key_count(psm_session *s);
PSM_API psm_result psm_config_meta_at(psm_session *s, size_t index, psm_config_meta *out);
PSM_API psm_result psm_config_meta_for(psm_session *s, const char *key, psm_config_meta *out);
PSM_API psm_result psm_config_enum_value_at(psm_session *s, const char *key, size_t index,
                                            char *out_value, size_t value_cap,
                                            char *out_label, size_t label_cap);

/** Wert als String lesen/setzen - eine Repraesentation fuer alle Typen,
 *  genau wie PrusaSlicer es intern auch macht. */
PSM_API psm_result psm_config_get(psm_session *s, const char *key, char *out, size_t out_cap);
PSM_API psm_result psm_config_set(psm_session *s, const char *key, const char *value);

/* ------------------------------------------------------------------ */
/* Slicing                                                             */
/* ------------------------------------------------------------------ */

typedef enum {
    PSM_STATE_IDLE     = 0,
    PSM_STATE_RUNNING  = 1,
    PSM_STATE_DONE     = 2,
    PSM_STATE_FAILED   = 3,
    PSM_STATE_CANCELLED= 4,
    /**
     * Ein Slice wurde fertig, waehrend Modell oder Konfiguration bereits
     * weiter bearbeitet wurden. Sein G-Code darf nicht exportiert oder
     * an einen Drucker gesendet werden.
     */
    PSM_STATE_STALE    = 5
} psm_slice_state;

/**
 * Fortschrittsmeldung.
 * @param percent 0..100
 * @param stage   benannte Phase, UTF-8, z. B. "Perimeter", "Fuellung".
 *                Die UI zeigt den Namen an, nicht nur Prozent - das macht
 *                mehrminutige Wartezeiten ertraeglich.
 * @return        0 = weitermachen, ungleich 0 = abbrechen.
 *
 * Wird aus dem Slice-Thread aufgerufen, nicht aus dem UI-Thread.
 */
typedef int (*psm_progress_cb)(int percent, const char *stage, void *user);

/** Startet das Slicing und kehrt sofort zurueck. */
PSM_API psm_result psm_slice_start(psm_session *s, psm_progress_cb cb, void *user);

/** Fordert Abbruch an. Darf aus jedem Thread aufgerufen werden. */
PSM_API void psm_slice_cancel(psm_session *s);

PSM_API psm_slice_state psm_slice_state_get(psm_session *s);

/**
 * Ob das letzte Ergebnis noch zur Szene und zur Konfiguration passt.
 *
 * 1 = gueltig, es gibt Werkzeugwege zum Anzeigen und einen G-Code zum
 * Weitergeben. 0 = es muss neu geschnitten werden. Die Oberflaeche
 * braucht das, um die Vorschau nicht bei jedem Hinsehen neu zu rechnen.
 */
PSM_API int psm_slice_result_is_current(psm_session *s);

/** Blockiert, bis der Job fertig ist. timeout_ms < 0 = unbegrenzt. */
PSM_API psm_result psm_slice_wait(psm_session *s, int timeout_ms);

typedef struct {
    double  print_time_seconds;
    double  filament_used_mm;
    double  filament_used_g;
    double  filament_cost;
    int32_t layer_count;
    float   max_z;
    int32_t object_count;
} psm_slice_stats;

PSM_API psm_result psm_slice_stats_get(psm_session *s, psm_slice_stats *out);

/**
 * Verbrauch eines Extruders im letzten Ergebnis.
 *
 * Alles in Kubikmillimetern, wie PrusaSlicer es fuehrt. Gramm und
 * Kosten rechnet die Oberflaeche daraus aus - Dichte und Preis stehen
 * im Filamentprofil, das sie ohnehin kennt.
 */
typedef struct {
    int32_t extruder;       /**< 0-basiert, wie in den Profilen */
    double  volume_mm3;     /**< im Modell */
    double  wipe_tower_mm3; /**< im Reinigungsturm */
    double  flush_mm3;      /**< beim Spuelen verworfen */
} psm_extruder_usage;

/**
 * Wie viele Extruder im letzten Ergebnis wirklich gedruckt haben.
 *
 * Nicht dasselbe wie die Zahl der eingerichteten: ein Fuenf-Farb-Drucker
 * kann einfarbig drucken, und dann gibt es nur eine Zeile.
 */
PSM_API size_t psm_slice_extruder_count(psm_session *s);

PSM_API psm_result psm_slice_extruder_at(psm_session *s, size_t index,
                                         psm_extruder_usage *out);


/**
 * Der Dateiname, den PrusaSlicer fuer dieses Ergebnis vergeben wuerde.
 *
 * Nicht selbst zusammengesetzt: Print::output_filename() wertet
 * output_filename_format aus dem Druckprofil aus. Prusas eigene Profile
 * setzen dort etwa
 *   {input_filename_base}_{layer_height}mm_{initial_filament_type}_{printer_model}_{print_time}.gcode
 * womit Modellname, Schichthoehe, Material, Drucker und Druckzeit im
 * Namen stehen. Endet auf .bgcode, wenn binary_gcode gesetzt ist.
 */
PSM_API psm_result psm_gcode_suggested_name(psm_session *s, char *out, size_t out_cap);

/** Schreibt den G-Code direkt in eine Datei. Nie ueber den RAM -
 *  siehe Speicherstrategie in docs/02-architektur.md. */
PSM_API psm_result psm_gcode_export(psm_session *s, const char *out_path);

/** Exportiert das aktive mobile Bett inklusive Transformationen. */
PSM_API psm_result psm_plate_export_stl(psm_session *s, const char *out_path);
PSM_API psm_result psm_plate_export_obj(psm_session *s, const char *out_path);

/** Repariert eine STL beim Einlesen mit libslic3r/admesh und schreibt sie neu. */
PSM_API psm_result psm_stl_repair(psm_session *s,
                                  const char *input_path,
                                  const char *output_path);

/** Konvertiert G-Code zwischen ASCII und Prusas binaerem BGCode. */
PSM_API psm_result psm_gcode_convert(psm_session *s,
                                     const char *input_path,
                                     const char *output_path,
                                     int32_t to_binary);

/* ------------------------------------------------------------------ */
/* Speicher                                                            */
/* ------------------------------------------------------------------ */

/** Grober Schaetzwert des Spitzenspeichers fuer den aktuellen Bettinhalt,
 *  in Bytes. Die App vergleicht das gegen das Geraetebudget und warnt
 *  vorher, statt vom Low-Memory-Killer erwischt zu werden. */
PSM_API uint64_t psm_estimate_slice_memory(psm_session *s);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* PSMOBILE_CORE_H */
