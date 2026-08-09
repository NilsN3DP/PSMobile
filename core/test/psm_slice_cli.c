/*
 * psm_slice_cli - headlose Kommandozeile fuer den Remote-Slice-Server.
 *
 * Anders als psm_testcli (STL/OBJ + Druckername, M2-Nachweis) nimmt
 * dieser hier ein vollstaendiges .3mf-Projekt, wie es die App selbst
 * exportiert (psm_project_save_3mf): Drucker-, Filament- und
 * Druckprofil stecken bereits darin, keine Kommandozeilen-Auswahl
 * noetig. Fortschritt und Ergebnis gehen als NDJSON (eine JSON-Zeile
 * pro Ereignis) nach stdout - server.py liest das zeilenweise und
 * reicht es unveraendert an den Client weiter, ohne selbst etwas
 * ueber Slicer-Interna wissen zu muessen.
 *
 * Nutzung:
 *   psm_slice_cli --res DIR --data DIR --project PROJEKT.3mf --out OUT.gcode
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* Anfuehrungszeichen und Backslashes maskieren - Stufennamen und
 * Fehlertexte koennen beides enthalten (z.B. Dateipfade unter Windows-
 * exportierten Projekten). Ohne das waere die NDJSON-Zeile kaputt. */
static void print_json_string(const char *s)
{
    putchar('"');
    for (const char *p = s; *p; ++p) {
        if (*p == '"' || *p == '\\') putchar('\\');
        if ((unsigned char) *p < 0x20) { printf("\\u%04x", *p); continue; }
        putchar(*p);
    }
    putchar('"');
}

static void on_log(psm_log_level lvl, const char *msg, void *user)
{
    (void) user;
    static const char *tag[] = { "error", "warn", "info", "debug" };
    printf("{\"type\":\"log\",\"level\":");
    print_json_string(tag[lvl]);
    printf(",\"message\":");
    print_json_string(msg);
    printf("}\n");
    fflush(stdout);
}

static int last_pct = -1;

static int on_progress(int percent, const char *stage, void *user)
{
    (void) user;
    if (percent != last_pct) {
        printf("{\"type\":\"progress\",\"percent\":%d,\"stage\":", percent);
        print_json_string(stage ? stage : "");
        printf("}\n");
        fflush(stdout);
        last_pct = percent;
    }
    return 0; /* nicht abbrechen - der Server bricht ueber SIGTERM ab,
               * nicht ueber diesen Rueckkanal */
}

static void fail(const char *stage, const char *message)
{
    printf("{\"type\":\"error\",\"stage\":");
    print_json_string(stage);
    printf(",\"message\":");
    print_json_string(message);
    printf("}\n");
    fflush(stdout);
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
    const char *project = NULL;

    for (int i = 1; i < argc; ++i) {
        if (strcmp(argv[i], "--res") == 0 && i + 1 < argc)          resdir  = argv[++i];
        else if (strcmp(argv[i], "--data") == 0 && i + 1 < argc)    datadir = argv[++i];
        else if (strcmp(argv[i], "--out") == 0 && i + 1 < argc)     outfile = argv[++i];
        else if (strcmp(argv[i], "--project") == 0 && i + 1 < argc) project = argv[++i];
    }

    if (project == NULL) {
        fprintf(stderr,
            "Nutzung: psm_slice_cli --res DIR --data DIR "
            "--project PROJEKT.3mf --out OUT.gcode\n");
        return 2;
    }

    setvbuf(stdout, NULL, _IOLBF, 0);
    psm_set_log_callback(on_log, NULL);

    psm_session *s = psm_session_create(datadir, resdir);
    if (s == NULL) {
        fail("session", psm_last_error(NULL));
        return 1;
    }

    /* Wie beim Client: nur die drei gebuendelten Hersteller. Das
     * Projekt bringt sein eigenes Profil eingebettet mit (siehe
     * psm_project_load_3mf) - fehlt das passende installierte Profil,
     * bleibt die eingebettete Konfiguration als projektlokales
     * externes Profil erhalten. Reicht fuer v1, siehe
     * docs/remote-slicing.md. */
    if (psm_presets_load_bundled(s) != PSM_OK)
        fail("presets", psm_last_error(s));

    psm_project_import_info info;
    if (psm_project_load_3mf(s, project, &info) != PSM_OK) {
        fail("project", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }
    printf("{\"type\":\"loaded\",\"object_count\":%d,\"bed_count\":%d,"
           "\"printer\":", info.object_count, info.bed_count);
    print_json_string(info.selected_printer);
    printf("}\n");
    fflush(stdout);

    const double t0 = now_seconds();
    if (psm_slice_start(s, on_progress, NULL) != PSM_OK) {
        fail("slice-start", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }

    const psm_result wr = psm_slice_wait(s, -1);
    const double secs = now_seconds() - t0;

    if (wr != PSM_OK) {
        fail("slice", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }

    psm_slice_stats st;
    memset(&st, 0, sizeof(st));
    psm_slice_stats_get(s, &st);

    if (psm_gcode_export(s, outfile) != PSM_OK) {
        fail("export", psm_last_error(s));
        psm_session_destroy(s);
        return 1;
    }

    printf("{\"type\":\"done\",\"seconds\":%.3f,\"layer_count\":%d,"
           "\"max_z\":%.3f,\"print_time_seconds\":%.0f,"
           "\"filament_mm\":%.2f,\"filament_g\":%.2f,\"out\":",
           secs, st.layer_count, st.max_z, st.print_time_seconds,
           st.filament_used_mm, st.filament_used_g);
    print_json_string(outfile);
    printf("}\n");

    psm_session_destroy(s);
    return 0;
}
