; build.bat passes its APP_VERSION as /DAppVer; this default is for compiling the script by hand
#ifndef AppVer
  #define AppVer "1.1.0"
#endif

[Setup]
AppName=Minecraft Server Launcher
AppVersion={#AppVer}
AppPublisher=Omree
AppPublisherURL=https://github.com/omreetafesh/MinCraftServerLauncher
AppSupportURL=https://github.com/omreetafesh/MinCraftServerLauncher/issues
AppCopyright=Copyright (C) 2025 Omree

DefaultDirName={localappdata}\Programs\Minecraft Server Launcher
DefaultGroupName=Minecraft Tools
DisableProgramGroupPage=yes
DisableWelcomePage=no
DisableReadyPage=yes

OutputDir=dist
OutputBaseFilename=MinecraftServerLauncher-Setup
SetupIconFile=dist\icon.ico

; Dark "midnight / emerald" look to match the launcher (needs Inno Setup 6.7+):
; the launcher's aurora as the page background, with a transparent logo banner and header icon on top.
; All art comes from tools\MakeWizardArt.java; several sizes each so it stays sharp at any DPI.
WizardStyle=modern dark hidebevels includetitlebar
WizardBackColor=#0b0e14
WizardBackImageFile=dist\wizard_back_*.png
WizardImageFile=dist\wizard_image_*.png
WizardSmallImageFile=dist\wizard_icon_*.png
WizardSizePercent=130

; No solid compression — allows DisableReadyPage to be respected
Compression=lzma2/max
SolidCompression=no

UninstallDisplayIcon={app}\Minecraft Server Launcher.exe
UninstallDisplayName=Minecraft Server Launcher
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to%nMinecraft Server Launcher
WelcomeLabel2=This wizard will install Minecraft Server Launcher {#SetupSetting('AppVersion')} on your computer.%n%nManage Minecraft Java Edition servers with a modern dashboard — live console, performance charts, RCON support, and more.%n%nThe launcher brings its own runtime. Your Minecraft server needs Java 21 or newer — the launcher finds it for you, or tells you where to get it.
FinishedHeadingLabel=You're all set!
FinishedLabel=Minecraft Server Launcher {#SetupSetting('AppVersion')} has been installed.%n%nLaunch it from the Start Menu or the desktop shortcut.

[Files]
Source: "dist\output\Minecraft Server Launcher\Minecraft Server Launcher.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "dist\output\Minecraft Server Launcher\app\*";     DestDir: "{app}\app";     Flags: ignoreversion recursesubdirs createallsubdirs
Source: "dist\output\Minecraft Server Launcher\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Minecraft Server Launcher";           Filename: "{app}\Minecraft Server Launcher.exe"; WorkingDir: "{app}"
Name: "{group}\Uninstall Minecraft Server Launcher"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Minecraft Server Launcher";     Filename: "{app}\Minecraft Server Launcher.exe"; WorkingDir: "{app}"

[Run]
Filename: "{app}\Minecraft Server Launcher.exe"; Description: "Launch Minecraft Server Launcher"; Flags: nowait postinstall skipifsilent

[Code]

// PostMessage lets us auto-click the Install button without re-entering VCL
function PostMessage(hWnd: HWND; Msg: DWORD; wParam: Integer; lParam: Integer): Integer;
  external 'PostMessageW@user32.dll stdcall';

const
  BM_CLICK = $00F5;

// --------------------------------------------------------------------------
// Colors come from WizardStyle / WizardBackColor above (a custom style ignores colors set here);
// this only sets the app's fonts.
procedure ApplyTheme;
begin
  WizardForm.PageNameLabel.Font.Name        := 'Segoe UI Semibold';
  WizardForm.PageDescriptionLabel.Font.Name := 'Segoe UI';
  WizardForm.WelcomeLabel1.Font.Name        := 'Segoe UI Semibold';
  WizardForm.WelcomeLabel2.Font.Name        := 'Segoe UI';
  WizardForm.FinishedHeadingLabel.Font.Name := 'Segoe UI Semibold';
  WizardForm.FinishedLabel.Font.Name        := 'Segoe UI';
  WizardForm.SelectDirLabel.Font.Name       := 'Segoe UI';
  WizardForm.DirEdit.Font.Name              := 'Segoe UI';
  WizardForm.StatusLabel.Font.Name          := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Name        := 'Consolas';
  WizardForm.FilenameLabel.Font.Size        := 8;
end;

// --------------------------------------------------------------------------
// Hard-skip every page we don't need
function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PageID = wpReady)
         or (PageID = wpSelectTasks)
         or (PageID = wpSelectProgramGroup)
         or (PageID = wpLicense)
         or (PageID = wpPassword)
         or (PageID = wpInfoBefore)
         or (PageID = wpInfoAfter);
end;

// --------------------------------------------------------------------------
procedure InitializeWizard;
begin
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
  // Inno Setup handles Next via WM_COMMAND, not OnClick — use PostMessage
  if CurPageID = wpReady then
    PostMessage(WizardForm.NextButton.Handle, BM_CLICK, 0, 0);
end;
