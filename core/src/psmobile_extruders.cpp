/*
 * psmobile_extruders.cpp - Filament und Farbe je Extruder, geaenderte
 * Werte gegenueber dem Preset, Auswahl der Filamenthersteller.
 *
 * Alles hier folgt Mechanismen, die PrusaSlicer schon hat:
 *
 *   - Aenderungen leben im "edited preset" jeder Sammlung, nicht in einer
 *     eigenen Kopie. Damit funktionieren current_dirty_options(),
 *     discard_current_changes() und save_current_preset() unveraendert.
 *   - Das Filament je Extruder steht in PresetBundle::extruders_filaments,
 *     genau wie es GUI_App::select_filament_preset benutzt.
 *   - Die Farbe je Extruder ist die Druckeroption extruder_colour.
 *   - Der Hersteller eines Filaments steht als filament_vendor im Profil;
 *     der Assistent des Desktops gruppiert damit seine Materialliste.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"
#include "psmobile_session.hpp"

#include <algorithm>
#include <cstring>
#include <map>
#include <set>
#include <string>
#include <vector>

#include "libslic3r/Preset.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/PrintConfig.hpp"

using namespace Slic3r;

namespace {

void copy_str(char *dst, size_t cap, const std::string &src)
{
    if (dst == nullptr || cap == 0)
        return;
    const size_t n = std::min(cap - 1, src.size());
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

PresetCollection *collection_of(psm_session *s, psm_preset_type type)
{
    if (s == nullptr || ! s->presets)
        return nullptr;
    switch (type) {
        case PSM_PRESET_PRINT:    return &s->presets->prints;
        case PSM_PRESET_FILAMENT: return &s->presets->filaments;
        case PSM_PRESET_PRINTER:  return &s->presets->printers;
        default:                  return nullptr;
    }
}

/* Die flache Sicht neu aufbauen - so geht sie beim Slicen an Print::apply. */
void refresh_config(psm_session *s)
{
    if (s->presets)
        s->config = s->presets->full_config();
}

/* Hersteller eines Filaments, wie ihn der Assistent des Desktops liest. */
std::string vendor_of(const Preset &p)
{
    const auto *opt = p.config.opt<ConfigOptionStrings>("filament_vendor");
    if (opt != nullptr && ! opt->values.empty() && ! opt->values.front().empty())
        return opt->values.front();
    return "Ohne Herstellerangabe";
}

/* extruder_colour ist eine Druckeroption mit einem Eintrag je Extruder. */
ConfigOptionStrings *extruder_colours(psm_session *s, bool create)
{
    if (! s->presets)
        return nullptr;
    DynamicPrintConfig &cfg = s->presets->printers.get_edited_preset().config;
    auto *opt = cfg.option<ConfigOptionStrings>("extruder_colour", create);
    if (opt == nullptr)
        return nullptr;
    const auto n = static_cast<size_t>(psm_extruder_count(s));
    if (opt->values.size() < n)
        opt->values.resize(n, std::string());
    return opt;
}

struct VendorRow {
    std::string name;
    int32_t     total   = 0;
    int32_t     visible = 0;
};

/*
 * Alle Hersteller unter den zum Drucker passenden Filamenten.
 *
 * Bewusst nicht zwischengespeichert: die Liste haengt am gewaehlten
 * Drucker, und ein paar hundert Profile durchzugehen ist billig.
 */
std::vector<VendorRow> vendor_rows(psm_session *s)
{
    std::vector<VendorRow> rows;
    if (! s->presets || s->presets->extruders_filaments.empty())
        return rows;

    const PresetCollection  &fc = s->presets->filaments;
    const ExtruderFilaments &ef = s->presets->extruders_filaments.front();

    std::map<std::string, VendorRow> by_name;
    for (size_t i = 0; i < fc.size(); ++i) {
        const Preset &p = fc.preset(i);
        if (p.is_default || ! ef.filament(i).is_compatible)
            continue;
        const std::string v = vendor_of(p);
        VendorRow &row = by_name[v];
        row.name = v;
        ++row.total;
        if (p.is_visible)
            ++row.visible;
    }

    rows.reserve(by_name.size());
    for (auto &kv : by_name)
        rows.push_back(kv.second);

    /* Die groessten zuerst - oben steht, was man am ehesten sucht. */
    std::sort(rows.begin(), rows.end(),
              [](const VendorRow &a, const VendorRow &b) {
                  if (a.total != b.total) return a.total > b.total;
                  return a.name < b.name;
              });
    return rows;
}

} /* namespace */

extern "C" {

/* ------------------------------------------------------------------ */
/* Geaenderte Werte gegenueber dem gewaehlten Preset                    */
/* ------------------------------------------------------------------ */

PSM_API size_t psm_preset_dirty_count(psm_session *s, psm_preset_type type)
{
    PresetCollection *c = collection_of(s, type);
    if (c == nullptr)
        return 0;
    try {
        return c->current_dirty_options().size();
    } catch (const std::exception &) {
        return 0;
    }
}

PSM_API psm_result psm_preset_dirty_at(psm_session *s, psm_preset_type type, size_t index,
                                       char *out_key, size_t key_cap,
                                       char *out_old, size_t old_cap,
                                       char *out_new, size_t new_cap)
{
    PresetCollection *c = collection_of(s, type);
    if (c == nullptr)
        return s == nullptr ? PSM_ERR_INVALID_ARG : PSM_ERR_NOT_FOUND;
    try {
        const std::vector<std::string> keys = c->current_dirty_options();
        if (index >= keys.size())
            return PSM_ERR_NOT_FOUND;

        const std::string &key = keys[index];
        copy_str(out_key, key_cap, key);

        const DynamicPrintConfig &was = c->get_selected_preset().config;
        const DynamicPrintConfig &now = c->get_edited_preset().config;
        copy_str(out_old, old_cap, was.has(key) ? was.opt_serialize(key) : std::string());
        copy_str(out_new, new_cap, now.has(key) ? now.opt_serialize(key) : std::string());
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_preset_discard(psm_session *s, psm_preset_type type)
{
    PresetCollection *c = collection_of(s, type);
    if (c == nullptr)
        return s == nullptr ? PSM_ERR_INVALID_ARG : PSM_ERR_NOT_FOUND;
    try {
        c->discard_current_changes();
        refresh_config(s);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_preset_save_as(psm_session *s, psm_preset_type type, const char *name)
{
    if (name == nullptr || *name == 0)
        return PSM_ERR_INVALID_ARG;
    PresetCollection *c = collection_of(s, type);
    if (c == nullptr)
        return s == nullptr ? PSM_ERR_INVALID_ARG : PSM_ERR_NOT_FOUND;
    try {
        /* Legt das Preset an, waehlt es aus und schreibt es in den
         * Datenordner - das "Speichern unter" des Desktops. */
        c->save_current_preset(name);
        s->presets->update_compatible(PresetSelectCompatibleType::Never);
        refresh_config(s);
        psm_emit_log(PSM_LOG_INFO, std::string("Preset gespeichert: ") + name);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_preset_select_keeping(psm_session *s, psm_preset_type type,
                                             const char *name,
                                             const char *const *keys,
                                             const char *const *values,
                                             size_t count)
{
    const psm_result r = psm_preset_select(s, type, name);
    if (r != PSM_OK)
        return r;
    if (keys == nullptr || values == nullptr || count == 0)
        return PSM_OK;

    /* Einzeln nachtragen. Was das neue Preset nicht kennt, wird
     * uebersprungen, statt den ganzen Wechsel scheitern zu lassen. */
    size_t taken = 0;
    for (size_t i = 0; i < count; ++i) {
        if (keys[i] == nullptr || values[i] == nullptr)
            continue;
        if (psm_config_set(s, keys[i], values[i]) == PSM_OK)
            ++taken;
    }
    psm_emit_log(PSM_LOG_INFO,
                 std::to_string(taken) + " von " + std::to_string(count) +
                 " Aenderungen uebernommen");
    return PSM_OK;
}

/* ------------------------------------------------------------------ */
/* Werte mit einem Eintrag je Extruder                                 */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_config_get_at(psm_session *s, const char *key, int32_t index,
                                     char *out, size_t out_cap)
{
    if (s == nullptr || key == nullptr || out == nullptr || index < 0)
        return PSM_ERR_INVALID_ARG;
    try {
        const ConfigOption *opt = s->config.option(key);
        if (opt == nullptr)
            return PSM_ERR_NOT_FOUND;

        /* Nur Vektoren haben Eintraege je Extruder; alles andere gibt es
         * genau einmal und wird unveraendert durchgereicht. */
        const auto *vec = dynamic_cast<const ConfigOptionVectorBase *>(opt);
        if (vec == nullptr || static_cast<size_t>(index) >= vec->size()) {
            copy_str(out, out_cap, opt->serialize());
            return PSM_OK;
        }
        copy_str(out, out_cap, vec->vserialize()[static_cast<size_t>(index)]);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_config_set_at(psm_session *s, const char *key, int32_t index,
                                     const char *value)
{
    if (s == nullptr || key == nullptr || value == nullptr || index < 0)
        return PSM_ERR_INVALID_ARG;
    try {
        const ConfigOption *opt = s->config.option(key);
        if (opt == nullptr) {
            s->set_error(std::string("unbekannter Parameter: ") + key);
            return PSM_ERR_NOT_FOUND;
        }
        const auto *vec = dynamic_cast<const ConfigOptionVectorBase *>(opt);
        if (vec == nullptr)
            return psm_config_set(s, key, value);

        /* Die ganze Reihe holen, den einen Eintrag ersetzen, zurueck-
         * schreiben. So bleibt es bei einem einzigen Weg ins bearbeitete
         * Preset - psm_config_set kuemmert sich um Sammlung und Dirty-
         * Kennzeichnung. */
        std::vector<std::string> values = vec->vserialize();
        if (static_cast<size_t>(index) >= values.size())
            values.resize(static_cast<size_t>(index) + 1,
                          values.empty() ? std::string() : values.back());
        values[static_cast<size_t>(index)] = value;

        std::string joined;
        for (size_t i = 0; i < values.size(); ++i) {
            if (i) joined += ',';
            joined += values[i];
        }
        return psm_config_set(s, key, joined.c_str());
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

/* ------------------------------------------------------------------ */
/* Extruder: Filament und Farbe je Kopf                                */
/* ------------------------------------------------------------------ */

PSM_API int32_t psm_extruder_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    const auto *nd = s->config.opt<ConfigOptionFloats>("nozzle_diameter");
    if (nd == nullptr || nd->values.empty())
        return 1;
    return static_cast<int32_t>(nd->values.size());
}

PSM_API psm_result psm_extruder_filament_get(psm_session *s, int32_t extruder,
                                             char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr || extruder < 0)
        return PSM_ERR_INVALID_ARG;
    if (! s->presets)
        return PSM_ERR_NOT_FOUND;
    const auto idx = static_cast<size_t>(extruder);
    if (idx >= s->presets->extruders_filaments.size())
        return PSM_ERR_NOT_FOUND;
    try {
        copy_str(out, out_cap,
                 s->presets->extruders_filaments[idx].get_selected_preset_name());
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_extruder_filament_set(psm_session *s, int32_t extruder,
                                             const char *name)
{
    if (s == nullptr || name == nullptr || extruder < 0)
        return PSM_ERR_INVALID_ARG;
    if (! s->presets)
        return PSM_ERR_NOT_FOUND;
    const auto idx = static_cast<size_t>(extruder);
    if (idx >= s->presets->extruders_filaments.size())
        return PSM_ERR_NOT_FOUND;
    try {
        /* Der Weg aus GUI_App::select_filament_preset: ein unsichtbares
         * Preset erst ueber die flache Sammlung sichtbar machen, sonst
         * lehnt select_filament es ab. */
        const size_t pi = s->presets->filaments.get_preset_idx_by_name(name);
        if (pi == size_t(-1)) {
            s->set_error(std::string("Filament nicht gefunden: ") + name);
            return PSM_ERR_NOT_FOUND;
        }
        if (! s->presets->filaments.preset(pi).is_visible)
            s->presets->filaments.select_preset(pi);

        if (! s->presets->extruders_filaments[idx].select_filament(name)) {
            s->set_error(std::string("Filament passt nicht zum Drucker: ") + name);
            return PSM_ERR_INVALID_ARG;
        }
        /* Extruder 0 ist zugleich das bearbeitete Filamentpreset. */
        if (idx == 0)
            s->presets->filaments.select_preset_by_name(name, false);

        refresh_config(s);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_extruder_color_get(psm_session *s, int32_t extruder,
                                          char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr || extruder < 0)
        return PSM_ERR_INVALID_ARG;
    try {
        const ConfigOptionStrings *opt = extruder_colours(s, false);
        const auto idx = static_cast<size_t>(extruder);
        copy_str(out, out_cap,
                 (opt != nullptr && idx < opt->values.size()) ? opt->values[idx]
                                                             : std::string());
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_extruder_color_set(psm_session *s, int32_t extruder,
                                          const char *rgb)
{
    if (s == nullptr || rgb == nullptr || extruder < 0)
        return PSM_ERR_INVALID_ARG;
    try {
        ConfigOptionStrings *opt = extruder_colours(s, true);
        const auto idx = static_cast<size_t>(extruder);
        if (opt == nullptr || idx >= opt->values.size())
            return PSM_ERR_NOT_FOUND;
        opt->values[idx] = rgb;
        s->presets->printers.update_dirty();
        refresh_config(s);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

/* ------------------------------------------------------------------ */
/* Filamenthersteller                                                  */
/* ------------------------------------------------------------------ */

PSM_API size_t psm_filament_vendor_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    try {
        return vendor_rows(s).size();
    } catch (const std::exception &) {
        return 0;
    }
}

PSM_API psm_result psm_filament_vendor_at(psm_session *s, size_t index,
                                          char *out, size_t out_cap,
                                          int32_t *out_filaments,
                                          int32_t *out_enabled)
{
    if (s == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        const std::vector<VendorRow> rows = vendor_rows(s);
        if (index >= rows.size())
            return PSM_ERR_NOT_FOUND;
        copy_str(out, out_cap, rows[index].name);
        if (out_filaments != nullptr) *out_filaments = rows[index].total;
        if (out_enabled   != nullptr) *out_enabled   = rows[index].visible > 0 ? 1 : 0;
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_filament_vendors_set(psm_session *s,
                                            const char *const *names, size_t count)
{
    if (s == nullptr)
        return PSM_ERR_INVALID_ARG;
    if (! s->presets)
        return PSM_ERR_NOT_FOUND;
    try {
        std::set<std::string> wanted;
        for (size_t i = 0; i < count; ++i)
            if (names != nullptr && names[i] != nullptr)
                wanted.insert(names[i]);

        /* is_visible ist genau der Schalter, den der Assistent des
         * Desktops ueber die Sektion "filaments" der AppConfig setzt.
         * Hier direkt gesetzt - das spart ein Neuladen aller Presets. */
        PresetCollection &fc = s->presets->filaments;
        size_t shown = 0;
        for (size_t i = 0; i < fc.size(); ++i) {
            Preset &p = fc.preset(i, false);
            if (p.is_default)
                continue;
            p.is_visible = wanted.empty() || wanted.count(vendor_of(p)) > 0;
            if (p.is_visible)
                ++shown;
        }

        /* Falls das gewaehlte Filament gerade ausgeblendet wurde, zieht
         * Always ein passendes sichtbares nach. */
        s->presets->update_compatible(PresetSelectCompatibleType::Always);
        refresh_config(s);

        psm_emit_log(PSM_LOG_INFO,
                     std::to_string(wanted.size()) + " Hersteller gewaehlt, " +
                     std::to_string(shown) + " Filamentprofile sichtbar");
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

} /* extern "C" */
