/*
 * psm_toggles.cpp - ERZEUGT, NICHT VON HAND BEARBEITEN.
 *
 * Erzeugt von build/scripts/extract-toggles.py aus
 * src/slic3r/GUI/ConfigManipulation.cpp.
 *
 * Der Rumpf unten ist woertlich der von
 * ConfigManipulation::toggle_print_fff_options. Er benutzt nur
 * toggle_field() und den uebergebenen config-Zeiger, keinen wx-Code und
 * keine Klassenmitglieder - deshalb laesst er sich unveraendert in eine
 * andere Huelle setzen. Aendert Prusa die Regeln, kommen sie beim
 * naechsten Lauf des Skripts mit.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psmobile_toggles.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/Print.hpp"

using namespace Slic3r;

namespace psm {

/* --- ab hier woertlich aus dem Original ---------------------------- */

void ToggleCollector::collect_print_fff(DynamicPrintConfig *config)
{
    bool have_perimeters = config->opt_int("perimeters") > 0;
    for (auto el : { "extra_perimeters","extra_perimeters_on_overhangs", "thin_walls", "overhangs",
                    "seam_position","staggered_inner_seams", "external_perimeters_first", "external_perimeter_extrusion_width",
                    "perimeter_speed", "small_perimeter_speed", "external_perimeter_speed", "enable_dynamic_overhang_speeds"})
        toggle_field(el, have_perimeters);

    for (size_t i = 0; i < 4; i++) {
        toggle_field("overhang_speed_" + std::to_string(i), config->opt_bool("enable_dynamic_overhang_speeds"));
    }

    const bool have_infill                      = config->option<ConfigOptionPercent>("fill_density")->value > 0;
    const bool has_automatic_infill_combination = config->option<ConfigOptionBool>("automatic_infill_combination")->value;
    // infill_extruder uses the same logic as in Print::extruders()
    for (auto el : { "fill_pattern","solid_infill_every_layers", "solid_infill_below_area", "infill_extruder",
                    "infill_anchor_max", "automatic_infill_combination" }) {
        toggle_field(el, have_infill);
    }

    toggle_field("infill_every_layers", have_infill && !has_automatic_infill_combination);
    toggle_field("automatic_infill_combination_max_layer_height", have_infill && has_automatic_infill_combination);

    // Only allow configuration of open anchors if the anchoring is enabled.
    bool has_infill_anchors = have_infill && config->option<ConfigOptionFloatOrPercent>("infill_anchor_max")->value > 0;
    toggle_field("infill_anchor", has_infill_anchors);

    bool has_spiral_vase         = config->opt_bool("spiral_vase");
    bool has_top_solid_infill 	 = config->opt_int("top_solid_layers") > 0;
    bool has_bottom_solid_infill = config->opt_int("bottom_solid_layers") > 0;
    bool has_solid_infill 		 = has_top_solid_infill || has_bottom_solid_infill;
    // solid_infill_extruder uses the same logic as in Print::extruders()
    for (auto el : { "top_fill_pattern", "bottom_fill_pattern", "infill_first", "solid_infill_extruder",
                    "solid_infill_extrusion_width", "solid_infill_speed" })
        toggle_field(el, has_solid_infill);

    for (auto el : { "fill_angle", "bridge_angle", "infill_extrusion_width",
                    "infill_speed", "bridge_speed", "over_bridge_speed" })
        toggle_field(el, have_infill || has_solid_infill);

    const bool has_ensure_vertical_shell_thickness = config->opt_enum<EnsureVerticalShellThickness>("ensure_vertical_shell_thickness") != EnsureVerticalShellThickness::Disabled;
    toggle_field("top_solid_min_thickness", !has_spiral_vase && has_top_solid_infill && has_ensure_vertical_shell_thickness);
    toggle_field("bottom_solid_min_thickness", !has_spiral_vase && has_bottom_solid_infill && has_ensure_vertical_shell_thickness);

    // Gap fill is newly allowed in between perimeter lines even for empty infill (see GH #1476).
    toggle_field("gap_fill_speed", have_perimeters);

    for (auto el : { "top_infill_extrusion_width", "top_solid_infill_speed" })
        toggle_field(el, has_top_solid_infill || (has_spiral_vase && has_bottom_solid_infill));

    bool have_default_acceleration = config->opt_float("default_acceleration") > 0;
    for (auto el : { "perimeter_acceleration", "infill_acceleration", "top_solid_infill_acceleration",
                    "solid_infill_acceleration", "external_perimeter_acceleration",
                    "bridge_acceleration", "first_layer_acceleration", "wipe_tower_acceleration"})
        toggle_field(el, have_default_acceleration);

    bool have_skirt = config->opt_int("skirts") > 0;
    toggle_field("skirt_height", have_skirt && config->opt_enum<DraftShield>("draft_shield") != dsEnabled);
    for (auto el : { "skirt_distance", "draft_shield", "min_skirt_length" })
        toggle_field(el, have_skirt);

    bool have_brim = config->opt_enum<BrimType>("brim_type") != btNoBrim;
    for (auto el : { "brim_width", "brim_separation" })
        toggle_field(el, have_brim);
    // perimeter_extruder uses the same logic as in Print::extruders()
    toggle_field("perimeter_extruder", have_perimeters || have_brim);

    bool have_raft = config->opt_int("raft_layers") > 0;
    bool have_support_material = config->opt_bool("support_material") || have_raft;
    bool have_support_material_auto = have_support_material && config->opt_bool("support_material_auto");
    bool have_support_interface = config->opt_int("support_material_interface_layers") > 0;
    bool have_support_soluble = have_support_material && config->opt_float("support_material_contact_distance") == 0;
    auto support_material_style = config->opt_enum<SupportMaterialStyle>("support_material_style");
    for (auto el : { "support_material_style", "support_material_pattern", "support_material_with_sheath",
                    "support_material_spacing", "support_material_angle", 
                    "support_material_interface_pattern", "support_material_interface_layers",
                    "dont_support_bridges", "support_material_extrusion_width", "support_material_contact_distance",
                    "support_material_xy_spacing" })
        toggle_field(el, have_support_material);
    toggle_field("support_material_threshold", have_support_material_auto);
    toggle_field("support_material_bottom_contact_distance", have_support_material && ! have_support_soluble);
    toggle_field("support_material_closing_radius", have_support_material && support_material_style == smsSnug);

    const bool has_organic_supports = support_material_style == smsOrganic && 
                                     (config->opt_bool("support_material") || 
                                      config->opt_int("support_material_enforce_layers") > 0);
    for (const std::string& key : { "support_tree_angle", "support_tree_angle_slow", "support_tree_branch_diameter",
                                    "support_tree_branch_diameter_angle", "support_tree_branch_diameter_double_wall", 
                                    "support_tree_tip_diameter", "support_tree_branch_distance", "support_tree_top_rate" })
        toggle_field(key, has_organic_supports);

    for (auto el : { "support_material_bottom_interface_layers", "support_material_interface_spacing", "support_material_interface_extruder",
                    "support_material_interface_speed", "support_material_interface_contact_loops" })
        toggle_field(el, have_support_material && have_support_interface);
    toggle_field("support_material_synchronize_layers", have_support_soluble);

    toggle_field("perimeter_extrusion_width", have_perimeters || have_skirt || have_brim);
    toggle_field("support_material_extruder", have_support_material || have_skirt);
    toggle_field("support_material_speed", have_support_material || have_brim || have_skirt);

    toggle_field("raft_contact_distance", have_raft && !have_support_soluble);
    for (auto el : { "raft_expansion", "first_layer_acceleration_over_raft", "first_layer_speed_over_raft" })
        toggle_field(el, have_raft);

    bool has_ironing = config->opt_bool("ironing");
    for (auto el : { "ironing_type", "ironing_flowrate", "ironing_spacing", "ironing_speed" })
    	toggle_field(el, has_ironing);

    bool have_ooze_prevention = config->opt_bool("ooze_prevention");
    toggle_field("standby_temperature_delta", have_ooze_prevention);

    bool have_wipe_tower = config->opt_bool("wipe_tower");
    for (auto el : { "wipe_tower_width",  "wipe_tower_brim_width", "wipe_tower_cone_angle",
                     "wipe_tower_extra_spacing", "wipe_tower_extra_flow", "wipe_tower_bridging", "wipe_tower_no_sparse_layers", "single_extruder_multi_material_priming" })
        toggle_field(el, have_wipe_tower);

    toggle_field("avoid_crossing_curled_overhangs", !config->opt_bool("avoid_crossing_perimeters"));
    toggle_field("avoid_crossing_perimeters", !config->opt_bool("avoid_crossing_curled_overhangs"));

    bool have_avoid_crossing_perimeters = config->opt_bool("avoid_crossing_perimeters");
    toggle_field("avoid_crossing_perimeters_max_detour", have_avoid_crossing_perimeters);

    bool have_arachne = config->opt_enum<PerimeterGeneratorType>("perimeter_generator") == PerimeterGeneratorType::Arachne;
    toggle_field("wall_transition_length", have_arachne);
    toggle_field("wall_transition_filter_deviation", have_arachne);
    toggle_field("wall_transition_angle", have_arachne);
    toggle_field("wall_distribution_count", have_arachne);
    toggle_field("min_feature_size", have_arachne);
    toggle_field("min_bead_width", have_arachne);
    toggle_field("thin_walls", !have_arachne);

    toggle_field("scarf_seam_placement", !has_spiral_vase);
    const auto scarf_seam_placement{config->opt_enum<ScarfSeamPlacement>("scarf_seam_placement")};
    const bool uses_scarf_seam{!has_spiral_vase && scarf_seam_placement != ScarfSeamPlacement::nowhere};
    toggle_field("scarf_seam_only_on_smooth", uses_scarf_seam);
    toggle_field("scarf_seam_start_height", uses_scarf_seam);
    toggle_field("scarf_seam_entire_loop", uses_scarf_seam);
    toggle_field("scarf_seam_length", uses_scarf_seam);
    toggle_field("scarf_seam_max_segment_length", uses_scarf_seam);
    toggle_field("scarf_seam_on_inner_perimeters", uses_scarf_seam);

    bool use_beam_interlocking = config->opt_bool("interlocking_beam");
    toggle_field("interlocking_beam_width", use_beam_interlocking);
    toggle_field("interlocking_orientation", use_beam_interlocking);
    toggle_field("interlocking_beam_layer_count", use_beam_interlocking);
    toggle_field("interlocking_depth", use_beam_interlocking);
    toggle_field("interlocking_boundary_avoidance", use_beam_interlocking);
    toggle_field("mmu_segmented_region_max_width", !use_beam_interlocking);

    bool have_non_zero_mmu_segmented_region_max_width = !use_beam_interlocking && config->opt_float("mmu_segmented_region_max_width") > 0.;
    toggle_field("mmu_segmented_region_interlocking_depth", have_non_zero_mmu_segmented_region_max_width);
}

} // namespace psm
