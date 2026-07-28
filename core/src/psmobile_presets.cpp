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
         * Auf dem Desktop holt der PresetUpdater sie aus dem Netz und
         * PresetBundle::load_presets() liest sie ueber eine AppConfig ein,
         * in der installierte Drucker vermerkt sind. Auf dem Mobilgeraet
         * gibt es weder Updater noch vorhandene AppConfig, deshalb laden
         * wir die Bundles direkt als Systemprofile. Das ist der kuerzere
         * und robustere Weg - und er kommt ohne beschreibbares
         * Vendor-Verzeichnis aus. */
        const fs::path src_profiles = fs::path(s->resdir) / "profiles";
        if (! fs::exists(src_profiles)) {
            s->set_error("Profilverzeichnis fehlt: " + src_profiles.string());
            return PSM_ERR_IO;
        }

        size_t loaded = 0;
        for (fs::directory_iterator it(src_profiles); it != fs::directory_iterator(); ++it) {
            if (! fs::is_regular_file(it->status()) || it->path().extension() != ".ini")
                continue;
            try {
                auto res = s->presets->load_configbundle(
                    it->path().string(),
                    PresetBundle::LoadConfigBundleAttribute::LoadSystem,
                    ForwardCompatibilitySubstitutionRule::EnableSilent);
                loaded += res.second;
            } catch (const std::exception &e) {
                /* Ein kaputtes Vendor-Bundle darf nicht den ganzen Start
                 * verhindern - der Rest bleibt nutzbar. */
                psm_emit_log(PSM_LOG_WARN,
                             "Bundle uebersprungen: " + it->path().filename().string() +
                             " (" + e.what() + ")");
            }
        }

        if (loaded == 0) {
            s->set_error("keine Profile gefunden in " + src_profiles.string());
            return PSM_ERR_PARSE;
        }

        s->presets->update_multi_material_filament_presets();
        s->presets->update_compatible(PresetSelectCompatibleType::Always);

        /* Die aktive Konfiguration ist ab jetzt die aus den Presets. */
        s->config = s->presets->full_config();
        psm_emit_log(PSM_LOG_INFO, std::to_string(loaded) + " Profile geladen");

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
