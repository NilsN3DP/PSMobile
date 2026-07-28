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

/** Letzte Fehlermeldung des Viewports, etwa beim Laden der Shader. */
PSM_API const char *psm_viewport_last_error(psm_viewport *v);

#ifdef __cplusplus
}
#endif

#endif /* PSM_VIEWPORT_H */
