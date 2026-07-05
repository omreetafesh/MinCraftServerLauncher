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

// --------------------------------------------------------------------------
// Palette (Windows BGR: $BBGGRR = #RRGGBB byte-reversed)
const
  BG    = $0D1A0D;
  CARD  = $1A1A1A;
  WHITE = $FFFFFF;
  TEXT  = $F2F2F2;
  SUB   = $AAAAAA;
  DIM   = $666666;

// --------------------------------------------------------------------------
// Dark theme applied to every page

procedure ApplyTheme;
begin
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

  WizardForm.WelcomeLabel1.Font.Color := WHITE;
  WizardForm.WelcomeLabel1.Font.Name  := 'Segoe UI Semibold';
  WizardForm.WelcomeLabel1.Font.Size  := 17;
  WizardForm.WelcomeLabel2.Font.Color := SUB;
  WizardForm.WelcomeLabel2.Font.Name  := 'Segoe UI';
  WizardForm.WelcomeLabel2.Font.Size  := 9;

  WizardForm.FinishedHeadingLabel.Font.Color := WHITE;
  WizardForm.FinishedHeadingLabel.Font.Name  := 'Segoe UI Semibold';
  WizardForm.FinishedHeadingLabel.Font.Size  := 17;
  WizardForm.FinishedLabel.Font.Color        := SUB;
  WizardForm.FinishedLabel.Font.Name         := 'Segoe UI';
  WizardForm.FinishedLabel.Font.Size         := 9;

  WizardForm.StatusLabel.Font.Color   := TEXT;
  WizardForm.StatusLabel.Font.Name    := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Color := DIM;
  WizardForm.FilenameLabel.Font.Name  := 'Consolas';
  WizardForm.FilenameLabel.Font.Size  := 8;
end;

// --------------------------------------------------------------------------

procedure InitializeWizard;
begin
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
end;
