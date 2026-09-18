#ifndef PSM_BED_GEOMETRY_HPP
#define PSM_BED_GEOMETRY_HPP

namespace psm {
struct BedModelTranslation { float x; float y; };
BedModelTranslation align_bed_model(float shape_min_x, float shape_max_x,
                                    float shape_min_y, float shape_max_y,
                                    float model_min_x, float model_max_x,
                                    float model_min_y, float model_max_y);
} // namespace psm

#endif
