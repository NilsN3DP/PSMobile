# Android 1:1 from iOS – the twin rules

Since September 2026: **the iOS app is the reference, Android is a
line-by-line translation of it.** Branch conventions: work happens on one
branch; the public `main` is a snapshot.

## Why

Two user interfaces maintained separately drift apart. So every SwiftUI file
under `ios/PSMobile/Screens` and `ios/PSMobile/UI` has exactly one Kotlin file
with the same name under `android/app/src/main/java/de/psmobile/ui/`. Whoever
changes iOS then changes the same place in the Kotlin file of the same name –
no more, no less.

What stays Android-specific (infrastructure, not UI):

| Android | Role | iOS counterpart |
|---|---|---|
| `slicing/SlicerService.kt` | core session, foreground service for slicing | parts of `SlicerModel.swift` |
| `SlicerModel.kt` | **1:1 facade** with the names from `SlicerModel.swift`, delegates to the service | `SlicerModel.swift` |
| `core/PsmCore.kt`, `core/PsmViewport.kt` | JNI binding | `Core/PsmCore*.swift`, `Viewport/PsmViewport.swift` |
| `ui/SceneView.kt` + `ui/ViewportView.kt` | GL viewport; `ViewportView` takes the parameters of `ViewportView.swift` | `Viewport/ViewportView.swift` |
| `net/*` | PrusaLink, OctoPrint, remote slicing, keystore | `Networking/*` |
| `MainActivity.kt` | file picking (SAF), sharing, intents; its Compose content is the port of `PSMobileApp.swift` | `PSMobileApp.swift` |

## Translation table SwiftUI → Compose

| Swift | Kotlin |
|---|---|
| `struct XyView: View { var body }` | `@Composable fun XyView(...)` – same name, same parameters (closures → lambdas, bindings → value + `onXyChange`) |
| `@EnvironmentObject var model: SlicerModel` | parameter `model: SlicerModel` (pass through) or `LocalSlicerModel.current` |
| `@Environment(\.psScale) var ps` | `val ps = LocalPsScale.current` |
| `ps.pt(16)` | `ps.pt(16)` → `Dp` (identical maths; the app density is already compressed, `pt` returns `16.dp`) |
| `ps.font(13)` | `ps.font(13)` → `TextUnit` |
| `ps.touch(44)` | `ps.touch(44)` → `Dp` (never below 44 physical dp) |
| `ps.windowSize.width/height` | `ps.windowSize.width/height` (Dp in the same unit system as `pt`) |
| `ps.factor` | `ps.factor` |
| `st("English", "Deutsch")` | `st("English", "Deutsch")` (top-level function in `Texte.kt`) |
| `PsUiCatalog.tr("Layers")` | `PsUiCatalog.tr("Layers")` |
| `PsUiCatalog.pages("print", extruderCount:)` | `PsUiCatalog.pages("print", extruderCount)` |
| `PrusaColors.orange` … | `PrusaColors.orange` |
| `Color(hexString: s)` | `colorFromHex(s)` → `Color?` |
| `Color(hex: 0xED6B21)` | `Color(0xFFED6B21)` |
| `VStack(alignment:spacing:)` | `Column(verticalArrangement = Arrangement.spacedBy(...), horizontalAlignment = ...)` |
| `HStack` | `Row(horizontalArrangement = Arrangement.spacedBy(...), verticalAlignment = ...)` |
| `ZStack(alignment:)` | `Box(contentAlignment = ...)` |
| `Spacer()` | `Spacer(Modifier.weight(1f))` (in Row/Column); `Spacer(minLength: x)` → `Spacer(Modifier.height(x))` or `.width(x)` |
| `.padding(x)` / `.padding(.horizontal, x)` | `Modifier.padding(x)` / `.padding(horizontal = x)` |
| `.frame(width:height:)` | `Modifier.size(w, h)`; `.frame(maxWidth: .infinity)` → `.fillMaxWidth()`; `.frame(minHeight:)` → `.heightIn(min = )` |
| `.background(PrusaColors.panel)` | `Modifier.background(PrusaColors.panel)` |
| `.clipShape(RoundedRectangle(cornerRadius: r))` | `Modifier.clip(RoundedCornerShape(r))` |
| `.overlay(RoundedRectangle(...).stroke(c, lineWidth: 1))` | `Modifier.border(1.dp, c, RoundedCornerShape(r))` |
| `.clipShape(Capsule())` | `Modifier.clip(RoundedCornerShape(50))` |
| `Text(x).font(.system(size: ps.font(13), weight: .semibold))` | `Text(x, fontSize = ps.font(13), fontWeight = FontWeight.SemiBold)` |
| `.foregroundStyle(c)` | `color = c` or `LocalContentColor` |
| `.lineLimit(1)` | `maxLines = 1, overflow = TextOverflow.Ellipsis` |
| `Image(systemName: "plus")` | `SfSymbol("plus")` (table in `SfSymbol.kt`; size via `Modifier.size(ps.font(18).value.dp)` or `Modifier.size(x)`) |
| `Button(action:) { label }.buttonStyle(.plain)` | `Box(Modifier.clickable(onClick = ...)) { label }` **without** Material button styling |
| `Button("Text") {}` (system button) | `TextButton` only where iOS really shows the system button; otherwise as above |
| `Toggle` | `Switch` (Material3, orange) |
| `TextField` | `BasicTextField` with panel background in the iOS style, or `OutlinedTextField` only where iOS uses `.textFieldStyle(.roundedBorder)` |
| `Picker` / `Menu` | `DropdownMenu` anchored to a clickable field |
| `ScrollView` | `Column(Modifier.verticalScroll(rememberScrollState()))` or `LazyColumn` for long lists |
| `List` | `LazyColumn` with the same rows |
| `ForEach(items, id:)` | `items.forEach {}` or `items(items, key = …)` in lazy containers |
| `.sheet(isPresented:)` | `ModalBottomSheet` (Material3) with `ScaledOverlay` **or** – for full-screen sheets on the iPad – `Dialog` with `usePlatformDefaultWidth = false`. Content and labels 1:1 |
| `.alert(title, isPresented:) { buttons } message: {}` | `AlertDialog` from `de.psmobile.ui.theme` (scaled) with the same buttons/texts |
| `.confirmationDialog(title) { buttons }` | `AlertDialog` with the buttons as a list, or `ModalBottomSheet` with rows (as iPadOS renders it) |
| `.contextMenu { … }` | `DropdownMenu` after `combinedClickable(onLongClick)` |
| `.onLongPressGesture` | `combinedClickable(onLongClick = …)` or `pointerInput { detectTapGestures(onLongPress = …) }` |
| `DragGesture` | `pointerInput { detectDragGestures(...) }` |
| `.accessibilityIdentifier("x")` | `Modifier.testTag("x")` – **identical string** |
| `PSMarke(name: "x")` | `PSMarke(name = "x")` (1×1 dp box with testTag) |
| `@State private var x = false` | `var x by remember { mutableStateOf(false) }` |
| `@AppStorage(key) var x = default` | `val einstellungen = LocalAppSettingsStore.current` + `einstellungen.bool(key, standard)` (or `rememberAppSetting(key, standard)`) |
| `.onAppear {}` | `LaunchedEffect(Unit) {}` |
| `.onChange(of: x) {}` | `LaunchedEffect(x) {}` |
| `withAnimation {}` | `animate*AsState` or leave out |
| `GeometryReader` | `BoxWithConstraints` |
| `.fileImporter(isPresented:, allowedContentTypes:)` | lambda parameter `onPickFile()` etc., filled by `MainActivity` with the SAF picker |
| `ShareLink(item: url)` | lambda `onShare(file)` → `MainActivity.shareFile(File)` |
| `UIPasteboard` | `LocalClipboardManager` |
| `UIDevice.current.userInterfaceIdiom == .pad` | `ps.windowSize.width >= 600` (tablet width); where iOS checks `.pad || width >= 760`, `width >= 760` is enough |
| `Task { … }` / `DispatchQueue.main.asyncAfter` | `rememberCoroutineScope().launch { delay(...) }` |
| `Int32` / `SIMD3<Float>` | `Int` / `Triple<Float, Float, Float>` (`.first/.second/.third` instead of `.x/.y/.z`) |
| `URL` (file) | `java.io.File`; external picks arrive as `android.net.Uri` via `MainActivity` |

## Allowed exceptions

- **Rotation angles**: `PsmCore.ObjectInfo.rotation` is already in **degrees** on Android (iOS: radians). Do not convert.
- **Files**: saving/opening goes through `SlicerModel` (the app's project folder) as on iOS; the system file picker is provided by `MainActivity`. Sharing uses the Android share sheet.
- **Credentials**: Android Keystore instead of Keychain; the stores under `net/` stay, the UI above them is the one from iOS.
- **System back**: `BackHandler` wherever iOS has a back button – does the same as the button.
- **Android-only functions** (profile update channel, USB export, backup folder): stay in `MainActivity`/service, but get **no** controls of their own that iOS does not have – except where iOS has an entry point with the same purpose.

## What every ported file must satisfy

1. Same file name, same public composables as the Swift structs.
2. All helpers `private` (avoid name collisions in the package).
3. Every label comes from the Swift original (`st(...)`, `PsUiCatalog.tr(...)`, fixed texts) – no new words, no rephrasing.
4. Every `accessibilityIdentifier` becomes a `testTag` with the identical string.
5. Order, visibility conditions and states (`if`, `disabled`, `opacity`) are 1:1 as in the Swift code.
6. Dimensions via `ps.pt/ps.font/ps.touch`, colours only from `PrusaColors`.
7. Shorten or carry over the original's comments – but no references to the old Android UI.

## Build

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" \
  ./android/gradlew -p android :app:assembleProductionDebug --console=plain
```
