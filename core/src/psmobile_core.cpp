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
#include <chrono>
#include <cstring>
#include <memory>
#include <string>

#include <boost/filesystem.hpp>

#include "libslic3r/libslic3r.h"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/FileReader.hpp"
#include "libslic3r/Utils.hpp"

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

void psm_session::teardown_print()
{
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
    for (Slic3r::ModelObject *o : s->model.objects)
        if (static_cast<psm_object_id>(o->id().id) == id)
            return o;
    return nullptr;
}

/* Sorgt dafuer, dass ein Objekt genau eine Instanz hat, und liefert sie. */
Slic3r::ModelInstance *first_instance(Slic3r::ModelObject *o)
{
    if (o->instances.empty())
        o->add_instance();
    return o->instances.front();
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
        s->model.clear_objects();
        s->teardown_print();
        s->state = PSM_STATE_IDLE;
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

        /* Bettmitte bestimmen: neue Objekte landen dort, so wie es
         * PrusaSlicer beim Laden auch macht. Ohne das klebt jedes Modell
         * im Ursprung, also in der vorderen linken Bettecke. */
        Slic3r::Vec2d bed_center(0.0, 0.0);
        try {
            const Slic3r::Points bedpts = Slic3r::get_bed_shape(s->config);
            if (bedpts.size() >= 3) {
                Slic3r::BoundingBoxf bb;
                for (const Slic3r::Point &p : bedpts)
                    bb.merge(Slic3r::Vec2d(Slic3r::unscale<double>(p.x()),
                                           Slic3r::unscale<double>(p.y())));
                bed_center = bb.center();
            }
        } catch (...) { /* ohne Bett bleibt es beim Ursprung */ }

        size_t written = 0;
        size_t total   = 0;
        for (Slic3r::ModelObject *src : loaded.objects) {
            Slic3r::ModelObject *dst = s->model.add_object(*src);
            Slic3r::ModelInstance *inst = first_instance(dst);

            /* Objekt um seinen eigenen Schwerpunkt zentrieren und dann
             * auf die Bettmitte setzen. */
            dst->center_around_origin(false);
            inst->set_offset(Slic3r::Vec3d(bed_center.x(), bed_center.y(),
                                           inst->get_offset().z()));
            dst->ensure_on_bed();
            if (out_ids != nullptr && written < out_ids_cap)
                out_ids[written++] = static_cast<psm_object_id>(dst->id().id);
            ++total;
        }

        if (out_count != nullptr)
            *out_count = total;

        emit_log(PSM_LOG_INFO, std::string("geladen: ") + path +
                               " (" + std::to_string(total) + " Objekte)");
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_remove(psm_session *s, psm_object_id id)
{
    PSM_GUARD_BEGIN(s)
        for (size_t i = 0; i < s->model.objects.size(); ++i) {
            if (static_cast<psm_object_id>(s->model.objects[i]->id().id) == id) {
                s->model.delete_object(i);
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
    return s->model.objects.size();
}

PSM_API psm_result psm_model_list(psm_session *s, psm_object_id *out_ids,
                                  size_t cap, size_t *out_count)
{
    PSM_GUARD_BEGIN(s)
        size_t n = 0;
        for (Slic3r::ModelObject *o : s->model.objects) {
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

PSM_API psm_result psm_model_set_position(psm_session *s, psm_object_id id, float x, float y, float z)
{
    PSM_GUARD_BEGIN(s)
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        first_instance(o)->set_offset(Slic3r::Vec3d(x, y, z));
        o->invalidate_bounding_box();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_rotation(psm_session *s, psm_object_id id, float rx, float ry, float rz)
{
    PSM_GUARD_BEGIN(s)
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        first_instance(o)->set_rotation(Slic3r::Vec3d(rx, ry, rz));
        o->invalidate_bounding_box();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_set_scale(psm_session *s, psm_object_id id, float sx, float sy, float sz)
{
    PSM_GUARD_BEGIN(s)
        if (sx <= 0.f || sy <= 0.f || sz <= 0.f)
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        first_instance(o)->set_scaling_factor(Slic3r::Vec3d(sx, sy, sz));
        o->invalidate_bounding_box();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_drop_to_bed(psm_session *s, psm_object_id id)
{
    PSM_GUARD_BEGIN(s)
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        o->ensure_on_bed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_scale_to_fit(psm_session *s, psm_object_id id, float size_mm)
{
    PSM_GUARD_BEGIN(s)
        if (size_mm <= 0.f)
            return PSM_ERR_INVALID_ARG;
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;

        const Slic3r::BoundingBoxf3 bb = o->raw_mesh_bounding_box();
        const Slic3r::Vec3d sz = bb.size();
        const double longest = std::max({ sz.x(), sz.y(), sz.z() });
        if (longest <= 0.0)
            return PSM_ERR_GENERIC;

        const double f = static_cast<double>(size_mm) / longest;
        first_instance(o)->set_scaling_factor(Slic3r::Vec3d(f, f, f));
        o->invalidate_bounding_box();
        o->ensure_on_bed();
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_model_duplicate(psm_session *s, psm_object_id id, psm_object_id *out_new_id)
{
    PSM_GUARD_BEGIN(s)
        Slic3r::ModelObject *o = find_object(s, id);
        if (o == nullptr) return PSM_ERR_NOT_FOUND;
        Slic3r::ModelObject *copy = s->model.add_object(*o);
        first_instance(copy);
        copy->ensure_on_bed();
        if (out_new_id != nullptr)
            *out_new_id = static_cast<psm_object_id>(copy->id().id);
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_arrange(psm_session *s, float gap_mm)
{
    PSM_GUARD_BEGIN(s)
        if (s->model.objects.empty())
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

        Slic3r::arrange_objects(s->model, bed, cfg);
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
        if (s->model.objects.empty()) {
            s->set_error("kein Objekt auf dem Bett");
            return PSM_ERR_INVALID_ARG;
        }

        s->join_worker();          /* alten Thread einsammeln */
        s->cancel_requested = false;
        s->progress_cb  = cb;
        s->progress_usr = user;
        s->state        = PSM_STATE_RUNNING;
        s->last_error.clear();
        s->stats = psm_slice_stats{};

        s->worker = std::thread([s]() {
            try {
                /* Nicht einfach zuweisen: der alte Print muss ueber
                 * teardown_print() abgebaut werden, sonst laeuft sein
                 * Destruktor in den alten Cancel-Callback. */
                s->teardown_print();
                s->print = std::make_unique<Slic3r::Print>();

                /* Rohzeiger fassen, damit der Callback nicht ueber
                 * s->print laeuft - das kann waehrend des Abbaus bereits
                 * null sein. */
                Slic3r::Print *print_ptr = s->print.get();

                s->print->set_status_callback(
                    [s](const Slic3r::PrintBase::SlicingStatus &st) {
                        if (st.percent >= 0 && s->progress_cb != nullptr) {
                            if (s->progress_cb(st.percent, st.text.c_str(), s->progress_usr) != 0)
                                s->cancel_requested = true;
                        }
                    });

                s->print->set_cancel_callback([s, print_ptr]() {
                    if (s->cancel_requested.load())
                        print_ptr->cancel();
                });

                for (Slic3r::ModelObject *mo : s->model.objects)
                    s->print->auto_assign_extruders(mo);

                s->print->apply(s->model, s->config);

                const std::string err = s->print->validate();
                if (! err.empty()) {
                    s->set_error(err);
                    s->state = PSM_STATE_FAILED;
                    s->cv.notify_all();
                    return;
                }
                if (s->print->empty()) {
                    s->set_error("nichts zu drucken - Objekte ausserhalb des Druckraums?");
                    s->state = PSM_STATE_FAILED;
                    s->cv.notify_all();
                    return;
                }

                s->print->process();

                if (s->cancel_requested.load()) {
                    s->state = PSM_STATE_CANCELLED;
                    s->cv.notify_all();
                    return;
                }

                /* G-Code in eine temporaere Datei schreiben. Nie in den RAM -
                 * siehe Speicherstrategie in docs/02-architektur.md. */
                const boost::filesystem::path tmp =
                    boost::filesystem::path(s->datadir) / "last.gcode";
                s->gcode_tmp_path = s->print->export_gcode(tmp.string(), nullptr, nullptr);

                const Slic3r::PrintStatistics &ps = s->print->print_statistics();
                s->stats.print_time_seconds = ps.normal_print_time_seconds;
                s->stats.filament_used_mm   = ps.total_used_filament;
                s->stats.filament_used_g    = ps.total_weight;
                s->stats.filament_cost      = ps.total_cost;
                s->stats.object_count       = static_cast<int32_t>(s->model.objects.size());

                /* Layerzahl und Bauhoehe kommen nicht aus PrintStatistics,
                 * sondern aus den geschnittenen Objekten. Die UI zeigt
                 * beides im Vorschau-Slider (M6). */
                size_t max_layers = 0;
                double max_z      = 0.0;
                for (const Slic3r::PrintObject *po : s->print->objects()) {
                    max_layers = std::max(max_layers, po->layer_count());
                    if (! po->layers().empty())
                        max_z = std::max(max_z, po->layers().back()->print_z);
                }
                s->stats.layer_count = static_cast<int32_t>(max_layers);
                s->stats.max_z       = static_cast<float>(max_z);

                s->state = PSM_STATE_DONE;
                emit_log(PSM_LOG_INFO, "Slicing fertig: " + s->gcode_tmp_path);
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
            default:                  return PSM_OK;
        }
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_slice_stats_get(psm_session *s, psm_slice_stats *out)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr)
            return PSM_ERR_INVALID_ARG;
        if (s->state.load() != PSM_STATE_DONE)
            return PSM_ERR_BUSY;
        *out = s->stats;
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_gcode_export(psm_session *s, const char *out_path)
{
    PSM_GUARD_BEGIN(s)
        if (out_path == nullptr)
            return PSM_ERR_INVALID_ARG;
        if (s->state.load() != PSM_STATE_DONE || s->gcode_tmp_path.empty()) {
            s->set_error("es liegt kein fertiger G-Code vor");
            return PSM_ERR_BUSY;
        }
        boost::filesystem::copy_file(s->gcode_tmp_path, out_path,
                                     boost::filesystem::copy_options::overwrite_existing);
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API psm_result psm_gcode_suggested_name(psm_session *s, char *out, size_t out_cap)
{
    PSM_GUARD_BEGIN(s)
        if (out == nullptr || out_cap == 0)
            return PSM_ERR_INVALID_ARG;
        if (! s->print) {
            s->set_error("es liegt kein Slice-Ergebnis vor");
            return PSM_ERR_BUSY;
        }
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
                return PSM_OK;
            }
            if (! c->get_edited_preset().config.set_deserialize_nothrow(key, value, subs)) {
                s->set_error(std::string("ungueltiger Wert fuer ") + key + ": " + value);
                return PSM_ERR_INVALID_ARG;
            }
            c->update_dirty();
            s->config = s->presets->full_config();
            return PSM_OK;
        }

        /* Ohne geladene Presets, und fuer die wenigen Werte ausserhalb der
         * drei Sammlungen (Projektkonfiguration), bleibt es beim direkten
         * Schreiben. */
        if (! s->config.set_deserialize_nothrow(key, value, subs)) {
            s->set_error(std::string("ungueltiger Wert fuer ") + key + ": " + value);
            return PSM_ERR_INVALID_ARG;
        }
        return PSM_OK;
    PSM_GUARD_END(s)
}

PSM_API uint64_t psm_estimate_slice_memory(psm_session *s)
{
    if (s == nullptr)
        return 0;
    /* Grobe Faustformel aus Messungen am Desktop: rund 350 Byte pro
     * Dreieck ueber den gesamten Slice-Lauf, plus Grundlast.
     * Wird in M2 gegen echte Messungen auf dem Geraet nachgezogen. */
    uint64_t tris = 0;
    for (const Slic3r::ModelObject *o : s->model.objects)
        for (const Slic3r::ModelVolume *v : o->volumes)
            if (v->is_model_part())
                tris += v->mesh().facets_count() * std::max<size_t>(1, o->instances.size());
    return 96ull * 1024 * 1024 + tris * 350ull;
}

} /* extern "C" */
