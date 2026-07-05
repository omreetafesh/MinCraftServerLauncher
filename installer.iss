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
// Palette  (Windows BGR format: $BBGGRR — bytes of #RRGGBB reversed)
const
  BG      = $0D1A0D;  // outer bg — dark green
  CARD    = $1A1A1A;  // inner page bg
  SEP     = $333333;  // separator line
  WHITE   = $FFFFFF;
  TEXT    = $F2F2F2;  // primary text
  SUB     = $999999;  // secondary / subtitle text
  DIM     = $555555;  // muted label
  TRK_OFF = $484848;  // toggle track  off-state
  THB_OFF = $888888;  // toggle thumb  off-state
  TRK_ON  = $2A8800;  // toggle track  on-state  (medium green)
  THB_ON  = $FFFFFF;  // toggle thumb  on-state

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
// Helpers — X/Y/W always in unscaled units; ScaleX/Y applied inside.
// Do NOT pass SurfaceWidth here — it is already in screen pixels.

procedure PutLbl(Parent: TWinControl; X, Y, W, FSz, FClr: Integer;
                 const S, FName: String);
var L: TNewStaticText;
begin
  L := TNewStaticText.Create(WizardForm);
  L.Parent      := Parent;
  L.Left        := ScaleX(X);
  L.Top         := ScaleY(Y);
  L.Width       := ScaleX(W);
  L.AutoSize    := False;
  L.Caption     := S;
  L.Font.Name   := FName;
  L.Font.Size   := FSz;
  L.Font.Color  := FClr;
  L.Color       := CARD;
  L.ParentColor := False;
end;

// Full-width 1-px separator; Y is unscaled
procedure PutSep(Parent: TWinControl; Y: Integer);
var P: TPanel;
begin
  P := TPanel.Create(WizardForm);
  P.Parent     := Parent;
  P.Left       := 0;
  P.Top        := ScaleY(Y);
  P.Width      := Parent.ClientWidth;  // already in screen pixels — correct
  P.Height     := 1;
  P.Color      := SEP;
  P.BevelOuter := bvNone;
end;

// Toggle switch; X/Y unscaled
function MakeToggle(Parent: TWinControl; X, Y: Integer;
                    var ThumbOut: TPanel): TPanel;
var Track, Thumb: TPanel;
begin
  Track := TPanel.Create(WizardForm);
  Track.Parent     := Parent;
  Track.Left       := ScaleX(X);
  Track.Top        := ScaleY(Y);
  Track.Width      := ScaleX(46);
  Track.Height     := ScaleY(24);
  Track.Color      := TRK_OFF;
  Track.BevelOuter := bvNone;

  Thumb := TPanel.Create(WizardForm);
  Thumb.Parent     := Track;
  Thumb.Left       := ScaleX(3);
  Thumb.Top        := ScaleY(4);
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
    DeskThumb.Left  := ScaleX(27);
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
    StartThumb.Left  := ScaleX(27);
    StartThumb.Color := THB_ON;
    StartTrack.Color := TRK_ON;
  end else begin
    StartThumb.Left  := ScaleX(3);
    StartThumb.Color := THB_OFF;
    StartTrack.Color := TRK_OFF;
  end;
end;

// --------------------------------------------------------------------------
// Custom Configure page
// Layout: toggle on the LEFT (X=0), labels to the RIGHT (X=54).
// All values are unscaled units — ScaleX/Y are applied inside helpers.
// Never use SurfaceWidth as an argument to PutLbl/MakeToggle.

procedure BuildConfigPage;
var Surf: TWinControl;
begin
  ConfigPage := CreateCustomPage(wpWelcome,
    'Configure Installation',
    'Customize your setup before we begin');
  Surf := ConfigPage.Surface;

  // Row 1 — Desktop shortcut
  DeskTrack := MakeToggle(Surf, 0, 12, DeskThumb);
  DeskTrack.OnClick := @ToggleDesk;
  DeskThumb.OnClick := @ToggleDesk;
  PutLbl(Surf, 56, 13, 255, 10, TEXT, 'Create a desktop shortcut', 'Segoe UI Semibold');
  PutLbl(Surf, 56, 31, 255,  9, SUB,  'Adds a quick-access icon to your Desktop', 'Segoe UI');

  PutSep(Surf, 58);

  // Row 2 — Launch on startup
  StartTrack := MakeToggle(Surf, 0, 68, StartThumb);
  StartTrack.OnClick := @ToggleStart;
  StartThumb.OnClick := @ToggleStart;
  PutLbl(Surf, 56, 69, 255, 10, TEXT, 'Launch on Windows startup', 'Segoe UI Semibold');
  PutLbl(Surf, 56, 87, 255,  9, SUB,  'Open the launcher automatically when Windows starts', 'Segoe UI');

  PutSep(Surf, 114);

  // Install location (read-only, informational)
  PutLbl(Surf, 0, 124, 310,  8, DIM,  'INSTALLS TO', 'Segoe UI');
  PutLbl(Surf, 0, 138, 310,  9, SUB,
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

  // Inner page header
  WizardForm.PageNameLabel.Font.Color        := WHITE;
  WizardForm.PageNameLabel.Font.Name         := 'Segoe UI Semibold';
  WizardForm.PageNameLabel.Font.Size         := 14;
  WizardForm.PageDescriptionLabel.Font.Color := SUB;
  WizardForm.PageDescriptionLabel.Font.Name  := 'Segoe UI';
  WizardForm.PageDescriptionLabel.Font.Size  := 9;

  // Welcome page — right panel
  WizardForm.WelcomeLabel1.Font.Color := WHITE;
  WizardForm.WelcomeLabel1.Font.Name  := 'Segoe UI Semibold';
  WizardForm.WelcomeLabel1.Font.Size  := 17;
  WizardForm.WelcomeLabel2.Font.Color := SUB;
  WizardForm.WelcomeLabel2.Font.Name  := 'Segoe UI';
  WizardForm.WelcomeLabel2.Font.Size  := 9;

  // Finish page — right panel
  WizardForm.FinishedHeadingLabel.Font.Color := WHITE;
  WizardForm.FinishedHeadingLabel.Font.Name  := 'Segoe UI Semibold';
  WizardForm.FinishedHeadingLabel.Font.Size  := 17;
  WizardForm.FinishedLabel.Font.Color        := SUB;
  WizardForm.FinishedLabel.Font.Name         := 'Segoe UI';
  WizardForm.FinishedLabel.Font.Size         := 9;

  // Installing page
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
