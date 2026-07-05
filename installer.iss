[Setup]
AppName=Minecraft Server Launcher
AppVersion=1.0.0
AppPublisher=Omree
AppPublisherURL=https://github.com/omreetafesh/MinCraftServerLauncher
AppSupportURL=https://github.com/omreetafesh/MinCraftServerLauncher/issues
AppCopyright=Copyright (C) 2025 Omree

DefaultDirName={localappdata}\Programs\Minecraft Server Launcher
DefaultGroupName=Minecraft Tools
DisableProgramGroupPage=yes
DisableReadyPage=yes

OutputDir=dist
OutputBaseFilename=MinecraftServerLauncher-Setup
SetupIconFile=dist\icon.ico

WizardStyle=modern
WizardImageFile=dist\wizard_banner.bmp
WizardSmallImageFile=dist\wizard_icon.bmp
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
WelcomeLabel2=This wizard will install Minecraft Server Launcher {#SetupSetting('AppVersion')} on your computer.%n%nManage Minecraft Java Edition servers with a modern dashboard — live console, performance charts, RCON support, and more.%n%nA bundled Java runtime is included. No existing Java installation is required.%n%nClick Next to install.
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

function DwmSetWindowAttribute(Wnd: HWND; Attr: DWORD;
                                var Value: DWORD; Size: DWORD): HRESULT;
  external 'DwmSetWindowAttribute@dwmapi.dll stdcall';

// PostMessage lets us auto-click the Install button without re-entering VCL
function PostMessage(hWnd: HWND; Msg: DWORD; wParam: Integer; lParam: Integer): Integer;
  external 'PostMessageW@user32.dll stdcall';

const
  BM_CLICK = $00F5;

// --------------------------------------------------------------------------
// Palette (Windows BGR: $BBGGRR)
// Outer BG matches the banner's darkest edge so there's no colour clash
const
  BG    = $0A120A;  // dark green — identical to banner edge
  CARD  = $181E18;  // slightly lighter green-dark for inner page
  WHITE = $FFFFFF;
  TEXT  = $F0F0F0;
  SUB   = $B0B0B0;
  DIM   = $606060;

// --------------------------------------------------------------------------
procedure ApplyTheme;
var Dark: DWORD;
begin
  // Request dark title bar from Windows (Win10 v2004+ / Win11)
  Dark := 1;
  DwmSetWindowAttribute(WizardForm.Handle, 20, Dark, 4);

  WizardForm.Color           := BG;
  WizardForm.Font.Color      := TEXT;
  WizardForm.MainPanel.Color := BG;
  WizardForm.Bevel1.Visible  := False;
  WizardForm.InnerPage.Color := CARD;

  WizardForm.PageNameLabel.Font.Color        := WHITE;
  WizardForm.PageNameLabel.Font.Name         := 'Segoe UI Semibold';
  WizardForm.PageNameLabel.Font.Size         := 14;
  WizardForm.PageDescriptionLabel.Font.Color := SUB;
  WizardForm.PageDescriptionLabel.Font.Name  := 'Segoe UI';
  WizardForm.PageDescriptionLabel.Font.Size  := 9;
  WizardForm.PageDescriptionLabel.Top :=
    WizardForm.PageNameLabel.Top + WizardForm.PageNameLabel.Height + ScaleY(2);

  // Welcome page — size 14 avoids overlap with WelcomeLabel2 below it
  WizardForm.WelcomeLabel1.Font.Color := WHITE;
  WizardForm.WelcomeLabel1.Font.Name  := 'Segoe UI Semibold';
  WizardForm.WelcomeLabel1.Font.Size  := 14;
  WizardForm.WelcomeLabel2.Font.Color := SUB;
  WizardForm.WelcomeLabel2.Font.Name  := 'Segoe UI';
  WizardForm.WelcomeLabel2.Font.Size  := 9;
  // Reposition so WelcomeLabel2 sits below the resized WelcomeLabel1
  WizardForm.WelcomeLabel2.Top :=
    WizardForm.WelcomeLabel1.Top + WizardForm.WelcomeLabel1.Height + ScaleY(8);

  // Finished page
  WizardForm.FinishedHeadingLabel.Font.Color := WHITE;
  WizardForm.FinishedHeadingLabel.Font.Name  := 'Segoe UI Semibold';
  WizardForm.FinishedHeadingLabel.Font.Size  := 14;
  WizardForm.FinishedLabel.Font.Color        := SUB;
  WizardForm.FinishedLabel.Font.Name         := 'Segoe UI';
  WizardForm.FinishedLabel.Font.Size         := 9;
  WizardForm.FinishedLabel.Top :=
    WizardForm.FinishedHeadingLabel.Top + WizardForm.FinishedHeadingLabel.Height + ScaleY(8);

  // Dir chooser page
  WizardForm.SelectDirLabel.Font.Color := SUB;
  WizardForm.SelectDirLabel.Font.Name  := 'Segoe UI';
  WizardForm.DirEdit.Color             := $282E28;
  WizardForm.DirEdit.Font.Color        := TEXT;
  WizardForm.DirEdit.Font.Name         := 'Segoe UI';

  WizardForm.StatusLabel.Font.Color   := TEXT;
  WizardForm.StatusLabel.Font.Name    := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Color := DIM;
  WizardForm.FilenameLabel.Font.Name  := 'Consolas';
  WizardForm.FilenameLabel.Font.Size  := 8;
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
