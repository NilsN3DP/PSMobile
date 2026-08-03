import SwiftUI
import PSMShared

/// Erstellt virtuelle Farben aus zwei oder drei physischen Positionen.
/// Die Rezepte werden als normale 3MF-Projektinformation gespeichert.
struct ColorMixView: View {
    @EnvironmentObject private var model: SlicerModel
    @Environment(\.psScale) private var ps
    let onClose: () -> Void

    @State private var selected: [Int] = [0, 1]
    @State private var firstShare = 0.5
    @State private var recipes: [ColorMixRecipe] = []
    @State private var saveFailed = false

    private func st(_ english: String, _ german: String) -> String {
        SimpleModeState.shared.text(english: english, german: german)
    }

    private var positions: [ExtruderPosition] {
        ExtruderPresentation.shared.positions(count: Int32(model.extruderCount))
    }

    private var components: [ColorMixComponent] {
        guard !selected.isEmpty else { return [] }
        if selected.count == 2 {
            return [
                ColorMixComponent(head: Int32(selected[0]), ratio: firstShare),
                ColorMixComponent(head: Int32(selected[1]), ratio: 1 - firstShare),
            ]
        }
        let rest = (1 - firstShare) / Double(selected.count - 1)
        return [ColorMixComponent(head: Int32(selected[0]), ratio: firstShare)]
            + selected.dropFirst().map { ColorMixComponent(head: Int32($0), ratio: rest) }
    }

    private var physicalColors: [String] {
        (0..<model.extruderCount).map { index in
            let color = model.extruderColor(index)
            return color.isEmpty ? "#808080" : color
        }
    }

    private var preview: String? {
        ColorMixCodec.shared.previewColor(physicalColors: physicalColors, components: components)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ps.pt(18)) {
                    Text(st("Create a mixed color", "Gemischte Farbe erstellen"))
                        .font(.system(size: ps.font(20), weight: .bold))
                        .foregroundStyle(PrusaColors.textPrimary)
                    Text(st("Choose two or three physical positions. Their existing materials stay assigned.",
                            "Wähle zwei oder drei physische Positionen. Die vorhandenen Materialien bleiben zugeordnet."))
                        .font(.system(size: ps.font(13)))
                        .foregroundStyle(PrusaColors.textMuted)

                    LazyVGrid(columns: [GridItem(.adaptive(minimum: ps.touch(46)))], spacing: ps.pt(8)) {
                        ForEach(positions, id: \.index) { position in
                            let isSelected = selected.contains(Int(position.index))
                            Button {
                                toggle(Int(position.index))
                            } label: {
                                Text(position.label)
                                    .font(.system(size: ps.font(15), weight: .bold))
                                    .frame(width: ps.touch(46), height: ps.touch(46))
                                    .background(isSelected ? PrusaColors.orange : PrusaColors.panelRaised)
                                    .foregroundStyle(isSelected ? .black : PrusaColors.textPrimary)
                                    .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                            }
                            .accessibilityIdentifier("colormix.position.\(position.index)")
                        }
                    }

                    if selected.count >= 2 {
                        VStack(alignment: .leading, spacing: ps.pt(8)) {
                            Text(st("Share of position \(selected[0] + 1): \(Int((firstShare * 100).rounded()))%",
                                    "Anteil von Position \(selected[0] + 1): \(Int((firstShare * 100).rounded()))%"))
                                .font(.system(size: ps.font(13)))
                                .foregroundStyle(PrusaColors.textPrimary)
                            Slider(value: $firstShare, in: 0.1...0.9, step: 0.1)
                                .tint(PrusaColors.orange)
                        }
                    }

                    HStack(spacing: ps.pt(12)) {
                        RoundedRectangle(cornerRadius: ps.pt(8))
                            .fill(Color(hexString: preview ?? "#808080") ?? PrusaColors.panelRaised)
                            .frame(width: ps.touch(62), height: ps.touch(62))
                            .overlay(RoundedRectangle(cornerRadius: ps.pt(8)).stroke(PrusaColors.divider, lineWidth: 1))
                            .accessibilityIdentifier("colormix.preview")
                            .accessibilityValue(preview ?? "")
                        VStack(alignment: .leading, spacing: ps.pt(4)) {
                            Text(st("Preview", "Vorschau"))
                                .font(.system(size: ps.font(13), weight: .semibold))
                                .foregroundStyle(PrusaColors.textPrimary)
                            Text(preview ?? st("Select valid colors", "Gültige Farben wählen"))
                                .font(.system(size: ps.font(12)))
                                .foregroundStyle(PrusaColors.textMuted)
                        }
                    }

                    Button {
                        saveRecipe()
                    } label: {
                        Text(st("Save mixed color", "Gemischte Farbe speichern"))
                            .frame(maxWidth: .infinity, minHeight: ps.touch(46))
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(PrusaColors.orange)
                    .disabled(preview == nil || selected.count < 2)
                    .accessibilityIdentifier("colormix.save")

                    if saveFailed {
                        Text(st("The mixed color could not be saved.", "Die gemischte Farbe konnte nicht gespeichert werden."))
                            .font(.system(size: ps.font(12)))
                            .foregroundStyle(.red)
                    }

                    if !recipes.isEmpty {
                        Text(st("Saved mixed colors", "Gespeicherte Mischfarben"))
                            .font(.system(size: ps.font(15), weight: .semibold))
                            .foregroundStyle(PrusaColors.textPrimary)
                        ForEach(recipes, id: \.id) { recipe in
                            HStack {
                                RoundedRectangle(cornerRadius: ps.pt(3))
                                    .fill(Color(hexString: recipe.color ?? "#808080") ?? PrusaColors.panelRaised)
                                    .frame(width: ps.touch(28), height: ps.touch(28))
                                Text(recipe.components.map { "\($0.head + 1)" }.joined(separator: " + "))
                                    .font(.system(size: ps.font(13)))
                                    .foregroundStyle(PrusaColors.textPrimary)
                                Spacer()
                                Button(role: .destructive) {
                                    recipes.removeAll { $0.id == recipe.id }
                                    _ = model.saveColorMix(recipes)
                                } label: {
                                    Image(systemName: "trash")
                                }
                                .accessibilityLabel(st("Delete mixed color", "Mischfarbe löschen"))
                            }
                            .padding(ps.pt(10))
                            .background(PrusaColors.panelRaised)
                            .clipShape(RoundedRectangle(cornerRadius: ps.pt(6)))
                            .accessibilityIdentifier("colormix.recipe.\(recipe.id)")
                        }
                    }

                    Button(st("Done", "Fertig"), action: onClose)
                        .buttonStyle(.bordered)
                        .tint(PrusaColors.orange)
                        .frame(maxWidth: .infinity, minHeight: ps.touch(46))
                        .accessibilityIdentifier("colormix.done")
                }
                .padding(ps.pt(20))
            }
            .background(PrusaColors.background.ignoresSafeArea())
            .navigationTitle(st("ColorMix", "ColorMix"))
            .onAppear { recipes = model.colorMixRecipes() }
        }
    }

    private func toggle(_ index: Int) {
        if let existing = selected.firstIndex(of: index) {
            guard selected.count > 2 else { return }
            selected.remove(at: existing)
        } else if selected.count < 3 {
            selected.append(index)
        } else {
            selected.removeLast()
            selected.append(index)
        }
    }

    private func saveRecipe() {
        guard let preview else { return }
        // IDs der virtuellen Extruder beginnen strikt hinter den physischen
        // Positionen: bei acht Positionen ist die erste Mischung also 9.
        let nextID = (recipes.map(\.id).max() ?? Int32(model.extruderCount)) + 1
        let recipe = ColorMixRecipe(id: nextID, components: components, color: preview)
        recipes.append(recipe)
        if !model.saveColorMix(recipes) {
            recipes.removeLast()
            saveFailed = true
        } else {
            saveFailed = false
        }
    }
}
