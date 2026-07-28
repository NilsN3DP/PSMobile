/*
 * psmobile_presets.cpp - Preset-Verwaltung und Konfigurations-Metadaten.
 *
 * Die Experten-UI der App wird aus diesen Metadaten *generiert* statt
 * handgebaut - siehe docs/05-ui-konzept-touch-stift.md. PrintConfig
 * beschreibt jeden Parameter bereits vollstaendig (Typ, Grenzen, Einheit,
 * Tooltip, Enum-Werte); wir reichen das nur nach oben durch.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"
#include "psmobile_session.hpp"

#include <algorithm>
#include <cstring>

#include <boost/filesystem.hpp>
#include <set>
#include <map>

#include "libslic3r/AppConfig.hpp"
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

/*
 * Indizes der Presets, die zur aktuellen Auswahl passen.
 *
 * PrusaSlicer fuehrt je Preset zwei Flags: is_visible (vom Nutzer
 * installiert) und is_compatible (passt zum gewaehlten Drucker bzw.
 * Druckprofil). Ohne diesen Filter stehen alle 5762 Filamente in der
 * Liste, auch die fuer voellig andere Drucker - unbrauchbar.
 *
 * Das "- default -" wird uebersprungen, solange es echte Profile gibt.
 */
std::vector<size_t> usable_indices(const PresetCollection &c)
{
    std::vector<size_t> out;
    out.reserve(64);
    for (size_t i = 0; i < c.size(); ++i) {
        const Preset &p = c.preset(i);
        if (p.is_default && c.size() > c.num_default_presets())
            continue;
        if (! p.is_visible || ! p.is_compatible)
            continue;
        out.push_back(i);
    }
    /* Falls der Filter alles wegnimmt - etwa weil noch kein Drucker
     * gewaehlt ist - lieber alles Sichtbare zeigen als eine leere Liste. */
    if (out.empty())
        for (size_t i = 0; i < c.size(); ++i)
            if (c.preset(i).is_visible)
                out.push_back(i);
    return out;
}

/*
 * Filamente sind ein Sonderfall.
 *
 * Ihre Kompatibilitaet steht nicht in der flachen PresetCollection,
 * sondern je Extruder in PresetBundle::extruders_filaments. PrusaSlicer
 * prueft dort genau so:
 *     m_extr_filaments[i].is_compatible && m_filaments->preset(i).is_visible
 * Ohne diesen Umweg blieben von 5762 Filamenten alle stehen, statt der
 * paar hundert, die zum gewaehlten Drucker passen.
 */
std::vector<size_t> usable_filament_indices(psm_session *s)
{
    const PresetCollection &fc = s->presets->filaments;
    std::vector<size_t> out;

    if (s->presets->extruders_filaments.empty())
        return {};

    /* ExtruderFilaments hat kein size() - die Liste laeuft aber index-
     * gleich zur Filament-Sammlung, so nutzt PrusaSlicer sie auch. */
    const ExtruderFilaments &ef = s->presets->extruders_filaments.front();
    for (size_t i = 0; i < fc.size(); ++i) {
        const Preset &p = fc.preset(i);
        if (p.is_default && fc.size() > fc.num_default_presets())
            continue;
        if (! p.is_visible || ! ef.filament(i).is_compatible)
            continue;
        out.push_back(i);
    }
    return out;
}

PresetCollection *collection_for(psm_session *s, psm_preset_type type)
{
    if (! s->presets)
        return nullptr;
    switch (type) {
        case PSM_PRESET_PRINT:    return &s->presets->prints;
        case PSM_PRESET_FILAMENT: return &s->presets->filaments;
        case PSM_PRESET_PRINTER:  return &s->presets->printers;
        default:                  return nullptr;
    }
}

psm_config_type map_type(ConfigOptionType t)
{
    switch (t) {
        case coBool:    case coBools:    return PSM_CFG_BOOL;
        case coInt:     case coInts:     return PSM_CFG_INT;
        case coFloat:   case coFloats:   return PSM_CFG_FLOAT;
        case coString:  case coStrings:  return PSM_CFG_STRING;
        case coEnum:                     return PSM_CFG_ENUM;
        case coPercent: case coPercents: return PSM_CFG_PERCENT;
        case coFloatOrPercent:           return PSM_CFG_PERCENT;
        case coPoint:   case coPoints:   return PSM_CFG_POINT;
        default:                         return PSM_CFG_OTHER;
    }
}

/* Sortierte Schluesselliste, einmal aufgebaut und dann gecacht. */
const std::vector<std::string> &config_keys(psm_session *s)
{
    if (s->config_keys.empty()) {
        for (const auto &kv : Slic3r::print_config_def.options)
            s->config_keys.push_back(kv.first);
        std::sort(s->config_keys.begin(), s->config_keys.end());
    }
    return s->config_keys;
}

} // namespace

extern "C" {

/* ------------------------------------------------------------------ */
/* Presets                                                             */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_printer_models_scan(psm_session *s, size_t *out_count)
{
    if (s == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        namespace fs = boost::filesystem;
        s->printer_models.clear();

        const fs::path src_profiles = fs::path(s->resdir) / "profiles";
        if (! fs::exists(src_profiles)) {
            s->set_error("Profilverzeichnis fehlt: " + src_profiles.string());
            return PSM_ERR_IO;
        }

        /* Nur die Vendor-Abschnitte lesen, keine Presets materialisieren.
         * Das ist der schnelle Teil - das Laden der 5762 Filamentprofile
         * dauert Sekunden, dieser Durchlauf Millisekunden. */
        for (fs::directory_iterator it(src_profiles); it != fs::directory_iterator(); ++it) {
            if (! fs::is_regular_file(it->status()) || it->path().extension() != ".ini")
                continue;
            try {
                const VendorProfile vp = VendorProfile::from_ini(it->path(), true);
                if (! vp.valid())
                    continue;
                for (const VendorProfile::PrinterModel &m : vp.models) {
                    psm_session::ScannedModel sm;
                    sm.vendor_id  = vp.id;
                    sm.model_id   = m.id;
                    sm.name       = m.name;
                    sm.family     = m.family;
                    sm.technology = (m.technology == ptSLA) ? 1 : 0;
                    for (const VendorProfile::PrinterVariant &v : m.variants)
                        sm.variants.push_back(v.name);
                    sm.bundle_path = it->path().string();
                    s->printer_models.push_back(std::move(sm));
                }
            } catch (const std::exception &e) {
                psm_emit_log(PSM_LOG_WARN, "Bundle uebersprungen: " +
                             it->path().filename().string() + " (" + e.what() + ")");
            }
        }

        if (out_count != nullptr)
            *out_count = s->printer_models.size();
        psm_emit_log(PSM_LOG_INFO,
                     std::to_string(s->printer_models.size()) + " Druckermodelle gefunden");
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_printer_model_at(psm_session *s, size_t index, psm_printer_model *out)
{
    if (s == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    if (index >= s->printer_models.size())
        return PSM_ERR_INVALID_ARG;

    const psm_session::ScannedModel &m = s->printer_models[index];
    std::memset(out, 0, sizeof(*out));
    copy_str(out->vendor_id, sizeof(out->vendor_id), m.vendor_id);
    copy_str(out->model_id,  sizeof(out->model_id),  m.model_id);
    copy_str(out->name,      sizeof(out->name),      m.name);
    copy_str(out->family,    sizeof(out->family),    m.family);
    out->technology    = m.technology;
    out->variant_count = static_cast<int32_t>(m.variants.size());
    return PSM_OK;
}

PSM_API psm_result psm_printer_variant_at(psm_session *s, size_t model_index,
                                          size_t variant_index, char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    if (model_index >= s->printer_models.size())
        return PSM_ERR_INVALID_ARG;
    const auto &vars = s->printer_models[model_index].variants;
    if (variant_index >= vars.size())
        return PSM_ERR_INVALID_ARG;
    copy_str(out, out_cap, vars[variant_index]);
    return PSM_OK;
}

PSM_API psm_result psm_presets_load_bundled(psm_session *s)
{
    return psm_presets_install(s, nullptr, 0);
}

PSM_API psm_result psm_presets_install(psm_session *s,
                                       const char *const *model_keys,
                                       size_t key_count)
{
    if (s == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        namespace fs = boost::filesystem;

        /* Auswahl als Menge "vendor:model" oder "vendor:model:variant".
         * Leer bedeutet: alles.
         *
         * Die Variante ist die Duesengroesse. Ohne sie werden alle
         * Varianten eines Modells eingerichtet - fuer einen MK4S sind
         * das zehn Druckerprofile, obwohl man in aller Regel eine
         * einzige Duese benutzt. */
        std::set<std::string> wanted_models;                    // "vendor:model"
        std::map<std::string, std::set<std::string>> wanted_variants;

        for (size_t i = 0; i < key_count; ++i) {
            if (model_keys == nullptr || model_keys[i] == nullptr)
                continue;
            const std::string key = model_keys[i];
            const size_t second = key.find(':', key.find(':') == std::string::npos
                                                ? 0 : key.find(':') + 1);
            if (second != std::string::npos) {
                wanted_models.insert(key.substr(0, second));
                wanted_variants[key.substr(0, second)].insert(key.substr(second + 1));
            } else {
                wanted_models.insert(key);
            }
        }
        const std::set<std::string> &wanted = wanted_models;

        s->presets = std::make_unique<PresetBundle>();
        s->presets->setup_directories();

        /* Die mitgelieferten Vendor-Bundles liegen unter resdir/profiles.
         *
         * Der direkte Weg ueber load_configbundle(LoadSystem) sieht
         * naheliegend aus, fuehrt aber nicht zum Ziel: die Presets werden
         * zwar geparst (gemessen: 6983), landen aber nicht in den
         * Sammlungen, weil kein Drucker als installiert gilt. In den
         * Auswahllisten blieben dann nur die "- default -"-Eintraege.
         *
         * Richtig ist der Weg, den auch der Desktop nimmt:
         *   1. Bundles ins beschreibbare datadir/vendor kopieren
         *   2. eine AppConfig aufbauen, in der jedes Druckermodell und
         *      jede Variante als installiert markiert ist
         *   3. load_presets() damit fuettern
         * Auf dem Desktop erledigt Schritt 2 der Installationsassistent;
         * mobil liefern wir die Profile mit und nehmen alles. */
        const fs::path src_profiles = fs::path(s->resdir) / "profiles";
        if (! fs::exists(src_profiles)) {
            s->set_error("Profilverzeichnis fehlt: " + src_profiles.string());
            return PSM_ERR_IO;
        }

        const fs::path vendor_dir = fs::path(Slic3r::data_dir()) / "vendor";
        fs::create_directories(vendor_dir);

        AppConfig app_config(AppConfig::EAppMode::Editor);
        size_t bundles = 0, models = 0;

        for (fs::directory_iterator it(src_profiles); it != fs::directory_iterator(); ++it) {
            if (! fs::is_regular_file(it->status()) || it->path().extension() != ".ini")
                continue;
            try {
                const fs::path dst = vendor_dir / it->path().filename();
                fs::copy_file(it->path(), dst, fs::copy_options::overwrite_existing);

                /* .idx daneben legen, falls vorhanden - PresetBundle
                 * zieht daraus die Versionsinformation. */
                fs::path idx = it->path();
                idx.replace_extension(".idx");
                if (fs::exists(idx))
                    fs::copy_file(idx, vendor_dir / idx.filename(),
                                  fs::copy_options::overwrite_existing);

                const VendorProfile vp = VendorProfile::from_ini(dst, true);
                if (! vp.valid()) {
                    psm_emit_log(PSM_LOG_WARN,
                                 "Bundle ohne gueltigen Vendor-Abschnitt: " +
                                 it->path().filename().string());
                    continue;
                }
                for (const VendorProfile::PrinterModel &m : vp.models) {
                    /* Nur die gewaehlten Modelle installieren. Alles zu
                     * nehmen kostet 13 s Startzeit und ueberschwemmt die
                     * Auswahllisten - siehe E-13. */
                    if (! wanted.empty() &&
                        wanted.find(vp.id + ":" + m.id) == wanted.end())
                        continue;

                    const std::string mkey = vp.id + ":" + m.id;
                    const auto vit = wanted_variants.find(mkey);
                    const std::set<std::string> *vars =
                        (vit == wanted_variants.end()) ? nullptr : &vit->second;

                    if (m.variants.empty()) {
                        app_config.set_variant(vp.id, m.id, "default", true);
                    } else {
                        for (const VendorProfile::PrinterVariant &v : m.variants) {
                            /* Ohne Duesenangabe alle Varianten, sonst nur
                             * die ausgewaehlten. */
                            if (vars != nullptr && vars->find(v.name) == vars->end())
                                continue;
                            app_config.set_variant(vp.id, m.id, v.name, true);
                        }
                    }
                    ++models;
                }
                ++bundles;
            } catch (const std::exception &e) {
                /* Ein kaputtes Vendor-Bundle darf nicht den ganzen Start
                 * verhindern - der Rest bleibt nutzbar. */
                psm_emit_log(PSM_LOG_WARN,
                             "Bundle uebersprungen: " + it->path().filename().string() +
                             " (" + e.what() + ")");
            }
        }

        if (bundles == 0) {
            s->set_error("keine brauchbaren Profile in " + src_profiles.string());
            return PSM_ERR_PARSE;
        }

        s->presets->load_presets(app_config, ForwardCompatibilitySubstitutionRule::EnableSilent);
        s->presets->update_multi_material_filament_presets();
        s->presets->update_compatible(PresetSelectCompatibleType::Always);

        psm_emit_log(PSM_LOG_INFO,
                     std::to_string(bundles) + " Bundle(s), " + std::to_string(models) +
                     " Druckermodelle installiert");
        size_t loaded = s->presets->printers.size();

        /* Die aktive Konfiguration ist ab jetzt die aus den Presets. */
        s->config = s->presets->full_config();
        /* Nutzbare Anzahl melden, nicht die Rohsumme: load_presets() legt
         * immer alle Profile in die Sammlungen, sichtbar und kompatibel
         * ist aber nur ein Bruchteil. Die Rohsumme zu melden waere
         * irrefuehrend. */
        psm_emit_log(PSM_LOG_INFO,
                     std::to_string(usable_indices(s->presets->printers).size()) + " Drucker, " +
                     std::to_string(usable_indices(s->presets->prints).size()) + " Druckprofile, " +
                     std::to_string(usable_filament_indices(s).size()) + " Filamente nutzbar" +
                     " (von " + std::to_string(loaded) + "/" +
                     std::to_string(s->presets->prints.size()) + "/" +
                     std::to_string(s->presets->filaments.size()) + " geladen)");

        s->last_error.clear();
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    } catch (...) {
        s->set_error("unbekannter Fehler beim Laden der Profile");
        return PSM_ERR_GENERIC;
    }
}

PSM_API size_t psm_preset_count(psm_session *s, psm_preset_type type)
{
    if (s == nullptr)
        return 0;
    PresetCollection *c = collection_for(s, type);
    if (c == nullptr)
        return 0;
    return (type == PSM_PRESET_FILAMENT)
        ? usable_filament_indices(s).size() : usable_indices(*c).size();
}

PSM_API psm_result psm_preset_name_at(psm_session *s, psm_preset_type type,
                                      size_t index, char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        PresetCollection *c = collection_for(s, type);
        if (c == nullptr)
            return PSM_ERR_NOT_FOUND;
        const std::vector<size_t> idx = (type == PSM_PRESET_FILAMENT)
            ? usable_filament_indices(s) : usable_indices(*c);
        if (index >= idx.size())
            return PSM_ERR_INVALID_ARG;
        copy_str(out, out_cap, c->preset(idx[index]).name);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_preset_select(psm_session *s, psm_preset_type type, const char *name)
{
    if (s == nullptr || name == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        PresetCollection *c = collection_for(s, type);
        if (c == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (! c->select_preset_by_name(name, false)) {
            s->set_error(std::string("Preset nicht waehlbar: ") + name);
            return PSM_ERR_NOT_FOUND;
        }

        /* Ein neuer Drucker aendert, welche Druck- und Filamentprofile
         * ueberhaupt passen. Ohne diesen Aufruf blieben die alten Flags
         * stehen und die Listen zeigten Unpassendes. Always waehlt bei
         * Bedarf gleich ein kompatibles Profil aus. */
        s->presets->update_multi_material_filament_presets();
        s->presets->update_compatible(PresetSelectCompatibleType::Always);

        s->config = s->presets->full_config();
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_preset_selected(psm_session *s, psm_preset_type type,
                                       char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    PresetCollection *c = collection_for(s, type);
    if (c == nullptr)
        return PSM_ERR_NOT_FOUND;
    copy_str(out, out_cap, c->get_selected_preset_name());
    return PSM_OK;
}

/* ------------------------------------------------------------------ */
/* Konfigurations-Metadaten                                            */
/* ------------------------------------------------------------------ */

PSM_API size_t psm_config_key_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    return config_keys(s).size();
}

PSM_API psm_result psm_config_meta_for(psm_session *s, const char *key, psm_config_meta *out)
{
    if (s == nullptr || key == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        const ConfigOptionDef *def = Slic3r::print_config_def.get(key);
        if (def == nullptr)
            return PSM_ERR_NOT_FOUND;

        std::memset(out, 0, sizeof(*out));
        copy_str(out->key,      sizeof(out->key),      key);
        copy_str(out->label,    sizeof(out->label),    def->label);
        copy_str(out->category, sizeof(out->category), def->category);
        copy_str(out->tooltip,  sizeof(out->tooltip),  def->tooltip);
        copy_str(out->unit,     sizeof(out->unit),     def->sidetext);

        out->type = map_type(def->type);

        /* Sichtbarkeitsstufe direkt aus PrintConfig uebernehmen. */
        switch (def->mode) {
            case comSimple:   out->mode = PSM_MODE_SIMPLE;   break;
            case comAdvanced: out->mode = PSM_MODE_ADVANCED; break;
            default:          out->mode = PSM_MODE_EXPERT;   break;
        }

        /* PrusaSlicer nutzt FLT_MAX/-FLT_MAX als "keine Grenze". */
        if (def->min > -FLT_MAX) { out->has_min = 1; out->min = static_cast<float>(def->min); }
        if (def->max <  FLT_MAX) { out->has_max = 1; out->max = static_cast<float>(def->max); }

        out->enum_count = 0;
        if (def->enum_def)
            out->enum_count = static_cast<int32_t>(def->enum_def->values().size());

        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_config_meta_at(psm_session *s, size_t index, psm_config_meta *out)
{
    if (s == nullptr || out == nullptr)
        return PSM_ERR_INVALID_ARG;
    const std::vector<std::string> &keys = config_keys(s);
    if (index >= keys.size())
        return PSM_ERR_INVALID_ARG;
    return psm_config_meta_for(s, keys[index].c_str(), out);
}

PSM_API psm_result psm_config_enum_value_at(psm_session *s, const char *key, size_t index,
                                            char *out_value, size_t value_cap,
                                            char *out_label, size_t label_cap)
{
    if (s == nullptr || key == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        const ConfigOptionDef *def = Slic3r::print_config_def.get(key);
        if (def == nullptr || ! def->enum_def)
            return PSM_ERR_NOT_FOUND;

        const std::vector<std::string> &values = def->enum_def->values();
        const std::vector<std::string> &labels = def->enum_def->labels();
        if (index >= values.size())
            return PSM_ERR_INVALID_ARG;

        copy_str(out_value, value_cap, values[index]);
        copy_str(out_label, label_cap,
                 index < labels.size() ? labels[index] : values[index]);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

} /* extern "C" */
