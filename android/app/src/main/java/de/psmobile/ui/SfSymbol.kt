package de.psmobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HorizontalSplit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * Apple-SF-Symbol-Namen auf Material-Icons abgebildet.
 *
 * Die iOS-Bildschirme schreiben `Image(systemName: "trash")`; die 1:1
 * portierten Compose-Bildschirme schreiben `SfSymbol("trash")` und
 * bekommen das naechstliegende Material-Icon. So bleibt die Portierung
 * Zeile fuer Zeile lesbar, ohne dass jede Stelle selbst nach einem
 * Icon suchen muss.
 *
 * Alle Namen, die in ios/PSMobile/ vorkommen, stehen in der Tabelle;
 * dazu die gaengigen Nachbarn (.fill-Varianten, Werkzeugsymbole), damit
 * ein spaeter nachgezogener Swift-Bildschirm nicht gleich ins Leere
 * greift. Unbekannte Namen landen bei einem Fragezeichen - sichtbar,
 * damit man es beim Testen bemerkt.
 *
 * Jedes Icon hier ist gegen material-icons-core bzw.
 * material-icons-extended 1.7.6 (Compose-BOM 2024.12.01) geprueft.
 *
 * | SF Symbol                                   | Material-Icon                               | in iOS |
 * |---------------------------------------------|---------------------------------------------|--------|
 * | arrow.counterclockwise                      | Outlined.Refresh                            | ja     |
 * | arrow.left.and.right                        | Outlined.SwapHoriz                          | ja     |
 * | arrow.right                                 | AutoMirrored.Outlined.ArrowForward          | ja     |
 * | arrow.triangle.2.circlepath                 | Outlined.Autorenew                          | ja     |
 * | arrow.up.and.down.and.arrow.left.and.right  | Outlined.OpenWith                           | ja     |
 * | arrow.up.left.and.arrow.down.right          | Outlined.OpenInFull                         | ja     |
 * | arrow.uturn.backward                        | AutoMirrored.Outlined.Undo                  | ja     |
 * | arrow.uturn.forward                         | AutoMirrored.Outlined.Redo                  | ja     |
 * | bolt.fill                                   | Filled.Bolt                                 | ja     |
 * | checkmark                                   | Outlined.Check                              | ja     |
 * | checkmark.circle.fill                       | Filled.CheckCircle                          | ja     |
 * | checkmark.square.fill                       | Filled.CheckBox                             | ja     |
 * | chevron.down                                | Outlined.KeyboardArrowDown                  | ja     |
 * | chevron.left                                | AutoMirrored.Outlined.KeyboardArrowLeft     | ja     |
 * | chevron.right                               | AutoMirrored.Outlined.KeyboardArrowRight    | ja     |
 * | chevron.up.chevron.down                     | Outlined.UnfoldMore                         | ja     |
 * | circle.circle                               | Outlined.RadioButtonChecked                 | ja     |
 * | circle.lefthalf.filled                      | Outlined.Tonality                           | ja     |
 * | clock                                       | Outlined.Schedule                           | ja     |
 * | cloud                                       | Outlined.Cloud                              | ja     |
 * | cloud.fill                                  | Filled.Cloud                                | ja     |
 * | cube                                        | Outlined.ViewInAr                           | ja     |
 * | cube.fill                                   | Filled.ViewInAr                             | ja     |
 * | doc                                         | Outlined.InsertDriveFile                    | ja     |
 * | doc.on.clipboard                            | Outlined.ContentPaste                       | ja     |
 * | doc.on.doc                                  | Outlined.ContentCopy                        | ja     |
 * | exclamationmark.triangle.fill               | Filled.Warning                              | ja     |
 * | eye.fill                                    | Filled.Visibility                           | ja     |
 * | eye.slash                                   | Outlined.VisibilityOff                      | ja     |
 * | folder                                      | Outlined.FolderOpen                         | ja     |
 * | gearshape                                   | Outlined.Settings                           | ja     |
 * | hand.point.up.left                          | Outlined.TouchApp                           | ja     |
 * | house                                       | Outlined.Home                               | ja     |
 * | list.bullet.rectangle                       | AutoMirrored.Outlined.ListAlt               | ja     |
 * | lock                                        | Outlined.Lock                               | ja     |
 * | lock.fill                                   | Filled.Lock                                 | ja     |
 * | lock.open                                   | Outlined.LockOpen                           | ja     |
 * | magnifyingglass                             | Outlined.Search                             | ja     |
 * | minus.square                                | Outlined.IndeterminateCheckBox              | ja     |
 * | paintbrush.pointed                          | Outlined.Brush                              | ja     |
 * | paintpalette                                | Outlined.Palette                            | ja     |
 * | paperplane                                  | AutoMirrored.Outlined.Send                  | ja     |
 * | pencil                                      | Outlined.Edit                               | ja     |
 * | plus                                        | Outlined.Add                                | ja     |
 * | plus.square.on.square                       | Outlined.LibraryAdd                         | ja     |
 * | printer                                     | Outlined.Print                              | ja     |
 * | qrcode                                      | Outlined.QrCode                             | ja     |
 * | qrcode.viewfinder                           | Outlined.QrCodeScanner                      | ja     |
 * | scalemass                                   | Outlined.Scale                              | ja     |
 * | scribble                                    | Outlined.Gesture                            | ja     |
 * | scribble.variable                           | Outlined.Gesture                            | ja     |
 * | sidebar.left                                | AutoMirrored.Outlined.MenuOpen              | ja     |
 * | sidebar.right                               | AutoMirrored.Outlined.ViewSidebar           | ja     |
 * | slider.horizontal.3                         | Outlined.Tune                               | ja     |
 * | square                                      | Outlined.CheckBoxOutlineBlank               | ja     |
 * | square.3.layers.3d                          | Outlined.Layers                             | ja     |
 * | square.and.arrow.down                       | Outlined.Save                               | ja     |
 * | square.and.arrow.up                         | Outlined.Share                              | ja     |
 * | square.grid.2x2                             | Outlined.GridView                           | ja     |
 * | square.righthalf.filled                     | Outlined.Dashboard                          | ja     |
 * | square.split.2x1                            | Outlined.VerticalSplit                      | ja     |
 * | square.stack.3d.up                          | Outlined.Layers                             | ja     |
 * | trash                                       | Outlined.Delete                             | ja     |
 * | trash.slash                                 | Outlined.DeleteSweep                        | ja     |
 * | xmark                                       | Outlined.Close                              | ja     |
 * | xmark.circle.fill                           | Filled.Cancel                               | ja     |
 * | angle                                       | Outlined.Architecture                       |        |
 * | archivebox                                  | Outlined.Archive                            |        |
 * | arrow.2.squarepath                          | Outlined.Sync                               |        |
 * | arrow.clockwise                             | Outlined.Refresh                            |        |
 * | arrow.down                                  | Outlined.ArrowDownward                      |        |
 * | arrow.down.doc                              | Outlined.Download                           |        |
 * | arrow.down.right.and.arrow.up.left          | Outlined.FullscreenExit                     |        |
 * | arrow.down.to.line                          | Outlined.Download                           |        |
 * | arrow.left                                  | AutoMirrored.Outlined.ArrowBack             |        |
 * | arrow.left.arrow.right                      | Outlined.SwapHoriz                          |        |
 * | arrow.up                                    | Outlined.ArrowUpward                        |        |
 * | arrow.up.and.down                           | Outlined.SwapVert                           |        |
 * | arrow.up.arrow.down                         | Outlined.SwapVert                           |        |
 * | arrow.up.doc                                | Outlined.Upload                             |        |
 * | arrow.up.to.line                            | Outlined.Upload                             |        |
 * | bell                                        | Outlined.Notifications                      |        |
 * | bell.fill                                   | Filled.Notifications                        |        |
 * | bolt                                        | Outlined.Bolt                               |        |
 * | bolt.slash                                  | Outlined.FlashOff                           |        |
 * | bookmark                                    | Outlined.BookmarkBorder                     |        |
 * | calendar                                    | Outlined.CalendarToday                      |        |
 * | camera                                      | Outlined.CameraAlt                          |        |
 * | cart                                        | Outlined.ShoppingCart                       |        |
 * | chart.bar                                   | Outlined.BarChart                           |        |
 * | chart.line.uptrend.xyaxis                   | AutoMirrored.Outlined.TrendingUp            |        |
 * | chart.pie                                   | Outlined.PieChart                           |        |
 * | checkmark.circle                            | Outlined.CheckCircle                        |        |
 * | checkmark.square                            | Outlined.CheckBox                           |        |
 * | chevron.up                                  | Outlined.KeyboardArrowUp                    |        |
 * | circle                                      | Outlined.Circle                             |        |
 * | circle.fill                                 | Filled.Circle                               |        |
 * | circle.lefthalf.fill                        | Outlined.Tonality                           |        |
 * | circle.righthalf.filled                     | Outlined.Tonality                           |        |
 * | circle.grid.3x3                             | Outlined.Apps                               |        |
 * | clock.arrow.circlepath                      | Outlined.History                            |        |
 * | clock.fill                                  | Filled.Schedule                             |        |
 * | cloud.slash                                 | Outlined.CloudOff                           |        |
 * | cube.transparent                            | Outlined.ViewInAr                           |        |
 * | desktopcomputer                             | Outlined.DesktopWindows                     |        |
 * | doc.badge.plus                              | AutoMirrored.Outlined.NoteAdd               |        |
 * | doc.fill                                    | Filled.InsertDriveFile                      |        |
 * | doc.plaintext                               | Outlined.Description                        |        |
 * | doc.text                                    | Outlined.Description                        |        |
 * | doc.text.fill                               | Filled.Description                          |        |
 * | drop                                        | Outlined.WaterDrop                          |        |
 * | drop.fill                                   | Filled.WaterDrop                            |        |
 * | ellipsis                                    | Outlined.MoreHoriz                          |        |
 * | ellipsis.circle                             | Outlined.MoreHoriz                          |        |
 * | ellipsis.circle.fill                        | Filled.MoreHoriz                            |        |
 * | ellipsis.vertical                           | Outlined.MoreVert                           |        |
 * | envelope                                    | Outlined.Email                              |        |
 * | exclamationmark.circle                      | Outlined.Error                              |        |
 * | exclamationmark.circle.fill                 | Filled.Error                                |        |
 * | exclamationmark.triangle                    | Outlined.Warning                            |        |
 * | externaldrive                               | Outlined.Storage                            |        |
 * | eye                                         | Outlined.Visibility                         |        |
 * | eye.slash.fill                              | Filled.VisibilityOff                        |        |
 * | eyedropper                                  | Outlined.Colorize                           |        |
 * | eyedropper.halffull                         | Outlined.Colorize                           |        |
 * | flag                                        | Outlined.Flag                               |        |
 * | flag.fill                                   | Filled.Flag                                 |        |
 * | flame                                       | Outlined.Whatshot                           |        |
 * | flame.fill                                  | Filled.Whatshot                             |        |
 * | flip.horizontal                             | Outlined.Flip                               |        |
 * | folder.badge.plus                           | Outlined.CreateNewFolder                    |        |
 * | folder.fill                                 | Filled.Folder                               |        |
 * | gauge                                       | Outlined.Speed                              |        |
 * | gear                                        | Outlined.Settings                           |        |
 * | gearshape.2                                 | Outlined.Settings                           |        |
 * | gearshape.fill                              | Filled.Settings                             |        |
 * | globe                                       | Outlined.Language                           |        |
 * | gobackward                                  | Outlined.Replay                             |        |
 * | hammer                                      | Outlined.Build                              |        |
 * | hand.point.up.left.fill                     | Filled.TouchApp                             |        |
 * | hand.raised                                 | Outlined.PanTool                            |        |
 * | hand.tap                                    | Outlined.TouchApp                           |        |
 * | heart                                       | Outlined.FavoriteBorder                     |        |
 * | hourglass                                   | Outlined.HourglassEmpty                     |        |
 * | house.fill                                  | Filled.Home                                 |        |
 * | icloud                                      | Outlined.Cloud                              |        |
 * | icloud.and.arrow.down                       | Outlined.CloudDownload                      |        |
 * | icloud.and.arrow.up                         | Outlined.CloudUpload                        |        |
 * | icloud.fill                                 | Filled.Cloud                                |        |
 * | info.circle                                 | Outlined.Info                               |        |
 * | info.circle.fill                            | Filled.Info                                 |        |
 * | internaldrive                               | Outlined.Storage                            |        |
 * | ipad                                        | Outlined.Tablet                             |        |
 * | iphone                                      | Outlined.Smartphone                         |        |
 * | key                                         | Outlined.VpnKey                             |        |
 * | key.fill                                    | Filled.VpnKey                               |        |
 * | keyboard                                    | Outlined.Keyboard                           |        |
 * | laptopcomputer                              | Outlined.Laptop                             |        |
 * | largecircle.fill.circle                     | Outlined.RadioButtonChecked                 |        |
 * | lightbulb                                   | Outlined.Lightbulb                          |        |
 * | lightbulb.fill                              | Filled.Lightbulb                            |        |
 * | line.3.horizontal                           | Outlined.Menu                               |        |
 * | line.3.horizontal.decrease                  | Outlined.FilterList                         |        |
 * | line.3.horizontal.decrease.circle           | Outlined.FilterList                         |        |
 * | line.horizontal.3                           | Outlined.Menu                               |        |
 * | link                                        | Outlined.Link                               |        |
 * | link.slash                                  | Outlined.LinkOff                            |        |
 * | list.bullet                                 | AutoMirrored.Outlined.FormatListBulleted    |        |
 * | lock.open.fill                              | Filled.LockOpen                             |        |
 * | lock.shield                                 | Outlined.Security                           |        |
 * | map                                         | Outlined.Map                                |        |
 * | memorychip                                  | Outlined.Memory                             |        |
 * | cpu                                         | Outlined.Memory                             |        |
 * | minus                                       | Outlined.Remove                             |        |
 * | minus.circle                                | Outlined.RemoveCircleOutline                |        |
 * | minus.circle.fill                           | Filled.RemoveCircle                         |        |
 * | minus.magnifyingglass                       | Outlined.ZoomOut                            |        |
 * | move.3d                                     | Outlined.OpenWith                           |        |
 * | paintbrush                                  | Outlined.Brush                              |        |
 * | paintbrush.fill                             | Filled.Brush                                |        |
 * | paintbrush.pointed.fill                     | Filled.Brush                                |        |
 * | paintpalette.fill                           | Filled.Palette                              |        |
 * | pause                                       | Outlined.Pause                              |        |
 * | pause.circle                                | Outlined.PauseCircleOutline                 |        |
 * | pause.fill                                  | Filled.Pause                                |        |
 * | pencil.circle                               | Outlined.Edit                               |        |
 * | person                                      | Outlined.Person                             |        |
 * | person.fill                                 | Filled.Person                               |        |
 * | perspective                                 | Outlined.ScreenRotation                     |        |
 * | phone                                       | Outlined.Phone                              |        |
 * | photo                                       | Outlined.Image                              |        |
 * | play                                        | Outlined.PlayArrow                          |        |
 * | play.circle                                 | Outlined.PlayCircleOutline                  |        |
 * | play.fill                                   | Filled.PlayArrow                            |        |
 * | plus.app                                    | Outlined.AddBox                             |        |
 * | plus.circle                                 | Outlined.AddCircleOutline                   |        |
 * | plus.circle.fill                            | Filled.AddCircle                            |        |
 * | plus.magnifyingglass                        | Outlined.ZoomIn                             |        |
 * | plus.square                                 | Outlined.AddBox                             |        |
 * | printer.fill                                | Filled.Print                                |        |
 * | puzzlepiece                                 | Outlined.Extension                          |        |
 * | puzzlepiece.extension                       | Outlined.Extension                          |        |
 * | questionmark                                | AutoMirrored.Outlined.HelpOutline           |        |
 * | questionmark.circle                         | AutoMirrored.Outlined.HelpOutline           |        |
 * | questionmark.circle.fill                    | AutoMirrored.Filled.Help                    |        |
 * | rectangle.grid.2x2                          | Outlined.Dashboard                          |        |
 * | rectangle.portrait.and.arrow.right          | AutoMirrored.Outlined.Logout                |        |
 * | rectangle.split.2x1                         | Outlined.VerticalSplit                      |        |
 * | rectangle.split.3x1                         | Outlined.ViewColumn                         |        |
 * | rotate.3d                                   | Outlined.Rotate90DegreesCw                  |        |
 * | rotate.left                                 | Outlined.RotateLeft                         |        |
 * | rotate.right                                | Outlined.RotateRight                        |        |
 * | ruler                                       | Outlined.Straighten                         |        |
 * | ruler.fill                                  | Outlined.Straighten                         |        |
 * | scale.3d                                    | Outlined.OpenInFull                         |        |
 * | scissors                                    | Outlined.ContentCut                         |        |
 * | scope                                       | Outlined.CenterFocusStrong                  |        |
 * | screwdriver                                 | Outlined.Build                              |        |
 * | shield                                      | Outlined.Security                           |        |
 * | sidebar.leading                             | AutoMirrored.Outlined.MenuOpen              |        |
 * | sidebar.trailing                            | AutoMirrored.Outlined.ViewSidebar           |        |
 * | slider.vertical.3                           | Outlined.Tune                               |        |
 * | smallcircle.filled.circle                   | Outlined.RadioButtonChecked                 |        |
 * | snowflake                                   | Outlined.AcUnit                             |        |
 * | speedometer                                 | Outlined.Speed                              |        |
 * | square.and.arrow.down.fill                  | Filled.Save                                 |        |
 * | square.and.arrow.up.fill                    | Filled.Share                                |        |
 * | square.and.pencil                           | Outlined.Edit                               |        |
 * | square.dashed                               | Outlined.CropSquare                         |        |
 * | square.fill                                 | Filled.Square                               |        |
 * | square.grid.2x2.fill                        | Filled.GridView                             |        |
 * | square.grid.3x3                             | Outlined.Apps                               |        |
 * | square.on.square                            | Outlined.ContentCopy                        |        |
 * | square.split.1x2                            | Outlined.HorizontalSplit                    |        |
 * | square.stack                                | Outlined.Layers                             |        |
 * | square.stack.3d.down.right                  | Outlined.Layers                             |        |
 * | square.stack.3d.up.fill                     | Filled.Layers                               |        |
 * | square.3.stack.3d                           | Outlined.Layers                             |        |
 * | star                                        | Outlined.StarBorder                         |        |
 * | star.fill                                   | Filled.Star                                 |        |
 * | stop                                        | Outlined.Stop                               |        |
 * | stop.fill                                   | Filled.Stop                                 |        |
 * | stopwatch                                   | Outlined.Timer                              |        |
 * | swatchpalette                               | Outlined.Palette                            |        |
 * | switch.2                                    | Outlined.Tune                               |        |
 * | tablecells                                  | Outlined.TableChart                         |        |
 * | tag                                         | Outlined.LocalOffer                         |        |
 * | thermometer                                 | Outlined.Thermostat                         |        |
 * | thermometer.medium                          | Outlined.Thermostat                         |        |
 * | timer                                       | Outlined.Timer                              |        |
 * | trash.circle                                | Outlined.Delete                             |        |
 * | trash.fill                                  | Filled.Delete                               |        |
 * | trash.slash.fill                            | Filled.DeleteSweep                          |        |
 * | tray.and.arrow.down                         | Outlined.Download                           |        |
 * | tray.and.arrow.up                           | Outlined.Upload                             |        |
 * | view.3d                                     | Outlined.ScreenRotation                     |        |
 * | viewfinder                                  | Outlined.CenterFocusWeak                    |        |
 * | wifi                                        | Outlined.Wifi                               |        |
 * | wifi.slash                                  | Outlined.WifiOff                            |        |
 * | antenna.radiowaves.left.and.right           | Outlined.SettingsInputAntenna               |        |
 * | wrench                                      | Outlined.Build                              |        |
 * | wrench.adjustable                           | Outlined.Build                              |        |
 * | wrench.and.screwdriver                      | Outlined.Build                              |        |
 * | xmark.bin                                   | Outlined.DeleteSweep                        |        |
 * | xmark.circle                                | Outlined.Cancel                             |        |
 * | (unbekannt)                                 | AutoMirrored.Outlined.HelpOutline           |        |
 */

/**
 * Zeigt das Material-Gegenstueck zu einem SF Symbol - Gegenstueck zu
 * `Image(systemName:)` auf iOS.
 *
 * Bewusst getrennt von [PsIcon]: das hier sind Systemsymbole der
 * Bedienoberflaeche (Schliessen, Suchen, Zahnrad), [PsIcon] zeigt die
 * Original-SVGs aus PrusaSlicer fuer Werkzeuge und Einstellungen.
 */
@Composable
fun SfSymbol(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = sfSymbolVector(name),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

/**
 * Das Material-Icon zu einem SF-Symbol-Namen.
 *
 * Unbekannte Namen probieren es erst ohne `.fill`, dann ohne
 * `.circle`/`.square`-Anhang - so faengt `gearshape.circle.fill` bei
 * `gearshape` und nicht beim Fragezeichen.
 */
fun sfSymbolVector(name: String): ImageVector {
    SF_SYMBOLE[name]?.let { return it }
    val ohneFill = name.removeSuffix(".fill")
    SF_SYMBOLE[ohneFill]?.let { return it }
    for (anhang in listOf(".circle", ".square", ".rectangle")) {
        SF_SYMBOLE[ohneFill.removeSuffix(anhang)]?.let { return it }
    }
    return Icons.AutoMirrored.Outlined.HelpOutline
}

/** Wie viele Symbole die Tabelle kennt - fuer Selbsttest und Doku. */
val sfSymbolCount: Int get() = SF_SYMBOLE.size

private val SF_SYMBOLE: Map<String, ImageVector> by lazy {
    mapOf(
        // --- In ios/PSMobile/ verwendet ------------------------------------
        "arrow.counterclockwise" to Icons.Outlined.Refresh,
        "arrow.left.and.right" to Icons.Outlined.SwapHoriz,
        "arrow.right" to Icons.AutoMirrored.Outlined.ArrowForward,
        "arrow.triangle.2.circlepath" to Icons.Outlined.Autorenew,
        "arrow.up.and.down.and.arrow.left.and.right" to Icons.Outlined.OpenWith,
        "arrow.up.left.and.arrow.down.right" to Icons.Outlined.OpenInFull,
        "arrow.uturn.backward" to Icons.AutoMirrored.Outlined.Undo,
        "arrow.uturn.forward" to Icons.AutoMirrored.Outlined.Redo,
        "bolt.fill" to Icons.Filled.Bolt,
        "checkmark" to Icons.Outlined.Check,
        "checkmark.circle.fill" to Icons.Filled.CheckCircle,
        "checkmark.square.fill" to Icons.Filled.CheckBox,
        "chevron.down" to Icons.Outlined.KeyboardArrowDown,
        "chevron.left" to Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
        "chevron.right" to Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        "chevron.up.chevron.down" to Icons.Outlined.UnfoldMore,
        "circle.circle" to Icons.Outlined.RadioButtonChecked,
        "circle.lefthalf.filled" to Icons.Outlined.Tonality,
        "clock" to Icons.Outlined.Schedule,
        "cloud" to Icons.Outlined.Cloud,
        "cloud.fill" to Icons.Filled.Cloud,
        "cube" to Icons.Outlined.ViewInAr,
        "cube.fill" to Icons.Filled.ViewInAr,
        "doc" to Icons.Outlined.InsertDriveFile,
        "doc.on.clipboard" to Icons.Outlined.ContentPaste,
        "doc.on.doc" to Icons.Outlined.ContentCopy,
        "exclamationmark.triangle.fill" to Icons.Filled.Warning,
        "eye.fill" to Icons.Filled.Visibility,
        "eye.slash" to Icons.Outlined.VisibilityOff,
        "folder" to Icons.Outlined.FolderOpen,
        "gearshape" to Icons.Outlined.Settings,
        "hand.point.up.left" to Icons.Outlined.TouchApp,
        "house" to Icons.Outlined.Home,
        "list.bullet.rectangle" to Icons.AutoMirrored.Outlined.ListAlt,
        "lock" to Icons.Outlined.Lock,
        "lock.fill" to Icons.Filled.Lock,
        "lock.open" to Icons.Outlined.LockOpen,
        "magnifyingglass" to Icons.Outlined.Search,
        "minus.square" to Icons.Outlined.IndeterminateCheckBox,
        "paintbrush.pointed" to Icons.Outlined.Brush,
        "paintpalette" to Icons.Outlined.Palette,
        "paperplane" to Icons.AutoMirrored.Outlined.Send,
        "pencil" to Icons.Outlined.Edit,
        "plus" to Icons.Outlined.Add,
        "plus.square.on.square" to Icons.Outlined.LibraryAdd,
        "printer" to Icons.Outlined.Print,
        "qrcode" to Icons.Outlined.QrCode,
        "qrcode.viewfinder" to Icons.Outlined.QrCodeScanner,
        "scalemass" to Icons.Outlined.Scale,
        "scribble" to Icons.Outlined.Gesture,
        "scribble.variable" to Icons.Outlined.Gesture,
        "sidebar.left" to Icons.AutoMirrored.Outlined.MenuOpen,
        "sidebar.right" to Icons.AutoMirrored.Outlined.ViewSidebar,
        "slider.horizontal.3" to Icons.Outlined.Tune,
        "square" to Icons.Outlined.CheckBoxOutlineBlank,
        "square.3.layers.3d" to Icons.Outlined.Layers,
        "square.and.arrow.down" to Icons.Outlined.Save,
        "square.and.arrow.up" to Icons.Outlined.Share,
        "square.grid.2x2" to Icons.Outlined.GridView,
        "square.righthalf.filled" to Icons.Outlined.Dashboard,
        "square.split.2x1" to Icons.Outlined.VerticalSplit,
        "square.stack.3d.up" to Icons.Outlined.Layers,
        "trash" to Icons.Outlined.Delete,
        "trash.slash" to Icons.Outlined.DeleteSweep,
        "xmark" to Icons.Outlined.Close,
        "xmark.circle.fill" to Icons.Filled.Cancel,

        // --- Gaengige Nachbarn -----------------------------------------------
        "angle" to Icons.Outlined.Architecture,
        "antenna.radiowaves.left.and.right" to Icons.Outlined.SettingsInputAntenna,
        "archivebox" to Icons.Outlined.Archive,
        "arrow.2.squarepath" to Icons.Outlined.Sync,
        "arrow.clockwise" to Icons.Outlined.Refresh,
        "arrow.down" to Icons.Outlined.ArrowDownward,
        "arrow.down.doc" to Icons.Outlined.Download,
        "arrow.down.right.and.arrow.up.left" to Icons.Outlined.FullscreenExit,
        "arrow.down.to.line" to Icons.Outlined.Download,
        "arrow.left" to Icons.AutoMirrored.Outlined.ArrowBack,
        "arrow.left.arrow.right" to Icons.Outlined.SwapHoriz,
        "arrow.up" to Icons.Outlined.ArrowUpward,
        "arrow.up.and.down" to Icons.Outlined.SwapVert,
        "arrow.up.arrow.down" to Icons.Outlined.SwapVert,
        "arrow.up.doc" to Icons.Outlined.Upload,
        "arrow.up.to.line" to Icons.Outlined.Upload,
        "bell" to Icons.Outlined.Notifications,
        "bell.fill" to Icons.Filled.Notifications,
        "bolt" to Icons.Outlined.Bolt,
        "bolt.slash" to Icons.Outlined.FlashOff,
        "bookmark" to Icons.Outlined.BookmarkBorder,
        "calendar" to Icons.Outlined.CalendarToday,
        "camera" to Icons.Outlined.CameraAlt,
        "cart" to Icons.Outlined.ShoppingCart,
        "chart.bar" to Icons.Outlined.BarChart,
        "chart.line.uptrend.xyaxis" to Icons.AutoMirrored.Outlined.TrendingUp,
        "chart.pie" to Icons.Outlined.PieChart,
        "checkmark.circle" to Icons.Outlined.CheckCircle,
        "checkmark.square" to Icons.Outlined.CheckBox,
        "chevron.up" to Icons.Outlined.KeyboardArrowUp,
        "circle" to Icons.Outlined.Circle,
        "circle.fill" to Icons.Filled.Circle,
        "circle.lefthalf.fill" to Icons.Outlined.Tonality,
        "circle.righthalf.filled" to Icons.Outlined.Tonality,
        "circle.grid.3x3" to Icons.Outlined.Apps,
        "clock.arrow.circlepath" to Icons.Outlined.History,
        "clock.fill" to Icons.Filled.Schedule,
        "cloud.slash" to Icons.Outlined.CloudOff,
        "cpu" to Icons.Outlined.Memory,
        "cube.transparent" to Icons.Outlined.ViewInAr,
        "desktopcomputer" to Icons.Outlined.DesktopWindows,
        "doc.badge.plus" to Icons.AutoMirrored.Outlined.NoteAdd,
        "doc.fill" to Icons.Filled.InsertDriveFile,
        "doc.plaintext" to Icons.Outlined.Description,
        "doc.text" to Icons.Outlined.Description,
        "doc.text.fill" to Icons.Filled.Description,
        "drop" to Icons.Outlined.WaterDrop,
        "drop.fill" to Icons.Filled.WaterDrop,
        "ellipsis" to Icons.Outlined.MoreHoriz,
        "ellipsis.circle" to Icons.Outlined.MoreHoriz,
        "ellipsis.circle.fill" to Icons.Filled.MoreHoriz,
        "ellipsis.vertical" to Icons.Outlined.MoreVert,
        "envelope" to Icons.Outlined.Email,
        "exclamationmark.circle" to Icons.Outlined.Error,
        "exclamationmark.circle.fill" to Icons.Filled.Error,
        "exclamationmark.triangle" to Icons.Outlined.Warning,
        "externaldrive" to Icons.Outlined.Storage,
        "eye" to Icons.Outlined.Visibility,
        "eye.slash.fill" to Icons.Filled.VisibilityOff,
        "eyedropper" to Icons.Outlined.Colorize,
        "eyedropper.halffull" to Icons.Outlined.Colorize,
        "flag" to Icons.Outlined.Flag,
        "flag.fill" to Icons.Filled.Flag,
        "flame" to Icons.Outlined.Whatshot,
        "flame.fill" to Icons.Filled.Whatshot,
        "flip.horizontal" to Icons.Outlined.Flip,
        "folder.badge.plus" to Icons.Outlined.CreateNewFolder,
        "folder.fill" to Icons.Filled.Folder,
        "gauge" to Icons.Outlined.Speed,
        "gear" to Icons.Outlined.Settings,
        "gearshape.2" to Icons.Outlined.Settings,
        "gearshape.fill" to Icons.Filled.Settings,
        "globe" to Icons.Outlined.Language,
        "gobackward" to Icons.Outlined.Replay,
        "hammer" to Icons.Outlined.Build,
        "hand.point.up.left.fill" to Icons.Filled.TouchApp,
        "hand.raised" to Icons.Outlined.PanTool,
        "hand.tap" to Icons.Outlined.TouchApp,
        "heart" to Icons.Outlined.FavoriteBorder,
        "hourglass" to Icons.Outlined.HourglassEmpty,
        "house.fill" to Icons.Filled.Home,
        "icloud" to Icons.Outlined.Cloud,
        "icloud.and.arrow.down" to Icons.Outlined.CloudDownload,
        "icloud.and.arrow.up" to Icons.Outlined.CloudUpload,
        "icloud.fill" to Icons.Filled.Cloud,
        "info.circle" to Icons.Outlined.Info,
        "info.circle.fill" to Icons.Filled.Info,
        "internaldrive" to Icons.Outlined.Storage,
        "ipad" to Icons.Outlined.Tablet,
        "iphone" to Icons.Outlined.Smartphone,
        "key" to Icons.Outlined.VpnKey,
        "key.fill" to Icons.Filled.VpnKey,
        "keyboard" to Icons.Outlined.Keyboard,
        "laptopcomputer" to Icons.Outlined.Laptop,
        "largecircle.fill.circle" to Icons.Outlined.RadioButtonChecked,
        "lightbulb" to Icons.Outlined.Lightbulb,
        "lightbulb.fill" to Icons.Filled.Lightbulb,
        "line.3.horizontal" to Icons.Outlined.Menu,
        "line.3.horizontal.decrease" to Icons.Outlined.FilterList,
        "line.3.horizontal.decrease.circle" to Icons.Outlined.FilterList,
        "line.horizontal.3" to Icons.Outlined.Menu,
        "link" to Icons.Outlined.Link,
        "link.slash" to Icons.Outlined.LinkOff,
        "list.bullet" to Icons.AutoMirrored.Outlined.FormatListBulleted,
        "lock.open.fill" to Icons.Filled.LockOpen,
        "lock.shield" to Icons.Outlined.Security,
        "map" to Icons.Outlined.Map,
        "memorychip" to Icons.Outlined.Memory,
        "minus" to Icons.Outlined.Remove,
        "minus.circle" to Icons.Outlined.RemoveCircleOutline,
        "minus.circle.fill" to Icons.Filled.RemoveCircle,
        "minus.magnifyingglass" to Icons.Outlined.ZoomOut,
        "move.3d" to Icons.Outlined.OpenWith,
        "paintbrush" to Icons.Outlined.Brush,
        "paintbrush.fill" to Icons.Filled.Brush,
        "paintbrush.pointed.fill" to Icons.Filled.Brush,
        "paintpalette.fill" to Icons.Filled.Palette,
        "pause" to Icons.Outlined.Pause,
        "pause.circle" to Icons.Outlined.PauseCircleOutline,
        "pause.fill" to Icons.Filled.Pause,
        "pencil.circle" to Icons.Outlined.Edit,
        "person" to Icons.Outlined.Person,
        "person.fill" to Icons.Filled.Person,
        "perspective" to Icons.Outlined.ScreenRotation,
        "phone" to Icons.Outlined.Phone,
        "photo" to Icons.Outlined.Image,
        "play" to Icons.Outlined.PlayArrow,
        "play.circle" to Icons.Outlined.PlayCircleOutline,
        "play.fill" to Icons.Filled.PlayArrow,
        "plus.app" to Icons.Outlined.AddBox,
        "plus.circle" to Icons.Outlined.AddCircleOutline,
        "plus.circle.fill" to Icons.Filled.AddCircle,
        "plus.magnifyingglass" to Icons.Outlined.ZoomIn,
        "plus.square" to Icons.Outlined.AddBox,
        "printer.fill" to Icons.Filled.Print,
        "puzzlepiece" to Icons.Outlined.Extension,
        "puzzlepiece.extension" to Icons.Outlined.Extension,
        "questionmark" to Icons.AutoMirrored.Outlined.HelpOutline,
        "questionmark.circle" to Icons.AutoMirrored.Outlined.HelpOutline,
        "questionmark.circle.fill" to Icons.AutoMirrored.Filled.Help,
        "rectangle.grid.2x2" to Icons.Outlined.Dashboard,
        "rectangle.portrait.and.arrow.right" to Icons.AutoMirrored.Outlined.Logout,
        "rectangle.split.2x1" to Icons.Outlined.VerticalSplit,
        "rectangle.split.3x1" to Icons.Outlined.ViewColumn,
        "rotate.3d" to Icons.Outlined.Rotate90DegreesCw,
        "rotate.left" to Icons.Outlined.RotateLeft,
        "rotate.right" to Icons.Outlined.RotateRight,
        "ruler" to Icons.Outlined.Straighten,
        "ruler.fill" to Icons.Outlined.Straighten,
        "scale.3d" to Icons.Outlined.OpenInFull,
        "scissors" to Icons.Outlined.ContentCut,
        "scope" to Icons.Outlined.CenterFocusStrong,
        "screwdriver" to Icons.Outlined.Build,
        "shield" to Icons.Outlined.Security,
        "sidebar.leading" to Icons.AutoMirrored.Outlined.MenuOpen,
        "sidebar.trailing" to Icons.AutoMirrored.Outlined.ViewSidebar,
        "slider.vertical.3" to Icons.Outlined.Tune,
        "smallcircle.filled.circle" to Icons.Outlined.RadioButtonChecked,
        "snowflake" to Icons.Outlined.AcUnit,
        "speedometer" to Icons.Outlined.Speed,
        "square.and.arrow.down.fill" to Icons.Filled.Save,
        "square.and.arrow.up.fill" to Icons.Filled.Share,
        "square.and.pencil" to Icons.Outlined.Edit,
        "square.dashed" to Icons.Outlined.CropSquare,
        "square.fill" to Icons.Filled.Square,
        "square.grid.2x2.fill" to Icons.Filled.GridView,
        "square.grid.3x3" to Icons.Outlined.Apps,
        "square.on.square" to Icons.Outlined.ContentCopy,
        "square.split.1x2" to Icons.Outlined.HorizontalSplit,
        "square.stack" to Icons.Outlined.Layers,
        "square.stack.3d.down.right" to Icons.Outlined.Layers,
        "square.stack.3d.up.fill" to Icons.Filled.Layers,
        "square.3.stack.3d" to Icons.Outlined.Layers,
        "star" to Icons.Outlined.StarBorder,
        "star.fill" to Icons.Filled.Star,
        "stop" to Icons.Outlined.Stop,
        "stop.fill" to Icons.Filled.Stop,
        "stopwatch" to Icons.Outlined.Timer,
        "swatchpalette" to Icons.Outlined.Palette,
        "switch.2" to Icons.Outlined.Tune,
        "tablecells" to Icons.Outlined.TableChart,
        "tag" to Icons.Outlined.LocalOffer,
        "thermometer" to Icons.Outlined.Thermostat,
        "thermometer.medium" to Icons.Outlined.Thermostat,
        "timer" to Icons.Outlined.Timer,
        "trash.circle" to Icons.Outlined.Delete,
        "trash.fill" to Icons.Filled.Delete,
        "trash.slash.fill" to Icons.Filled.DeleteSweep,
        "tray.and.arrow.down" to Icons.Outlined.Download,
        "tray.and.arrow.up" to Icons.Outlined.Upload,
        "view.3d" to Icons.Outlined.ScreenRotation,
        "viewfinder" to Icons.Outlined.CenterFocusWeak,
        "wifi" to Icons.Outlined.Wifi,
        "wifi.slash" to Icons.Outlined.WifiOff,
        "wrench" to Icons.Outlined.Build,
        "wrench.adjustable" to Icons.Outlined.Build,
        "wrench.and.screwdriver" to Icons.Outlined.Build,
        "xmark.bin" to Icons.Outlined.DeleteSweep,
        "xmark.circle" to Icons.Outlined.Cancel,
    )
}
