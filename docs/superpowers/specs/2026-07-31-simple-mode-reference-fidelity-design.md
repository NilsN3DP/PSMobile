# Simple Mode — mobile reference-fidelity design

## Goal

Replace the current card-based Easy UI with a direct mobile slicer workspace.
The supplied Prusa mobile reference is the visual and interaction target. The
product label is **Simple Mode**; no screen, string, or logo uses “EasyPrint”.
The user states Prusa approves this close visual direction.

## Scope

This design applies only to Simple Mode. Advanced Mode keeps its existing
information architecture and is not redesigned by this work.

## Phone composition

1. A compact, dark application header contains account, the title `Simple Mode`,
   and notifications.
2. Directly below is a dense, square-cornered floating toolbar. Its order is:
   Projects, Printer, Material, Settings, Preview, G-Code/Print. The primary
   end action is orange; inactive Preview is subdued.
3. The default content is immediately the actual 3D build-plate workspace.
   There is no card dashboard, setup summary page, or mandatory preflight page.
4. Existing viewport controls remain over the workspace: object/plate actions,
   transform affordances, and undo/redo.
5. `+ Modell hinzufügen` is an orange, rectangular floating action at the lower
   right above the navigation. It opens the existing import/model workflow.
6. A persistent dark bottom navigation remains available. It is visual chrome
   around the workspace, not a replacement for the primary toolbar.
7. Printer, Material, Supports/Adhesion, and Print Settings open dark sheets or
   overlays above the workspace. They retain search and configured-profile
   behavior, but use the reference hierarchy rather than a home screen of tiles.

## Submenus — reference-aligned

The supplied reference screens are the interaction and visual target for the
Simple Mode submenus as well as the workspace. Existing app data/services are
reused; only the presentation and navigation change.

- **Projects:** dark, scrollable project list with thumbnail, project name,
  printer, material, date, search field, per-item overflow action, and a clear
  `New Project` action. It opens as a full overlay/modal above the workspace.
- **Printer:** compact configured-printer selector. On phone it is a full dark
  sheet/overlay; on tablet it may be a two-column grid like the reference.
  Cards identify the printer model, state, configured filament and nozzle
  summary. Simple Mode chooses a model only; nozzle mapping remains internal.
- **Material:** first show a slim material palette with selected material slots
  and `Add new`; choosing a slot opens a searchable spool/material browser.
  The browser includes material-type and colour quick filters, configured spools,
  and a concise basic-material comparison. It must not use the prior oversized
  dashboard cards.
- **Supports:** a dark selection overlay with the reference’s visual hierarchy:
  Disabled, Everywhere (Snug/Organic), and Build plate only (Snug/Organic).
  Short explanatory copy is retained. The selected option uses the orange
  accent; selection writes the existing support setting.
- **Adhesion:** separate dark selection overlay reached from Settings/Supports,
  with Disabled, Automatic, and Outline around the model (brim) equivalents.
  It writes the existing brim setting. On a phone it is a sheet; on tablet it
  may be an in-place context panel.
- **Print Settings:** compact three-column reference layout: Print Profile,
  Infill, Shell Thickness. It exposes safe Simple Mode choices and reset action,
  while Advanced remains the route to uncommon expert parameters.

All submenu overlays provide a clear back/close affordance, preserve the current
workspace and selected model, and respect the dark system/root background.

## Visual language

- Near-black chrome (`#151619` family), charcoal controls, and a neutral-grey
  viewport.
- Orange is reserved for active state and primary action (`#f36f21` family).
- Toolbar buttons are compact, rectangular, icon-first, with fine dividers and
  light elevation. Avoid oversized rounded cards and dashboard-style sections.
- Typography is compact and functional; title/actions remain legible at phone
  scale. Use localized German labels already present in the app.
- Build plate remains the visual focus. Controls never cover the primary object
  manipulation area without a deliberate sheet/overlay state.

## Responsive behavior

- Phone portrait uses the reference-style header, floating toolbar, workspace,
  overlay action, and bottom navigation.
- Phone landscape preserves the workspace and converts the toolbar to a compact
  horizontal row without losing the primary G-Code/Print action.
- Tablet keeps the same visual family. The toolbar gains room and selection
  sheets may become a fixed or side context panel, but the workspace remains
  primary and no dashboard cards are introduced.

## Data and actions

- The printer selector displays configured printer **models**. Nozzle variants
  stay internal until the slice overview.
- Material, supports, adhesion, and Print Settings retain the selections already
  implemented in the shared slicer service.
- Preview and Print/G-Code keep their existing slice action wiring until a
  separate printer-dispatch API is introduced; the Simple Mode UI must not fake
  a completed print state.
- Loading a model changes the build plate immediately and enables the existing
  slice path once required selections are present.

## Acceptance criteria

1. A cold Simple Mode launch shows the workspace, not a tile/card dashboard.
2. The phone composition visibly matches the supplied reference in hierarchy:
   header → dense toolbar → build plate → floating model action → bottom nav.
3. The visible product name is `Simple Mode`, never `EasyPrint`.
4. All toolbar actions and the model-add action are touch reachable at phone
   width; sheets close without losing the workspace or current selection.
5. Portrait, landscape, Medium tablet, and Expanded tablet have no clipped
   primary action and no white system/root background.
6. The five Simple Mode submenus follow their matching supplied reference
   layout rather than generic card/dashboard patterns.
7. Emulator smoke evidence covers those sizes plus import/slice readiness and
   checks for fatal app process errors.

## Out of scope

- Renaming the overall application.
- Advanced Mode redesign.
- New printer dispatch protocol, cloud synchronization, ZIP import, or iOS port.
