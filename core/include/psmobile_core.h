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

#define PSM_ABI_VERSION 1

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
    PSM_ERR_UNSUPPORTED    = -10
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

PSM_API psm_result psm_model_set_position(psm_session *s, psm_object_id id, float x, float y, float z);
PSM_API psm_result psm_model_set_rotation(psm_session *s, psm_object_id id, float rx, float ry, float rz);
PSM_API psm_result psm_model_set_scale(psm_session *s, psm_object_id id, float sx, float sy, float sz);

/** Legt das Objekt flach auf das Bett (kleinster Z-Punkt auf 0). */
PSM_API psm_result psm_model_drop_to_bed(psm_session *s, psm_object_id id);

/** Skaliert so, dass die groesste Kante genau size_mm betraegt. */
PSM_API psm_result psm_model_scale_to_fit(psm_session *s, psm_object_id id, float size_mm);

PSM_API psm_result psm_model_duplicate(psm_session *s, psm_object_id id, psm_object_id *out_new_id);

/** Auto-Arrange ueber libnest2d. Blockierend, aber typisch unter 1 s. */
PSM_API psm_result psm_arrange(psm_session *s, float gap_mm);

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
    PSM_STATE_CANCELLED= 4
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
