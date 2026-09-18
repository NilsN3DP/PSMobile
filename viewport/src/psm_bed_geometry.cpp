#include "psm_bed_geometry.hpp"
namespace psm {
BedModelTranslation align_bed_model(float shape_min_x, float shape_max_x,
                                    float shape_min_y, float shape_max_y,
                                    float model_min_x, float model_max_x,
                                    float model_min_y, float model_max_y)
{
    return {(shape_min_x + shape_max_x - model_min_x - model_max_x) * 0.5f,
            (shape_min_y + shape_max_y - model_min_y - model_max_y) * 0.5f};
}
} // namespace psm
