/*
 * psm_testcli - Machbarkeitsbeweis fuer M2.
 *
 * Bewusst reines C: das beweist nebenbei, dass das ABI wirklich C ist
 * und ohne C++-Laufzeit auf der Aufruferseite funktioniert.
 *
 * Nutzung auf dem Geraet:
 *   adb push psm_testcli /data/local/tmp/
 *   adb push modell.stl  /data/local/tmp/
 *   adb shell /data/local/tmp/psm_testcli \
 *        --res /data/local/tmp/resources \
 *        --data /data/local/tmp/psmdata \
 *        --out /data/local/tmp/out.gcode \
 *        /data/local/tmp/modell.stl
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static void on_log(psm_log_level lvl, const char *msg, void *user)
{
    (void) user;
    static const char *tag[] = { "FEHLER", "WARN  ", "INFO  ", "DEBUG " };
    fprintf(stderr, "[%s] %s\n", tag[lvl], msg);
}

static int last_pct = -1;

static int on_progress(int percent, const char *stage, void *user)
{
    (void) user;
    if (percent != last_pct) {
        printf("  %3d%%  %s\n", percent, stage ? stage : "");
        fflush(stdout);
        last_pct = percent;
    }
    return 0; /* nicht abbrechen */
}

static double now_seconds(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec + (double) ts.tv_nsec / 1e9;
}

int main(int argc, char **argv)
{
    const char *resdir  = "resources";
    const char *datadir = "psmdata";
    const char *outfile = "out.gcode";
    const char *input   = NULL;
    const char *printer = NULL;

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--res") == 0 && i + 1 < argc)        resdir  = argv[++i];
        else if (strcmp(argv[i], "--data") == 0 && i + 1 < argc)  datadir = argv[++i];
        else if (strcmp(argv[i], "--out") == 0 && i + 1 < argc)   outfile = argv[++i];
        else if (strcmp(argv[i], "--printer") == 0 && i + 1 < argc) printer = argv[++i];
        else input = argv[i];
    }

    int list_only = 0, scan_only = 0;
    const char *install_keys[32];
    size_t install_count = 0;

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--list") == 0)      { list_only = 1; input = NULL; }
        else if (strcmp(argv[i], "--scan") == 0) { scan_only = 1; input = NULL; }
        else if (strcmp(argv[i], "--install") == 0 && i + 1 < argc &&
                 install_count < 32) {
            install_keys[install_count++] = argv[++i];
            if (input == argv[i]) input = NULL;
        }
    }

    if (input == NULL && ! list_only && ! scan_only) {
        fprintf(stderr,
            "Nutzung: psm_testcli [--res DIR] [--data DIR] [--out DATEI]\n"
            "                     [--printer NAME] MODELL\n"
            "         psm_testcli [--res DIR] [--data DIR] --list\n");
        return 2;
    }

    /* Zeilenweise puffern: ueber adb haengt stdout an einer Pipe und
     * waere sonst blockgepuffert - bei einem Absturz gingen genau die
     * interessanten Zeilen verloren. */
    setvbuf(stdout, NULL, _IOLBF, 0);

    psm_set_log_callback(on_log, NULL);

    printf("PSMobile-Kern %s, ABI %d\n", psm_core_version(), psm_abi_version());

    psm_session *s = psm_session_create(datadir, resdir);
    if (s == NULL) {
        fprintf(stderr, "Session liess sich nicht anlegen: %s\n", psm_last_error(NULL));
        return 1;
    }

    /* Druckermodelle sichten - das ist der schnelle Schritt. */
    size_t n_models = 0;
    const double t_scan = now_seconds();
    if (psm_printer_models_scan(s, &n_models) == PSM_OK)
        printf("%zu Druckermodelle gefunden in %.2f s\n",
               n_models, now_seconds() - t_scan);

    if (scan_only) {
        for (size_t i = 0; i < n_models && i < 200; ++i) {
            psm_printer_model m;
            if (psm_printer_model_at(s, i, &m) == PSM_OK)
                printf("  %s:%-24s %-34s %s (%d Varianten)\n",
                       m.vendor_id, m.model_id, m.name,
                       m.technology == 0 ? "FFF" : "SLA", m.variant_count);
        }
        psm_session_destroy(s);
        return 0;
    }

    /* Nur die per --install gewaehlten Modelle einrichten, sonst alle. */
    const double t_inst = now_seconds();
    const psm_result ir = (install_count > 0)
        ? psm_presets_install(s, install_keys, install_count)
        : psm_presets_load_bundled(s);
    if (ir != PSM_OK)
        fprintf(stderr, "Warnung: Profile nicht geladen (%s) - nutze Vorgabewerte\n",
                psm_last_error(s));
    else
        printf("Profile eingerichtet in %.2f s\n", now_seconds() - t_inst);

    if (list_only) {
        static const char *label[] = { "Druckprofile", "Filamente", "Drucker" };
        for (int t = 0; t < 3; ++t) {
            const size_t n = psm_preset_count(s, (psm_preset_type) t);
            printf("\n%s: %zu\n", label[t], n);
            for (size_t i = 0; i < n && i < 40; ++i) {
                char name[256];
                if (psm_preset_name_at(s, (psm_preset_type) t, i, name, sizeof(name)) == PSM_OK)
                    printf("  %s\n", name);
            }
            if (n > 40)
                printf("  ... (%zu weitere)\n", n - 40);
        }
        psm_session_destroy(s);
        return 0;
    }

    if (printer != NULL && psm_preset_select(s, PSM_PRESET_PRINTER, printer) != PSM_OK)
        fprintf(stderr, "Warnung: Drucker '%s' nicht waehlbar: %s\n", printer, psm_last_error(s));

    psm_object_id ids[64];
    size_t        count = 0;
    if (psm_model_load(s, input, ids, 64, &count) != PSM_OK) {
        fprintf(stderr, "Laden fehlgeschlagen: %s\n", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }
    printf("geladen: %zu Objekt(e) aus %s\n", count, input);

    for (size_t i = 0; i < count && i < 64; ++i) {
        psm_object_info info;
        if (psm_model_info(s, ids[i], &info) == PSM_OK)
            printf("  #%d  %-28s  %7u Dreiecke  %.1f x %.1f x %.1f mm\n",
                   info.id, info.name, info.triangle_count,
                   info.bbox_max[0] - info.bbox_min[0],
                   info.bbox_max[1] - info.bbox_min[1],
                   info.bbox_max[2] - info.bbox_min[2]);
    }

    printf("geschaetzter Spitzenspeicher: %.0f MB\n",
           (double) psm_estimate_slice_memory(s) / (1024.0 * 1024.0));

    printf("slice...\n");
    const double t0 = now_seconds();
    if (psm_slice_start(s, on_progress, NULL) != PSM_OK) {
        fprintf(stderr, "Slice-Start fehlgeschlagen: %s\n", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }

    const psm_result wr = psm_slice_wait(s, -1);
    const double secs = now_seconds() - t0;

    if (wr != PSM_OK) {
        fprintf(stderr, "Slicing fehlgeschlagen (%d): %s\n", wr, psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }

    psm_slice_stats st;
    if (psm_slice_stats_get(s, &st) == PSM_OK)
        printf("fertig in %.3f s - %d Layer, %.2f mm hoch, Druckzeit %.0f min, "
               "Filament %.2f m / %.1f g\n",
               secs, st.layer_count, st.max_z,
               st.print_time_seconds / 60.0,
               st.filament_used_mm / 1000.0, st.filament_used_g);

    if (psm_gcode_export(s, outfile) != PSM_OK) {
        fprintf(stderr, "Export fehlgeschlagen: %s\n", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }
    printf("G-Code geschrieben: %s\n", outfile);

    psm_session_destroy(s);
    return 0;
}
