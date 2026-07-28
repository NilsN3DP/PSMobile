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

PSM_API psm_result psm_presets_load_bundled(psm_session *s)
{
    if (s == nullptr)
        return PSM_ERR_INVALID_ARG;
    try {
        namespace fs = boost::filesystem;

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
                    if (m.variants.empty()) {
                        app_config.set_variant(vp.id, m.id, "default", true);
                    } else {
                        for (const VendorProfile::PrinterVariant &v : m.variants)
                            app_config.set_variant(vp.id, m.id, v.name, true);
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
        psm_emit_log(PSM_LOG_INFO,
                     std::to_string(loaded) + " Drucker, " +
                     std::to_string(s->presets->prints.size()) + " Druckprofile, " +
                     std::to_string(s->presets->filaments.size()) + " Filamente");

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
    return c == nullptr ? 0 : c->size();
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
        if (index >= c->size())
            return PSM_ERR_INVALID_ARG;
        copy_str(out, out_cap, c->preset(index).name);
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
