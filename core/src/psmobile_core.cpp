/*
 * psmobile_core.cpp - Implementierung des C-ABI gegen libslic3r.
 *
 * Grundsatz: Alle Ausnahmen werden hier abgefangen. Ueber die C-Grenze
 * darf nie eine C++-Exception laufen - weder ueber JNI noch ueber Swift
 * ist das definiert.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_core.h"
#include "psmobile_session.hpp"

#include <algorithm>
#include <cmath>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include <boost/filesystem.hpp>
#include <boost/algorithm/string/predicate.hpp>
#include <boost/nowide/cstdio.hpp>

#include "libslic3r/libslic3r.h"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/Preset.hpp"
#include "libslic3r/FileReader.hpp"
#include "libslic3r/CutUtils.hpp"
#include "libslic3r/Emboss.hpp"
#include "libslic3r/Format/3mf.hpp"
#include "libslic3r/Format/OBJ.hpp"
#include "libslic3r/Format/STL.hpp"
#include "libslic3r/Format/SVG.hpp"
#include "libslic3r/GCode/GCodeProcessor.hpp"
#include "libslic3r/ModelProcessing.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/QuadricEdgeCollapse.hpp"
#include "libslic3r/TriangleSelector.hpp"
#include "libslic3r/TextConfiguration.hpp"
#include "libslic3r/BuildVolume.hpp"
#include "libslic3r/MultipleBeds.hpp"
#include "libslic3r/Utils.hpp"
#include "libslic3r/Feature/FullSpectrum/VirtualExtruder.hpp"

#include <LibBGCode/convert/convert.hpp>

#include <arrange/Beds.hpp>
#include <arrange-wrapper/ModelArrange.hpp>
#include <arrange-wrapper/ArrangeSettingsView.hpp>

namespace {

/* ------------------------------------------------------------------ */
/* Hilfen                                                              */
/* ------------------------------------------------------------------ */

void copy_str(char *dst, size_t cap, const std::string &src)
{
    if (dst == nullptr || cap == 0)
        return;
    const size_t n = std::min(cap - 1, src.size());
    std::memcpy(dst, src.data(), n);
    dst[n] = '\0';
}

psm_log_cb g_log_cb  = nullptr;
void      *g_log_usr = nullptr;
std::string g_global_error;

} // namespace

/* Von psmobile_session.hpp deklariert, hier definiert - damit auch
 * psmobile_presets.cpp protokollieren kann. */
void psm_emit_log(psm_log_level lvl, const std::string &msg)
{
    if (g_log_cb)
        g_log_cb(lvl, msg.c_str(), g_log_usr);
}

psm_session::psm_session()
{
    bed_models.emplace_back(std::make_unique<Slic3r::Model>());
}

void psm_session::teardown_print()
{
    std::lock_guard<std::mutex> lock(print_mtx);
    if (! print)
        return;
    /* Callbacks entschaerfen, bevor der Destruktor sie noch einmal
     * ausloest - siehe Kommentar in psmobile_session.hpp. */
    print->set_cancel_callback([]() {});
    print->set_status_callback(nullptr);
    print.reset();
}

psm_session::~psm_session()
{
    cancel_requested = true;
    join_worker();
    teardown_print();
}

namespace {

inline void emit_log(psm_log_level lvl, const std::string &msg) { psm_emit_log(lvl, msg); }

/* Findet ein ModelObject anhand der stabilen ObjectID. */
Slic3r::ModelObject *find_object(psm_session *s, psm_object_id id)
{
    for (Slic3r::ModelObject *o : s->model().objects)
        if (static_cast<psm_object_id>(o->id().id) == id)
            return o;
    return nullptr;
}

/*
 * Modell-/Volumen-Extruder bleiben 1-basiert wie in 3MF. Neben den
 * physischen Köpfen darf nur eine im aktiven Modell definierte
 * FullSpectrum-ID verwendet werden – niemals bloß irgendeine große Zahl.
 * Der Aufrufer hält data_mtx.
 */
bool is_selectable_extruder(psm_session *s, int32_t extruder)
{
    if (extruder == 0)
        return true; // Vererbung vom Druckerprofil.
    if (extruder < 0)
        return false;
    if (extruder <= psm_extruder_count(s))
        return true;
    return std::any_of(
        s->model().virtual_extruders.begin(),
        s->model().virtual_extruders.end(),
        [extruder](const Slic3r::FullSpectrum::VirtualExtruder &candidate) {
            return candidate.id == static_cast<unsigned int>(extruder);
        });
}

/* Sorgt dafuer, dass ein Objekt genau eine Instanz hat, und liefert sie. */
Slic3r::ModelInstance *first_instance(Slic3r::ModelObject *o)
{
    if (o->instances.empty())
        o->add_instance();
    return o->instances.front();
}

/* Grundflaeche einer Instanz als Rechteck in Bettkoordinaten. */
static Slic3r::BoundingBoxf footprint_of(const Slic3r::ModelObject *o, size_t idx)
{
    const Slic3r::BoundingBoxf3 b = o->instance_bounding_box(idx);
    return Slic3r::BoundingBoxf(Slic3r::Vec2d(b.min.x(), b.min.y()),
                                Slic3r::Vec2d(b.max.x(), b.max.y()));
}

static bool rects_overlap(const Slic3r::BoundingBoxf &a,
                          const Slic3r::BoundingBoxf &b)
{
    return a.min.x() < b.max.x() && b.min.x() < a.max.x() &&
           a.min.y() < b.max.y() && b.min.y() < a.max.y();
}

/*
 * Setzt ein frisch geladenes Objekt auf einen freien Platz.
 *
 * Vorher landete jedes geladene Modell auf der Bettmitte. Beim zweiten
 * Import steckte es dann im ersten - im Viewport sah man nur noch ein
 * Objekt und hielt den Import fuer fehlgeschlagen. PrusaSlicer Desktop
 * faellt das nicht auf, weil dort nach dem Laden ohnehin meist arrangiert
 * wird.
 *
 * Gesucht wird auf einem Raster um die Bettmitte, von innen nach aussen,
 * damit die Anordnung kompakt bleibt. Liefert false, wenn kein Platz
 * blieb - der Aufrufer weicht dann auf das naechste Bett aus.
 *
 * Ein leeres Bett gilt immer als Erfolg: passt das Objekt dort nicht,
 * passt es nirgends, und weiterzuwandern wuerde nur leere Betten
 * erzeugen. Es bleibt dann mittig liegen und wird als ausserhalb des
 * Betts gemeldet.
 */
static bool place_on_free_spot(Slic3r::Model &model,
                               Slic3r::ModelObject *dst,
                               const Slic3r::BoundingBoxf &bed,
                               double gap_mm)
{
    Slic3r::ModelInstance *inst = first_instance(dst);
    if (inst == nullptr)
        return true;

    std::vector<Slic3r::BoundingBoxf> taken;
    for (const Slic3r::ModelObject *o : model.objects) {
        if (o == dst)
            continue;
        for (size_t i = 0; i < o->instances.size(); ++i)
            taken.push_back(footprint_of(o, i));
    }
    if (taken.empty())
        return true;                  /* die Bettmitte ist frei */

    const Slic3r::BoundingBoxf own = footprint_of(dst, 0);
    const Slic3r::Vec2d size = own.size();
    const Slic3r::Vec2d start = inst->get_offset().head<2>();
    const double step_x = size.x() + gap_mm;
    const double step_y = size.y() + gap_mm;
    if (step_x <= 0.0 || step_y <= 0.0)
        return true;

    const bool bed_known = bed.max.x() > bed.min.x() && bed.max.y() > bed.min.y();

    /* Ring 0 ist die Bettmitte selbst; sie wird gleich mitgeprueft. */
    for (int ring = 0; ring < 24; ++ring) {
        for (int dy = -ring; dy <= ring; ++dy) {
            for (int dx = -ring; dx <= ring; ++dx) {
                /* Nur den Rand des Rings, das Innere war schon dran. */
                if (ring > 0 && std::abs(dx) != ring && std::abs(dy) != ring)
                    continue;

                const Slic3r::Vec2d shift(dx * step_x, dy * step_y);
                Slic3r::BoundingBoxf candidate(own.min + shift, own.max + shift);

                if (bed_known &&
                    (candidate.min.x() < bed.min.x() || candidate.max.x() > bed.max.x() ||
                     candidate.min.y() < bed.min.y() || candidate.max.y() > bed.max.y()))
                    continue;

                bool blocked = false;
                for (const Slic3r::BoundingBoxf &t : taken) {
                    if (rects_overlap(candidate, t)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked)
                    continue;

                const Slic3r::Vec3d offset = inst->get_offset();
                inst->set_offset(Slic3r::Vec3d(start.x() + shift.x(),
                                               start.y() + shift.y(),
                                               offset.z()));
                return true;
            }
        }
    }
    return false;
}

/*
 * Zerlegt PrusaSlicers Desktop-Mehrbett-Landschaft in getrennte mobile
 * Modelle. Die 3MF speichert Objekte auf virtuellen Betten mit grossen
 * XY-Verschiebungen. MultipleBeds kennt genau diese historische und
 * aktuelle Anordnung; danach ziehen wir jedes Bett wieder auf lokale
 * Bettkoordinaten zurueck.
 */
std::vector<std::unique_ptr<Slic3r::Model>> split_project_beds(
    Slic3r::Model &&loaded,
    const Slic3r::DynamicPrintConfig &config)
{
    auto single_bed = [&loaded]() {
        std::vector<std::unique_ptr<Slic3r::Model>> beds;
        beds.emplace_back(std::make_unique<Slic3r::Model>(std::move(loaded)));
        return beds;
    };

    const auto *bed_shape =
        config.opt<Slic3r::ConfigOptionPoints>("bed_shape");
    const auto *max_height =
        config.opt<Slic3r::ConfigOptionFloat>("max_print_height");
    if (bed_shape == nullptr || bed_shape->values.size() < 3 ||
        max_height == nullptr)
        return single_bed();

    try {
        Slic3r::BuildVolume volume(bed_shape->values, max_height->value);
        if (! volume.valid())
            return single_bed();

        Slic3r::s_multiple_beds.update_build_volume(
            volume.bounding_volume2d());
        Slic3r::s_multiple_beds.rearrange_after_load(loaded, volume);
        Slic3r::s_multiple_beds.update_shown_beds(loaded, volume);

        const auto instance_beds = Slic3r::s_multiple_beds.get_inst_map();
        int highest_bed = 0;
        for (const Slic3r::ModelObject *object : loaded.objects) {
            for (const Slic3r::ModelInstance *instance : object->instances) {
                const auto it = instance_beds.find(instance->id());
                /* Ein wirklich ausserhalb liegendes Objekt gehoert nicht
                 * automatisch auf ein neues Bett. In diesem Zweifelsfall
                 * bleibt die Projektgeometrie gemeinsam auf Bett 1. */
                if (it == instance_beds.end())
                    return single_bed();
                highest_bed = std::max(highest_bed, it->second);
            }
        }

        const size_t bed_count =
            static_cast<size_t>(highest_bed + 1);
        if (bed_count <= 1 || bed_count > PSM_MAX_BEDS)
            return single_bed();

        std::vector<std::unique_ptr<Slic3r::Model>> beds;
        beds.reserve(bed_count);
        for (size_t bed_index = 0; bed_index < bed_count; ++bed_index) {
            auto bed = std::make_unique<Slic3r::Model>(loaded);
            const Slic3r::Vec3d translation =
                Slic3r::s_multiple_beds.get_bed_translation(
                    static_cast<int>(bed_index));

            for (Slic3r::ModelObject *object : bed->objects) {
                object->instances.erase(
                    std::remove_if(
                        object->instances.begin(),
                        object->instances.end(),
                        [&](Slic3r::ModelInstance *instance) {
                            const auto it = instance_beds.find(instance->id());
                            if (it == instance_beds.end() ||
                                it->second != static_cast<int>(bed_index))
                                return true;
                            instance->set_offset(
                                instance->get_offset() - translation);
                            return false;
                        }),
                    object->instances.end());
                object->invalidate_bounding_box();
            }
            for (size_t i = bed->objects.size(); i-- > 0;)
                if (bed->objects[i]->instances.empty())
                    bed->delete_object(i);

            /* Jedes mobile Modell ist aus Sicht des Slicers Bett 0.
             * Projektbezogene Farbwechsel und Wipe-Tower-Positionen des
             * urspruenglichen Betts deshalb ebenfalls auf Slot 0 legen. */
            if (bed_index < bed->get_custom_gcode_per_print_z_vector().size())
                bed->get_custom_gcode_per_print_z_vector()[0] =
                    bed->get_custom_gcode_per_print_z_vector()[bed_index];
            if (bed_index < bed->get_wipe_tower_vector().size())
                bed->get_wipe_tower_vector()[0] =
                    bed->get_wipe_tower_vector()[bed_index];
            beds.emplace_back(std::move(bed));
        }

        Slic3r::s_multiple_beds.set_active_bed(0);
        return beds;
    } catch (const std::exception &e) {
        emit_log(PSM_LOG_WARN,
                 std::string("Mehrbett-Erkennung uebersprungen: ") + e.what());
        return single_bed();
    }
}

/*
 * Gegenstück zu split_project_beds(): Die App hält jedes Bett in lokalen
 * Koordinaten, PrusaSlicers 3MF-Format erwartet dagegen alle Instanzen in
 * seiner virtuellen Bettlandschaft. Für den Export werden nur Kopien
 * verschoben; der sichtbare Projektzustand bleibt unverändert.
 */
Slic3r::Model merge_project_beds(psm_session *s)
{
    const auto *bed_shape =
        s->config.opt<Slic3r::ConfigOptionPoints>("bed_shape");
    const auto *max_height =
        s->config.opt<Slic3r::ConfigOptionFloat>("max_print_height");
    if (bed_shape == nullptr || bed_shape->values.size() < 3 ||
        max_height == nullptr)
        throw std::runtime_error("Druckbettgeometrie fehlt in der Projektkonfiguration");

    Slic3r::BuildVolume volume(bed_shape->values, max_height->value);
    if (! volume.valid())
        throw std::runtime_error("Druckbettgeometrie ist ungueltig");

    Slic3r::s_multiple_beds.update_build_volume(volume.bounding_volume2d());
    Slic3r::s_multiple_beds.clear_inst_map();

    Slic3r::Model merged;
    bool copied_model_metadata = false;
    for (size_t bed_index = 0; bed_index < s->bed_models.size(); ++bed_index) {
        /* PrusaSlicer 2.9.6 bietet fuer den Custom-G-Code-Vektor keinen
         * const-Getter. Wir lesen das Modell hier trotzdem nur; die
         * nicht-const Referenz umgeht ausschliesslich diese API-Luecke. */
        Slic3r::Model &bed = *s->bed_models[bed_index];

        if (! copied_model_metadata) {
            merged.get_virtual_extruders() = bed.virtual_extruders;
            merged.sla_workflow_uuid = bed.sla_workflow_uuid;
            copied_model_metadata = true;
        }

        for (const auto &[material_id, material] : bed.materials)
            if (merged.get_material(material_id) == nullptr)
                merged.add_material(material_id, *material);

        const Slic3r::Vec3d translation =
            Slic3r::s_multiple_beds.get_bed_translation(
                static_cast<int>(bed_index));
        for (const Slic3r::ModelObject *source : bed.objects) {
            Slic3r::ModelObject *object = merged.add_object(*source);
            for (Slic3r::ModelInstance *instance : object->instances) {
                instance->set_offset(instance->get_offset() + translation);
                Slic3r::s_multiple_beds.set_instance_bed(
                    instance->id(), instance->printable,
                    static_cast<int>(bed_index));
            }
            object->invalidate_bounding_box();
        }

        if (! bed.get_custom_gcode_per_print_z_vector().empty())
            merged.get_custom_gcode_per_print_z_vector()[bed_index] =
                bed.get_custom_gcode_per_print_z_vector()[0];
        if (! bed.get_wipe_tower_vector().empty())
            merged.get_wipe_tower_vector()[bed_index] =
                bed.get_wipe_tower_vector()[0];
    }

    Slic3r::s_multiple_beds.inst_map_updated();
    Slic3r::s_multiple_beds.set_active_bed(
        static_cast<int>(std::min(s->active_bed,
                                  s->bed_models.size() - 1)));
    return merged;
}

/* Wandelt libslic3r-Ausnahmen in Fehlercodes. Macro, damit der
 * try/catch-Rahmen nicht in jeder Funktion ausgeschrieben werden muss. */
#define PSM_GUARD_BEGIN(sess)                    \
    if ((sess) == nullptr) return PSM_ERR_INVALID_ARG; \
    try {

#define PSM_GUARD_END(sess)                                          \
    } catch (const std::bad_alloc &) {                               \
        (sess)->set_error("Speicher erschoepft");                    \
        return PSM_ERR_OUT_OF_MEMORY;                                \
    } catch (const std::exception &e) {                              \
        (sess)->set_error(e.what());                                 \
        return PSM_ERR_GENERIC;                                      \
    } catch (...) {                                                  \
        (sess)->set_error("unbekannter Fehler");                     \
        return PSM_ERR_GENERIC;                                      \
    }

} // namespace

/* ------------------------------------------------------------------ */
/* Version, Fehler, Protokoll                                          */
/* ------------------------------------------------------------------ */

extern "C" {

PSM_API int psm_abi_version(void) { return PSM_ABI_VERSION; }

PSM_API const char *psm_core_version(void) { return SLIC3R_VERSION; }

PSM_API psm_result psm_colormix_get_json(psm_session *s, char *out, size_t out_cap)
{
    if (s == nullptr || out == nullptr || out_cap == 0)
        return PSM_ERR_INVALID_ARG;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    try {
        const size_t physical_count = static_cast<size_t>(std::max(1, psm_extruder_count(s)));
        std::vector<std::string> colors(physical_count);
        if (const auto *option = s->config.opt<Slic3r::ConfigOptionStrings>("extruder_colour")) {
            for (size_t i = 0; i < std::min(colors.size(), option->values.size()); ++i)
                colors[i] = option->values[i];
        }
        // Ein neues bzw. importiertes Profil kann noch keine Farben enthalten.
        // Der Prusa-Serializer erwartet dennoch fuer jeden Kopf einen gueltigen
        // Hexwert; ein neutraler Fallback macht das Rezept deshalb lesbar statt
        // die komplette ColorMix-Abfrage fehlschlagen zu lassen.
        for (std::string &color : colors)
            if (color.empty())
                color = "#808080";
        const std::string json = Slic3r::FullSpectrum::serialize_virtual_extruders_to_json(
            colors, s->model().virtual_extruders);
        if (json.size() + 1 > out_cap) {
            s->set_error("ColorMix-Konfiguration passt nicht in den Ausgabepuffer");
            return PSM_ERR_OUT_OF_MEMORY;
        }
        copy_str(out, out_cap, json);
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_GENERIC;
    }
}

PSM_API psm_result psm_colormix_set_json(psm_session *s, const char *json)
{
    if (s == nullptr || json == nullptr)
        return PSM_ERR_INVALID_ARG;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    try {
        const std::string source(json);
        if (source.find("virtual_extruders") == std::string::npos) {
            s->set_error("ColorMix-JSON enthält keine virtuellen Extruder");
            return PSM_ERR_PARSE;
        }
        const Slic3r::FullSpectrum::FullSpectrumConfig parsed =
            Slic3r::FullSpectrum::deserialize_virtual_extruders_from_json(source);
        const auto normalized = Slic3r::FullSpectrum::normalize_virtual_extruders(
            parsed.virtual_extruders);
        const auto physical_count = static_cast<unsigned int>(std::max(1, psm_extruder_count(s)));
        const auto filtered = Slic3r::FullSpectrum::filter_virtual_extruders_for_physical_count(
            physical_count, normalized);

        // Ein leeres Array löscht bewusst alle Rezepte. Ansonsten darf
        // kein Teil eines ungültigen Rezepts still verschwinden.
        if ((! parsed.virtual_extruders.empty()) &&
            (parsed.virtual_extruders.size() != normalized.size() ||
             normalized.size() != filtered.size())) {
            s->set_error("ColorMix-Rezept verweist auf ungültige Köpfe oder Mischanteile");
            return PSM_ERR_INVALID_ARG;
        }
        for (const auto &bed : s->bed_models)
            bed->virtual_extruders = filtered;
        s->mark_design_changed();
        return PSM_OK;
    } catch (const std::exception &e) {
        s->set_error(e.what());
        return PSM_ERR_PARSE;
    }
}

PSM_API const char *psm_last_error(void *session)
{
    if (session == nullptr)
        return g_global_error.c_str();
    return static_cast<psm_session *>(session)->last_error.c_str();
}

PSM_API void psm_set_log_callback(psm_log_cb cb, void *user)
{
    g_log_cb  = cb;
    g_log_usr = user;
}

/* ------------------------------------------------------------------ */
/* Session                                                             */
/* ------------------------------------------------------------------ */

PSM_API psm_session *psm_session_create(const char *datadir, const char *resdir)
{
    if (datadir == nullptr || resdir == nullptr) {
        g_global_error = "datadir oder resdir ist NULL";
        return nullptr;
    }
    try {
        auto s = std::make_unique<psm_session>();
        s->datadir = datadir;
        s->resdir  = resdir;

        boost::filesystem::create_directories(s->datadir);

        /* libslic3r braucht beide Pfade global - es fragt sie an
         * mehreren Stellen ueber set_data_dir/set_resources_dir ab. */
        Slic3r::set_data_dir(s->datadir);
        Slic3r::set_resources_dir(s->resdir);
        Slic3r::set_var_dir((boost::filesystem::path(s->resdir) / "icons").string());
        Slic3r::set_local_dir((boost::filesystem::path(s->resdir) / "localization").string());

        s->config = Slic3r::DynamicPrintConfig::full_print_config();

        emit_log(PSM_LOG_INFO, std::string("Session bereit, Kern ") + SLIC3R_VERSION);
        return s.release();
    } catch (const std::exception &e) {
        g_global_error = e.what();
        return nullptr;
    } catch (...) {
        g_global_error = "unbekannter Fehler in psm_session_create";
        return nullptr;
    }
}

PSM_API void psm_session_destroy(psm_session *s)
{
    if (s == nullptr)
        return;
    delete s;
}

PSM_API psm_result psm_session_clear(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_checkpoint("Neues Projekt");
        s->bed_models.clear();
        s->bed_models.emplace_back(std::make_unique<Slic3r::Model>());
        s->active_bed = 0;
        s->teardown_print();
        s->state = PSM_STATE_IDLE;
        {
            std::lock_guard<std::mutex> result_lock(s->result_mtx);
            s->stats = psm_slice_stats{};

            /* Den fertigen G-Code mit vergessen. Sonst bietet die Oberflaeche
             * nach dem Leeren weiter Export und Senden an - mit dem G-Code des
             * vorherigen Modells. Das kann einen falschen Druck ausloesen.
             * Befund B5 in docs/09-fehlerliste.md. */
            if (! s->gcode_tmp_path.empty()) {
                boost::system::error_code ec;
                boost::filesystem::remove(s->gcode_tmp_path, ec);
                s->gcode_tmp_path.clear();
            }
        }
        s->slice_model.reset();
        s->result_revision.store(0, std::memory_order_release);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Modelle                                                             */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_model_load(psm_session *s,
                                  const char *path,
                                  psm_object_id *out_ids,
                                  size_t out_ids_cap,
                                  size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        if (path == nullptr)
            return PSM_ERR_INVALID_ARG;
        if (! boost::filesystem::exists(path)) {
            s->set_error(std::string("Datei nicht gefunden: ") + path);
            return PSM_ERR_IO;
        }

        Slic3r::FileReader::LoadStats stats;
        Slic3r::Model loaded = Slic3r::FileReader::load_model(
            path,
            Slic3r::FileReader::LoadAttribute::AddDefaultInstances,
            &stats);

        if (loaded.objects.empty()) {
            s->set_error("Datei enthaelt keine Objekte");
            return PSM_ERR_PARSE;
        }

        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_checkpoint("Objekte importieren");

        /* Bettmitte bestimmen: neue Objekte landen dort, so wie es
         * PrusaSlicer beim Laden auch macht. Ohne das klebt jedes Modell
         * im Ursprung, also in der vorderen linken Bettecke. */
        Slic3r::Vec2d bed_center(0.0, 0.0);
        Slic3r::BoundingBoxf bed_bb;
        try {
            const Slic3r::Points bedpts = Slic3r::get_bed_shape(s->config);
            if (bedpts.size() >= 3) {
                Slic3r::BoundingBoxf bb;
                for (const Slic3r::Point &p : bedpts)
                    bb.merge(Slic3r::Vec2d(Slic3r::unscale<double>(p.x()),
                                           Slic3r::unscale<double>(p.y())));
                bed_center = bb.center();
                bed_bb     = bb;
            }
        } catch (...) { /* ohne Bett bleibt es beim Ursprung */ }

        size_t written = 0;
        size_t total   = 0;
        size_t spilled = 0;
        for (Slic3r::ModelObject *src : loaded.objects) {
            /*
             * Auf welches Bett das Objekt kommt, entscheidet sich hier.
             * Es beginnt beim aktiven Bett; ist dort kein Platz mehr,
             * geht es eines weiter und notfalls auf ein neu angelegtes.
             * Ohne das landete bei einem vollen Bett alles Weitere wieder
             * auf der Mitte und steckte ineinander.
             */
            size_t bed = s->active_bed;
            Slic3r::ModelObject *dst = nullptr;
            while (true) {
                Slic3r::Model &m = *s->bed_models[bed];
                dst = m.add_object(*src);
                Slic3r::ModelInstance *inst = first_instance(dst);

                /* Objekt um seinen eigenen Schwerpunkt zentrieren und
                 * dann auf die Bettmitte setzen. */
                dst->center_around_origin(false);
                inst->set_offset(Slic3r::Vec3d(bed_center.x(), bed_center.y(),
                                               inst->get_offset().z()));
                dst->ensure_on_bed();
                if (place_on_free_spot(m, dst, bed_bb, 3.0))
                    break;

                /* Kein Platz: wieder herausnehmen und ein Bett weiter. */
                m.delete_object(m.objects.size() - 1);
                dst = nullptr;
                if (bed + 1 >= PSM_MAX_BEDS)
                    break;             /* alle Betten voll */
                if (bed + 1 >= s->bed_models.size())
                    s->bed_models.emplace_back(std::make_unique<Slic3r::Model>());
                ++bed;
                ++spilled;
            }

            if (dst == nullptr) {
                /* Auch das letzte Bett ist voll. Dann lieber mittig
                 * ablegen als das Objekt stillschweigend fallenlassen -
                 * es wird als ausserhalb des Betts gemeldet. */
                Slic3r::Model &m = *s->bed_models[PSM_MAX_BEDS - 1];
                dst = m.add_object(*src);
                Slic3r::ModelInstance *inst = first_instance(dst);
                dst->center_around_origin(false);
                inst->set_offset(Slic3r::Vec3d(bed_center.x(), bed_center.y(),
                                               inst->get_offset().z()));
                dst->ensure_on_bed();
            }

            if (out_ids != nullptr && written < out_ids_cap)
                out_ids[written++] = static_cast<psm_object_id>(dst->id().id);
            ++total;
        }
        if (spilled > 0)
            emit_log(PSM_LOG_INFO,
                     "Druckbett voll - weitere Objekte auf das naechste gelegt");

        if (out_count != nullptr)
            *out_count = total;

        emit_log(PSM_LOG_INFO, std::string("geladen: ") + path +
                               " (" + std::to_string(total) + " Objekte)");
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_project_load_3mf(psm_session *s,
                                        const char *path,
                                        psm_project_import_info *out_info)
{
    PSM_GUARD_BEGIN(s)
        if (path == nullptr || out_info == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::memset(out_info, 0, sizeof(*out_info));

        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        if (! boost::filesystem::exists(path)) {
            s->set_error(std::string("Datei nicht gefunden: ") + path);
            return PSM_ERR_IO;
        }
        if (! boost::algorithm::iends_with(path, ".3mf") &&
            ! boost::algorithm::iends_with(path, ".zip")) {
            s->set_error("Projektimport erwartet eine 3MF-Datei");
            return PSM_ERR_INVALID_ARG;
        }

        Slic3r::DynamicPrintConfig loaded_config;
        Slic3r::ConfigSubstitutionContext substitutions{
            Slic3r::ForwardCompatibilitySubstitutionRule::EnableSilent
        };
        boost::optional<Slic3r::Semver> generator_version;
        Slic3r::FileReader::LoadStats stats;
        Slic3r::FileReader::LoadAttributes load_attributes{
            Slic3r::FileReader::LoadAttribute::CheckVersion
        };
        load_attributes = load_attributes |
            Slic3r::FileReader::LoadAttribute::AddDefaultInstances;
        Slic3r::Model loaded = Slic3r::FileReader::load_model_with_config(
            path,
            &loaded_config,
            &substitutions,
            generator_version,
            load_attributes,
            &stats);

        if (loaded.objects.empty()) {
            s->set_error("3MF-Projekt enthaelt keine Objekte");
            return PSM_ERR_PARSE;
        }

        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);

        if (! loaded_config.empty()) {
            if (Slic3r::Preset::printer_technology(loaded_config) != Slic3r::ptFFF) {
                s->set_error("SLA-Projekte werden in der Android-Version noch nicht unterstuetzt");
                return PSM_ERR_UNSUPPORTED;
            }

            /* Wie im Desktop: Defaults als Basis, danach die im Projekt
             * gespeicherten Werte darueberlegen. So bleiben auch aeltere
             * Projekte mit inzwischen hinzugekommenen Pflichtwerten
             * vollstaendig. */
            Slic3r::DynamicPrintConfig project_config;
            project_config.apply(Slic3r::FullPrintConfig::defaults());
            project_config.null_nullables();
            project_config += std::move(loaded_config);
            Slic3r::Preset::normalize(project_config);

            copy_str(out_info->requested_printer,
                     sizeof(out_info->requested_printer),
                     project_config.opt_string("printer_settings_id", true));
            copy_str(out_info->requested_print,
                     sizeof(out_info->requested_print),
                     project_config.opt_string("print_settings_id", true));

            /* Ein 3MF-Projekt darf keine Programme auf dem Mobilgeraet
             * ausfuehren. PrusaSlicer Desktop zeigt dafuer mindestens eine
             * Warnung; mobil entfernen wir die Skripte konsequent. */
            if (auto *post = project_config.option<Slic3r::ConfigOptionStrings>(
                    "post_process", true);
                post != nullptr && ! post->values.empty()) {
                post->values.clear();
                out_info->post_process_removed = 1;
            }

            /* load_config_model ist derselbe Pfad wie im Desktop. Er
             * aktiviert ein vorhandenes, exakt passendes Profil. Weicht
             * die eingebettete Fassung davon ab oder fehlt sie lokal,
             * erzeugt er ein projektlokales externes Profil und waehlt
             * dieses aus, statt still ein falsches Profil zu verwenden. */
            if (! s->presets) {
                s->presets = std::make_unique<Slic3r::PresetBundle>();
                s->presets->setup_directories();
            }
            s->presets->load_config_model(path, std::move(project_config));
            s->presets->update_multi_material_filament_presets();
            s->presets->update_compatible(
                Slic3r::PresetSelectCompatibleType::Always);
            s->config = s->presets->full_config();
            ++s->config_revision;

            copy_str(out_info->selected_printer,
                     sizeof(out_info->selected_printer),
                     s->presets->printers.get_selected_preset_name());
            copy_str(out_info->selected_print,
                     sizeof(out_info->selected_print),
                     s->presets->prints.get_selected_preset_name());
            out_info->config_loaded = 1;
        }

        /* "Als Projekt" ersetzt das aktuelle Projekt. Die Instanz-
         * positionen aus der 3MF bleiben unangetastet; insbesondere wird
         * hier nicht wie beim reinen Objektimport auf die Bettmitte
         * zentriert. */
        for (Slic3r::ModelObject *object : loaded.objects)
            if (! object->instances.empty())
                object->ensure_on_bed(true);
        s->bed_models = split_project_beds(std::move(loaded), s->config);
        s->active_bed = 0;
        size_t imported_objects = 0;
        for (const auto &bed : s->bed_models)
            imported_objects += bed->objects.size();
        out_info->object_count = static_cast<int32_t>(imported_objects);
        out_info->bed_count = static_cast<int32_t>(s->bed_models.size());

        s->teardown_print();
        s->slice_model.reset();
        s->state = PSM_STATE_IDLE;
        {
            std::lock_guard<std::mutex> result_lock(s->result_mtx);
            s->stats = psm_slice_stats{};
            if (! s->gcode_tmp_path.empty()) {
                boost::system::error_code ec;
                boost::filesystem::remove(s->gcode_tmp_path, ec);
                s->gcode_tmp_path.clear();
            }
        }
        s->result_revision.store(0, std::memory_order_release);
        s->mark_design_changed();
        s->history_clear();

        emit_log(PSM_LOG_INFO,
                 std::string("3MF-Projekt geladen: ") + path + " (" +
                 std::to_string(out_info->object_count) + " Objekte, Drucker " +
                 out_info->selected_printer + ")");
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_project_save_3mf(psm_session *s, const char *path)
{
    PSM_GUARD_BEGIN(s)
        if (path == nullptr || *path == '\0')
            return PSM_ERR_INVALID_ARG;
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        if (! boost::algorithm::iends_with(path, ".3mf")) {
            s->set_error("Projektdatei muss auf .3mf enden");
            return PSM_ERR_INVALID_ARG;
        }

        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::Model merged = merge_project_beds(s);

        /* Derselbe Schutz wie full_config_secure() im Desktop, erweitert
         * um mobile Zugangsdaten und ausführbare Post-Processing-Skripte. */
        Slic3r::DynamicPrintConfig export_config = s->config;
        export_config.erase("print_host");
        export_config.erase("printhost_apikey");
        export_config.erase("printhost_cafile");
        if (auto *post = export_config.option<Slic3r::ConfigOptionStrings>(
                "post_process", false);
            post != nullptr)
            post->values.clear();

        const boost::filesystem::path output(path);
        if (! output.parent_path().empty())
            boost::filesystem::create_directories(output.parent_path());
        if (! Slic3r::store_3mf(path, &merged, &export_config,
                                false, nullptr, true)) {
            s->set_error(std::string("3MF-Projekt konnte nicht gespeichert werden: ") +
                         path);
            return PSM_ERR_IO;
        }

        emit_log(PSM_LOG_INFO,
                 std::string("3MF-Projekt gespeichert: ") + path + " (" +
                 std::to_string(s->bed_models.size()) + " Betten)");
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Undo / Redo                                                         */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_history_begin(psm_session *s, const char *label)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_begin(label != nullptr ? label : "Änderung");
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_history_end(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_end();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_history_undo_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->undo_history.size();
}

PSM_API size_t psm_history_redo_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->redo_history.size();
}

PSM_API psm_result psm_history_undo_label(psm_session *s,
                                          char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr || out_cap == 0)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->undo_history.empty())
            return PSM_ERR_NOT_FOUND;
        copy_str(out, out_cap, s->undo_history.back().label);
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_history_redo_label(psm_session *s,
                                          char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr || out_cap == 0)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->redo_history.empty())
            return PSM_ERR_NOT_FOUND;
        copy_str(out, out_cap, s->redo_history.back().label);
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_history_undo(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->history_depth != 0 || s->undo_history.empty())
            return PSM_ERR_NOT_FOUND;

        psm_session::HistorySnapshot target =
            std::move(s->undo_history.back());
        s->undo_history.pop_back();
        s->redo_history.emplace_back(
            s->bed_models, s->active_bed, target.label);
        while (s->redo_history.size() > psm_session::HISTORY_LIMIT)
            s->redo_history.pop_front();
        s->bed_models = std::move(target.beds);
        s->active_bed = std::min(target.active_bed,
                                 s->bed_models.size() - 1);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_history_redo(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->history_depth != 0 || s->redo_history.empty())
            return PSM_ERR_NOT_FOUND;

        psm_session::HistorySnapshot target =
            std::move(s->redo_history.back());
        s->redo_history.pop_back();
        s->undo_history.emplace_back(
            s->bed_models, s->active_bed, target.label);
        while (s->undo_history.size() > psm_session::HISTORY_LIMIT)
            s->undo_history.pop_front();
        s->bed_models = std::move(target.beds);
        s->active_bed = std::min(target.active_bed,
                                 s->bed_models.size() - 1);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_history_clear(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_clear();
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Mehrbett                                                            */
/* ------------------------------------------------------------------ */

PSM_API size_t psm_bed_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->bed_models.size();
}

PSM_API size_t psm_bed_active(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->active_bed;
}

PSM_API psm_result psm_bed_select(psm_session *s, size_t index)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (index >= s->bed_models.size())
            return PSM_ERR_INVALID_ARG;
        if (index == s->active_bed)
            return PSM_OK;
        s->active_bed = index;
        /* Ein vorhandener G-Code gehoert zum zuvor aktiven Bett. */
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_bed_add(psm_session *s, size_t *out_index)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->bed_models.size() >= PSM_MAX_BEDS) {
            s->set_error("Maximale Zahl von Druckbetten erreicht");
            return PSM_ERR_UNSUPPORTED;
        }
        s->history_checkpoint("Druckbett hinzufügen");
        s->bed_models.emplace_back(std::make_unique<Slic3r::Model>());
        s->active_bed = s->bed_models.size() - 1;
        if (out_index != nullptr)
            *out_index = s->active_bed;
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_bed_remove(psm_session *s, size_t index)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (index >= s->bed_models.size())
            return PSM_ERR_INVALID_ARG;
        if (s->bed_models.size() == 1) {
            s->set_error("Das einzige Druckbett kann nicht entfernt werden");
            return PSM_ERR_INVALID_ARG;
        }
        s->history_checkpoint("Druckbett entfernen");
        s->bed_models.erase(s->bed_models.begin() +
                            static_cast<std::ptrdiff_t>(index));
        if (s->active_bed > index)
            --s->active_bed;
        else if (s->active_bed == index)
            s->active_bed = std::min(index, s->bed_models.size() - 1);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_bed_clear(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->model().objects.empty())
            return PSM_OK;
        s->history_checkpoint("Druckbett leeren");
        s->model().clear_objects();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_bed_object_count(psm_session *s, size_t index)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return index < s->bed_models.size()
        ? s->bed_models[index]->objects.size() : 0;
}

PSM_API psm_result psm_bed_move_object(psm_session *s,
                                       psm_object_id id,
                                       size_t target_bed,
                                       psm_object_id *out_new_id)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (target_bed >= s->bed_models.size())
            return PSM_ERR_INVALID_ARG;
        if (target_bed == s->active_bed) {
            if (out_new_id != nullptr)
                *out_new_id = id;
            return find_object(s, id) != nullptr ? PSM_OK : PSM_ERR_NOT_FOUND;
        }

        Slic3r::Model &source = s->model();
        for (size_t i = 0; i < source.objects.size(); ++i) {
            Slic3r::ModelObject *object = source.objects[i];
            if (static_cast<psm_object_id>(object->id().id) != id)
                continue;

            s->history_checkpoint("Objekt auf anderes Bett verschieben");
            Slic3r::ModelObject *moved =
                s->bed_models[target_bed]->add_object(*object);
            moved->ensure_on_bed();
            if (out_new_id != nullptr)
                *out_new_id = static_cast<psm_object_id>(moved->id().id);
            source.delete_object(i);
            s->mark_design_changed();
            return PSM_OK;
        }
        return PSM_ERR_NOT_FOUND;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_remove(psm_session *s, psm_object_id id)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        for (size_t i = 0; i < s->model().objects.size(); ++i) {
            if (static_cast<psm_object_id>(s->model().objects[i]->id().id) == id) {
                s->history_checkpoint("Objekt entfernen");
                s->model().delete_object(i);
                s->mark_design_changed();
                return PSM_OK;
            }
        }
        return PSM_ERR_NOT_FOUND;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_model_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->model().objects.size();
}

PSM_API psm_result psm_model_list(psm_session *s, psm_object_id *out_ids,
                                  size_t cap, size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        size_t n = 0;
        for (Slic3r::ModelObject *o : s->model().objects) {
            if (out_ids != nullptr && n < cap)
                out_ids[n] = static_cast<psm_object_id>(o->id().id);
            ++n;
        }
        if (out_count != nullptr)
            *out_count = n;
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_info(psm_session *s, psm_object_id id, psm_object_info *out)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr)
            return PSM_ERR_NOT_FOUND;

        std::memset(out, 0, sizeof(*out));
        out->id = id;
        copy_str(out->name, sizeof(out->name), o->name);

        Slic3r::ModelInstance *inst = first_instance(o);
        const Slic3r::Vec3d off = inst->get_offset();
        const Slic3r::Vec3d rot = inst->get_rotation();
        const Slic3r::Vec3d scl = inst->get_scaling_factor();
        for (int i = 0; i < 3; ++i) {
            out->position[i] = static_cast<float>(off(i));
            out->rotation[i] = static_cast<float>(rot(i));
            out->scale[i]    = static_cast<float>(scl(i));
        }

        const Slic3r::BoundingBoxf3 bb = o->instance_bounding_box(0, false);
        for (int i = 0; i < 3; ++i) {
            out->bbox_min[i] = static_cast<float>(bb.min(i));
            out->bbox_max[i] = static_cast<float>(bb.max(i));
        }

        size_t tri = 0;
        for (const Slic3r::ModelVolume *v : o->volumes)
            if (v->is_model_part())
                tri += v->mesh().facets_count();
        out->triangle_count = static_cast<uint32_t>(tri);
        out->instance_count = static_cast<int32_t>(o->instances.size());
        out->outside_bed    = inst->is_printable() ? 0 : 1;
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API int32_t psm_model_extruder_get(psm_session *s, psm_object_id id)
{
    if (s == nullptr)
        return -1;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    const Slic3r::ModelObject *object = find_object(s, id);
    if (object == nullptr)
        return -1;
    const Slic3r::ConfigOption *option = object->config.option("extruder");
    return option != nullptr ? option->getInt() : 0;
}

PSM_API psm_result psm_model_extruder_set(psm_session *s,
                                          psm_object_id id,
                                          int32_t extruder)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (! is_selectable_extruder(s, extruder)) {
            s->set_error("Extruder existiert nicht im aktiven Drucker oder ColorMix-Projekt");
            return PSM_ERR_INVALID_ARG;
        }
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (psm_model_extruder_get(s, id) == extruder)
            return PSM_OK;

        s->history_checkpoint("Objekt-Extruder ändern");
        if (extruder == 0)
            object->config.erase("extruder");
        else
            object->config.set("extruder", extruder);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_model_volume_count(psm_session *s, psm_object_id id)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    const Slic3r::ModelObject *object = find_object(s, id);
    return object != nullptr ? object->volumes.size() : 0;
}

PSM_API psm_result psm_model_volume_info(psm_session *s,
                                         psm_object_id id,
                                         size_t volume_index,
                                         psm_volume_info *out)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size())
            return PSM_ERR_INVALID_ARG;

        const Slic3r::ModelVolume *volume = object->volumes[volume_index];
        std::memset(out, 0, sizeof(*out));
        out->index = static_cast<int32_t>(volume_index);
        out->type = static_cast<psm_volume_type>(
            static_cast<int>(volume->type()));
        copy_str(out->name, sizeof(out->name), volume->name);
        out->triangle_count =
            static_cast<uint32_t>(volume->mesh().facets_count());
        const int effective = volume->extruder_id();
        out->extruder = effective >= 0 ? effective : 0;
        if (const Slic3r::ConfigOption *option =
                volume->config.option("extruder");
            option != nullptr)
            out->explicit_extruder = option->getInt();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_volume_extruder_set(psm_session *s,
                                                  psm_object_id id,
                                                  size_t volume_index,
                                                  int32_t extruder)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (! is_selectable_extruder(s, extruder)) {
            s->set_error("Extruder existiert nicht im aktiven Drucker oder ColorMix-Projekt");
            return PSM_ERR_INVALID_ARG;
        }
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size())
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelVolume *volume = object->volumes[volume_index];
        if (! volume->is_model_part()) {
            s->set_error("Extruder gilt nur für druckbare Modellteile");
            return PSM_ERR_UNSUPPORTED;
        }
        const Slic3r::ConfigOption *old =
            volume->config.option("extruder");
        const int old_value = old != nullptr ? old->getInt() : 0;
        if (old_value == extruder)
            return PSM_OK;

        s->history_checkpoint("Volumen-Extruder ändern");
        if (extruder == 0)
            volume->config.erase("extruder");
        else
            volume->config.set("extruder", extruder);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_position(psm_session *s, psm_object_id id, float x, float y, float z)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objekt verschieben");
        first_instance(o)->set_offset(Slic3r::Vec3d(x, y, z));
        o->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_rotation(psm_session *s, psm_object_id id, float rx, float ry, float rz)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objekt drehen");
        first_instance(o)->set_rotation(Slic3r::Vec3d(rx, ry, rz));
        o->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_scale(psm_session *s, psm_object_id id, float sx, float sy, float sz)
{
    PSM_GUARD_BEGIN(s)
        if (sx <= 0.f || sy <= 0.f || sz <= 0.f)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objekt skalieren");
        first_instance(o)->set_scaling_factor(Slic3r::Vec3d(sx, sy, sz));
        o->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_mirror(psm_session *s, psm_object_id id, int32_t axis)
{
    PSM_GUARD_BEGIN(s)
        if (axis < 0 || axis > 2) return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        Slic3r::ModelInstance *inst = first_instance(o);
        if (inst == nullptr) return PSM_ERR_NOT_FOUND;

        s->history_checkpoint("Objekt spiegeln");
        Slic3r::Vec3d m = inst->get_mirror();
        m(axis) = -m(axis);
        inst->set_mirror(m);
        o->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_instances(psm_session *s, psm_object_id id, int32_t count)
{
    PSM_GUARD_BEGIN(s)
        if (count < 1) return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        if (static_cast<int32_t>(o->instances.size()) == count)
            return PSM_OK;
        s->history_checkpoint("Kopien ändern");

        while (static_cast<int32_t>(o->instances.size()) > count)
            o->delete_last_instance();

        /* Neue Kopien leicht versetzt ablegen, sonst stecken sie
         * ineinander. Das Anordnen raeumt danach sauber auf. */
        while (static_cast<int32_t>(o->instances.size()) < count) {
            Slic3r::ModelInstance *src = o->instances.front();
            Slic3r::ModelInstance *dst = o->add_instance(*src);
            Slic3r::Vec3d off = dst->get_offset();
            /* bounding_box_exact() umfasst alle bereits vorhandenen
             * Instanzen. Würde sie hier bei jeder Kopie erneut verwendet,
             * würden die Abstände quadratisch anwachsen (20, 45, 95, ...)
             * und eine harmlose Serie von Kopien ausserhalb der virtuellen
             * Bettkoordinaten landen. Die Ausdehnung einer einzelnen,
             * kopierten Instanz bleibt dagegen konstant. */
            const double step = std::max(
                1.0, o->instance_bounding_box(0).size().x()) + 5.0;
            off.x() += step * static_cast<double>(o->instances.size() - 1);
            dst->set_offset(off);
        }
        o->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_drop_to_bed(psm_session *s, psm_object_id id)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objekt aufs Bett legen");
        o->ensure_on_bed();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_scale_to_fit(psm_session *s, psm_object_id id, float size_mm)
{
    PSM_GUARD_BEGIN(s)
        if (size_mm <= 0.f)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;

        s->history_checkpoint("Objekt skalieren");
        Slic3r::ModelInstance *instance = first_instance(o);
        const Slic3r::BoundingBoxf3 bb = o->instance_bounding_box(0, false);
        const Slic3r::Vec3d sz = bb.size();
        const double longest = std::max({ sz.x(), sz.y(), sz.z() });
        if (longest <= 0.0)
            return PSM_ERR_GENERIC;

        const double f = static_cast<double>(size_mm) / longest;
        instance->set_scaling_factor(instance->get_scaling_factor() * f);
        o->invalidate_bounding_box();
        o->ensure_on_bed();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_fit_to_bed(psm_session *s,
                                        psm_object_id id,
                                        float fill_ratio)
{
    PSM_GUARD_BEGIN(s)
        if (! (fill_ratio > 0.f && fill_ratio <= 1.f))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        Slic3r::BoundingBoxf bed_box;
        for (const Slic3r::Point &point : Slic3r::get_bed_shape(s->config))
            bed_box.merge(Slic3r::Vec2d(
                Slic3r::unscale<double>(point.x()),
                Slic3r::unscale<double>(point.y())));
        if (! bed_box.defined || bed_box.size().x() <= 0.0 ||
            bed_box.size().y() <= 0.0) {
            s->set_error("Druckbett ist nicht definiert");
            return PSM_ERR_GENERIC;
        }

        const Slic3r::BoundingBoxf3 before =
            object->instance_bounding_box(0, false);
        const Slic3r::Vec3d size = before.size();
        if (size.x() <= 0.0 || size.y() <= 0.0)
            return PSM_ERR_GENERIC;

        const double factor = std::min(
            bed_box.size().x() * fill_ratio / size.x(),
            bed_box.size().y() * fill_ratio / size.y());
        if (! std::isfinite(factor) || factor <= 0.0)
            return PSM_ERR_GENERIC;

        s->history_checkpoint("Objekt aufs Bett einpassen");
        Slic3r::ModelInstance *instance = first_instance(object);
        instance->set_scaling_factor(
            instance->get_scaling_factor() * factor);
        object->invalidate_bounding_box();

        /* Nach der Skalierung anhand der tatsaechlichen transformierten
         * Box zentrieren. Das funktioniert auch fuer Bettformen mit
         * negativen Koordinaten und fuer bereits gedrehte Objekte. */
        const Slic3r::BoundingBoxf3 after =
            object->instance_bounding_box(0, false);
        const Slic3r::Vec2d delta =
            bed_box.center() - Slic3r::Vec2d(after.center().x(),
                                              after.center().y());
        Slic3r::Vec3d offset = instance->get_offset();
        offset.x() += delta.x();
        offset.y() += delta.y();
        instance->set_offset(offset);
        object->invalidate_bounding_box();
        object->ensure_on_bed();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_duplicate(psm_session *s, psm_object_id id, psm_object_id *out_new_id)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        /*
         * Die Zwischenablage bleibt beim Bettwechsel erhalten. Deshalb
         * die Quell-ID in allen Bettmodellen suchen; das Ziel ist wie bei
         * jeder normalen Einfuegeoperation das aktuell aktive Bett.
         */
        Slic3r::ModelObject *o = nullptr;
        for (const auto &bed : s->bed_models) {
            for (Slic3r::ModelObject *candidate : bed->objects) {
                if (static_cast<psm_object_id>(candidate->id().id) == id) {
                    o = candidate;
                    break;
                }
            }
            if (o != nullptr)
                break;
        }
        if (o == nullptr)
            return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objekt duplizieren");
        Slic3r::ModelObject *copy = s->model().add_object(*o);
        first_instance(copy);
        copy->ensure_on_bed();
        if (out_new_id != nullptr)
            *out_new_id = static_cast<psm_object_id>(copy->id().id);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_arrange(psm_session *s, float gap_mm)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->model().objects.empty())
            return PSM_OK;

        /* Bettform kommt aus der aktiven Konfiguration; ohne gewaehlten
         * Drucker ist das die Vorgabe aus FullPrintConfig. */
        const Slic3r::Points bedpts = Slic3r::get_bed_shape(s->config);
        if (bedpts.empty()) {
            s->set_error("Druckbett ist nicht definiert");
            return PSM_ERR_GENERIC;
        }

        const Slic3r::Vec2crd gap{ 0, 0 };
        Slic3r::arr2::ArrangeBed bed = Slic3r::arr2::to_arrange_bed(bedpts, gap);

        Slic3r::arr2::ArrangeSettings cfg;
        const double dist = (gap_mm > 0.f)
            ? static_cast<double>(gap_mm)
            : Slic3r::min_object_distance(s->config);
        cfg.set_distance_from_objects(dist);

        /* Die mobile Ebene ist immer ein lokales Einzelbett. Nach dem
         * Öffnen eines Mehrbett-3MF kennt MultipleBeds noch die frühere
         * Desktop-Landschaft; neue Instanzen besitzen dort folglich keinen
         * gültigen Bettindex (-1). arrange_objects konsultiert diese globale
         * Zuordnung trotz des lokalen Model-Arguments. Deshalb jede lokale
         * Instanz vor dem Arrange explizit auf Bett 0 abbilden. */
        Slic3r::s_multiple_beds.clear_inst_map();
        for (Slic3r::ModelObject *object : s->model().objects) {
            for (Slic3r::ModelInstance *instance : object->instances) {
                Slic3r::s_multiple_beds.set_instance_bed(
                    instance->id(), instance->printable, 0);
            }
        }
        Slic3r::s_multiple_beds.inst_map_updated();
        Slic3r::s_multiple_beds.set_active_bed(0);

        s->history_checkpoint("Objekte anordnen");
        Slic3r::arrange_objects(s->model(), bed, cfg);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Erweiterte Modellwerkzeuge                                         */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_model_split_objects(psm_session *s,
                                            psm_object_id id,
                                            psm_object_id *out_ids,
                                            size_t out_ids_cap,
                                            size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *source = find_object(s, id);
        if (source == nullptr)
            return PSM_ERR_NOT_FOUND;

        /*
         * ModelProcessing::split haengt seine Ergebnisse an das
         * Quellmodell. In einem temporaeren Modell koennen wir erst
         * pruefen, ob wirklich mehrere Koerper entstanden sind, bevor
         * der sichtbare Projektzustand veraendert wird.
         */
        Slic3r::Model temporary;
        Slic3r::ModelObject *temporary_source =
            temporary.add_object(*source);
        Slic3r::ModelObjectPtrs parts;
        Slic3r::ModelProcessing::split(temporary_source, &parts);
        if (parts.size() <= 1) {
            s->set_error("Das Objekt enthaelt nur einen zusammenhaengenden Koerper");
            return PSM_ERR_UNSUPPORTED;
        }

        size_t source_index = s->model().objects.size();
        for (size_t i = 0; i < s->model().objects.size(); ++i)
            if (s->model().objects[i] == source) {
                source_index = i;
                break;
            }
        if (source_index == s->model().objects.size())
            return PSM_ERR_NOT_FOUND;

        s->history_checkpoint("In Objekte teilen");
        std::vector<psm_object_id> ids;
        ids.reserve(parts.size());
        for (const Slic3r::ModelObject *part : parts) {
            Slic3r::ModelObject *copy = s->model().add_object(*part);
            ids.push_back(static_cast<psm_object_id>(copy->id().id));
        }
        s->model().delete_object(source_index);

        if (out_count != nullptr)
            *out_count = ids.size();
        if (out_ids != nullptr)
            for (size_t i = 0; i < std::min(ids.size(), out_ids_cap); ++i)
                out_ids[i] = ids[i];
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_split_volumes(psm_session *s,
                                            psm_object_id id,
                                            size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        bool changed = false;
        const size_t original_count = object->volumes.size();
        const unsigned int max_extruders =
            static_cast<unsigned int>(std::max(1, psm_extruder_count(s)));

        /*
         * Rueckwaerts laufen: split fuegt direkt hinter dem aktuellen
         * Volumen neue Eintraege ein.
         */
        for (size_t i = original_count; i-- > 0;) {
            Slic3r::ModelVolume *volume = object->volumes[i];
            if (volume->is_model_part() && volume->is_splittable()) {
                if (! changed)
                    s->history_checkpoint("In Volumen teilen");
                changed |=
                    Slic3r::ModelProcessing::split(volume, max_extruders) > 1;
            }
        }

        if (! changed) {
            s->set_error("Keine trennbaren Volumen gefunden");
            return PSM_ERR_UNSUPPORTED;
        }
        object->invalidate_bounding_box();
        if (out_count != nullptr)
            *out_count = object->volumes.size();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_cut_z(psm_session *s,
                                   psm_object_id id,
                                   float z_mm,
                                   int32_t keep_upper,
                                   int32_t keep_lower,
                                   int32_t keep_as_parts,
                                   psm_object_id *out_ids,
                                   size_t out_ids_cap,
                                   size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        if ((! keep_upper && ! keep_lower) || ! std::isfinite(z_mm))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *source = find_object(s, id);
        if (source == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (source->instances.empty())
            source->add_instance();

        const Slic3r::BoundingBoxf3 bb =
            source->instance_bounding_box(0, false);
        if (z_mm <= bb.min.z() + ::EPSILON ||
            z_mm >= bb.max.z() - ::EPSILON) {
            s->set_error("Schnittebene liegt ausserhalb des Objekts");
            return PSM_ERR_INVALID_ARG;
        }

        const Slic3r::Vec3d instance_offset =
            source->instances.front()->get_offset();
        const Slic3r::Vec3d plane_center(
            bb.center().x(), bb.center().y(), static_cast<double>(z_mm));
        const Slic3r::Transform3d cut_matrix =
            Slic3r::Transform3d::Identity() *
            Slic3r::Geometry::translation_transform(
                instance_offset - plane_center);

        const Slic3r::ModelObjectCutAttributes attributes =
            Slic3r::only_if(
                keep_upper, Slic3r::ModelObjectCutAttribute::KeepUpper) |
            Slic3r::only_if(
                keep_lower, Slic3r::ModelObjectCutAttribute::KeepLower) |
            Slic3r::only_if(
                keep_as_parts,
                Slic3r::ModelObjectCutAttribute::KeepAsParts);

        Slic3r::Cut cut(source, 0, cut_matrix, attributes);
        const Slic3r::ModelObjectPtrs &parts = cut.perform_with_plane();
        if (parts.empty()) {
            s->set_error("Schnitt ergab keine druckbare Geometrie");
            return PSM_ERR_SLICING;
        }

        size_t source_index = s->model().objects.size();
        for (size_t i = 0; i < s->model().objects.size(); ++i)
            if (s->model().objects[i] == source) {
                source_index = i;
                break;
            }
        if (source_index == s->model().objects.size())
            return PSM_ERR_NOT_FOUND;

        s->history_checkpoint("Objekt schneiden");
        std::vector<psm_object_id> ids;
        ids.reserve(parts.size());
        for (const Slic3r::ModelObject *part : parts) {
            Slic3r::ModelObject *copy = s->model().add_object(*part);
            copy->ensure_on_bed();
            ids.push_back(static_cast<psm_object_id>(copy->id().id));
        }
        s->model().delete_object(source_index);

        if (out_count != nullptr)
            *out_count = ids.size();
        if (out_ids != nullptr)
            for (size_t i = 0; i < std::min(ids.size(), out_ids_cap); ++i)
                out_ids[i] = ids[i];
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_simplify(psm_session *s,
                                      psm_object_id id,
                                      float ratio,
                                      uint32_t *out_before,
                                      uint32_t *out_after)
{
    PSM_GUARD_BEGIN(s)
        if (! std::isfinite(ratio) || ratio < 0.01f || ratio > 1.f)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        uint64_t before = 0;
        for (const Slic3r::ModelVolume *volume : object->volumes)
            if (volume->is_model_part())
                before += volume->mesh().facets_count();
        if (before < 8 || ratio >= 0.999f) {
            if (out_before) *out_before = static_cast<uint32_t>(before);
            if (out_after)  *out_after  = static_cast<uint32_t>(before);
            return PSM_OK;
        }

        s->history_checkpoint("Mesh vereinfachen");
        uint64_t after = 0;
        for (Slic3r::ModelVolume *volume : object->volumes) {
            if (! volume->is_model_part() || volume->mesh().facets_count() < 8) {
                if (volume->is_model_part())
                    after += volume->mesh().facets_count();
                continue;
            }

            indexed_triangle_set mesh = volume->mesh().its;
            const uint32_t target = std::max<uint32_t>(
                4u, static_cast<uint32_t>(
                    std::llround(mesh.indices.size() * ratio)));
            Slic3r::its_quadric_edge_collapse(mesh, target);
            volume->reset_extra_facets();
            volume->set_mesh(std::move(mesh));
            volume->calculate_convex_hull();
            volume->set_new_unique_id();
            after += volume->mesh().facets_count();
        }
        object->invalidate_bounding_box();
        object->ensure_on_bed();
        if (out_before)
            *out_before = static_cast<uint32_t>(
                std::min<uint64_t>(before, UINT32_MAX));
        if (out_after)
            *out_after = static_cast<uint32_t>(
                std::min<uint64_t>(after, UINT32_MAX));
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_add_primitive_volume(psm_session *s,
                                                   psm_object_id id,
                                                   psm_volume_type type,
                                                   psm_primitive_shape shape,
                                                   float size_x,
                                                   float size_y,
                                                   float size_z,
                                                   size_t *out_volume_index)
{
    PSM_GUARD_BEGIN(s)
        if (type < PSM_VOLUME_NEGATIVE ||
            type > PSM_VOLUME_SUPPORT_ENFORCER ||
            shape < PSM_PRIMITIVE_BOX || shape > PSM_PRIMITIVE_SPHERE ||
            size_x <= 0.f || size_y <= 0.f || size_z <= 0.f)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        Slic3r::TriangleMesh mesh;
        switch (shape) {
            case PSM_PRIMITIVE_CYLINDER:
                mesh = Slic3r::make_cylinder(
                    0.5 * std::min(size_x, size_y), size_z,
                    2.0 * ::PI / 64.0);
                break;
            case PSM_PRIMITIVE_SPHERE:
                mesh = Slic3r::make_sphere(
                    0.5 * std::min({ size_x, size_y, size_z }),
                    2.0 * ::PI / 48.0);
                break;
            case PSM_PRIMITIVE_BOX:
            default:
                mesh = Slic3r::make_cube(size_x, size_y, size_z);
                break;
        }

        s->history_checkpoint("Volumen hinzufuegen");
        Slic3r::ModelVolume *volume = object->add_volume(
            std::move(mesh),
            static_cast<Slic3r::ModelVolumeType>(
                static_cast<int>(type)));
        volume->name = type == PSM_VOLUME_NEGATIVE ? "Negativvolumen" :
                       type == PSM_VOLUME_MODIFIER ? "Modifikator" :
                       type == PSM_VOLUME_SUPPORT_BLOCKER ? "Stuetzblocker" :
                       "Stuetzverstaerker";
        volume->center_geometry_after_creation();

        /*
         * Volumenkoordinaten sind objektlokal. Der Mittelpunkt der
         * vorhandenen Rohgeometrie ist deshalb der erwartbare Startort.
         */
        const Slic3r::BoundingBoxf3 raw = object->raw_bounding_box();
        if (raw.defined)
            volume->translate(raw.center());
        object->sort_volumes(true);
        object->invalidate_bounding_box();
        if (out_volume_index != nullptr) {
            const auto it = std::find(object->volumes.begin(),
                                      object->volumes.end(), volume);
            *out_volume_index =
                static_cast<size_t>(it - object->volumes.begin());
        }
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_remove_volume(psm_session *s,
                                           psm_object_id id,
                                           size_t volume_index)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size())
            return PSM_ERR_INVALID_ARG;

        size_t model_parts = 0;
        for (const Slic3r::ModelVolume *volume : object->volumes)
            model_parts += volume->is_model_part() ? 1u : 0u;
        if (object->volumes[volume_index]->is_model_part() &&
            model_parts <= 1) {
            s->set_error("Das letzte druckbare Volumen kann nicht entfernt werden");
            return PSM_ERR_UNSUPPORTED;
        }

        s->history_checkpoint("Volumen entfernen");
        object->delete_volume(volume_index);
        object->invalidate_bounding_box();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_add_text_volume(psm_session *s,
                                             psm_object_id id,
                                             const char *utf8_text,
                                             const char *font_path,
                                             float size_mm,
                                             float depth_mm,
                                             psm_volume_type type,
                                             size_t *out_volume_index)
{
    PSM_GUARD_BEGIN(s)
        if (utf8_text == nullptr || *utf8_text == '\0' ||
            font_path == nullptr || *font_path == '\0' ||
            ! std::isfinite(size_mm) || ! std::isfinite(depth_mm) ||
            size_mm <= 0.f || depth_mm <= 0.f ||
            type < PSM_VOLUME_MODEL_PART ||
            type > PSM_VOLUME_SUPPORT_ENFORCER)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        auto font_file = Slic3r::Emboss::create_font_file(font_path);
        if (font_file == nullptr) {
            s->set_error("Schriftdatei konnte nicht gelesen werden");
            return PSM_ERR_PARSE;
        }
        Slic3r::FontProp font_property(size_mm);
        Slic3r::Emboss::FontFileWithCache font(std::move(font_file));
        Slic3r::HealedExPolygons shapes =
            Slic3r::Emboss::text2shapes(
                font, utf8_text, font_property);
        if (shapes.expolygons.empty()) {
            s->set_error("Text enthaelt keine druckbare Kontur");
            return PSM_ERR_PARSE;
        }

        const double shape_scale =
            Slic3r::Emboss::get_text_shape_scale(
                font_property, *font.font_file);
        auto project_z = std::make_unique<Slic3r::Emboss::ProjectZ>(
            static_cast<double>(depth_mm) / shape_scale);
        const Slic3r::Transform3d scale_transform{
            Eigen::Scaling(shape_scale)
        };
        Slic3r::Emboss::ProjectTransform projection(
            std::move(project_z), scale_transform);
        indexed_triangle_set its =
            Slic3r::Emboss::polygons2model(
                shapes.expolygons, projection);
        if (its.empty()) {
            s->set_error("Text konnte nicht trianguliert werden");
            return PSM_ERR_PARSE;
        }

        const Slic3r::BoundingBoxf3 target = object->raw_bounding_box();
        s->history_checkpoint("Text praegen");
        Slic3r::ModelVolume *volume = object->add_volume(
            Slic3r::TriangleMesh(std::move(its)),
            static_cast<Slic3r::ModelVolumeType>(
                static_cast<int>(type)));
        volume->name =
            std::string("Text: ") +
            std::string(utf8_text).substr(0, 80);

        Slic3r::EmbossShape emboss_shape;
        emboss_shape.final_shape = shapes;
        emboss_shape.scale = shape_scale;
        emboss_shape.projection =
            Slic3r::EmbossProjection{depth_mm, false};
        volume->emboss_shape = std::move(emboss_shape);

        Slic3r::TextConfiguration text_configuration;
        text_configuration.text = utf8_text;
        text_configuration.style.name = "Android System Font";
        text_configuration.style.path =
            boost::filesystem::path(font_path).filename().string();
        text_configuration.style.type =
            Slic3r::EmbossStyle::Type::file_path;
        text_configuration.style.prop = font_property;
        volume->text_configuration =
            std::move(text_configuration);

        if (target.defined) {
            const double z =
                type == PSM_VOLUME_NEGATIVE
                    ? target.max.z() - 0.5 * depth_mm + 0.01
                    : target.max.z() + 0.5 * depth_mm - 0.01;
            volume->set_offset(
                Slic3r::Vec3d(
                    target.center().x(), target.center().y(), z));
        }
        object->sort_volumes(true);
        object->invalidate_bounding_box();
        if (out_volume_index != nullptr) {
            const auto it = std::find(
                object->volumes.begin(), object->volumes.end(), volume);
            *out_volume_index =
                it == object->volumes.end()
                    ? 0 : static_cast<size_t>(
                        std::distance(object->volumes.begin(), it));
        }
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_add_svg_volume(psm_session *s,
                                            psm_object_id id,
                                            const char *svg_path,
                                            float depth_mm,
                                            psm_volume_type type,
                                            size_t *out_volume_index)
{
    PSM_GUARD_BEGIN(s)
        if (svg_path == nullptr || *svg_path == '\0' ||
            ! std::isfinite(depth_mm) || depth_mm <= 0.f ||
            type < PSM_VOLUME_MODEL_PART ||
            type > PSM_VOLUME_SUPPORT_ENFORCER)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        Slic3r::Model imported;
        if (! Slic3r::load_svg(svg_path, imported) ||
            imported.objects.empty() ||
            imported.objects.front()->volumes.empty()) {
            s->set_error("SVG enthaelt keine druckbare geschlossene Kontur");
            return PSM_ERR_PARSE;
        }
        const Slic3r::ModelVolume *source =
            imported.objects.front()->volumes.front();
        Slic3r::TriangleMesh mesh = source->mesh();
        mesh.scale(Slic3r::Vec3f(
            1.f, 1.f, depth_mm / 10.f));
        const Slic3r::BoundingBoxf3 target = object->raw_bounding_box();

        s->history_checkpoint("SVG praegen");
        Slic3r::ModelVolume *volume = object->add_volume(
            std::move(mesh),
            static_cast<Slic3r::ModelVolumeType>(
                static_cast<int>(type)));
        volume->name =
            std::string("SVG: ") +
            boost::filesystem::path(svg_path).stem().string();
        volume->emboss_shape = source->emboss_shape;
        if (volume->emboss_shape.has_value())
            volume->emboss_shape->projection.depth = depth_mm;

        if (target.defined) {
            const double z =
                type == PSM_VOLUME_NEGATIVE
                    ? target.max.z() - 0.5 * depth_mm + 0.01
                    : target.max.z() + 0.5 * depth_mm - 0.01;
            volume->set_offset(
                Slic3r::Vec3d(
                    target.center().x(), target.center().y(), z));
        }
        object->sort_volumes(true);
        object->invalidate_bounding_box();
        if (out_volume_index != nullptr) {
            const auto it = std::find(
                object->volumes.begin(), object->volumes.end(), volume);
            *out_volume_index =
                it == object->volumes.end()
                    ? 0 : static_cast<size_t>(
                        std::distance(object->volumes.begin(), it));
        }
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_lay_on_facet(psm_session *s,
                                          psm_object_id id,
                                          size_t volume_index,
                                          size_t facet_index)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size())
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelVolume *volume = object->volumes[volume_index];
        if (! volume->is_model_part() ||
            facet_index >= volume->mesh().its.indices.size())
            return PSM_ERR_INVALID_ARG;

        Slic3r::ModelInstance *instance = first_instance(object);
        const auto &its = volume->mesh().its;
        const Slic3r::Vec3i32 &face = its.indices[facet_index];
        const Slic3r::Vec3d a = its.vertices[face(0)].cast<double>();
        const Slic3r::Vec3d b = its.vertices[face(1)].cast<double>();
        const Slic3r::Vec3d c = its.vertices[face(2)].cast<double>();
        Slic3r::Vec3d normal = (b - a).cross(c - a);
        if (normal.squaredNorm() < 1e-18)
            return PSM_ERR_INVALID_ARG;

        const Slic3r::Transform3d local_to_world =
            instance->get_matrix() * volume->get_matrix();
        normal = (local_to_world.linear().inverse().transpose() * normal)
                     .normalized();
        Eigen::Quaterniond turn;
        turn.setFromTwoVectors(normal, -Slic3r::Vec3d::UnitZ());

        s->history_checkpoint("Auf Flaeche legen");
        Slic3r::Transform3d transformed = instance->get_matrix();
        transformed.linear() =
            turn.toRotationMatrix() * transformed.linear();
        instance->set_transformation(
            Slic3r::Geometry::Transformation(transformed));
        object->invalidate_bounding_box();
        object->ensure_on_bed();
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_paint_facet(psm_session *s,
                                         psm_object_id id,
                                         size_t volume_index,
                                         size_t facet_index,
                                         psm_paint_tool tool,
                                         int32_t state)
{
    PSM_GUARD_BEGIN(s)
        if (tool < PSM_PAINT_SUPPORT || tool > PSM_PAINT_MMU ||
            state < 0 || state > 254)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size())
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelVolume *volume = object->volumes[volume_index];
        if (! volume->is_model_part() ||
            facet_index >= volume->mesh().its.indices.size())
            return PSM_ERR_INVALID_ARG;

        Slic3r::FacetsAnnotation *annotation = nullptr;
        switch (tool) {
            case PSM_PAINT_SUPPORT: annotation = &volume->supported_facets; break;
            case PSM_PAINT_SEAM:    annotation = &volume->seam_facets; break;
            case PSM_PAINT_FUZZY:   annotation = &volume->fuzzy_skin_facets; break;
            case PSM_PAINT_MMU:     annotation = &volume->mm_segmentation_facets; break;
        }
        if (annotation == nullptr)
            return PSM_ERR_INVALID_ARG;

        Slic3r::TriangleSelector selector(volume->mesh());
        selector.deserialize(annotation->get_data(), false);
        selector.set_facet(
            static_cast<int>(facet_index),
            static_cast<Slic3r::TriangleStateType>(state));

        s->history_checkpoint("Flaeche bemalen");
        annotation->set(selector);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_paint_brush(psm_session *s,
                                         psm_object_id id,
                                         size_t volume_index,
                                         size_t facet_index,
                                         psm_paint_tool tool,
                                         int32_t state,
                                         float radius_mm)
{
    PSM_GUARD_BEGIN(s)
        if (tool < PSM_PAINT_SUPPORT || tool > PSM_PAINT_MMU ||
            state < 0 || state > 254 || ! std::isfinite(radius_mm) ||
            radius_mm <= 0.f || radius_mm > 1000.f)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (volume_index >= object->volumes.size() ||
            object->instances.empty())
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelVolume *volume = object->volumes[volume_index];
        const auto &its = volume->mesh().its;
        if (! volume->is_model_part() || facet_index >= its.indices.size())
            return PSM_ERR_INVALID_ARG;

        Slic3r::FacetsAnnotation *annotation = nullptr;
        switch (tool) {
            case PSM_PAINT_SUPPORT: annotation = &volume->supported_facets; break;
            case PSM_PAINT_SEAM:    annotation = &volume->seam_facets; break;
            case PSM_PAINT_FUZZY:   annotation = &volume->fuzzy_skin_facets; break;
            case PSM_PAINT_MMU:     annotation = &volume->mm_segmentation_facets; break;
        }
        if (annotation == nullptr)
            return PSM_ERR_INVALID_ARG;

        const Slic3r::Transform3d local_to_world =
            object->instances.front()->get_matrix() * volume->get_matrix();
        auto centroid = [&](size_t index) {
            const auto &face = its.indices[index];
            return local_to_world *
                ((its.vertices[face(0)].cast<double>() +
                  its.vertices[face(1)].cast<double>() +
                  its.vertices[face(2)].cast<double>()) / 3.0);
        };
        auto normal = [&](size_t index) -> Slic3r::Vec3d {
            const auto &face = its.indices[index];
            const Slic3r::Vec3d a = its.vertices[face(0)].cast<double>();
            const Slic3r::Vec3d b = its.vertices[face(1)].cast<double>();
            const Slic3r::Vec3d c = its.vertices[face(2)].cast<double>();
            Slic3r::Vec3d result = (b - a).cross(c - a);
            if (result.squaredNorm() < 1e-18)
                return Slic3r::Vec3d::Zero().eval();
            return (local_to_world.linear().inverse().transpose() * result)
                .normalized();
        };

        std::vector<std::vector<size_t>> neighbours(its.indices.size());
        std::unordered_map<uint64_t, size_t> edge_owner;
        edge_owner.reserve(its.indices.size() * 3);
        for (size_t index = 0; index < its.indices.size(); ++index) {
            const auto &face = its.indices[index];
            for (int edge = 0; edge < 3; ++edge) {
                const uint32_t a = static_cast<uint32_t>(face(edge));
                const uint32_t b = static_cast<uint32_t>(face((edge + 1) % 3));
                const uint32_t lo = std::min(a, b);
                const uint32_t hi = std::max(a, b);
                const uint64_t key =
                    (static_cast<uint64_t>(lo) << 32U) | hi;
                const auto found = edge_owner.find(key);
                if (found == edge_owner.end()) {
                    edge_owner.emplace(key, index);
                } else {
                    neighbours[index].push_back(found->second);
                    neighbours[found->second].push_back(index);
                }
            }
        }

        const Slic3r::Vec3d origin = centroid(facet_index);
        const Slic3r::Vec3d origin_normal = normal(facet_index);
        const double radius_squared =
            static_cast<double>(radius_mm) * static_cast<double>(radius_mm);
        std::vector<uint8_t> seen(its.indices.size(), 0);
        std::vector<size_t> queue{facet_index};
        seen[facet_index] = 1;

        Slic3r::TriangleSelector selector(volume->mesh());
        selector.deserialize(annotation->get_data(), false);
        for (size_t cursor = 0; cursor < queue.size(); ++cursor) {
            const size_t index = queue[cursor];
            selector.set_facet(
                static_cast<int>(index),
                static_cast<Slic3r::TriangleStateType>(state));
            for (const size_t candidate : neighbours[index]) {
                if (seen[candidate])
                    continue;
                seen[candidate] = 1;
                const Slic3r::Vec3d candidate_normal = normal(candidate);
                if ((centroid(candidate) - origin).squaredNorm() <=
                        radius_squared &&
                    (origin_normal.isZero() || candidate_normal.isZero() ||
                     origin_normal.dot(candidate_normal) > 0.15))
                    queue.push_back(candidate);
            }
        }

        s->history_checkpoint("Flaeche mit Pinsel bemalen");
        annotation->set(selector);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_clear_paint(psm_session *s,
                                         psm_object_id id,
                                         psm_paint_tool tool)
{
    PSM_GUARD_BEGIN(s)
        if (tool < PSM_PAINT_SUPPORT || tool > PSM_PAINT_MMU)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        s->history_checkpoint("Bemalung loeschen");
        for (Slic3r::ModelVolume *volume : object->volumes) {
            if (! volume->is_model_part())
                continue;
            switch (tool) {
                case PSM_PAINT_SUPPORT: volume->supported_facets.reset(); break;
                case PSM_PAINT_SEAM:    volume->seam_facets.reset(); break;
                case PSM_PAINT_FUZZY:   volume->fuzzy_skin_facets.reset(); break;
                case PSM_PAINT_MMU:     volume->mm_segmentation_facets.reset(); break;
            }
        }
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_model_paint_count(psm_session *s,
                                     psm_object_id id,
                                     psm_paint_tool tool)
{
    if (s == nullptr || tool < PSM_PAINT_SUPPORT || tool > PSM_PAINT_MMU)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    const Slic3r::ModelObject *object = find_object(s, id);
    if (object == nullptr)
        return 0;
    size_t count = 0;
    for (const Slic3r::ModelVolume *volume : object->volumes) {
        if (! volume->is_model_part())
            continue;
        const Slic3r::FacetsAnnotation *annotation = nullptr;
        switch (tool) {
            case PSM_PAINT_SUPPORT: annotation = &volume->supported_facets; break;
            case PSM_PAINT_SEAM:    annotation = &volume->seam_facets; break;
            case PSM_PAINT_FUZZY:   annotation = &volume->fuzzy_skin_facets; break;
            case PSM_PAINT_MMU:     annotation = &volume->mm_segmentation_facets; break;
        }
        if (annotation == nullptr || annotation->empty())
            continue;
        Slic3r::TriangleSelector selector(volume->mesh());
        selector.deserialize(annotation->get_data(), false);
        for (int state = 1; state <= 254; ++state)
            count += static_cast<size_t>(std::max(
                0, selector.num_facets(
                    static_cast<Slic3r::TriangleStateType>(state))));
    }
    return count;
}

PSM_API psm_result psm_model_layer_profile_set(psm_session *s,
                                                psm_object_id id,
                                                const double *pairs,
                                                size_t pair_count)
{
    PSM_GUARD_BEGIN(s)
        if (pair_count > 0 && (pairs == nullptr || pair_count < 2))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;

        std::vector<double> profile;
        profile.reserve(pair_count * 2);
        double last_z = -1.0;
        for (size_t i = 0; i < pair_count; ++i) {
            const double z = pairs[i * 2];
            const double h = pairs[i * 2 + 1];
            if (! std::isfinite(z) || ! std::isfinite(h) ||
                z < 0.0 || z <= last_z || h <= 0.0) {
                s->set_error("Schichthoehenprofil ist ungueltig");
                return PSM_ERR_INVALID_ARG;
            }
            profile.push_back(z);
            profile.push_back(h);
            last_z = z;
        }

        s->history_checkpoint(
            pair_count == 0 ? "Variable Schichthoehe zuruecksetzen"
                            : "Variable Schichthoehe");
        if (profile.empty())
            object->layer_height_profile.clear();
        else
            object->layer_height_profile.set(std::move(profile));
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API size_t psm_model_layer_profile_count(psm_session *s,
                                              psm_object_id id)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    const Slic3r::ModelObject *object = find_object(s, id);
    return object == nullptr ? 0 :
        object->layer_height_profile.get().size() / 2;
}

PSM_API psm_result psm_model_layer_profile_at(psm_session *s,
                                               psm_object_id id,
                                               size_t index,
                                               double *out_z,
                                               double *out_height)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        const std::vector<double> &profile =
            object->layer_height_profile.get();
        if (index >= profile.size() / 2)
            return PSM_ERR_INVALID_ARG;
        if (out_z)      *out_z      = profile[index * 2];
        if (out_height) *out_height = profile[index * 2 + 1];
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_colour_get(psm_session *s, psm_object_id id,
                                        char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        copy_str(out, out_cap,
                 object->config.has("extruder_colour")
                    ? object->config.opt_serialize("extruder_colour")
                    : std::string());
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_colour_set(psm_session *s, psm_object_id id,
                                        const char *rgb)
{
    PSM_GUARD_BEGIN(s)
        if (rgb == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Objektfarbe");
        if (*rgb == '\0')
            object->config.erase("extruder_colour");
        else
            object->config.set_deserialize_strict("extruder_colour", rgb);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_wipe_get(psm_session *s, psm_object_id id,
                                      int32_t *out_infill, int32_t *out_objects)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        if (out_infill) {
            const auto *option =
                dynamic_cast<const Slic3r::ConfigOptionBool *>(
                    object->config.option("wipe_into_infill"));
            *out_infill =
                option != nullptr && option->value ? 1 : 0;
        }
        if (out_objects) {
            const auto *option =
                dynamic_cast<const Slic3r::ConfigOptionBool *>(
                    object->config.option("wipe_into_objects"));
            *out_objects =
                option != nullptr && option->value ? 1 : 0;
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_wipe_set(psm_session *s, psm_object_id id,
                                      int32_t into_infill, int32_t into_objects)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        Slic3r::ModelObject *object = find_object(s, id);
        if (object == nullptr)
            return PSM_ERR_NOT_FOUND;
        s->history_checkpoint("Wischoptionen");
        object->config.set("wipe_into_infill", into_infill != 0);
        object->config.set("wipe_into_objects", into_objects != 0);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Custom-G-Code und Wipe-Tower                                       */
/* ------------------------------------------------------------------ */

PSM_API size_t psm_custom_gcode_count(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    return s->model().get_custom_gcode_per_print_z_vector()[0]
        .gcodes.size();
}

PSM_API psm_result psm_custom_gcode_at(psm_session *s, size_t index,
                                       psm_custom_gcode *out)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const auto &gcodes =
            s->model().get_custom_gcode_per_print_z_vector()[0].gcodes;
        if (index >= gcodes.size())
            return PSM_ERR_INVALID_ARG;
        const Slic3r::CustomGCode::Item &item = gcodes[index];
        std::memset(out, 0, sizeof(*out));
        out->print_z = item.print_z;
        out->type = static_cast<psm_custom_gcode_type>(item.type);
        out->extruder = item.extruder;
        copy_str(out->color, sizeof(out->color), item.color);
        copy_str(out->extra, sizeof(out->extra), item.extra);
        return PSM_OK;
    PSM_GUARD_END(s)
}

namespace {

bool valid_custom_gcode(const psm_custom_gcode *item)
{
    return item != nullptr && std::isfinite(item->print_z) &&
           item->print_z >= 0.0 &&
           item->type >= PSM_CUSTOM_COLOR_CHANGE &&
           item->type <= PSM_CUSTOM_CODE &&
           item->extruder >= 0;
}

void sort_custom_gcode(Slic3r::CustomGCode::Info &info)
{
    std::stable_sort(info.gcodes.begin(), info.gcodes.end());
    Slic3r::CustomGCode::check_mode_for_custom_gcode_per_print_z(info);
}

} // namespace

PSM_API psm_result psm_custom_gcode_add(psm_session *s,
                                        const psm_custom_gcode *item)
{
    PSM_GUARD_BEGIN(s)
        if (! valid_custom_gcode(item))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_checkpoint("Custom G-Code hinzufuegen");
        auto &info =
            s->model().get_custom_gcode_per_print_z_vector()[0];
        info.gcodes.push_back({
            item->print_z,
            static_cast<Slic3r::CustomGCode::Type>(item->type),
            item->extruder,
            item->color,
            item->extra
        });
        sort_custom_gcode(info);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_custom_gcode_update(psm_session *s, size_t index,
                                           const psm_custom_gcode *item)
{
    PSM_GUARD_BEGIN(s)
        if (! valid_custom_gcode(item))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        auto &info =
            s->model().get_custom_gcode_per_print_z_vector()[0];
        if (index >= info.gcodes.size())
            return PSM_ERR_INVALID_ARG;
        s->history_checkpoint("Custom G-Code aendern");
        info.gcodes[index] = {
            item->print_z,
            static_cast<Slic3r::CustomGCode::Type>(item->type),
            item->extruder,
            item->color,
            item->extra
        };
        sort_custom_gcode(info);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_custom_gcode_remove(psm_session *s, size_t index)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        auto &info =
            s->model().get_custom_gcode_per_print_z_vector()[0];
        if (index >= info.gcodes.size())
            return PSM_ERR_INVALID_ARG;
        s->history_checkpoint("Custom G-Code entfernen");
        info.gcodes.erase(info.gcodes.begin() + index);
        sort_custom_gcode(info);
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_custom_gcode_clear(psm_session *s)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        auto &info =
            s->model().get_custom_gcode_per_print_z_vector()[0];
        if (info.gcodes.empty())
            return PSM_OK;
        s->history_checkpoint("Custom G-Code leeren");
        info.gcodes.clear();
        info.mode = Slic3r::CustomGCode::Undef;
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_wipe_tower_get(psm_session *s,
                                      float *out_x, float *out_y,
                                      float *out_rotation_deg)
{
    PSM_GUARD_BEGIN(s)
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        const Slic3r::ModelWipeTower &tower =
            s->model().get_wipe_tower_vector()[0];
        if (out_x) *out_x = static_cast<float>(tower.position.x());
        if (out_y) *out_y = static_cast<float>(tower.position.y());
        if (out_rotation_deg)
            *out_rotation_deg =
                static_cast<float>(
                    Slic3r::Geometry::rad2deg(tower.rotation));
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_wipe_tower_set(psm_session *s,
                                      float x, float y, float rotation_deg)
{
    PSM_GUARD_BEGIN(s)
        if (! std::isfinite(x) || ! std::isfinite(y) ||
            ! std::isfinite(rotation_deg))
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        s->history_checkpoint("Wipe-Tower positionieren");
        Slic3r::ModelWipeTower &tower =
            s->model().get_wipe_tower_vector()[0];
        tower.position = Slic3r::Vec2d(x, y);
        tower.rotation = Slic3r::Geometry::deg2rad(
            static_cast<double>(rotation_deg));
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Slicing                                                             */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_slice_start(psm_session *s, psm_progress_cb cb, void *user)
{
    PSM_GUARD_BEGIN(s)
        if (s->state.load() == PSM_STATE_RUNNING)
            return PSM_ERR_BUSY;

        s->join_worker();          /* alten Thread einsammeln */

        /*
         * Der Job bekommt einen unveraenderlichen Schnappschuss. Ab hier
         * darf die Oberflaeche weiterarbeiten, ohne dass Print::apply und
         * der Viewport dasselbe Model gleichzeitig veraendern.
         */
        {
            std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
            if (s->model().objects.empty()) {
                s->set_error("kein Objekt auf dem Bett");
                return PSM_ERR_INVALID_ARG;
            }
            s->slice_model = std::make_unique<Slic3r::Model>(s->model());
            s->slice_config = s->config;
            s->running_revision.store(
                s->design_revision.load(std::memory_order_acquire),
                std::memory_order_release);
        }

        {
            std::lock_guard<std::mutex> result_lock(s->result_mtx);
            if (! s->gcode_tmp_path.empty()) {
                boost::system::error_code ec;
                boost::filesystem::remove(s->gcode_tmp_path, ec);
                s->gcode_tmp_path.clear();
            }
            s->stats = psm_slice_stats{};
        }
        s->result_revision.store(0, std::memory_order_release);
        s->cancel_requested = false;
        s->progress_cb  = cb;
        s->progress_usr = user;
        s->state        = PSM_STATE_RUNNING;
        s->last_error.clear();

        s->worker = std::thread([s]() {
            try {
                /* Nicht einfach zuweisen: der alte Print muss ueber
                 * teardown_print() abgebaut werden, sonst laeuft sein
                 * Destruktor in den alten Cancel-Callback. */
                s->teardown_print();
                {
                    std::lock_guard<std::mutex> print_lock(s->print_mtx);
                    s->print = std::make_unique<Slic3r::Print>();
                }

                /* Rohzeiger fassen, damit der Callback nicht ueber
                 * s->print laeuft - das kann waehrend des Abbaus bereits
                 * null sein. */
                Slic3r::Print *print_ptr = nullptr;
                {
                    std::lock_guard<std::mutex> print_lock(s->print_mtx);
                    print_ptr = s->print.get();
                }

                print_ptr->set_status_callback(
                    [s](const Slic3r::PrintBase::SlicingStatus &st) {
                        if (st.percent >= 0 && s->progress_cb != nullptr) {
                            if (s->progress_cb(st.percent, st.text.c_str(), s->progress_usr) != 0)
                                s->cancel_requested = true;
                        }
                    });

                print_ptr->set_cancel_callback([s, print_ptr]() {
                    if (s->cancel_requested.load())
                        print_ptr->cancel();
                });

                const int32_t sliced_object_count =
                    static_cast<int32_t>(s->slice_model->objects.size());
                for (Slic3r::ModelObject *mo : s->slice_model->objects)
                    print_ptr->auto_assign_extruders(mo);

                print_ptr->apply(*s->slice_model, s->slice_config);
                /*
                 * Print::apply kopiert die komplette Model-/ModelObject-
                 * Hierarchie in den Backend-Print. Der unveraenderliche
                 * Start-Schnappschuss wird danach nicht mehr gebraucht.
                 *
                 * Gerade bemalte Multi-Material-3MFs enthalten neben dem
                 * Dreiecksnetz grosse TriangleSelector-Daten. Wenn
                 * slice_model bis zum G-Code-Export weiterlebt, liegen diese
                 * Daten dreifach vor (Editor, Schnappschuss, Print) und
                 * Android beendet grosse Jobs per Low-Memory-Killer.
                 */
                {
                    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
                    s->slice_model.reset();
                }

                const std::string err = print_ptr->validate();
                if (! err.empty()) {
                    s->set_error(err);
                    s->state = PSM_STATE_FAILED;
                    s->cv.notify_all();
                    return;
                }
                if (print_ptr->empty()) {
                    s->set_error("nichts zu drucken - Objekte ausserhalb des Druckraums?");
                    s->state = PSM_STATE_FAILED;
                    s->cv.notify_all();
                    return;
                }

                print_ptr->process();

                if (s->cancel_requested.load()) {
                    s->state = PSM_STATE_CANCELLED;
                    s->cv.notify_all();
                    return;
                }

                /* G-Code in eine temporaere Datei schreiben. Nie in den RAM -
                 * siehe Speicherstrategie in docs/02-architektur.md. */
                const uint64_t job_revision =
                    s->running_revision.load(std::memory_order_acquire);
                const boost::filesystem::path tmp =
                    boost::filesystem::path(s->datadir) /
                    ("slice-" + std::to_string(job_revision) + ".gcode");
                /*
                 * Print::export_gcode akzeptiert formal ein optionales
                 * GCodeProcessorResult. Sobald die Konfliktpruefung aber
                 * einen Treffer liefert, schreibt PrusaSlicer 2.9.6 ohne
                 * Nullpruefung in diesen Zeiger. Das crashte bei grossen
                 * oder ueberlappenden Modellen mit SIGSEGV auf 0x268.
                 * Ein lokales Ergebnis haelt denselben Exportpfad sicher
                 * und wird direkt nach der Statistikauswertung freigegeben.
                 */
                Slic3r::GCodeProcessorResult processor_result;
                const std::string generated =
                    print_ptr->export_gcode(
                        tmp.string(),
                        &processor_result,
                        nullptr);

                psm_slice_stats result_stats{};
                const Slic3r::PrintStatistics &ps = print_ptr->print_statistics();
                result_stats.print_time_seconds = ps.normal_print_time_seconds;
                result_stats.filament_used_mm   = ps.total_used_filament;
                result_stats.filament_used_g    = ps.total_weight;
                result_stats.filament_cost      = ps.total_cost;
                result_stats.object_count = sliced_object_count;

                /* Layerzahl und Bauhoehe kommen nicht aus PrintStatistics,
                 * sondern aus den geschnittenen Objekten. Die UI zeigt
                 * beides im Vorschau-Slider (M6). */
                size_t max_layers = 0;
                double max_z      = 0.0;
                for (const Slic3r::PrintObject *po : print_ptr->objects()) {
                    max_layers = std::max(max_layers, po->layer_count());
                    if (! po->layers().empty())
                        max_z = std::max(max_z, po->layers().back()->print_z);
                }
                result_stats.layer_count = static_cast<int32_t>(max_layers);
                result_stats.max_z       = static_cast<float>(max_z);

                /*
                 * Pruefung und Veroeffentlichung muessen unter demselben
                 * Daten-Lock passieren wie jede Designaenderung. Sonst
                 * koennte eine Aenderung genau zwischen Vergleich und
                 * PSM_STATE_DONE fallen.
                 */
                std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
                if (s->design_revision.load(std::memory_order_acquire) == job_revision) {
                    {
                        std::lock_guard<std::mutex> result_lock(s->result_mtx);
                        s->gcode_tmp_path = generated;
                        s->stats = result_stats;
                    }
                    s->result_revision.store(job_revision, std::memory_order_release);
                    s->state.store(PSM_STATE_DONE, std::memory_order_release);
                    emit_log(PSM_LOG_INFO, "Slicing fertig: " + generated);
                } else {
                    boost::system::error_code ec;
                    boost::filesystem::remove(generated, ec);
                    s->result_revision.store(0, std::memory_order_release);
                    s->state.store(PSM_STATE_STALE, std::memory_order_release);
                    emit_log(PSM_LOG_INFO,
                             "Slice verworfen: Projekt wurde waehrenddessen geaendert");
                }
            } catch (const Slic3r::CanceledException &) {
                s->state = PSM_STATE_CANCELLED;
            } catch (const std::bad_alloc &) {
                s->set_error("Speicher erschoepft beim Slicen");
                s->state = PSM_STATE_FAILED;
            } catch (const std::exception &e) {
                s->set_error(e.what());
                s->state = PSM_STATE_FAILED;
            } catch (...) {
                s->set_error("unbekannter Fehler beim Slicen");
                s->state = PSM_STATE_FAILED;
            }
            s->cv.notify_all();
        });

        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API void psm_slice_cancel(psm_session *s)
{
    if (s == nullptr)
        return;
    s->cancel_requested = true;
    std::lock_guard<std::mutex> print_lock(s->print_mtx);
    if (s->print)
        s->print->cancel();
}

PSM_API psm_slice_state psm_slice_state_get(psm_session *s)
{
    if (s == nullptr)
        return PSM_STATE_IDLE;
    return static_cast<psm_slice_state>(s->state.load());
}

PSM_API psm_result psm_slice_wait(psm_session *s, int timeout_ms)
{
    PSM_GUARD_BEGIN(s)
        std::unique_lock<std::mutex> lk(s->mtx);
        auto done = [s]() { return s->state.load() != PSM_STATE_RUNNING; };
        if (timeout_ms < 0)
            s->cv.wait(lk, done);
        else if (! s->cv.wait_for(lk, std::chrono::milliseconds(timeout_ms), done))
            return PSM_ERR_BUSY;

        switch (s->state.load()) {
            case PSM_STATE_DONE:      return PSM_OK;
            case PSM_STATE_CANCELLED: return PSM_ERR_CANCELLED;
            case PSM_STATE_FAILED:    return PSM_ERR_SLICING;
            case PSM_STATE_STALE:     return PSM_ERR_STALE_RESULT;
            default:                  return PSM_OK;
        }
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_slice_stats_get(psm_session *s, psm_slice_stats *out)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr)
            return PSM_ERR_INVALID_ARG;
        if (! s->result_is_current()) {
            s->set_error("Slice-Ergebnis ist nach einer Projektaenderung veraltet");
            return PSM_ERR_STALE_RESULT;
        }
        std::lock_guard<std::mutex> result_lock(s->result_mtx);
        *out = s->stats;
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_gcode_export(psm_session *s, const char *out_path)
{
    PSM_GUARD_BEGIN(s)
        if (out_path == nullptr)
            return PSM_ERR_INVALID_ARG;
        if (! s->result_is_current()) {
            s->set_error("G-Code ist nach einer Projektaenderung veraltet; erneut slicen");
            return PSM_ERR_STALE_RESULT;
        }
        std::lock_guard<std::mutex> result_lock(s->result_mtx);
        if (s->gcode_tmp_path.empty())
            return PSM_ERR_BUSY;
        boost::filesystem::copy_file(s->gcode_tmp_path, out_path,
                                     boost::filesystem::copy_options::overwrite_existing);
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_plate_export_stl(psm_session *s,
                                        const char *out_path)
{
    PSM_GUARD_BEGIN(s)
        if (out_path == nullptr || *out_path == '\0')
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->model().objects.empty())
            return PSM_ERR_NOT_FOUND;
        if (! Slic3r::store_stl(out_path, &s->model(), true)) {
            s->set_error("STL konnte nicht geschrieben werden");
            return PSM_ERR_IO;
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_plate_export_obj(psm_session *s,
                                        const char *out_path)
{
    PSM_GUARD_BEGIN(s)
        if (out_path == nullptr || *out_path == '\0')
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (s->model().objects.empty())
            return PSM_ERR_NOT_FOUND;
        if (! Slic3r::store_obj(out_path, &s->model())) {
            s->set_error("OBJ konnte nicht geschrieben werden");
            return PSM_ERR_IO;
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_stl_repair(psm_session *s,
                                  const char *input_path,
                                  const char *output_path)
{
    PSM_GUARD_BEGIN(s)
        if (input_path == nullptr || output_path == nullptr ||
            *input_path == '\0' || *output_path == '\0')
            return PSM_ERR_INVALID_ARG;
        Slic3r::TriangleMesh mesh;
        if (! mesh.ReadSTLFile(input_path, true) || mesh.empty()) {
            s->set_error("STL konnte nicht gelesen oder repariert werden");
            return PSM_ERR_PARSE;
        }
        if (! mesh.write_binary(output_path)) {
            s->set_error("Reparierte STL konnte nicht geschrieben werden");
            return PSM_ERR_IO;
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_gcode_convert(psm_session *s,
                                     const char *input_path,
                                     const char *output_path,
                                     int32_t to_binary)
{
    PSM_GUARD_BEGIN(s)
        if (input_path == nullptr || output_path == nullptr ||
            *input_path == '\0' || *output_path == '\0')
            return PSM_ERR_INVALID_ARG;

        std::FILE *input = boost::nowide::fopen(input_path, "rb");
        if (input == nullptr) {
            s->set_error("G-Code konnte nicht geoeffnet werden");
            return PSM_ERR_IO;
        }
        std::FILE *output = boost::nowide::fopen(output_path, "wb");
        if (output == nullptr) {
            std::fclose(input);
            s->set_error("Zieldatei konnte nicht angelegt werden");
            return PSM_ERR_IO;
        }

        bgcode::core::EResult result;
        if (to_binary) {
            const bgcode::binarize::BinarizerConfig &config =
                Slic3r::GCodeProcessor::get_binarizer_config();
            result = bgcode::convert::from_ascii_to_binary(
                *input, *output, config);
        } else {
            result = bgcode::convert::from_binary_to_ascii(
                *input, *output, true);
        }
        std::fclose(input);
        std::fclose(output);

        if (result != bgcode::core::EResult::Success &&
            result != bgcode::core::EResult::AlreadyBinarized &&
            result != bgcode::core::EResult::InvalidMagicNumber) {
            boost::system::error_code ec;
            boost::filesystem::remove(output_path, ec);
            s->set_error(
                std::string("G-Code-Konvertierung: ") +
                std::string(bgcode::core::translate_result(result)));
            return PSM_ERR_PARSE;
        }

        /*
         * Bereits im Zielformat: Die Converter melden das explizit.
         * Eine bytegleiche Kopie ist fuer SAF-Workflows hilfreicher als
         * eine Fehlermeldung.
         */
        if ((to_binary &&
             result == bgcode::core::EResult::AlreadyBinarized) ||
            (! to_binary &&
             result == bgcode::core::EResult::InvalidMagicNumber)) {
            boost::filesystem::copy_file(
                input_path, output_path,
                boost::filesystem::copy_options::overwrite_existing);
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_gcode_suggested_name(psm_session *s, char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr || out_cap == 0)
            return PSM_ERR_INVALID_ARG;
        if (! s->result_is_current()) {
            s->set_error("Slice-Ergebnis ist nach einer Projektaenderung veraltet");
            return PSM_ERR_STALE_RESULT;
        }
        std::lock_guard<std::mutex> print_lock(s->print_mtx);
        if (! s->print)
            return PSM_ERR_BUSY;
        /* Print::output_filename wertet output_filename_format aus dem
         * Druckprofil aus - Modellname, Schichthoehe, Material, Drucker
         * und Druckzeit stehen darin bereits. Selbst zusammenbauen waere
         * Nachbau; siehe E-12. */
        std::string name = s->print->output_filename();
        if (name.empty())
            name = "print.gcode";
        const size_t n = std::min(out_cap - 1, name.size());
        std::memcpy(out, name.data(), n);
        out[n] = '\0';
        return PSM_OK;
    PSM_GUARD_END(s)
}

/* ------------------------------------------------------------------ */
/* Konfiguration                                                       */
/* ------------------------------------------------------------------ */

PSM_API psm_result psm_config_get(psm_session *s, const char *key, char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        if (key == nullptr || out == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (! s->config.has(key))
            return PSM_ERR_NOT_FOUND;
        /* Bei Zeichenketten den rohen Wert: opt_serialize maskiert
         * Zeilenumbrueche zu "\n", was in Start- und End-G-code die
         * ganze Sequenz in eine Zeile zwingt. */
        const Slic3r::ConfigOption *opt = s->config.option(key);
        if (const auto *str = dynamic_cast<const Slic3r::ConfigOptionString *>(opt))
            copy_str(out, out_cap, str->value);
        else
            copy_str(out, out_cap, s->config.opt_serialize(key));
        return PSM_OK;
    PSM_GUARD_END(s)
}

namespace {

/*
 * Welche Preset-Sammlung besitzt einen Parameter?
 *
 * PrusaSlicer teilt die Parameter fest auf Druck, Filament und Drucker
 * auf; die Listen dazu liefert Preset selbst. Ohne diese Zuordnung
 * wuesste psm_config_set nicht, in welches bearbeitete Preset der Wert
 * gehoert.
 */
Slic3r::PresetCollection *owning_collection(psm_session *s, const std::string &key)
{
    if (! s->presets)
        return nullptr;

    using Slic3r::Preset;
    static const std::set<std::string> print_keys(
        Preset::print_options().begin(),    Preset::print_options().end());
    static const std::set<std::string> filament_keys(
        Preset::filament_options().begin(), Preset::filament_options().end());
    static const std::set<std::string> printer_keys(
        Preset::printer_options().begin(),  Preset::printer_options().end());

    if (print_keys.count(key))    return &s->presets->prints;
    if (filament_keys.count(key)) return &s->presets->filaments;
    if (printer_keys.count(key))  return &s->presets->printers;
    return nullptr;
}

} /* namespace */

/*
 * Ein geaenderter Wert geht ins bearbeitete Preset seiner Sammlung, nicht
 * in eine losgeloeste Kopie. Nur so weiss PrusaSlicer, was gegenueber dem
 * gewaehlten Preset abweicht - und nur so kann die App beim Profilwechsel
 * fragen, statt die Aenderungen stillschweigend wegzuwerfen.
 *
 * s->config bleibt die zusammengesetzte flache Sicht; sie geht beim
 * Slicen an Print::apply und beantwortet psm_config_get.
 */
PSM_API psm_result psm_config_set(psm_session *s, const char *key, const char *value)
{
    PSM_GUARD_BEGIN(s)
        if (key == nullptr || value == nullptr)
            return PSM_ERR_INVALID_ARG;
        std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
        if (! s->config.has(key)) {
            s->set_error(std::string("unbekannter Parameter: ") + key);
            return PSM_ERR_NOT_FOUND;
        }

        /* set_deserialize_nothrow nimmt eine nicht-konstante Referenz -
         * der Kontext muss also ein benanntes Objekt sein. */
        Slic3r::ConfigSubstitutionContext subs(Slic3r::ForwardCompatibilitySubstitutionRule::Disable);

        Slic3r::PresetCollection *c = owning_collection(s, key);
        if (c != nullptr && c->get_edited_preset().config.has(key)) {
            /* Gegenstueck zum Lesen: eine Zeichenkette wird direkt
             * zugewiesen, damit echte Zeilenumbrueche erhalten bleiben.
             * set_deserialize wuerde "\n" als zwei Zeichen lesen. */
            Slic3r::ConfigOption *dst = c->get_edited_preset().config.option(key);
            if (auto *str = dynamic_cast<Slic3r::ConfigOptionString *>(dst)) {
                str->value = value;
                c->update_dirty();
                s->config = s->presets->full_config();
                ++s->config_revision;
                s->mark_design_changed();
                return PSM_OK;
            }
            if (! c->get_edited_preset().config.set_deserialize_nothrow(key, value, subs)) {
                s->set_error(std::string("ungueltiger Wert fuer ") + key + ": " + value);
                return PSM_ERR_INVALID_ARG;
            }
            c->update_dirty();
            s->config = s->presets->full_config();
            ++s->config_revision;
            s->mark_design_changed();
            return PSM_OK;
        }

        /* Ohne geladene Presets, und fuer die wenigen Werte ausserhalb der
         * drei Sammlungen (Projektkonfiguration), bleibt es beim direkten
         * Schreiben. */
        if (! s->config.set_deserialize_nothrow(key, value, subs)) {
            s->set_error(std::string("ungueltiger Wert fuer ") + key + ": " + value);
            return PSM_ERR_INVALID_ARG;
        }
        ++s->config_revision;
        s->mark_design_changed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API uint64_t psm_estimate_slice_memory(psm_session *s)
{
    if (s == nullptr)
        return 0;
    std::lock_guard<std::recursive_mutex> data_lock(s->data_mtx);
    /*
     * Gemessener Peak auf Android/x86_64 mit einem bemalten 2.025.646-
     * Dreieck-3MF: rund 2,1 GiB RSS. Neben Polygonen und Infill fallen bei
     * Multi-Material-Projekten insbesondere TriangleSelector- und
     * Segmentierungsdaten an. 1 KiB pro instanziertem Dreieck plus
     * Grundlast bildet diesen realen Worst Case absichtlich konservativ
     * ab. Ein zu niedriger Wert ist auf Mobilgeraeten kein Komfortfehler,
     * sondern fuehrt zur kommentarlosen Prozessbeendigung durch Android.
     */
    uint64_t tris = 0;
    for (const Slic3r::ModelObject *o : s->model().objects)
        for (const Slic3r::ModelVolume *v : o->volumes)
            if (v->is_model_part())
                tris += v->mesh().facets_count() * std::max<size_t>(1, o->instances.size());
    return 160ull * 1024 * 1024 + tris * 1024ull;
}

} /* extern "C" */
