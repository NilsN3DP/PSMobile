#include "psmobile_core.h"
#include "psm_viewport.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
#include "psmobile_session.hpp"
#include "libslic3r/MultipleBeds.hpp"
#endif

namespace {

void require(bool value, const std::string &message)
{
    if (! value) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}

bool close_to(float actual, float expected, float epsilon = 0.0001f)
{
    return std::abs(actual - expected) <= epsilon;
}

/// Irgendeine vorhandene TrueType-Schrift des Systems.
///
/// Der Text-Test prueft, ob aus Buchstaben Geometrie wird - welche
/// Schrift das liefert, ist dafuer gleichgueltig. Ein fester Pfad waere
/// es aber nicht: /system/fonts gibt es nur auf Android, und auf iOS
/// scheiterte der Test deshalb an der Umgebung statt an der Sache.
const char *system_font()
{
    static const char *const kandidaten[] = {
        "/system/fonts/Roboto-Regular.ttf",              // Android
        "/System/Library/Fonts/Supplemental/Arial.ttf",  // iOS und macOS
        "/System/Library/Fonts/SFNSMono.ttf",            // macOS, aeltere Staende
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", // Linux
    };
    for (const char *pfad : kandidaten) {
        if (std::filesystem::exists(pfad)) return pfad;
    }
    return kandidaten[0];
}

#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
Slic3r::ModelObject *test_object(psm_session *session, psm_object_id id)
{
    for (Slic3r::ModelObject *candidate : session->model().objects)
        if (static_cast<psm_object_id>(candidate->id().id) == id)
            return candidate;
    return nullptr;
}

psm_paint_options paint_options_for(psm_session *session,
                                    psm_object_id id,
                                    size_t instance_index,
                                    size_t facet_index,
                                    psm_paint_mode mode,
                                    psm_paint_shape shape,
                                    float radius_mm,
                                    float fill_angle_deg = 30.f)
{
    Slic3r::ModelObject *object = test_object(session, id);
    require(object != nullptr && instance_index < object->instances.size() &&
                ! object->volumes.empty(),
            "paint fixture object and instance exist");
    const Slic3r::ModelVolume *volume = object->volumes.front();
    const indexed_triangle_set &its = volume->mesh().its;
    require(facet_index < its.indices.size(), "paint fixture facet exists");
    const Slic3r::Vec3i32 &face = its.indices[facet_index];
    const Slic3r::Vec3d local_hit =
        (its.vertices[face(0)].cast<double>() +
         its.vertices[face(1)].cast<double>() +
         its.vertices[face(2)].cast<double>()) / 3.0;
    const Slic3r::Transform3d local_to_world =
        object->instances[instance_index]->get_matrix() *
        volume->get_matrix();
    const Slic3r::Vec3d hit = local_to_world * local_hit;

    psm_paint_options options{};
    options.version = PSM_PAINT_OPTIONS_VERSION_1;
    options.mode = mode;
    options.shape = shape;
    options.radius_mm = radius_mm;
    options.fill_angle_deg = fill_angle_deg;
    options.split_triangles = 0;
    options.hit_position[0] = static_cast<float>(hit.x());
    options.hit_position[1] = static_cast<float>(hit.y());
    options.hit_position[2] = static_cast<float>(hit.z());
    return options;
}
#endif

} // namespace

int main(int argc, char **argv)
{
    require(argc == 6,
            "usage: psm_contract_tests DATADIR RESDIR MODEL PROJECT3MF "
            "INSTALLED_PROFILE_PROJECT3MF");
    require(PSM_ABI_VERSION == 8, "header ABI version includes object config exports");
    require(psm_abi_version() == 8, "runtime ABI version includes object config exports");

    std::filesystem::create_directories(argv[1]);
    psm_session *session = psm_session_create(argv[1], argv[2]);
    require(session != nullptr,
            std::string("session create: ") + psm_last_error(nullptr));

    psm_object_id id = PSM_INVALID_ID;
    size_t count = 0;
    require(psm_model_load(session, argv[3], &id, 1, &count) == PSM_OK,
            std::string("load cube: ") + psm_last_error(session));
    require(count == 1 && id != PSM_INVALID_ID, "one object id");
    require(psm_bed_count(session) == 1, "one initial bed");
    require(psm_bed_object_count(session, 0) == 1,
            "object belongs to first bed");

    psm_object_info info{};
    require(psm_model_info(session, id, &info) == PSM_OK, "object info");
    require(info.triangle_count == 12, "cube has twelve triangles");

    /*
     * Objektwerte sind echte lokale Overrides: ohne Override wird die
     * globale Konfiguration gelesen, ein gueltiger Write erzeugt genau
     * einen Verlaufsschritt, und Reset stellt die Vererbung wieder her.
     */
    {
        constexpr const char *key = "fill_density";
        char inherited[64]{};
        char value[64]{};
        char zero_cap = 'x';
        require(psm_config_get(session, key, inherited, sizeof(inherited)) == PSM_OK,
                "read inherited global object setting");
        require(psm_history_clear(session) == PSM_OK,
                "clear history before object config contract");
        const uint64_t design_before = psm_design_revision(session);
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
        const uint64_t config_before = session->config_revision;
#endif

        require(psm_object_config_is_overridden(session, id, key) == 0,
                "object config starts inherited");
        require(psm_object_config_get(session, id, key, value, sizeof(value)) == PSM_OK &&
                    std::string(value) == inherited,
                "object config reads inherited global value");
        require(psm_object_config_get(session, id, key, &zero_cap, 0) == PSM_OK &&
                    zero_cap == 'x',
                "object config follows global zero-capacity read convention");
        require(psm_object_config_get(nullptr, id, key, value, sizeof(value)) ==
                    PSM_ERR_INVALID_ARG &&
                    psm_object_config_get(session, id, nullptr, value, sizeof(value)) ==
                    PSM_ERR_INVALID_ARG &&
                    psm_object_config_get(session, id, key, nullptr, sizeof(value)) ==
                    PSM_ERR_INVALID_ARG,
                "object config get rejects invalid session key and buffer");
        require(psm_object_config_is_overridden(nullptr, id, key) == -1 &&
                    psm_object_config_is_overridden(session, id, nullptr) == -1,
                "object config override query rejects invalid session and key");
        require(psm_object_config_get(session, PSM_INVALID_ID, key, value, sizeof(value)) ==
                    PSM_ERR_NOT_FOUND &&
                    psm_object_config_set(session, PSM_INVALID_ID, key, "35%") ==
                    PSM_ERR_NOT_FOUND &&
                    psm_object_config_reset(session, PSM_INVALID_ID, key) ==
                    PSM_ERR_NOT_FOUND &&
                    psm_object_config_is_overridden(session, PSM_INVALID_ID, key) == -1,
                "object config rejects missing object without mutation");
        require(psm_object_config_set(session, id, "no_such_object_option", "1") ==
                    PSM_ERR_NOT_FOUND &&
                    psm_object_config_set(session, id, key, "not-a-density") ==
                    PSM_ERR_INVALID_ARG,
                "object config rejects unknown key and invalid value without mutation");
        require(psm_object_config_reset(session, id, key) == PSM_OK &&
                    psm_history_undo_count(session) == 0 &&
                    psm_design_revision(session) == design_before,
                "reset of inherited object config is a successful no-op");
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
        require(session->config_revision == config_before,
                "failed and no-op object config calls keep config revision");
#endif

        require(psm_object_config_set(session, id, key, "35%") == PSM_OK &&
                    psm_object_config_get(session, id, key, value, sizeof(value)) == PSM_OK &&
                    std::string(value) == "35%" &&
                    psm_object_config_is_overridden(session, id, key) == 1,
                "object config set creates a local validated override");
        require(psm_history_undo_count(session) == 1 &&
                    psm_design_revision(session) == design_before + 1,
                "object config set creates one checkpoint and invalidation");
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
        require(session->config_revision == config_before + 1,
                "object config set increments config revision once");
#endif
        require(psm_object_config_set(session, id, key, "35%") == PSM_OK &&
                    psm_history_undo_count(session) == 1 &&
                    psm_design_revision(session) == design_before + 1,
                "identical object config set is a successful no-op");
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
        require(session->config_revision == config_before + 1,
                "identical object config set keeps config revision");
#endif
        require(psm_object_config_reset(session, id, key) == PSM_OK &&
                    psm_object_config_is_overridden(session, id, key) == 0 &&
                    psm_object_config_get(session, id, key, value, sizeof(value)) == PSM_OK &&
                    std::string(value) == inherited,
                "object config reset restores inherited global value");
        require(psm_history_undo_count(session) == 2 &&
                    psm_design_revision(session) == design_before + 2,
                "object config reset creates one checkpoint and invalidation");
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
        require(session->config_revision == config_before + 2,
                "object config reset increments config revision once");

        const uint64_t undo_design_before = psm_design_revision(session);
        const uint64_t undo_config_before = session->config_revision;
        require(psm_history_undo(session) == PSM_OK &&
                    psm_object_config_is_overridden(session, id, key) == 1 &&
                    psm_object_config_get(session, id, key, value, sizeof(value)) == PSM_OK &&
                    std::string(value) == "35%" &&
                    psm_design_revision(session) == undo_design_before + 1 &&
                    session->config_revision == undo_config_before + 1,
                "undo restores object override and advances both revisions");
        require(psm_history_redo(session) == PSM_OK &&
                    psm_object_config_is_overridden(session, id, key) == 0 &&
                    psm_object_config_get(session, id, key, value, sizeof(value)) == PSM_OK &&
                    std::string(value) == inherited &&
                    psm_design_revision(session) == undo_design_before + 2 &&
                    session->config_revision == undo_config_before + 2,
                "redo restores inherited object config and advances both revisions");

        require(psm_history_clear(session) == PSM_OK,
                "clear object config history before geometry-only restoration");
        const uint64_t geometry_design_before = psm_design_revision(session);
        const uint64_t geometry_config_before = session->config_revision;
        psm_object_info geometry_info{};
        require(psm_model_info(session, id, &geometry_info) == PSM_OK &&
                    psm_model_set_position(session, id,
                                           geometry_info.position[0] + 0.25f,
                                           geometry_info.position[1],
                                           geometry_info.position[2]) == PSM_OK &&
                    psm_history_undo(session) == PSM_OK &&
                    psm_history_redo(session) == PSM_OK &&
                    psm_history_undo(session) == PSM_OK,
                "restore geometry through undo and redo");
        require(psm_design_revision(session) == geometry_design_before + 4 &&
                    session->config_revision == geometry_config_before,
                "geometry-only history restores invalidate design without config revision");
        require(psm_history_clear(session) == PSM_OK,
                "clear geometry-only history after revision contract");
#endif
    }

    /*
     * Ein zweiter Import darf nicht im ersten stecken. Vorher landete
     * jedes geladene Objekt auf der Bettmitte; im Viewport sah man dann
     * nur noch ein Objekt und hielt den Import fuer fehlgeschlagen.
     */
    {
        psm_object_id second = PSM_INVALID_ID;
        size_t second_count = 0;
        require(psm_model_load(session, argv[3], &second, 1, &second_count) == PSM_OK,
                std::string("load cube twice: ") + psm_last_error(session));
        require(second_count == 1 && second != id, "second import is its own object");

        psm_object_info a{};
        psm_object_info b{};
        require(psm_model_info(session, id, &a) == PSM_OK, "first object info");
        require(psm_model_info(session, second, &b) == PSM_OK, "second object info");

        const bool apart_in_x = a.bbox_max[0] <= b.bbox_min[0] ||
                                b.bbox_max[0] <= a.bbox_min[0];
        const bool apart_in_y = a.bbox_max[1] <= b.bbox_min[1] ||
                                b.bbox_max[1] <= a.bbox_min[1];
        require(apart_in_x || apart_in_y,
                "a second import gets its own free spot instead of the bed centre");
        require(b.outside_bed == 0, "the free spot stays on the bed");

        require(psm_model_remove(session, second) == PSM_OK, "remove second import");
        require(psm_model_count(session) == 1, "back to one object");
    }

    /*
     * Ist das Bett voll, muessen weitere Objekte auf das naechste
     * ausweichen statt sich auf der Mitte zu stapeln. Der Wuerfel ist
     * 20 mm gross, auf ein MK4-Bett passen davon ueber hundert - deshalb
     * hier so lange laden, bis ein zweites Bett entsteht.
     */
    {
        const size_t before_beds = psm_bed_count(session);
        require(before_beds == 1, "one bed before the flood");
        size_t loaded_total = 1;
        for (int i = 0; i < 200 && psm_bed_count(session) == before_beds; ++i) {
            psm_object_id extra = PSM_INVALID_ID;
            size_t extra_count = 0;
            require(psm_model_load(session, argv[3], &extra, 1, &extra_count) == PSM_OK,
                    std::string("flood the bed: ") + psm_last_error(session));
            ++loaded_total;
        }
        require(psm_bed_count(session) > before_beds,
                "a full bed spills onto the next one instead of stacking");
        require(psm_bed_object_count(session, 1) >= 1,
                "the second bed actually holds the overflow");
        require(psm_bed_object_count(session, 0) + psm_bed_object_count(session, 1) ==
                    loaded_total,
                "no object is lost while spilling");

        // Aufraeumen: der Rest des Vertragstests erwartet ein Bett mit
        // genau einem Objekt.
        require(psm_bed_remove(session, 1) == PSM_OK, "remove the overflow bed");
        require(psm_bed_select(session, 0) == PSM_OK, "back to the first bed");
        while (psm_model_count(session) > 1) {
            psm_object_id ids[256]{};
            size_t n = 0;
            require(psm_model_list(session, ids, 256, &n) == PSM_OK, "list objects");
            require(n > 1, "more than one object to clean up");
            require(psm_model_remove(session, ids[n - 1]) == PSM_OK, "clean up flood");
        }
        require(psm_model_count(session) == 1, "one object left after cleanup");
        require(psm_bed_count(session) == 1, "one bed left after cleanup");
    }

    require(psm_model_volume_count(session, id) == 1,
            "STL creates one model volume");
    psm_volume_info volume{};
    require(psm_model_volume_info(session, id, 0, &volume) == PSM_OK,
            "volume info");
    require(volume.type == PSM_VOLUME_MODEL_PART &&
            volume.triangle_count == 12,
            "volume type and triangle count");

    /*
     * Kaputte Eingaben dürfen das offene Projekt nicht leeren oder den
     * Prozess beenden. Die Android-Dateidialoge zeigen diesen Fehler nur
     * an und lassen den bisherigen Stand weiterbearbeiten.
     */
    const std::filesystem::path invalid_model =
        std::filesystem::path(argv[1]) / "invalid-input.stl";
    const std::filesystem::path invalid_project =
        std::filesystem::path(argv[1]) / "invalid-input.3mf";
    {
        std::ofstream broken(invalid_model);
        broken << "definitiv keine STL";
    }
    {
        std::ofstream broken(invalid_project);
        broken << "definitiv kein 3MF-Archiv";
    }
    psm_object_id invalid_ids[2]{};
    size_t invalid_count = 0;
    require(psm_model_load(
                session, invalid_model.string().c_str(),
                invalid_ids, 2, &invalid_count) != PSM_OK &&
            psm_model_count(session) == 1,
            "invalid model import preserves current project");
    psm_project_import_info invalid_import{};
    require(psm_project_load_3mf(
                session, invalid_project.string().c_str(),
                &invalid_import) != PSM_OK &&
            psm_model_count(session) == 1,
            "invalid project import preserves current project");

    /*
     * Mehrere Teiloperationen muessen fuer Touch und Zahlenfelder einen
     * einzigen, beschrifteten Verlaufseintrag ergeben.
     */
    const psm_object_info original = info;
    require(psm_history_clear(session) == PSM_OK, "clear history");
    require(psm_history_begin(session, "Kombinierte Transformation") == PSM_OK,
            "begin grouped history");
    constexpr float half_pi = 1.57079632679f;
    require(psm_model_set_position(session, id,
                                   original.position[0] + 5.f,
                                   original.position[1],
                                   original.position[2]) == PSM_OK,
            "set grouped position");
    require(psm_model_set_rotation(session, id, 0.f, 0.f, half_pi) == PSM_OK,
            "set radians");
    require(psm_history_end(session) == PSM_OK, "end grouped history");
    require(psm_history_undo_count(session) == 1,
            "grouped actions create one undo entry");
    char history_label[128]{};
    require(psm_history_undo_label(session, history_label,
                                   sizeof(history_label)) == PSM_OK &&
            std::string(history_label) == "Kombinierte Transformation",
            "undo exposes grouped label");

    require(psm_model_info(session, id, &info) == PSM_OK,
            "object info after rotation");
    require(close_to(info.rotation[2], half_pi),
            "C ABI rotation stays in radians");
    require(close_to(info.position[0], original.position[0] + 5.f),
            "grouped position applied");
    require(psm_history_undo(session) == PSM_OK, "undo grouped transform");
    require(psm_model_info(session, id, &info) == PSM_OK,
            "object info after undo");
    require(close_to(info.position[0], original.position[0]) &&
            close_to(info.rotation[2], original.rotation[2]),
            "undo restores position and rotation");
    require(psm_history_redo_count(session) == 1,
            "undo exposes redo");
    require(psm_history_redo_label(session, history_label,
                                   sizeof(history_label)) == PSM_OK &&
            std::string(history_label) == "Kombinierte Transformation",
            "redo exposes grouped label");
    require(psm_history_redo(session) == PSM_OK, "redo grouped transform");

    require(psm_extruder_count(session) >= 1, "at least one extruder");
    require(psm_model_extruder_get(session, id) == 0,
            "object initially inherits extruder");
    require(psm_model_extruder_set(session, id, 1) == PSM_OK,
            "assign object extruder");
    require(psm_model_extruder_get(session, id) == 1,
            "object extruder persisted");
    require(psm_history_undo(session) == PSM_OK,
            "object extruder is undoable");
    require(psm_model_extruder_get(session, id) == 0,
            "undo restores inherited object extruder");
    require(psm_history_redo(session) == PSM_OK,
            "redo object extruder");
    require(psm_model_volume_extruder_set(session, id, 0, 1) == PSM_OK,
            "assign volume extruder");
    require(psm_model_volume_info(session, id, 0, &volume) == PSM_OK &&
            volume.explicit_extruder == 1,
            "volume stores explicit extruder");

    require(psm_model_scale_to_fit(session, id, 10.f) == PSM_OK,
            "scale transformed longest edge");
    require(psm_model_fit_to_bed(session, id, 0.9f) == PSM_OK,
            "fit object to bed");

    /*
     * Erweiterte Modellwerkzeuge laufen auf einer Kopie. Dadurch bleibt
     * der nachfolgende Slice-Vertrag klein und reproduzierbar.
     */
    psm_object_id tool_object = PSM_INVALID_ID;
    require(psm_model_duplicate(session, id, &tool_object) == PSM_OK,
            "duplicate for extended tools");
    size_t modifier_index = 0;
    require(psm_model_add_primitive_volume(
                session, tool_object, PSM_VOLUME_SUPPORT_BLOCKER,
                PSM_PRIMITIVE_BOX, 3.f, 4.f, 5.f,
                &modifier_index) == PSM_OK,
            std::string("add support blocker: ") +
                psm_last_error(session));
    require(psm_model_volume_count(session, tool_object) == 2,
            "support blocker appears as second volume");
    require(psm_model_volume_info(
                session, tool_object, modifier_index, &volume) == PSM_OK &&
            volume.type == PSM_VOLUME_SUPPORT_BLOCKER,
            "support blocker keeps its role");
    require(psm_model_remove_volume(
                session, tool_object, modifier_index) == PSM_OK &&
            psm_model_volume_count(session, tool_object) == 1,
            "remove generated modifier");

    require(psm_model_paint_facet(
                session, tool_object, 0, 0,
                PSM_PAINT_SUPPORT, 1) == PSM_OK,
            std::string("paint support facet: ") +
                psm_last_error(session));
    require(psm_model_paint_brush(
                session, tool_object, 0, 0,
                PSM_PAINT_SEAM, 1, 4.f) == PSM_OK,
            std::string("paint connected touch brush: ") +
                psm_last_error(session));
    require(psm_model_clear_paint(
                session, tool_object, PSM_PAINT_SUPPORT) == PSM_OK,
            "clear support painting");
    require(psm_model_clear_paint(
                session, tool_object, PSM_PAINT_SEAM) == PSM_OK,
            "clear seam painting");

#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
    /*
     * Der neue Malvertrag reicht Treffer und Instanz nur an Prusas
     * TriangleSelector weiter. Persistiert wird weiterhin ausschliesslich
     * die FacetsAnnotation am Volumen, also fuer alle Kopien gemeinsam.
     */
    {
        const std::filesystem::path paint_data =
            std::filesystem::path(argv[1]) / "paint-options-data";
        psm_session *paint_session = psm_session_create(
            paint_data.string().c_str(), argv[2]);
        require(paint_session != nullptr, "create isolated paint session");
        psm_object_id paint_id = PSM_INVALID_ID;
        size_t paint_loaded = 0;
        require(psm_model_load(
                    paint_session, argv[3], &paint_id, 1,
                    &paint_loaded) == PSM_OK &&
                    paint_loaded == 1,
                "load paint fixture");
        require(psm_model_set_instances(paint_session, paint_id, 2) == PSM_OK,
                "paint fixture has two instances");

        psm_paint_options instance_probe = paint_options_for(
            paint_session, paint_id, 1, 0,
            PSM_PAINT_MODE_BRUSH, PSM_PAINT_SHAPE_SPHERE, 1.f);
        require(psm_model_paint_apply(
                    paint_session, paint_id, 1, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &instance_probe) == PSM_OK &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_SUPPORT) > 0,
                "small brush uses the hit instance transform");
        require(psm_model_clear_paint(
                    paint_session, paint_id, PSM_PAINT_SUPPORT) == PSM_OK,
                "clear instance-transform probe");

        psm_paint_options sphere = paint_options_for(
            paint_session, paint_id, 1, 0,
            PSM_PAINT_MODE_BRUSH, PSM_PAINT_SHAPE_SPHERE, 100.f);
        psm_paint_options bad = sphere;
        bad.version = 99;
        require(psm_model_paint_apply(
                    paint_session, paint_id, 1, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &bad) == PSM_ERR_INVALID_ARG,
                "unknown paint options version is rejected");
        require(psm_model_paint_apply(
                    paint_session, paint_id, 2, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &sphere) == PSM_ERR_INVALID_ARG,
                "paint instance scope is validated");
        require(psm_model_paint_apply(
                    paint_session, paint_id, 1, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &sphere) == PSM_OK,
                std::string("sphere brush on second instance: ") +
                    psm_last_error(paint_session));
        const size_t sphere_count = psm_model_paint_count(
            paint_session, paint_id, PSM_PAINT_SUPPORT);
        require(sphere_count > 2,
                "sphere reaches facets independent of camera facing");

        require(psm_model_clear_paint(
                    paint_session, paint_id, PSM_PAINT_SUPPORT) == PSM_OK,
                "clear sphere fixture");
        psm_paint_options circle = paint_options_for(
            paint_session, paint_id, 1, 0,
            PSM_PAINT_MODE_BRUSH, PSM_PAINT_SHAPE_CIRCLE, 100.f);
        require(psm_model_paint_apply(
                    paint_session, paint_id, 1, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &circle) == PSM_OK,
                "circle brush on second instance");
        const size_t circle_count = psm_model_paint_count(
            paint_session, paint_id, PSM_PAINT_SUPPORT);
        require(circle_count > 0 && circle_count < sphere_count,
                "circle excludes backward-facing facets unlike sphere");

        require(psm_model_clear_paint(
                    paint_session, paint_id, PSM_PAINT_SUPPORT) == PSM_OK,
                "clear circle fixture");
        psm_paint_options capsule = paint_options_for(
            paint_session, paint_id, 1, 0,
            PSM_PAINT_MODE_BRUSH, PSM_PAINT_SHAPE_SPHERE, 2.f);
        capsule.has_previous_position = 1;
        capsule.previous_position[0] = capsule.hit_position[0] + 4.f;
        capsule.previous_position[1] = capsule.hit_position[1];
        capsule.previous_position[2] = capsule.hit_position[2];
        require(psm_model_paint_apply(
                    paint_session, paint_id, 1, 0, 0,
                    PSM_PAINT_SUPPORT, 1, &capsule) == PSM_OK &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_SUPPORT) > 0,
                "two brush points exercise Prusa's capsule cursor");
        require(psm_model_clear_paint(
                    paint_session, paint_id, PSM_PAINT_SUPPORT) == PSM_OK,
                "clear capsule fixture");

        psm_paint_options smart = paint_options_for(
            paint_session, paint_id, 0, 0,
            PSM_PAINT_MODE_SMART_FILL, PSM_PAINT_SHAPE_CIRCLE, 5.f, 1.f);
        require(psm_model_paint_apply(
                    paint_session, paint_id, 0, 0, 0,
                    PSM_PAINT_SUPPORT, 2, &smart) == PSM_OK &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_SUPPORT) == 2,
                "smart fill applies only the coplanar cube face at one degree");
        require(psm_model_paint_apply(
                    paint_session, paint_id, 0, 0, 0,
                    PSM_PAINT_SEAM, 1, &smart) == PSM_ERR_INVALID_ARG,
                "seam exposes no invented smart fill");

        psm_paint_options bucket = smart;
        bucket.mode = PSM_PAINT_MODE_BUCKET_FILL;
        require(psm_model_paint_apply(
                    paint_session, paint_id, 0, 0, 0,
                    PSM_PAINT_MMU, 3, &bucket) == PSM_OK &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_MMU) == 2,
                "MMU bucket fill applies the connected coplanar face");
        require(psm_model_paint_count(
                    paint_session, paint_id, PSM_PAINT_SEAM) == 0,
                "fill writes only the requested annotation");
        require(psm_model_clear_paint(
                    paint_session, paint_id, PSM_PAINT_SUPPORT) == PSM_OK &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_SUPPORT) == 0 &&
                    psm_model_paint_count(
                        paint_session, paint_id, PSM_PAINT_MMU) == 2,
                "clear removes only the requested paint tool");
        psm_session_destroy(paint_session);
    }
#endif

    psm_object_info layer_object_info{};
    require(psm_model_info(session, tool_object, &layer_object_info) == PSM_OK,
            "read object height for variable layer profile");
    const double layer_object_height =
        layer_object_info.bbox_max[2] - layer_object_info.bbox_min[2];
    const double layer_profile[] = {
        0.0,                         0.20,
        layer_object_height * 0.5,   0.10,
        layer_object_height,         0.28
    };
    require(psm_model_layer_profile_set(
                session, tool_object, layer_profile, 3) == PSM_OK &&
            psm_model_layer_profile_count(session, tool_object) == 3,
            "store variable layer profile");
    double profile_z = -1.0;
    double profile_height = -1.0;
    require(psm_model_layer_profile_at(
                session, tool_object, 1,
                &profile_z, &profile_height) == PSM_OK &&
            std::abs(profile_z - layer_object_height * 0.5) < 0.0001 &&
            std::abs(profile_height - 0.10) < 0.0001,
            "read variable layer profile");
    psm_layer_visualization_info layer_visualization{};
    require(psm_viewport_layer_visualization_info(
                session, tool_object, &layer_visualization) == 1 &&
            layer_visualization.texture_width > 0 &&
            layer_visualization.texture_height > 0 &&
            layer_visualization.texture_cells > 0 &&
            close_to(layer_visualization.object_max_z,
                     layer_object_height, 0.01f) &&
            close_to(layer_visualization.min_layer_height, 0.10f) &&
            close_to(layer_visualization.max_layer_height, 0.28f),
            "stored layer profile activates real renderer texture data");
    require(psm_model_layer_profile_set(
                session, tool_object, nullptr, 0) == PSM_OK &&
            psm_viewport_layer_visualization_info(
                session, tool_object, &layer_visualization) == 0,
            "clearing layer profile removes renderer visualization data");

    require(psm_model_colour_set(
                session, tool_object, "#3366CC") == PSM_OK,
            std::string("set object colour: ") +
                psm_last_error(session));
    char object_colour[64]{};
    require(psm_model_colour_get(
                session, tool_object,
                object_colour, sizeof(object_colour)) == PSM_OK &&
            std::string(object_colour).find("3366CC") != std::string::npos,
            "read object colour");
    int32_t wipe_infill = 0;
    int32_t wipe_objects = 0;
    require(psm_model_wipe_set(
                session, tool_object, 1, 1) == PSM_OK &&
            psm_model_wipe_get(
                session, tool_object,
                &wipe_infill, &wipe_objects) == PSM_OK &&
            wipe_infill == 1 && wipe_objects == 1,
            "object wipe options roundtrip");

    require(psm_model_lay_on_facet(
                session, tool_object, 0, 0) == PSM_OK,
            std::string("lay on selected facet: ") +
                psm_last_error(session));
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
    /*
     * Ein Oberflaechentreffer benennt eine konkrete Instanz. Der alte
     * ABI-Einstieg verlor diese Zahl und drehte immer Kopie 0. Die
     * ungleichmaessige Skalierung ist wichtig: Bei einem Wuerfel saehe
     * eine Vierteldrehung gleich aus und koennte den Fehler verdecken.
     */
    {
        const std::filesystem::path surface_data =
            std::filesystem::path(argv[1]) / "surface-instance-data";
        psm_session *surface_session = psm_session_create(
            surface_data.string().c_str(), argv[2]);
        require(surface_session != nullptr,
                "create isolated surface-lay session");
        psm_object_id surface_object = PSM_INVALID_ID;
        size_t surface_count = 0;
        require(psm_model_load(
                    surface_session, argv[3], &surface_object, 1,
                    &surface_count) == PSM_OK &&
                surface_count == 1,
                "load object for instance-specific surface lay");
        require(psm_model_set_scale(
                    surface_session, surface_object,
                    1.f, 1.5f, 2.f) == PSM_OK,
                std::string("make surface-lay object asymmetric: ") +
                    psm_last_error(surface_session));
        require(psm_model_drop_to_bed(
                    surface_session, surface_object) == PSM_OK,
                "put asymmetric source instance on bed");
        require(psm_model_set_instances(
                    surface_session, surface_object, 2) == PSM_OK,
                "create two instances for surface lay");

        size_t side_facet = 0;
        Slic3r::Transform3d first_before =
            Slic3r::Transform3d::Identity();
        Slic3r::Transform3d second_before =
            Slic3r::Transform3d::Identity();
        {
            std::lock_guard<std::recursive_mutex> data_lock(
                surface_session->data_mtx);
            const Slic3r::ModelObject *object = nullptr;
            for (const Slic3r::ModelObject *candidate :
                 surface_session->model().objects) {
                if (static_cast<psm_object_id>(candidate->id().id) ==
                    surface_object) {
                    object = candidate;
                    break;
                }
            }
            require(object != nullptr && object->instances.size() == 2,
                    "surface-lay fixture exposes two internal instances");
            const auto &its = object->volumes.front()->mesh().its;
            bool found_side = false;
            for (size_t index = 0; index < its.indices.size(); ++index) {
                const Slic3r::Vec3i32 &face = its.indices[index];
                const Slic3r::Vec3d a =
                    its.vertices[face(0)].cast<double>();
                const Slic3r::Vec3d b =
                    its.vertices[face(1)].cast<double>();
                const Slic3r::Vec3d c =
                    its.vertices[face(2)].cast<double>();
                const Slic3r::Vec3d normal = (b - a).cross(c - a);
                if (normal.squaredNorm() > 1e-18 &&
                    std::abs(normal.normalized().z()) < 0.1) {
                    side_facet = index;
                    found_side = true;
                    break;
                }
            }
            require(found_side, "asymmetric fixture has a side facet");
            first_before = object->instances[0]->get_matrix();
            second_before = object->instances[1]->get_matrix();
        }

        require(psm_model_lay_on_facet_instance(
                    surface_session, surface_object,
                    2, 0, side_facet) == PSM_ERR_INVALID_ARG,
                "surface lay rejects an unknown instance index");
        require(psm_model_lay_on_facet_instance(
                    surface_session, surface_object,
                    1, 0, side_facet) == PSM_OK,
                std::string("lay hit instance on selected facet: ") +
                    psm_last_error(surface_session));

        {
            std::lock_guard<std::recursive_mutex> data_lock(
                surface_session->data_mtx);
            const Slic3r::ModelObject *object = nullptr;
            for (const Slic3r::ModelObject *candidate :
                 surface_session->model().objects) {
                if (static_cast<psm_object_id>(candidate->id().id) ==
                    surface_object) {
                    object = candidate;
                    break;
                }
            }
            require(object != nullptr && object->instances.size() == 2,
                    "surface-lay result retains both instances");
            require(object->instances[0]->get_matrix().isApprox(
                        first_before, 1e-12),
                    "laying instance one leaves instance zero unchanged");
            require(! object->instances[1]->get_matrix().isApprox(
                        second_before, 1e-6),
                    "laying instance one changes that instance");

            const Slic3r::ModelVolume *volume = object->volumes.front();
            const auto &its = volume->mesh().its;
            const Slic3r::Vec3i32 &face = its.indices[side_facet];
            const Slic3r::Vec3d a =
                its.vertices[face(0)].cast<double>();
            const Slic3r::Vec3d b =
                its.vertices[face(1)].cast<double>();
            const Slic3r::Vec3d c =
                its.vertices[face(2)].cast<double>();
            Slic3r::Vec3d normal = (b - a).cross(c - a);
            const Slic3r::Transform3d local_to_world =
                object->instances[1]->get_matrix() *
                volume->get_matrix();
            normal =
                (local_to_world.linear().inverse().transpose() * normal)
                    .normalized();
            require(normal.isApprox(-Slic3r::Vec3d::UnitZ(), 1e-6),
                    "the hit facet of instance one faces the bed");
            require(std::abs(
                        object->instance_bounding_box(1).min.z()) < 1e-6,
                    "the hit instance alone rests on the bed");
        }
        psm_session_destroy(surface_session);
    }
#endif
    uint32_t simplify_before = 0;
    uint32_t simplify_after = 0;
    require(psm_model_simplify(
                session, tool_object, 0.75f,
                &simplify_before, &simplify_after) == PSM_OK &&
            simplify_before == 12 &&
            simplify_after >= 4 &&
            simplify_after <= simplify_before,
            "simplify reports bounded triangle count");

    psm_object_id split_ids[8]{};
    size_t split_count = 0;
    require(psm_model_split_objects(
                session, tool_object, split_ids, 8,
                &split_count) == PSM_ERR_UNSUPPORTED,
            "single shell refuses split into objects");
    require(psm_model_split_volumes(
                session, tool_object,
                &split_count) == PSM_ERR_UNSUPPORTED,
            "single shell refuses split into volumes");

    size_t text_volume = 0;
    require(psm_model_add_text_volume(
                session, tool_object, "PSMobile",
                system_font(),
                5.f, 0.8f, PSM_VOLUME_MODEL_PART,
                &text_volume) == PSM_OK,
            std::string("create embossed text: ") +
                psm_last_error(session));
    const std::filesystem::path svg_source =
        std::filesystem::path(argv[1]) / "emboss-source.svg";
    {
        std::ofstream svg(svg_source);
        svg << "<svg xmlns=\"http://www.w3.org/2000/svg\" "
               "width=\"10\" height=\"8\" viewBox=\"0 0 10 8\">"
               "<path d=\"M1 1 H9 V7 H1 Z\"/></svg>";
    }
    size_t svg_volume = 0;
    require(psm_model_add_svg_volume(
                session, tool_object,
                svg_source.string().c_str(), 0.8f,
                PSM_VOLUME_NEGATIVE,
                &svg_volume) == PSM_OK,
            std::string("create embossed SVG: ") +
                psm_last_error(session));
    require(psm_model_volume_count(session, tool_object) == 3,
            "text and SVG are object volumes");
    require(psm_model_remove_volume(
                session, tool_object, svg_volume) == PSM_OK,
            "remove SVG volume");
    require(psm_model_remove_volume(
                session, tool_object, text_volume) == PSM_OK,
            "remove text volume");
    require(psm_model_remove(session, tool_object) == PSM_OK,
            "remove extended tool copy");

    psm_object_id cut_source = PSM_INVALID_ID;
    require(psm_model_duplicate(session, id, &cut_source) == PSM_OK,
            "duplicate for cut");
    psm_object_info cut_info{};
    require(psm_model_info(session, cut_source, &cut_info) == PSM_OK,
            "cut source bounds");
    psm_object_id cut_ids[8]{};
    size_t cut_count = 0;
    require(psm_model_cut_z(
                session, cut_source,
                0.5f * (cut_info.bbox_min[2] + cut_info.bbox_max[2]),
                1, 1, 0, cut_ids, 8, &cut_count) == PSM_OK &&
            cut_count == 2,
            std::string("horizontal cut keeps two halves: ") +
                psm_last_error(session));
    for (size_t i = 0; i < cut_count; ++i)
        require(psm_model_remove(session, cut_ids[i]) == PSM_OK,
                "remove cut result");

    const std::filesystem::path plate_stl =
        std::filesystem::path(argv[1]) / "plate-export.stl";
    const std::filesystem::path plate_obj =
        std::filesystem::path(argv[1]) / "plate-export.obj";
    const std::filesystem::path repaired_stl =
        std::filesystem::path(argv[1]) / "repaired.stl";
    require(psm_plate_export_stl(
                session, plate_stl.string().c_str()) == PSM_OK &&
            std::filesystem::file_size(plate_stl) > 0,
            "export active plate as STL");
    require(psm_plate_export_obj(
                session, plate_obj.string().c_str()) == PSM_OK &&
            std::filesystem::file_size(plate_obj) > 0,
            "export active plate as OBJ");
    require(psm_stl_repair(
                session, argv[3],
                repaired_stl.string().c_str()) == PSM_OK &&
            std::filesystem::file_size(repaired_stl) > 0,
            "repair STL into new file");

    /*
     * Eine Änderung direkt nach dem Start muss in beiden möglichen
     * Zeitabläufen sicher stale werden: entweder sieht der Worker die
     * geänderte Revision, oder ein schon fertiges Ergebnis wird durch
     * mark_design_changed() nachträglich invalidiert.
     */
    require(psm_slice_start(session, nullptr, nullptr) == PSM_OK,
            "start slice for stale contract");
    require(psm_history_begin(session, "Änderung während Slicing") == PSM_OK,
            "history transaction may begin while slice uses its snapshot");
    require(psm_model_info(session, id, &info) == PSM_OK,
            "object info before stale mutation");
    require(psm_model_set_position(
                session, id,
                info.position[0] + 1.f,
                info.position[1],
                info.position[2]) == PSM_OK,
            "mutate while slice is running");
    require(psm_history_end(session) == PSM_OK,
            "history transaction ends while slice runs");
    require(psm_slice_wait(session, -1) == PSM_ERR_STALE_RESULT,
            "changed design produces stale slice");
    psm_slice_stats slice_stats{};
    require(psm_slice_stats_get(session, &slice_stats) ==
                PSM_ERR_STALE_RESULT,
            "stale stats are rejected");
    psm_preview_snapshot stale_preview{};
    stale_preview.version = PSM_PREVIEW_SNAPSHOT_VERSION_1;
    require(psm_preview_snapshot_get(session, &stale_preview) ==
                PSM_ERR_STALE_RESULT,
            "stale final preview snapshot is rejected");

    require(psm_slice_start(session, nullptr, nullptr) == PSM_OK,
            "start current slice");
    require(psm_slice_wait(session, -1) == PSM_OK,
            std::string("current slice: ") + psm_last_error(session));
    require(psm_slice_stats_get(session, &slice_stats) == PSM_OK,
            "current stats are available");
    require(slice_stats.object_count == 1, "slice contains one object");

    /* Ein Remote-Ergebnis gehört zur Revision beim Upload. Ist die Szene
     * vorher geändert worden, darf der Kern die Datei nicht einmal mehr
     * einlesen und dadurch versehentlich als aktuell markieren. */
    const uint64_t remote_request_revision = psm_design_revision(session);
    require(psm_model_info(session, id, &info) == PSM_OK,
            "object info before remote stale mutation");
    require(psm_model_set_position(session, id,
                                   info.position[0] + 0.25f,
                                   info.position[1], info.position[2]) == PSM_OK,
            "mutate after remote request revision");
    require(psm_slice_accept_remote_gcode(
                session, "remote-result-must-not-be-read.gcode",
                remote_request_revision) == PSM_ERR_STALE_RESULT,
            "stale remote G-code is rejected before processing");
    require(psm_slice_start(session, nullptr, nullptr) == PSM_OK,
            "rebuild current preview after stale remote result");
    require(psm_slice_wait(session, -1) == PSM_OK,
            "current preview is restored after stale remote result");

    /*
     * Dieser Vertrag muss an der finalen G-Code-Verarbeitung hängen.
     * Ein Print-Schnappschuss vor dem Export kennt weder finale Moves noch
     * die vom GCodeProcessor berechneten Zeiten und Filamentmengen.
     */
    psm_preview_snapshot preview{};
    preview.version = PSM_PREVIEW_SNAPSHOT_VERSION_1;
    require(psm_preview_snapshot_get(session, &preview) == PSM_OK,
            std::string("final preview snapshot: ") +
                psm_last_error(session));
    require(preview.final_move_count > 0,
            "final preview contains processed G-code moves");
    require(preview.layer_count > 0 && preview.role_count > 0 &&
                preview.extruder_count > 0,
            "final preview exposes layers, roles and used extruders");
    require(preview.print_time_seconds > 0.0 &&
                preview.filament_used_mm > 0.0 &&
                preview.max_z > preview.min_z,
            "final preview exposes real time, filament and Z bounds");

    float previous_z = preview.min_z;
    double layer_time = 0.0;
    double layer_filament = 0.0;
    for (uint32_t layer_index = 0;
         layer_index < preview.layer_count;
         ++layer_index) {
        psm_preview_layer layer{};
        require(psm_preview_layer_at(
                    session, layer_index, &layer) == PSM_OK,
                "final preview layer is available");
        require(layer.index == layer_index &&
                    layer.z_lower >= previous_z - 0.0001f &&
                    layer.z_upper >= layer.z_lower,
                "final preview layer Z bounds are monotonic");
        previous_z = layer.z_upper;
        layer_time += layer.time_seconds;
        layer_filament += layer.filament_used_mm;
    }
    require(previous_z > preview.min_z && layer_time > 0.0 &&
                layer_filament > 0.0,
            "final layer range carries live time and filament totals");

    uint64_t role_moves = 0;
    for (uint32_t role_index = 0;
         role_index < preview.role_count;
         ++role_index) {
        psm_preview_role role{};
        require(psm_preview_role_at(
                    session, role_index, &role) == PSM_OK,
                "detected final feature role is available");
        require(role.role != PSM_PREVIEW_ROLE_NONE &&
                    role.move_count > 0,
                "only real extrusion feature roles are exposed");
        role_moves += role.move_count;
    }
    require(role_moves > 0, "final feature roles reference processed moves");

    for (uint32_t extruder_index = 0;
         extruder_index < preview.extruder_count;
         ++extruder_index) {
        psm_preview_extruder extruder{};
        require(psm_preview_extruder_at(
                    session, extruder_index, &extruder) == PSM_OK,
                "used final extruder is available");
        require(extruder.move_count > 0 &&
                    extruder.filament_used_mm > 0.0,
                "preview extruder is backed by final extrusion moves");
    }

    const std::filesystem::path ascii_gcode =
        std::filesystem::path(argv[1]) / "conversion-source.gcode";
    const std::filesystem::path binary_gcode =
        std::filesystem::path(argv[1]) / "conversion-source.bgcode";
    const std::filesystem::path restored_gcode =
        std::filesystem::path(argv[1]) / "conversion-restored.gcode";
    require(psm_gcode_export(
                session, ascii_gcode.string().c_str()) == PSM_OK,
            "export source G-code for conversion");
    require(psm_gcode_convert(
                session, ascii_gcode.string().c_str(),
                binary_gcode.string().c_str(), 1) == PSM_OK &&
            std::filesystem::file_size(binary_gcode) > 0,
            std::string("convert ASCII G-code to BGCode: ") +
                psm_last_error(session));
    require(psm_gcode_convert(
                session, binary_gcode.string().c_str(),
                restored_gcode.string().c_str(), 0) == PSM_OK &&
            std::filesystem::file_size(restored_gcode) > 0,
            std::string("convert BGCode to ASCII G-code: ") +
                psm_last_error(session));

    /*
     * Zwei deckungsgleiche Objekte erzeugen einen Konfliktfund im
     * G-Code. Print::export_gcode darf dabei seinen optionalen
     * GCodeProcessorResult nicht ueber einen Nullzeiger beschreiben.
     */
    psm_object_id conflicting = PSM_INVALID_ID;
    require(psm_model_duplicate(session, id, &conflicting) == PSM_OK &&
            conflicting != PSM_INVALID_ID,
            "duplicate overlapping object for conflict export");
    require(psm_slice_start(session, nullptr, nullptr) == PSM_OK,
            "start overlapping conflict slice");
    require(psm_slice_wait(session, -1) == PSM_OK,
            std::string("overlapping conflict slice: ") +
                psm_last_error(session));
    require(psm_slice_stats_get(session, &slice_stats) == PSM_OK &&
            slice_stats.object_count == 2,
            "conflicting paths export without null dereference");
    require(psm_model_remove(session, conflicting) == PSM_OK,
            "remove conflict regression object");

    require(psm_config_set(session, "layer_height", "0.21") == PSM_OK,
            "change config after slice");
    require(psm_slice_stats_get(session, &slice_stats) ==
                PSM_ERR_STALE_RESULT,
            "config change invalidates finished slice");
    preview.version = PSM_PREVIEW_SNAPSHOT_VERSION_1;
    require(psm_preview_snapshot_get(session, &preview) ==
                PSM_ERR_STALE_RESULT,
            "config change invalidates final preview snapshot");

    size_t second_bed = 0;
    require(psm_bed_add(session, &second_bed) == PSM_OK,
            "add second bed");
    require(second_bed == 1 && psm_bed_active(session) == 1,
            "new bed is selected directly");
    require(psm_bed_select(session, 0) == PSM_OK, "select first bed");

    psm_object_id moved = PSM_INVALID_ID;
    require(psm_bed_move_object(session, id, 1, &moved) == PSM_OK,
            "move object to second bed");
    require(moved != PSM_INVALID_ID, "moved object id");
    require(psm_bed_object_count(session, 0) == 0 &&
            psm_bed_object_count(session, 1) == 1,
            "bed object counts after move");

    psm_object_id copied_back = PSM_INVALID_ID;
    require(psm_model_duplicate(session, moved, &copied_back) == PSM_OK &&
            copied_back != PSM_INVALID_ID,
            "copy object from inactive bed to active bed");
    require(psm_bed_object_count(session, 0) == 1 &&
            psm_bed_object_count(session, 1) == 1,
            "cross-bed clipboard keeps source and targets active bed");
    require(psm_model_remove(session, copied_back) == PSM_OK,
            "remove cross-bed clipboard regression copy");

    require(psm_bed_select(session, 1) == PSM_OK, "select second bed");
    require(psm_model_info(session, moved, &info) == PSM_OK,
            "moved object is visible on selected bed");
    require(psm_bed_clear(session) == PSM_OK, "clear active bed only");
    require(psm_bed_object_count(session, 1) == 0, "active bed cleared");
    require(psm_bed_remove(session, 1) == PSM_OK, "remove second bed");
    require(psm_bed_count(session) == 1, "one bed remains");

    psm_project_import_info project{};
    require(psm_project_load_3mf(session, argv[4], &project) == PSM_OK,
            std::string("load 3mf project: ") + psm_last_error(session));
    require(project.config_loaded == 1, "embedded config loaded");
    require(project.post_process_removed == 1,
            "embedded post-process command removed");
    require(std::string(project.requested_printer) ==
                "PSMobile Test Printer 0.4",
            "requested printer retained");
    require(std::string(project.selected_printer).size() > 0,
            "project printer selected");
    require(project.bed_count == 2 && psm_bed_count(session) == 2,
            "desktop multibed project split into two mobile beds");
    require(project.object_count == 2 &&
            psm_bed_object_count(session, 0) == 1 &&
            psm_bed_object_count(session, 1) == 1,
            "one project instance on each selectable bed");
    require(psm_history_undo_count(session) == 0 &&
            psm_history_redo_count(session) == 0,
            "opening a project starts a clean history");

    /*
     * Bettnamen und Sperren gehoeren zur Session. Sie duerfen weder aus
     * UserDefaults noch aus einer anderen Core-Session wieder auftauchen.
     */
    psm_bed_metadata first_metadata{};
    require(psm_bed_metadata_get(session, 0, &first_metadata) == PSM_OK,
            "read initial first-bed metadata");
    require(std::string(first_metadata.name).empty() &&
            first_metadata.locked == 0,
            "new project beds start unnamed and unlocked");

    psm_bed_metadata named_locked{};
    std::snprintf(named_locked.name, sizeof(named_locked.name), "%s",
                  "Kundenplatte");
    named_locked.locked = 1;
    require(psm_bed_metadata_set(session, 0, &named_locked) == PSM_OK,
            "store session-owned bed name and lock");

    psm_bed_metadata stored_metadata{};
    require(psm_bed_metadata_get(session, 0, &stored_metadata) == PSM_OK,
            "read stored first-bed metadata");
    require(std::string(stored_metadata.name) == "Kundenplatte" &&
            stored_metadata.locked == 1,
            "bed metadata round-trips through the core");

    psm_arrange_info locked_arrange{};
    require(psm_arrange_bed_ex(session, 0, 6.f, 0, &locked_arrange) ==
                PSM_ERR_LOCKED,
            "locked bed rejects arrange in the core");
    require(locked_arrange.status == PSM_ARRANGE_LOCKED,
            "locked arrange has a machine-readable result");

    psm_session *metadata_isolation = psm_session_create(argv[1], argv[2]);
    require(metadata_isolation != nullptr,
            "create second session for metadata isolation");
    psm_bed_metadata isolated_metadata{};
    require(psm_bed_metadata_get(metadata_isolation, 0,
                                 &isolated_metadata) == PSM_OK,
            "read metadata from second session");
    require(std::string(isolated_metadata.name).empty() &&
            isolated_metadata.locked == 0,
            "bed metadata does not leak between sessions");

    /*
     * Ein zu grosses Objekt ist kein generischer Fehler. Das Panel muss
     * erklaeren koennen, dass das Zielbett voll beziehungsweise zu klein
     * ist, statt nur "Anordnen fehlgeschlagen" zu zeigen.
     */
    psm_object_id oversized = PSM_INVALID_ID;
    size_t oversized_count = 0;
    require(psm_model_load(metadata_isolation, argv[3], &oversized, 1,
                           &oversized_count) == PSM_OK &&
            oversized_count == 1,
            "load isolated object for full-bed result");
    require(psm_model_set_scale(metadata_isolation, oversized,
                                30.f, 30.f, 30.f) == PSM_OK,
            "make isolated object larger than the configured bed");
    psm_arrange_info full_arrange{};
    require(psm_arrange_bed_ex(metadata_isolation, 0, 6.f, 0,
                               &full_arrange) == PSM_ERR_FULL,
            "oversized target has a dedicated full-bed error");
    require(full_arrange.status == PSM_ARRANGE_FULL,
            "full arrange has a machine-readable result");
    psm_session_destroy(metadata_isolation);

    named_locked.locked = 0;
    require(psm_bed_metadata_set(session, 0, &named_locked) == PSM_OK,
            "unlock first bed for remaining project tests");
    /*
     * Arrange auf dem aktiven Bett 2 darf weder den Inhalt von Bett 1
     * noch Prusas prozessglobalen Mehrbettzustand anfassen.
     */
    require(psm_bed_select(session, 0) == PSM_OK,
            "keep first local bed active for arrange regression");
    psm_object_id first_bed_ids[4]{};
    size_t first_bed_count = 0;
    require(psm_model_list(session, first_bed_ids, 4, &first_bed_count) == PSM_OK &&
            first_bed_count == 1,
            "one object available on untouched first bed");
    psm_object_info first_before{};
    require(psm_model_info(session, first_bed_ids[0], &first_before) == PSM_OK,
            "capture first bed object before arranging second bed");

    require(psm_bed_select(session, 1) == PSM_OK,
            "inspect second local bed for arrange regression");
    psm_object_id arrange_ids[4]{};
    size_t arrange_count = 0;
    require(psm_model_list(session, arrange_ids, 4, &arrange_count) == PSM_OK &&
            arrange_count == 1,
            "one object available on second bed");
    require(psm_model_set_instances(session, arrange_ids[0], 12) == PSM_OK,
            "create twelve instances on second bed");
    /*
     * Der alte Fehler sass im bestehenden psm_arrange: Er ersetzte den
     * prozessglobalen Desktop-Mehrbettzustand durch lokale Bett-0-
     * Eintraege. Der iOS-Simulatortest liest genau diesen Singleton vor
     * und nach dem echten Aufruf.
     */
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
    Slic3r::s_multiple_beds.set_active_bed(1);
    const auto global_beds_before =
        Slic3r::s_multiple_beds.get_inst_map();
#endif
    psm_arrange_info arrange_info{};
    require(psm_arrange_bed_ex(session, 1, 0.f, 0, &arrange_info) == PSM_OK,
            std::string("arrange explicit second bed: ") +
                psm_last_error(session));
    require(arrange_info.status == PSM_ARRANGE_ARRANGED &&
            arrange_info.object_count == 1 &&
            arrange_info.instance_count == 12,
            "arrange reports the explicit target result");
    require(psm_bed_active(session) == 1,
            "arrange keeps the active second bed selected");
#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
    require(Slic3r::s_multiple_beds.get_active_bed() == 1,
            "arrange does not activate Prusa desktop bed zero");
    require(Slic3r::s_multiple_beds.get_inst_map() == global_beds_before,
            "arrange does not replace the global desktop bed membership");
#endif
    require(psm_bed_select(session, 0) == PSM_OK,
            "inspect untouched first bed after arranging second bed");
    psm_object_info first_after{};
    require(psm_model_info(session, first_bed_ids[0], &first_after) == PSM_OK,
            "first bed object remains visible after arranging second bed");
    for (int axis = 0; axis < 3; ++axis) {
        require(close_to(first_after.position[axis], first_before.position[axis]),
                "arranging second bed does not move first bed");
    }
    require(psm_bed_select(session, 1) == PSM_OK,
            "inspect arranged second bed");
    psm_object_info arranged_info{};
    require(psm_model_info(session, arrange_ids[0], &arranged_info) == PSM_OK &&
            arranged_info.instance_count == 12,
            "arrange preserves all second-bed instances");

#if defined(PSM_TEST_MULTIPLE_BEDS_STATE)
    /*
     * instance_count allein wuerde auch einen No-op bestehen lassen.
     * Deshalb prueft der gelinkte iOS-Vertrag die echten transformierten
     * Huellboxen jeder Instanz: Kein Paar darf sich in XY ueberlappen.
     */
    {
        std::lock_guard<std::recursive_mutex> data_lock(session->data_mtx);
        const Slic3r::ModelObject *arranged =
            session->bed_models[1]->objects.front();
        require(arranged->instances.size() == 12,
                "arrange retains twelve internal instance transforms");
        for (size_t i = 0; i < arranged->instances.size(); ++i) {
            const Slic3r::BoundingBoxf3 a =
                arranged->instance_bounding_box(i);
            for (size_t j = i + 1; j < arranged->instances.size(); ++j) {
                const Slic3r::BoundingBoxf3 b =
                    arranged->instance_bounding_box(j);
                constexpr double tolerance = 0.01;
                const bool getrennt =
                    a.max.x() <= b.min.x() + tolerance ||
                    b.max.x() <= a.min.x() + tolerance ||
                    a.max.y() <= b.min.y() + tolerance ||
                    b.max.y() <= a.min.y() + tolerance;
                require(getrennt,
                        "arrange separates every pair of second-bed instances");
            }
        }
    }
#endif

    /*
     * Der vorherige Projektfall deckt bewusst den Fallback auf ein
     * projektlokales Profil ab. Dieser zweite Fall installiert dagegen
     * nur das passende CORE-One-Modell vorab und erwartet deshalb, dass
     * load_config_model den vorhandenen Namen unveraendert auswaehlt.
     */
    const std::filesystem::path installed_data =
        std::filesystem::path(argv[1]) / "installed-profile-data";
    psm_session *installed_session = psm_session_create(
        installed_data.string().c_str(), argv[2]);
    require(installed_session != nullptr,
            "create session for installed profile fixture");
    const char *core_one_key[] = {"PrusaResearch:COREONE:0.4"};
    require(psm_presets_install(installed_session, core_one_key, 1) == PSM_OK,
            std::string("install exact CORE One profile: ") +
                psm_last_error(installed_session));
    psm_project_import_info installed_project{};
    require(psm_project_load_3mf(installed_session, argv[5],
                                 &installed_project) == PSM_OK,
            std::string("load installed-profile fixture: ") +
                psm_last_error(installed_session));
    require(std::string(installed_project.requested_printer) ==
                "Prusa CORE One 0.4 nozzle",
            "installed fixture requests exact CORE One preset");
    require(std::string(installed_project.selected_printer) ==
                "Prusa CORE One 0.4 nozzle",
            "installed CORE One preset selected without project-local fallback");
    require(std::string(installed_project.selected_print) ==
                "0.20mm SPEED @COREONE 0.4",
            "installed compatible print profile selected");
    require(psm_history_clear(installed_session) == PSM_OK,
            "clear history before printer selection");
    require(psm_preset_select(installed_session, PSM_PRESET_PRINTER,
                              "Prusa CORE One 0.4 nozzle") == PSM_OK,
            "select installed printer preset");
    require(psm_history_undo_count(installed_session) == 1,
            "printer selection creates exactly one undo checkpoint");
    psm_session_destroy(installed_session);

    /*
     * Strukturierte Desktop-Sonderwerte. compatible_printers kommt in
     * Druck- UND Filamentprofil vor; beide müssen gezielt und unabhängig
     * bearbeitbar bleiben.
     */
    require(psm_preset_config_set(
                session, PSM_PRESET_PRINT, "compatible_printers",
                "\"PSMobile Druckprofil-Ziel\"") == PSM_OK,
            std::string("set print compatibility: ") +
                psm_last_error(session));
    require(psm_preset_config_set(
                session, PSM_PRESET_FILAMENT, "compatible_printers",
                "\"PSMobile Filament-Ziel\"") == PSM_OK,
            std::string("set filament compatibility: ") +
                psm_last_error(session));
    char structured_value[4096]{};
    require(psm_preset_config_get(
                session, PSM_PRESET_PRINT, "compatible_printers",
                structured_value, sizeof(structured_value)) == PSM_OK &&
            std::string(structured_value).find("Druckprofil-Ziel") !=
                std::string::npos,
            "print compatibility remains scoped");
    require(psm_preset_config_get(
                session, PSM_PRESET_FILAMENT, "compatible_printers",
                structured_value, sizeof(structured_value)) == PSM_OK &&
            std::string(structured_value).find("Filament-Ziel") !=
                std::string::npos,
            "filament compatibility remains scoped");
    require(psm_preset_config_set(
                session, PSM_PRESET_PRINT, "gcode_substitutions",
                "\"M104\";\"M104 S0\";i;\"Temperaturtest\"") == PSM_OK,
            "set structured G-code substitution");
    require(psm_preset_config_get(
                session, PSM_PRESET_PRINT, "gcode_substitutions",
                structured_value, sizeof(structured_value)) == PSM_OK &&
            std::string(structured_value).find("Temperaturtest") !=
                std::string::npos,
            "read structured G-code substitution");
    require(psm_preset_config_set(
                session, PSM_PRESET_FILAMENT,
                "filament_ramming_parameters",
                "\"120 100 6.6 7.0| 0.05 6.6 0.45 7\"") == PSM_OK,
            "set structured ramming curve");
    require(psm_config_set(
                session, "wiping_volumes_matrix",
                "0,120,130,0") == PSM_OK &&
            psm_config_set(
                session, "wiping_volumes_use_custom_matrix",
                "1") == PSM_OK,
            "set project purge matrix");

    require(psm_bed_select(session, 0) == PSM_OK,
            "select first project bed for annotation roundtrip");
    psm_object_id project_ids[8]{};
    size_t project_id_count = 0;
    require(psm_model_list(
                session, project_ids, 8, &project_id_count) == PSM_OK &&
            project_id_count == 1,
            "locate first project object");
    const psm_object_id annotated_id = project_ids[0];
    psm_paint_options project_support = paint_options_for(
        session, annotated_id, 0, 0,
        PSM_PAINT_MODE_SMART_FILL, PSM_PAINT_SHAPE_CIRCLE, 3.f, 30.f);
    require(psm_model_paint_apply(
                session, annotated_id, 0, 0, 0,
                PSM_PAINT_SUPPORT, 1, &project_support) == PSM_OK &&
            psm_model_paint_count(
                session, annotated_id, PSM_PAINT_SUPPORT) > 0,
            "project object keeps a support annotation from Smart Fill");
    psm_paint_options project_seam = paint_options_for(
        session, annotated_id, 0, 0,
        PSM_PAINT_MODE_BRUSH, PSM_PAINT_SHAPE_SPHERE, 3.f);
    require(psm_model_paint_apply(
                session, annotated_id, 0, 0, 0,
                PSM_PAINT_SEAM, 1, &project_seam) == PSM_OK &&
            psm_model_paint_count(
                session, annotated_id, PSM_PAINT_SEAM) > 0,
            "project object keeps a seam annotation from the new brush path");
    psm_paint_options project_mmu = paint_options_for(
        session, annotated_id, 0, 0,
        PSM_PAINT_MODE_BUCKET_FILL, PSM_PAINT_SHAPE_CIRCLE, 3.f, 30.f);
    require(psm_model_paint_apply(
                session, annotated_id, 0, 0, 0,
                PSM_PAINT_MMU, 2, &project_mmu) == PSM_OK &&
            psm_model_paint_count(
                session, annotated_id, PSM_PAINT_MMU) > 0,
            "project object keeps an MMU annotation from Bucket Fill");
    size_t project_text_volume = 0;
    require(psm_model_add_text_volume(
                session, annotated_id, "3MF",
                system_font(),
                4.f, 0.6f, PSM_VOLUME_MODEL_PART,
                &project_text_volume) == PSM_OK &&
            psm_model_volume_count(session, annotated_id) >= 2,
            "project object receives embossed text before roundtrip");

    psm_custom_gcode custom{};
    custom.print_z = 5.0;
    custom.type = PSM_CUSTOM_PAUSE;
    custom.extruder = 0;
    std::snprintf(custom.extra, sizeof(custom.extra), "%s",
                  "M601 ; PSMobile Vertragstest");
    require(psm_custom_gcode_add(session, &custom) == PSM_OK &&
            psm_custom_gcode_count(session) == 1,
            "add project custom G-code");
    psm_custom_gcode custom_read{};
    require(psm_custom_gcode_at(session, 0, &custom_read) == PSM_OK &&
            custom_read.type == PSM_CUSTOM_PAUSE &&
            std::string(custom_read.extra).find("PSMobile") !=
                std::string::npos,
            "read project custom G-code");
    require(psm_wipe_tower_set(session, 18.f, 24.f, 35.f) == PSM_OK,
            "set project wipe tower");
    float wipe_x = 0.f;
    float wipe_y = 0.f;
    float wipe_rotation = 0.f;
    require(psm_wipe_tower_get(
                session, &wipe_x, &wipe_y,
                &wipe_rotation) == PSM_OK &&
            close_to(wipe_x, 18.f) &&
            close_to(wipe_y, 24.f) &&
            close_to(wipe_rotation, 35.f),
            "read project wipe tower");

    /*
     * Vollstaendiger 3MF-Roundtrip: mobile Einzelbetten werden wieder in
     * PrusaSlicers Desktop-Landschaft geschrieben und beim erneuten
     * Oeffnen identisch getrennt. Zugangsdaten duerfen dabei nicht in
     * der Projektdatei landen.
     */
    require(psm_config_set(session, "print_host",
                           "https://secret.invalid") == PSM_OK,
            "set secret host before project save");
    require(psm_config_set(session, "printhost_apikey",
                           "PSMOBILE-SECRET-KEY") == PSM_OK,
            "set secret api key before project save");

    const std::filesystem::path roundtrip =
        std::filesystem::path(argv[1]) / "roundtrip.3mf";
    require(psm_project_save_3mf(session, roundtrip.string().c_str()) == PSM_OK,
            std::string("save 3mf roundtrip: ") + psm_last_error(session));
    require(std::filesystem::exists(roundtrip) &&
            std::filesystem::file_size(roundtrip) > 0,
            "saved 3mf exists");

    const std::filesystem::path second_data =
        std::filesystem::path(argv[1]) / "roundtrip-data";
    std::filesystem::create_directories(second_data);
    psm_session *roundtrip_session =
        psm_session_create(second_data.string().c_str(), argv[2]);
    require(roundtrip_session != nullptr, "roundtrip session create");
    psm_project_import_info reopened{};
    require(psm_project_load_3mf(roundtrip_session,
                                 roundtrip.string().c_str(),
                                 &reopened) == PSM_OK,
            std::string("reopen saved 3mf: ") +
                psm_last_error(roundtrip_session));
    require(reopened.config_loaded == 1 &&
            reopened.object_count == 2 &&
            reopened.bed_count == 2,
            "roundtrip retains config, objects and beds");
    require(psm_bed_object_count(roundtrip_session, 0) == 1 &&
            psm_bed_object_count(roundtrip_session, 1) == 1,
            "roundtrip retains direct bed membership");
    require(psm_bed_select(roundtrip_session, 0) == PSM_OK,
            "select annotated roundtrip bed");
    psm_object_id reopened_ids[8]{};
    size_t reopened_id_count = 0;
    require(psm_model_list(
                roundtrip_session, reopened_ids, 8,
                &reopened_id_count) == PSM_OK &&
            reopened_id_count == 1 &&
            psm_model_volume_count(roundtrip_session, reopened_ids[0]) >= 2,
            "roundtrip retains embossed text volume");
    require(psm_model_paint_count(
                roundtrip_session, reopened_ids[0],
                PSM_PAINT_SUPPORT) > 0,
            "roundtrip retains support facet annotation");
    require(psm_model_paint_count(
                roundtrip_session, reopened_ids[0],
                PSM_PAINT_SEAM) > 0,
            "roundtrip retains seam facet annotation");
    require(psm_model_paint_count(
                roundtrip_session, reopened_ids[0],
                PSM_PAINT_MMU) > 0,
            "roundtrip retains MMU facet annotation");
    require(std::string(reopened.selected_printer).size() > 0,
            "roundtrip selects its embedded printer profile");
    require(psm_custom_gcode_count(roundtrip_session) == 1,
            "roundtrip retains custom G-code");
    psm_custom_gcode reopened_custom{};
    require(psm_custom_gcode_at(
                roundtrip_session, 0,
                &reopened_custom) == PSM_OK &&
            reopened_custom.type == PSM_CUSTOM_PAUSE,
            "roundtrip restores custom G-code type");
    require(psm_wipe_tower_get(
                roundtrip_session, &wipe_x, &wipe_y,
                &wipe_rotation) == PSM_OK &&
            close_to(wipe_x, 18.f) &&
            close_to(wipe_y, 24.f) &&
            close_to(wipe_rotation, 35.f),
            "roundtrip retains wipe tower transform");

    char config_value[256]{};
    require(psm_config_get(roundtrip_session, "print_host",
                           config_value, sizeof(config_value)) == PSM_OK &&
            std::string(config_value).empty(),
            "saved project strips print host");
    require(psm_config_get(roundtrip_session, "printhost_apikey",
                           config_value, sizeof(config_value)) == PSM_OK &&
            std::string(config_value).empty(),
            "saved project strips API key");
    require(psm_config_get(roundtrip_session, "post_process",
                           config_value, sizeof(config_value)) == PSM_OK &&
            std::string(config_value).empty(),
            "saved project contains no post-processing command");

    /*
     * ColorMix-Rezepte sind virtuelle Extruder. Sie müssen sich wie ein
     * echter Extruder am Objekt, am Volumen und auf jedem mobilen Bett
     * auswählen lassen, dürfen aber keine beliebigen IDs freischalten.
     */
    require(psm_config_set(roundtrip_session, "nozzle_diameter", "0.4,0.4") == PSM_OK &&
            psm_extruder_count(roundtrip_session) == 2,
            "configure two physical heads for ColorMix");
    const char *colormix_json = R"JSON({
        "version":1,
        "physical_extruders":[{"id":1,"color":"#FF0000"},{"id":2,"color":"#0000FF"}],
        "virtual_extruders":[{
            "id":3,"kind":"fullspectrum",
            "components":[{"extruder":1,"ratio":0.5},{"extruder":2,"ratio":0.5}]
        }]
    })JSON";
    require(psm_colormix_set_json(roundtrip_session, colormix_json) == PSM_OK,
            std::string("store ColorMix recipe: ") +
                psm_last_error(roundtrip_session));
    char colormix_read[4096]{};
    const psm_result colormix_read_result = psm_colormix_get_json(roundtrip_session, colormix_read,
                                                                   sizeof(colormix_read));
    require(colormix_read_result == PSM_OK &&
            std::string(colormix_read).find("\"id\": 3") != std::string::npos,
            std::string("read ColorMix recipe from active bed (rc=") +
                std::to_string(static_cast<int>(colormix_read_result)) + "): " +
                colormix_read + "; " + psm_last_error(roundtrip_session));
    require(psm_model_extruder_set(roundtrip_session, reopened_ids[0], 3) == PSM_OK &&
            psm_model_extruder_get(roundtrip_session, reopened_ids[0]) == 3,
            "assign virtual ColorMix extruder to object");
    require(psm_model_volume_extruder_set(roundtrip_session, reopened_ids[0], 0, 3) == PSM_OK &&
            psm_model_volume_info(roundtrip_session, reopened_ids[0], 0, &volume) == PSM_OK &&
            volume.explicit_extruder == 3,
            "assign virtual ColorMix extruder to volume");
    require(psm_model_extruder_set(roundtrip_session, reopened_ids[0], 4) == PSM_ERR_INVALID_ARG,
            "reject undefined virtual extruder");
    require(psm_bed_select(roundtrip_session, 1) == PSM_OK &&
            psm_colormix_get_json(roundtrip_session, colormix_read,
                                  sizeof(colormix_read)) == PSM_OK &&
            std::string(colormix_read).find("\"id\": 3") != std::string::npos,
            "ColorMix recipe is shared across mobile beds");

    psm_session_destroy(roundtrip_session);
    psm_session_destroy(session);
    std::cout << "PASS: psm_contract_tests\n";
    return 0;
}
