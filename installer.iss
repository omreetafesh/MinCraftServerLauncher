[Setup]
AppName=Minecraft Server Launcher
AppVersion=1.0.0
AppPublisher=Omree
AppPublisherURL=https://github.com/omreetafesh/MinCraftServerLauncher
AppSupportURL=https://github.com/omreetafesh/MinCraftServerLauncher/issues
AppCopyright=Copyright (C) 2025 Omree

DefaultDirName={autopf}\Minecraft Server Launcher
DefaultGroupName=Minecraft Tools
DisableProgramGroupPage=yes

OutputDir=dist
OutputBaseFilename=MinecraftServerLauncher-Setup
SetupIconFile=dist\icon.ico

WizardStyle=modern
WizardImageFile=dist\wizard_banner.bmp
WizardSmallImageFile=dist\wizard_icon.bmp
WizardSizePercent=130

Compression=lzma2/ultra64
SolidCompression=yes
InternalCompressLevel=ultra64

UninstallDisplayIcon={app}\Minecraft Server Launcher.exe
UninstallDisplayName=Minecraft Server Launcher
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel1=Welcome to%nMinecraft Server Launcher
WelcomeLabel2=This wizard will install Minecraft Server Launcher {#SetupSetting('AppVersion')} on your computer.%n%nManage Minecraft Java Edition servers with a modern dashboard. Live console, performance charts, RCON support, multi-profile management, and automatic JAR downloads.%n%nA bundled Java runtime is included. No existing Java installation is required.%n%nClick Next to continue.
SelectDirLabel3=Where should Minecraft Server Launcher be installed?
ReadyLabel1=Setup is ready to install Minecraft Server Launcher on your computer.
ReadyLabel2a=Click Install to continue, or Back to review your settings.
FinishedHeadingLabel=Installation complete
FinishedLabel=Minecraft Server Launcher has been installed successfully.%n%nLaunch it from the Start Menu at any time.

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Files]
Source: "dist\output\Minecraft Server Launcher\Minecraft Server Launcher.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "dist\output\Minecraft Server Launcher\app\*";     DestDir: "{app}\app";     Flags: ignoreversion recursesubdirs createallsubdirs
Source: "dist\output\Minecraft Server Launcher\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Minecraft Server Launcher";           Filename: "{app}\Minecraft Server Launcher.exe"; WorkingDir: "{app}"
Name: "{group}\Uninstall Minecraft Server Launcher"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Minecraft Server Launcher";     Filename: "{app}\Minecraft Server Launcher.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\Minecraft Server Launcher.exe"; Description: "Launch Minecraft Server Launcher"; Flags: nowait postinstall skipifsilent

[Code]
// ── Color constants (Windows BGR format) ──────────────────────────────────
const
  BG_DARK   = $0D1A0D;   // outer form & button strip
  BG_CARD   = $141414;   // inner page panels
  BG_INPUT  = $2D2D2D;   // text inputs, memos, listboxes
  FG_WHITE  = $FFFFFF;   // headings
  FG_TEXT   = $E0E0E0;   // body text
  FG_DIM    = $555555;   // muted labels

// ── Apply dark theme to every reachable wizard component ─────────────────
procedure ApplyTheme;
begin
  // Main form chrome
  WizardForm.Color                            := BG_DARK;
  WizardForm.MainPanel.Color                  := BG_DARK;
  WizardForm.Bevel1.Visible                   := False;

  // Page header band (title + description + small icon)
  WizardForm.InnerPage.Color                  := BG_CARD;
  WizardForm.PageNameLabel.Font.Color         := FG_WHITE;
  WizardForm.PageNameLabel.Font.Name          := 'Segoe UI';
  WizardForm.PageNameLabel.Font.Size          := 13;
  WizardForm.PageDescriptionLabel.Font.Color  := FG_DIM;
  WizardForm.PageDescriptionLabel.Font.Name   := 'Segoe UI';

  // ── Select-directory page ─────────────────────────────────────────────
  WizardForm.DirEdit.Color                    := BG_INPUT;
  WizardForm.DirEdit.Font.Color               := FG_TEXT;

  // ── Select-tasks page (desktop icon checkbox) ─────────────────────────
  WizardForm.TasksList.Color                  := BG_CARD;
  WizardForm.TasksList.Font.Color             := FG_TEXT;

  // ── Ready-to-install summary ──────────────────────────────────────────
  WizardForm.ReadyMemo.Color                  := BG_INPUT;
  WizardForm.ReadyMemo.Font.Color             := FG_TEXT;
  WizardForm.ReadyMemo.Font.Name              := 'Consolas';
  WizardForm.ReadyMemo.Font.Size              := 9;


end;

procedure InitializeWizard;
begin
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
end;
