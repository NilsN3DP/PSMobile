#pragma once

#include "psm_viewport.h"

#include <cstdint>
#include <vector>

namespace Slic3r {
class ModelObject;
}

struct psm_session;

namespace psm {

/**
 * CPU-Seite der PrusaSlicer-Schichthoehentextur.
 *
 * Der C-Vertrag und OpenGL verwenden absichtlich denselben Wert. Ein
 * separater Test-Nachbau koennte sonst gruen sein, waehrend der Shader
 * weiterhin andere oder gar keine Daten bekommt.
 */
struct LayerVisualizationData {
    std::vector<std::uint8_t> rgba;
    int width = 0;
    int height = 0;
    int cells = 0;
    float object_min_z = 0.f;
    float object_max_z = 0.f;
    float min_layer_height = 0.f;
    float max_layer_height = 0.f;

    bool enabled() const
    {
        return width > 0 && height > 0 && cells > 0 && ! rgba.empty();
    }
};

LayerVisualizationData build_layer_visualization(
    const psm_session &session,
    const Slic3r::ModelObject &object);

} // namespace psm
