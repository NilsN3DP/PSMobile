#include "psm_layer_profile_render.hpp"
#include "psmobile_session.hpp"

#include <algorithm>
#include <cstring>
#include <exception>
#include <mutex>
#include <string>

#include "libslic3r/Print.hpp"
#include "libslic3r/Slicing.hpp"

namespace {

/*
 * Dieselbe Kapazitaet wie PrusaSlicers LayersEditing. Der Generator legt
 * eine zweite LOD hinter die Basisebene; kleinere Quadrate koennen bei
 * hohen Modellen und feinen Schichten dafuer zu klein werden.
 */
constexpr int TEXTURE_WIDTH = 1024;
constexpr int TEXTURE_HEIGHT = 1024;

Slic3r::ModelObject *find_object(psm_session &session, psm_object_id id)
{
    for (Slic3r::ModelObject *object : session.model().objects)
        if (static_cast<psm_object_id>(object->id().id) == id)
            return object;
    return nullptr;
}

} // namespace

namespace psm {

LayerVisualizationData build_layer_visualization(
    const psm_session &session,
    const Slic3r::ModelObject &object)
{
    LayerVisualizationData result;
    const std::vector<double> &profile = object.layer_height_profile.get();
    if (profile.size() < 4 || (profile.size() & 1U) != 0U)
        return result;

    /*
     * Nicht selbst interpolieren: SlicingParameters, Objektlagen und
     * Farbpalette gehoeren PrusaSlicer. Damit zeigt der Editor genau die
     * Schichten, aus denen derselbe Kern spaeter seine Objektlagen bildet.
     */
    const float min_z = static_cast<float>(object.min_z());
    const float object_height = static_cast<float>(object.max_z()) - min_z;
    if (! (object_height > 0.f))
        return result;

    const Slic3r::SlicingParameters slicing =
        Slic3r::PrintObject::slicing_parameters(
            session.config, object, object_height, Slic3r::Vec3d::Ones());
    const std::vector<coordf_t> layers =
        Slic3r::generate_object_layers(slicing, profile);
    if (layers.empty())
        return result;

    result.width = TEXTURE_WIDTH;
    result.height = TEXTURE_HEIGHT;
    result.object_min_z = min_z;
    result.object_max_z = static_cast<float>(slicing.object_print_z_height());
    result.rgba.assign(
        static_cast<size_t>(TEXTURE_WIDTH * TEXTURE_HEIGHT * 5), 0);
    result.cells = Slic3r::generate_layer_height_texture(
        slicing, layers, result.rgba.data(),
        result.height, result.width, true);

    result.min_layer_height = static_cast<float>(profile[1]);
    result.max_layer_height = static_cast<float>(profile[1]);
    for (size_t i = 1; i < profile.size(); i += 2) {
        result.min_layer_height =
            std::min(result.min_layer_height, static_cast<float>(profile[i]));
        result.max_layer_height =
            std::max(result.max_layer_height, static_cast<float>(profile[i]));
    }

    if (! result.enabled())
        return {};
    return result;
}

} // namespace psm

extern "C" {

PSM_API int psm_viewport_layer_visualization_info(
    psm_session *session,
    psm_object_id object_id,
    psm_layer_visualization_info *out)
{
    if (session == nullptr || out == nullptr)
        return 0;
    std::memset(out, 0, sizeof(*out));

    try {
        std::lock_guard<std::recursive_mutex> lock(session->data_mtx);
        Slic3r::ModelObject *object = find_object(*session, object_id);
        if (object == nullptr)
            return 0;

        const psm::LayerVisualizationData data =
            psm::build_layer_visualization(*session, *object);
        if (! data.enabled())
            return 0;

        out->texture_width = data.width;
        out->texture_height = data.height;
        out->texture_cells = data.cells;
        out->object_max_z = data.object_max_z;
        out->min_layer_height = data.min_layer_height;
        out->max_layer_height = data.max_layer_height;
        return 1;
    } catch (const std::exception &e) {
        /*
         * Eine Anzeige darf ein weiterhin gueltiges Modell nicht
         * unbedienbar machen. Der eigentliche Profil-Setter meldet
         * Validierungsfehler bereits an seinem Schreibzeitpunkt.
         */
        psm_emit_log(PSM_LOG_WARN,
                     std::string("Schichthoehen-Visualisierung: ") + e.what());
        return 0;
    }
}

} // extern "C"
