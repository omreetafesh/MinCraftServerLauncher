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
DisableDirPage=yes
DisableReadyPage=yes

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
// ── Colors (Windows BGR: $BBGGRR) ─────────────────────────────────────────
const
  BG      = $0D1A0D;   // deep dark green — matches banner
  CARD    = $141414;   // inner panels
  INP     = $272727;   // inputs / list backgrounds
  WHITE   = $FFFFFF;
  TEXT    = $DDDDDD;
  DIM     = $4A4A4A;
  GREEN   = $41FF00;   // #00FF41

// Helper: darkens a label's parent panel if it is a TPanel
procedure DarkLabel(Lbl: TNewStaticText);
begin
  Lbl.Font.Color := TEXT;
  Lbl.Font.Name  := 'Segoe UI';
end;

procedure ApplyTheme;
var
  I: Integer;
begin
  // ── Outer chrome ────────────────────────────────────────────────────────
  WizardForm.Color           := BG;
  WizardForm.Font.Color      := TEXT;
  WizardForm.MainPanel.Color := BG;
  WizardForm.Bevel1.Visible  := False;

  // ── Inner page area ──────────────────────────────────────────────────────
  WizardForm.InnerPage.Color := CARD;

  // Page title & description
  WizardForm.PageNameLabel.Font.Color        := WHITE;
  WizardForm.PageNameLabel.Font.Name         := 'Segoe UI Semibold';
  WizardForm.PageNameLabel.Font.Size         := 14;
  WizardForm.PageDescriptionLabel.Font.Color := DIM;
  WizardForm.PageDescriptionLabel.Font.Name  := 'Segoe UI';

  // ── Tasks page ───────────────────────────────────────────────────────────
  WizardForm.SelectTasksLabel.Font.Color := TEXT;
  WizardForm.SelectTasksLabel.Font.Name  := 'Segoe UI';
  WizardForm.TasksList.Color             := INP;
  WizardForm.TasksList.Font.Color        := TEXT;
  WizardForm.TasksList.Font.Name         := 'Segoe UI';
  WizardForm.TasksList.Font.Size         := 10;

  // ── Installing page ───────────────────────────────────────────────────────
  WizardForm.StatusLabel.Font.Color      := TEXT;
  WizardForm.StatusLabel.Font.Name       := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Color    := DIM;
  WizardForm.FilenameLabel.Font.Name     := 'Consolas';
  WizardForm.FilenameLabel.Font.Size     := 8;
end;

procedure InitializeWizard;
begin
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
end;
