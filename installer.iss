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
WelcomeLabel2=This wizard will install Minecraft Server Launcher {#SetupSetting('AppVersion')} on your computer.%n%nManage Minecraft Java Edition servers with a modern dashboard — live console, performance charts, RCON support, and more.%n%nA bundled Java runtime is included. No existing Java installation is required.%n%nClick Next to continue.
FinishedHeadingLabel=Installation complete
FinishedLabel=Minecraft Server Launcher has been installed successfully.%n%nYou can launch it from the Start Menu at any time.

[Tasks]
Name: "desktopicon"; Description: "desktop shortcut"; Flags: unchecked

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

// --------------------------------------------------------------------------
// Palette  (Windows BGR format: $BBGGRR = #RRGGBB reversed)
const
  BG      = $0D1A0D;  // outer window bg
  CARD    = $141414;  // inner page bg
  SEP     = $282828;  // row separator line
  WHITE   = $FFFFFF;
  TEXT    = $E0E0E0;
  SUB     = $888888;  // subtitle / muted text
  DIM     = $404040;  // very muted (section labels)
  TRK_OFF = $363636;  // toggle track  - off
  THB_OFF = $666666;  // toggle thumb  - off
  TRK_ON  = $1A6600;  // toggle track  - on  (muted green)
  THB_ON  = $FFFFFF;  // toggle thumb  - on

// --------------------------------------------------------------------------
// Global state
var
  ConfigPage: TWizardPage;
  DeskTrack:  TPanel;
  DeskThumb:  TPanel;
  DeskOn:     Boolean;
  StartTrack: TPanel;
  StartThumb: TPanel;
  StartOn:    Boolean;

// --------------------------------------------------------------------------
// Put a label with explicit background colour
procedure PutLbl(Parent: TWinControl; X, Y, W, FSz, FClr: Integer;
                 const S, FName: String);
var L: TNewStaticText;
begin
  L := TNewStaticText.Create(WizardForm);
  L.Parent     := Parent;
  L.Left       := ScaleX(X);   L.Top   := ScaleY(Y);
  L.Width      := ScaleX(W);
  L.Caption    := S;
  L.Font.Name  := FName;
  L.Font.Size  := FSz;
  L.Font.Color := FClr;
  L.Color      := CARD;
  L.ParentColor := False;
end;

// 1-pixel horizontal separator line
procedure PutSep(Parent: TWinControl; Y: Integer);
var P: TPanel;
begin
  P := TPanel.Create(WizardForm);
  P.Parent     := Parent;
  P.Left       := 0;
  P.Top        := ScaleY(Y);
  P.Width      := Parent.ClientWidth;
  P.Height     := 1;
  P.Color      := SEP;
  P.BevelOuter := bvNone;
end;

// Build a toggle switch; returns track, writes thumb into ThumbOut
function MakeToggle(Parent: TWinControl; X, Y: Integer;
                    var ThumbOut: TPanel): TPanel;
var Track, Thumb: TPanel;
begin
  Track := TPanel.Create(WizardForm);
  Track.Parent     := Parent;
  Track.Left       := ScaleX(X);
  Track.Top        := ScaleY(Y);
  Track.Width      := ScaleX(44);
  Track.Height     := ScaleY(22);
  Track.Color      := TRK_OFF;
  Track.BevelOuter := bvNone;

  Thumb := TPanel.Create(WizardForm);
  Thumb.Parent     := Track;
  Thumb.Left       := ScaleX(3);
  Thumb.Top        := ScaleY(3);
  Thumb.Width      := ScaleX(16);
  Thumb.Height     := ScaleY(16);
  Thumb.Color      := THB_OFF;
  Thumb.BevelOuter := bvNone;

  ThumbOut := Thumb;
  Result   := Track;
end;

// --------------------------------------------------------------------------
// Toggle click handlers

procedure ToggleDesk(Sender: TObject);
begin
  DeskOn := not DeskOn;
  if DeskOn then begin
    DeskThumb.Left  := ScaleX(25);
    DeskThumb.Color := THB_ON;
    DeskTrack.Color := TRK_ON;
  end else begin
    DeskThumb.Left  := ScaleX(3);
    DeskThumb.Color := THB_OFF;
    DeskTrack.Color := TRK_OFF;
  end;
end;

procedure ToggleStart(Sender: TObject);
begin
  StartOn := not StartOn;
  if StartOn then begin
    StartThumb.Left  := ScaleX(25);
    StartThumb.Color := THB_ON;
    StartTrack.Color := TRK_ON;
  end else begin
    StartThumb.Left  := ScaleX(3);
    StartThumb.Color := THB_OFF;
    StartTrack.Color := TRK_OFF;
  end;
end;

// --------------------------------------------------------------------------
// Build the custom Configure page

procedure BuildConfigPage;
var
  Surf: TWinControl;
  SW:   Integer;
begin
  ConfigPage := CreateCustomPage(wpWelcome,
    'Configure Installation',
    'Customize your setup before we begin');

  Surf := ConfigPage.Surface;
  SW   := ConfigPage.SurfaceWidth;

  // Row 1 -- Desktop shortcut
  PutLbl(Surf,  0, 12, SW-62, 10, TEXT,  'Create a desktop shortcut', 'Segoe UI Semibold');
  PutLbl(Surf,  0, 30, SW-62,  9, SUB,   'Adds a quick-access icon to your Desktop', 'Segoe UI');
  DeskTrack := MakeToggle(Surf, SW-52, 13, DeskThumb);
  DeskTrack.OnClick := @ToggleDesk;
  DeskThumb.OnClick := @ToggleDesk;

  PutSep(Surf, 56);

  // Row 2 -- Launch on startup
  PutLbl(Surf,  0, 66, SW-62, 10, TEXT,  'Launch on Windows startup', 'Segoe UI Semibold');
  PutLbl(Surf,  0, 84, SW-62,  9, SUB,   'Open the launcher automatically when Windows starts', 'Segoe UI');
  StartTrack := MakeToggle(Surf, SW-52, 67, StartThumb);
  StartTrack.OnClick := @ToggleStart;
  StartThumb.OnClick := @ToggleStart;

  PutSep(Surf, 110);

  // Install location (read-only info)
  PutLbl(Surf, 0, 118, SW,  8, DIM,  'INSTALLS TO', 'Segoe UI');
  PutLbl(Surf, 0, 132, SW,  9, SUB,
         ExpandConstant('{localappdata}\Programs\Minecraft Server Launcher'),
         'Consolas');
end;

// --------------------------------------------------------------------------
// Apply dark theme to all wizard controls

procedure ApplyTheme;
begin
  WizardForm.Color           := BG;
  WizardForm.Font.Color      := TEXT;
  WizardForm.MainPanel.Color := BG;
  WizardForm.Bevel1.Visible  := False;
  WizardForm.InnerPage.Color := CARD;

  // Header labels (inner pages)
  WizardForm.PageNameLabel.Font.Color        := WHITE;
  WizardForm.PageNameLabel.Font.Name         := 'Segoe UI Semibold';
  WizardForm.PageNameLabel.Font.Size         := 14;
  WizardForm.PageDescriptionLabel.Font.Color := DIM;
  WizardForm.PageDescriptionLabel.Font.Name  := 'Segoe UI';

  // Welcome page right-panel text
  WizardForm.WelcomeLabel1.Font.Color := WHITE;
  WizardForm.WelcomeLabel1.Font.Name  := 'Segoe UI Semibold';
  WizardForm.WelcomeLabel1.Font.Size  := 18;
  WizardForm.WelcomeLabel2.Font.Color := SUB;
  WizardForm.WelcomeLabel2.Font.Name  := 'Segoe UI';
  WizardForm.WelcomeLabel2.Font.Size  := 9;

  // Finish page right-panel text
  WizardForm.FinishedHeadingLabel.Font.Color := WHITE;
  WizardForm.FinishedHeadingLabel.Font.Name  := 'Segoe UI Semibold';
  WizardForm.FinishedHeadingLabel.Font.Size  := 18;
  WizardForm.FinishedLabel.Font.Color        := SUB;
  WizardForm.FinishedLabel.Font.Name         := 'Segoe UI';
  WizardForm.FinishedLabel.Font.Size         := 9;

  // Installing page status
  WizardForm.StatusLabel.Font.Color   := TEXT;
  WizardForm.StatusLabel.Font.Name    := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Color := DIM;
  WizardForm.FilenameLabel.Font.Name  := 'Consolas';
  WizardForm.FilenameLabel.Font.Size  := 8;
end;

// --------------------------------------------------------------------------
// Skip the built-in SelectTasks page (we have our own)

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PageID = wpSelectTasks);
end;

// Sync toggle state -> actual task selection before install begins

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = ConfigPage.ID then
    WizardForm.TasksList.Checked[0] := DeskOn;
end;

// Post-install: write or clear startup registry entry

procedure CurStepChanged(CurStep: TSetupStep);
var Key: String;
begin
  if CurStep = ssDone then begin
    Key := 'Software\Microsoft\Windows\CurrentVersion\Run';
    if StartOn then
      RegWriteStringValue(HKCU, Key, 'MinecraftServerLauncher',
        ExpandConstant('"{app}\Minecraft Server Launcher.exe"'))
    else
      RegDeleteValue(HKCU, Key, 'MinecraftServerLauncher');
  end;
end;

// --------------------------------------------------------------------------

procedure InitializeWizard;
begin
  BuildConfigPage;
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
end;
