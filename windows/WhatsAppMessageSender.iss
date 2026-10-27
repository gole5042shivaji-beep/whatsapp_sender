; =====================================================================
;  WhatsApp Message Sender — Inno Setup स्क्रिप्ट (Windows)
;  वापर:
;   1) GitHub Actions मधून मिळालेल्या "GccSender-Windows" ZIP चा फोल्डर Extract करा.
;   2) खालील SourceDir ओळीत त्या फोल्डरचा पथ द्या (WhatsAppMessageSender.exe ज्यात आहे तो).
;   3) (ऐच्छिक) Microsoft चे MicrosoftEdgeWebview2Setup.exe (Evergreen Bootstrapper) याच फोल्डरमध्ये ठेवा.
;   4) Inno Setup मध्ये हे उघडून Build → Compile करा. Output: Output\WhatsAppMessageSender_Setup_1.0.0.exe
; =====================================================================

#define MyAppName "WhatsApp Message Sender"
#define MyAppVersion "1.0.0"
#define MyAppExe "WhatsAppMessageSender.exe"
#define SourceDir "C:\WMS\build"

[Setup]
AppId={{B7C2D9E4-5A31-4F8E-9C6D-2E7A1F40B835}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=WhatsAppMessageSender_Setup_{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
UninstallDisplayIcon={app}\{#MyAppExe}
UninstallDisplayName={#MyAppName}

CloseApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Desktop वर शॉर्टकट बनवा"; GroupDescription: "अतिरिक्त शॉर्टकट:"; Flags: checkedonce

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs; Excludes: "*.pdb,MicrosoftEdgeWebview2Setup.exe"
Source: "{#SourceDir}\MicrosoftEdgeWebview2Setup.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall skipifsourcedoesntexist; Check: NeedsWebView2

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"; Tasks: desktopicon

[Run]
Filename: "{tmp}\MicrosoftEdgeWebview2Setup.exe"; Parameters: "/silent /install"; StatusMsg: "Microsoft Edge WebView2 Install होत आहे..."; Flags: waituntilterminated skipifdoesntexist; Check: NeedsWebView2
Filename: "{app}\{#MyAppExe}"; Description: "{#MyAppName} सुरू करा"; Flags: nowait postinstall skipifsilent

[Code]
function WebView2Installed: Boolean;
var
  v: String;
begin
  Result :=
    (RegQueryStringValue(HKLM, 'SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', v) and (v <> '') and (v <> '0.0.0.0')) or
    (RegQueryStringValue(HKCU, 'Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}', 'pv', v) and (v <> '') and (v <> '0.0.0.0'));
end;

function NeedsWebView2: Boolean;
begin
  Result := not WebView2Installed;
end;
