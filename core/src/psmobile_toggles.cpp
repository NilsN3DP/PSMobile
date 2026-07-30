/*
 * psmobile_toggles.cpp - welcher Parameter ist gerade wirksam?
 *
 * Die Regeln selbst kommen aus PrusaSlicer und liegen erzeugt in
 * generated/psm_toggles.cpp. Hier steht nur die Huelle drumherum: das
 * Ergebnis zwischenspeichern und die Begruendung nachreichen.
 *
 * Zur Begruendung: der Desktop graut aus und schweigt. Wer nicht weiss,
 * dass "Support material" aus ist, sucht lange, warum sich die
 * Stuetzenwerte nicht anfassen lassen. Auf einem Tablet, wo kein
 * Handbuch danebenliegt, ist das schlimmer - deshalb sagen wir, welcher
 * Parameter die Sperre ausloest.
 *
 * Die Zuordnung Parameter auf Ausloeser ist von Hand gepflegt. Sie liesse
 * sich nicht aus dem Original ziehen: dort steckt sie in booleschen
 * Ausdruecken, nicht in Daten.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"
#include "psmobile_session.hpp"
#include "psmobile_toggles.hpp"

#include <cstring>
#include <map>
#include <string>

#include "libslic3r/PrintConfig.hpp"

namespace {

void copy_str(char *dst, size_t cap, const std::string &src)
{
    if (dst == nullptr || cap == 0)
        return;
    const size_t n = std::min(cap - 1, src.size());
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

/*
 * Wer sperrt wen.
 *
 * Nur die Faelle, die man tatsaechlich trifft - eine vollstaendige Karte
 * aller 56 Regeln waere ueberwiegend Rauschen. Fehlt ein Eintrag, bleibt
 * das Feld gesperrt und die Begruendung leer; das ist immer noch besser
 * als der Desktop.
 */
const std::map<std::string, const char *> &reason_map()
{
    static const std::map<std::string, const char *> m = {
        /* Stuetzen */
        { "support_material_threshold",              "support_material" },
        { "support_material_pattern",                "support_material" },
        { "support_material_spacing",                "support_material" },
        { "support_material_angle",                  "support_material" },
        { "support_material_contact_distance",       "support_material" },
        { "support_material_bottom_contact_distance","support_material" },
        { "support_material_interface_layers",       "support_material" },
        { "support_material_interface_spacing",      "support_material" },
        { "support_material_buildplate_only",        "support_material" },
        { "support_material_xy_spacing",             "support_material" },
        { "support_material_with_sheath",            "support_material" },
        { "dont_support_bridges",                    "support_material" },
        { "support_material_extruder",               "support_material" },
        { "support_material_interface_extruder",     "support_material" },
        { "support_material_speed",                  "support_material" },
        { "support_material_interface_speed",        "support_material" },
        { "support_material_extrusion_width",        "support_material" },

        /* Fuellung */
        { "fill_pattern",                            "fill_density" },
        { "infill_every_layers",                     "fill_density" },
        { "solid_infill_every_layers",               "fill_density" },
        { "solid_infill_below_area",                 "fill_density" },
        { "infill_extruder",                         "fill_density" },
        { "infill_anchor_max",                       "fill_density" },
        { "infill_speed",                            "fill_density" },

        /* Buegeln */
        { "ironing_type",                            "ironing" },
        { "ironing_flowrate",                        "ironing" },
        { "ironing_spacing",                         "ironing" },
        { "ironing_speed",                           "ironing" },

        /* Perimeter */
        { "extra_perimeters",                        "perimeters" },
        { "thin_walls",                              "perimeters" },
        { "overhangs",                               "perimeters" },
        { "seam_position",                           "perimeters" },
        { "external_perimeters_first",               "perimeters" },
        { "perimeter_speed",                         "perimeters" },
        { "external_perimeter_speed",                "perimeters" },

        /* Brim */
        { "brim_width",                              "brim_type" },
        { "brim_separation",                         "brim_type" },

        /* Reinigungsturm */
        { "wipe_tower_x",                            "wipe_tower" },
        { "wipe_tower_y",                            "wipe_tower" },
        { "wipe_tower_width",                        "wipe_tower" },
        { "wipe_tower_rotation_angle",               "wipe_tower" },
        { "wipe_tower_brim_width",                   "wipe_tower" },
        { "wipe_tower_cone_angle",                   "wipe_tower" },

        /* Schuerze */
        { "skirt_distance",                          "skirts" },
        { "skirt_height",                            "skirts" },
        { "min_skirt_length",                        "skirts" },

        /* Kuehlung */
        { "min_fan_speed",                           "fan_always_on" },
        { "max_fan_speed",                           "fan_always_on" },
        { "disable_fan_first_layers",                "fan_always_on" },
    };
    return m;
}

} // namespace

extern "C" {

PSM_API int32_t psm_config_enabled(psm_session *s, const char *key,
                                   char *out_reason, size_t reason_cap)
{
    if (out_reason != nullptr && reason_cap > 0)
        out_reason[0] = '\0';
    if (s == nullptr || key == nullptr)
        return 1;

    try {
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        /*
         * Die Regeln einmal je Konfigurationsstand auswerten, nicht je
         * Anfrage: eine Seite fragt vierzig Parameter ab, und der
         * Durchlauf kostet jedes Mal dasselbe.
         */
        if (s->toggle_revision != s->config_revision) {
            psm::ToggleCollector c;
            c.collect_print_fff(&s->config);
            s->toggles = std::move(c.enabled);
            s->toggle_revision = s->config_revision;
        }

        const auto it = s->toggles.find(key);
        if (it == s->toggles.end() || it->second)
            return 1;                       /* keine Regel oder bedienbar */

        const auto r = reason_map().find(key);
        if (r != reason_map().end())
            copy_str(out_reason, reason_cap, r->second);
        return 0;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return 1;   /* im Zweifel bedienbar lassen, nicht sperren */
    }
}

} /* extern "C" */
