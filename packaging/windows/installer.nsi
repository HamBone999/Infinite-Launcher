; Infinite Launcher -- Windows installer. Built on Linux by package.sh with makensis.
; Per-user install: no administrator prompt, nothing outside the user's own folders.
Unicode true
!include "MUI2.nsh"

!ifndef VERSION
  !define VERSION "0.0.0"
!endif
!ifndef STAGE
  !define STAGE "stage"
!endif
!ifndef OUTFILE
  !define OUTFILE "InfiniteLauncher-Setup.exe"
!endif

Name "Infinite Launcher"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\Infinite Launcher"
InstallDirRegKey HKCU "Software\InfiniteLauncher" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
BrandingText "Minecraft Infinite Reborn"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "Infinite Launcher"
VIAddVersionKey "FileDescription" "Infinite Launcher installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "Minecraft is a trademark of Mojang AB. Not affiliated with Mojang or Microsoft."

!define MUI_ICON "${STAGE}\icon.ico"
!define MUI_UNICON "${STAGE}\icon.ico"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\runtime\bin\javaw.exe"
!define MUI_FINISHPAGE_RUN_PARAMETERS "-jar $\"$INSTDIR\InfiniteLauncher.jar$\""
!define MUI_FINISHPAGE_RUN_TEXT "Start Infinite Launcher"

!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Install"
  SetOutPath "$INSTDIR"
  ; An upgrade replaces the bundled Java wholesale rather than layering files over it.
  RMDir /r "$INSTDIR\runtime"
  File "${STAGE}\InfiniteLauncher.jar"
  File "${STAGE}\icon.ico"
  File /r "${STAGE}\runtime"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateShortcut "$SMPROGRAMS\Infinite Launcher.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-jar $\"$INSTDIR\InfiniteLauncher.jar$\"" "$INSTDIR\icon.ico" 0
  CreateShortcut "$DESKTOP\Infinite Launcher.lnk" "$INSTDIR\runtime\bin\javaw.exe" "-jar $\"$INSTDIR\InfiniteLauncher.jar$\"" "$INSTDIR\icon.ico" 0

  WriteRegStr HKCU "Software\InfiniteLauncher" "InstallDir" "$INSTDIR"
  !define UNINST "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteLauncher"
  WriteRegStr HKCU "${UNINST}" "DisplayName" "Infinite Launcher"
  WriteRegStr HKCU "${UNINST}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST}" "Publisher" "HamBone999"
  WriteRegStr HKCU "${UNINST}" "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegStr HKCU "${UNINST}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegStr HKCU "${UNINST}" "URLInfoAbout" "https://github.com/HamBone999/Minecraft-Infinite-Reborn"
  WriteRegDWORD HKCU "${UNINST}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST}" "NoRepair" 1
  WriteRegDWORD HKCU "${UNINST}" "EstimatedSize" 110000
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\Infinite Launcher.lnk"
  Delete "$DESKTOP\Infinite Launcher.lnk"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteLauncher"
  DeleteRegKey HKCU "Software\InfiniteLauncher"
  ; Worlds, settings and sign-ins live in %APPDATA%\InfiniteLauncher and are deliberately kept.
SectionEnd
