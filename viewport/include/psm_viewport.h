/*
 * psm_viewport.h - Lebenszyklus und Eingabe des 3D-Viewports.
 *
 * Wichtig zur Abgrenzung gegenueber psmobile_core.h:
 * Hier gehen ausschliesslich Steuerbefehle durch (Groesse, Frame, Geste).
 * Geometrie wandert NIE ueber diese Grenze - der Viewport liest das Modell
 * direkt aus der Session, weil er im selben C++-Prozessraum lebt.
 * Siehe docs/entscheidungen.md, E-03.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#ifndef PSM_VIEWPORT_H
#define PSM_VIEWPORT_H

#include <stdint.h>
#include "psmobile_core.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct psm_viewport psm_viewport;

/**
 * @param shader_dir Verzeichnis mit den GLES-Shadern, also
 *                   <resdir>/shaders/ES. Die stammen unveraendert aus
 *                   PrusaSlicer (SLIC3R_OPENGL_ES).
 *
 * Muss auf dem GL-Thread mit gueltigem Kontext aufgerufen werden.
 */
PSM_API psm_viewport *psm_viewport_create(psm_session *session, const char *shader_dir);

PSM_API void psm_viewport_destroy(psm_viewport *v);

PSM_API void psm_viewport_resize(psm_viewport *v, int width, int height);

/** Zeichnet ein Bild. Nur auf dem GL-Thread. */
PSM_API void psm_viewport_render(psm_viewport *v);

/** Nach jeder Aenderung am Modell aufrufen - baut die Puffer neu auf. */
PSM_API void psm_viewport_invalidate(psm_viewport *v);

/* --- Kamera ------------------------------------------------------- */

PSM_API void psm_viewport_orbit(psm_viewport *v, float dx, float dy);
PSM_API void psm_viewport_pan(psm_viewport *v, float dx, float dy);
PSM_API void psm_viewport_zoom(psm_viewport *v, float factor);
PSM_API void psm_viewport_reset_view(psm_viewport *v);

/** Feste Blickrichtung: 0 iso, 1 oben, 2 vorne, 3 hinten, 4 links, 5 rechts. */
PSM_API void psm_viewport_view_preset(psm_viewport *v, int which);

/* --- Auswahl ------------------------------------------------------ */

/** Strahltest an Bildschirmposition. Liefert die Objekt-ID oder PSM_INVALID_ID. */
PSM_API psm_object_id psm_viewport_pick(psm_viewport *v, float x, float y);

PSM_API void psm_viewport_set_selection(psm_viewport *v, psm_object_id id);

/** Mehrfachauswahl; primary traegt Gizmos und numerische Bearbeitung. */
PSM_API void psm_viewport_set_selections(psm_viewport *v,
                                         const psm_object_id *ids,
                                         size_t count,
                                         psm_object_id primary);

typedef struct {
    psm_object_id object_id;
    int32_t       volume_index;
    int32_t       facet_index;
    int32_t       instance_index;
    float         position[3];
    float         normal[3];
} psm_surface_hit;

/**
 * Exakter Dreieckstreffer fuer Flachlegen, Bemalen und Messen.
 * Anders als psm_viewport_pick prueft dieser Aufruf nicht nur Huellboxen.
 */
PSM_API int psm_viewport_pick_surface(psm_viewport *v, float x, float y,
                                      psm_surface_hit *out);

/*
 * Ausgewaehltes Objekt mit dem Finger verschieben.
 *
 * Der Bildschirmversatz wird auf die Bettebene projiziert, damit sich das
 * Objekt unter dem Finger mitbewegt statt mit fester Empfindlichkeit -
 * bei schraeger Kamera waere jede Pixelumrechnung falsch.
 *
 * @return 1 wenn etwas bewegt wurde, sonst 0.
 */
PSM_API int psm_viewport_drag_selected(psm_viewport *v,
                                       float from_x, float from_y,
                                       float to_x, float to_y);

/*
 * Ausgewaehltes Objekt gleichmaessig skalieren.
 *
 * Fuer die Spreizgeste: solange das Skalieren-Werkzeug aktiv ist,
 * vergroessert und verkleinert sie das Objekt, statt die Kamera zu
 * zoomen. Danach setzt der Aufruf es wieder aufs Bett - sonst schwebt es
 * beim Verkleinern in der Luft oder steckt beim Vergroessern darin.
 *
 * @return 1 wenn skaliert wurde, sonst 0.
 */
PSM_API int psm_viewport_scale_selected(psm_viewport *v, float factor);

/* --- Griffe am Objekt ---------------------------------------------- */
/*
 * PrusaSlicers Gizmo-Klassen haengen an wx und an dessen eigenem
 * Auswahlmechanismus; sie sind nicht uebernehmbar. Aussehen und
 * Bedienlogik folgen dem Original, der Code ist neu - einer der wenigen
 * Punkte, an denen E-12 den Nachbau vorsieht.
 */
typedef enum {
    PSM_GIZMO_NONE   = 0,
    PSM_GIZMO_MOVE   = 1,   /* drei Pfeile entlang der Achsen */
    PSM_GIZMO_ROTATE = 2,   /* drei Kreise um die Achsen */
    PSM_GIZMO_SCALE  = 3    /* Wuerfel an den Achsenenden */
} psm_gizmo_mode;

PSM_API void psm_viewport_set_gizmo(psm_viewport *v, psm_gizmo_mode mode);
PSM_API psm_gizmo_mode psm_viewport_get_gizmo(const psm_viewport *v);

/**
 * Sucht den Griff unter dem Finger.
 *
 * Nicht ueber GL-Picking, sondern indem die Ankerpunkte der Griffe auf
 * den Bildschirm projiziert werden und der naechste innerhalb des
 * Schwellwerts gewinnt. Auf dem Tablet muss der grosszuegig sein - der
 * Desktop kommt mit fuenf Pixeln aus, ein Finger nicht.
 *
 * @param radius_px Trefferradius in Bildpunkten
 * @return 0 = X, 1 = Y, 2 = Z, 3 = gleichmaessig (nur Skalieren),
 *         -1 = keiner
 */
PSM_API int psm_viewport_gizmo_pick(psm_viewport *v, float x, float y, float radius_px);

/**
 * Wendet einen Zug auf den zuvor gegriffenen Griff an.
 *
 * @param axis   Ergebnis von psm_viewport_gizmo_pick
 * @param snap   1 = auf sinnvolle Schritte rasten (15 Grad, 1 mm)
 * @return 1 wenn sich etwas geaendert hat
 */
PSM_API int psm_viewport_gizmo_drag(psm_viewport *v, int axis,
                                    float from_x, float from_y,
                                    float to_x, float to_y, int snap);

/* --- Vorschau ------------------------------------------------------ */
/*
 * Die G-Code-Vorschau ist derselbe Renderer wie im Vorschau-Tab des
 * Desktops: libvgcode aus PrusaSlicer, mit dessen eigener Umwandlung
 * von Print nach GCodeInputData. Nichts davon ist nachgebaut.
 */

typedef enum {
    PSM_VIEW_EDITOR  = 0,   /* Bett und Modelle */
    PSM_VIEW_PREVIEW = 1    /* Werkzeugwege */
} psm_view_mode;

PSM_API void psm_viewport_set_mode(psm_viewport *v, psm_view_mode mode);
PSM_API psm_view_mode psm_viewport_get_mode(psm_viewport *v);

/**
 * Uebernimmt das Ergebnis des letzten Slice-Laufs in die Vorschau.
 * Muss auf dem GL-Thread laufen. Ohne fertigen Slice passiert nichts.
 * @return 1 bei Erfolg.
 */
PSM_API int psm_viewport_load_preview(psm_viewport *v);

PSM_API int32_t psm_viewport_layer_count(psm_viewport *v);

/** Sichtbaren Layerbereich setzen, wie der Slider im Desktop. */
PSM_API void psm_viewport_set_layer_range(psm_viewport *v, int32_t first, int32_t last);

/** Letzte Fehlermeldung des Viewports, etwa beim Laden der Shader. */
PSM_API const char *psm_viewport_last_error(psm_viewport *v);

#ifdef __cplusplus
}
#endif

#endif /* PSM_VIEWPORT_H */
