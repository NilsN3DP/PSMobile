import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class IOSPresetSwitchGuardTests(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_ios_model_defers_dirty_preset_switches(self):
        source = self.read("ios/PSMobile/SlicerModel.swift")
        self.assertIn("pendingPresetSwitch", source)
        self.assertIn("requestPresetSwitch", source)
        self.assertIn("performPresetSwitch", source)
        guarded = re.search(
            r"func requestPresetSwitch\(_ type: PsmCore\.PresetType, _ name: String\).*?dirtyProfileChanges\(for: type\).*?pendingPresetSwitch",
            source,
            re.S,
        )
        self.assertIsNotNone(guarded, "iOS must queue dirty preset switches instead of applying them immediately")

    def test_ios_core_exposes_select_preset_keeping(self):
        source = self.read("ios/PSMobile/Core/PsmCoreSetup.swift")
        self.assertIn("func selectPresetKeeping", source)
        self.assertIn("psm_preset_select_keeping", source)

    def test_ios_roots_present_profile_switch_dialog(self):
        for relative in [
            "ios/PSMobile/Screens/SimpleModeView.swift",
            "ios/PSMobile/Screens/AdvancedWorkspaceView.swift",
        ]:
            source = self.read(relative)
            self.assertIn("presetSwitchDialog", source, relative)
            self.assertIn("ProfilWechselDialog", source, relative)


if __name__ == "__main__":
    unittest.main()
