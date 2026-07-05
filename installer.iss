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
// Palette (Windows BGR: $BBGGRR)
const
  BG      = $0D1A0D;   // deep dark green  — matches banner
  CARD    = $141414;   // inner page bg
  CARD2   = $1C1C1C;   // card face
  BORDER  = $2E2E2E;   // card border
  HOVER   = $252525;   // card hover bg
  WHITE   = $FFFFFF;
  TEXT    = $DDDDDD;
  MUTED   = $666666;
  DIM     = $4A4A4A;
  GREEN   = $41FF00;   // #00FF41

// Custom config page vars 
var
  ConfigPage:    TWizardPage;
  DeskChk:       TNewCheckBox;
  DeskCardOuter: TPanel;
  StartChk:      TNewCheckBox;
  StartCardOuter:TPanel;

// Helpers 

function MakeCard(Parent: TWinControl; Top, H: Integer): TPanel;
var
  Inner: TPanel;
begin
  // Outer = border colour
  Result := TPanel.Create(WizardForm);
  Result.Parent := Parent;
  Result.Left   := 0;
  Result.Top    := Top;
  Result.Width  := Parent.ClientWidth;
  Result.Height := ScaleY(H);
  Result.Color  := BORDER;
  Result.BevelOuter := bvNone;

  // Inner = card face
  Inner := TPanel.Create(WizardForm);
  Inner.Parent := Result;
  Inner.Left   := 1;
  Inner.Top    := 1;
  Inner.Width  := Result.Width  - 2;
  Inner.Height := Result.Height - 2;
  Inner.Color  := CARD2;
  Inner.BevelOuter := bvNone;
end;

function InnerOf(Card: TPanel): TPanel;
begin
  // Returns the inner face panel of a card created by MakeCard
  Result := TPanel(Card.Controls[0]);
end;

procedure AddLabel(Parent: TWinControl; X, Y, W: Integer;
                   Txt, FontName: String; Sz, Clr: Integer);
var
  Lbl: TNewStaticText;
begin
  Lbl := TNewStaticText.Create(WizardForm);
  Lbl.Parent      := Parent;
  Lbl.Left        := ScaleX(X);
  Lbl.Top         := ScaleY(Y);
  Lbl.Width       := ScaleX(W);
  Lbl.Caption     := Txt;
  Lbl.Font.Name   := FontName;
  Lbl.Font.Size   := Sz;
  Lbl.Font.Color  := Clr;
  Lbl.Color       := CARD2;
  Lbl.ParentColor := False;
end;

procedure AddCardCheck(Card: TPanel; var Chk: TNewCheckBox;
                       Title, Subtitle: String);
var
  Face: TPanel;
begin
  Face := InnerOf(Card);

  Chk := TNewCheckBox.Create(WizardForm);
  Chk.Parent      := Face;
  Chk.Left        := ScaleX(16);
  Chk.Top         := ScaleY(11);
  Chk.Width       := Face.Width - ScaleX(32);
  Chk.Height      := ScaleY(18);
  Chk.Caption     := Title;
  Chk.Checked     := False;
  Chk.Font.Name   := 'Segoe UI Semibold';
  Chk.Font.Size   := 10;
  Chk.Font.Color  := TEXT;
  Chk.Color       := CARD2;
  Chk.ParentColor := False;

  AddLabel(Face, 38, 32, Face.Width - 50,
           Subtitle, 'Segoe UI', 9, MUTED);
end;

// Build the custom configuration page 

procedure BuildConfigPage;
var
  Face:      TPanel;
  SurfW:     Integer;
  PathPanel: TPanel;
  PathFace:  TPanel;
  PathLbl:   TNewStaticText;
begin
  ConfigPage := CreateCustomPage(wpWelcome,
    'Configure Installation',
    'Choose your installation preferences before we begin');

  SurfW := ConfigPage.SurfaceWidth;

  // Card 1: Desktop Shortcut 
  DeskCardOuter := MakeCard(ConfigPage.Surface, 10, 58);
  AddCardCheck(DeskCardOuter, DeskChk,
    'Create a desktop shortcut',
    'Adds a shortcut to your Desktop for quick access');

  // Card 2: Launch on system startup 
  StartCardOuter := MakeCard(ConfigPage.Surface, ScaleY(10) + DeskCardOuter.Height + ScaleY(10), 58);
  AddCardCheck(StartCardOuter, StartChk,
    'Launch on Windows startup',
    'Automatically open the launcher when Windows starts');

  // Install location (read-only, informational) 
  AddLabel(ConfigPage.Surface, 0, 148, SurfW,
           'INSTALL LOCATION', 'Segoe UI', 8, DIM);

  PathPanel := MakeCard(ConfigPage.Surface, ScaleY(162), 36);
  Face      := InnerOf(PathPanel);
  PathFace  := Face;

  PathLbl := TNewStaticText.Create(WizardForm);
  PathLbl.Parent      := PathFace;
  PathLbl.Left        := ScaleX(14);
  PathLbl.Top         := ScaleY(10);
  PathLbl.Width       := PathFace.Width - ScaleX(28);
  PathLbl.Caption     := ExpandConstant('{localappdata}\Programs\Minecraft Server Launcher');
  PathLbl.Font.Name   := 'Consolas';
  PathLbl.Font.Size   := 9;
  PathLbl.Font.Color  := MUTED;
  PathLbl.Color       := CARD2;
  PathLbl.ParentColor := False;
end;

// Global dark theme 

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
  WizardForm.PageDescriptionLabel.Font.Color := DIM;
  WizardForm.PageDescriptionLabel.Font.Name  := 'Segoe UI';

  WizardForm.StatusLabel.Font.Color    := TEXT;
  WizardForm.StatusLabel.Font.Name     := 'Segoe UI';
  WizardForm.FilenameLabel.Font.Color  := MUTED;
  WizardForm.FilenameLabel.Font.Name   := 'Consolas';
  WizardForm.FilenameLabel.Font.Size   := 8;
end;

// Skip the stock SelectTasks page 

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PageID = wpSelectTasks);
end;

// Sync our custom checkboxes → actual tasks before install 

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if CurPageID = ConfigPage.ID then begin
    // Desktop shortcut task
    WizardForm.TasksList.Checked[0] := DeskChk.Checked;

    // Startup registry entry — write/delete in CurStepChanged instead
    // (stored in DeskChk / StartChk for access later)
  end;
end;

// Post-install: handle startup registry entry 

procedure CurStepChanged(CurStep: TSetupStep);
var
  Key: String;
begin
  if CurStep = ssDone then begin
    Key := 'Software\Microsoft\Windows\CurrentVersion\Run';
    if StartChk.Checked then
      RegWriteStringValue(HKCU, Key, 'MinecraftServerLauncher',
        ExpandConstant('"{app}\Minecraft Server Launcher.exe"'))
    else
      RegDeleteValue(HKCU, Key, 'MinecraftServerLauncher');
  end;
end;

// Entry points 

procedure InitializeWizard;
begin
  BuildConfigPage;
  ApplyTheme;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ApplyTheme;
end;
