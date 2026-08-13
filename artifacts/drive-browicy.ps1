$ErrorActionPreference = 'Stop'

Add-Type @"
using System;
using System.Runtime.InteropServices;
public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
public class W {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int cmd);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
}
"@

$constLd = 0x02; $constLu = 0x04; $swRestore = 9

$proc = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'browicy' } | Select-Object -First 1
if (-not $proc) { throw 'browicy window not found' }
$hwnd = $proc.MainWindowHandle
Write-Output ("hwnd=" + $hwnd + " pid=" + $proc.Id)

# Activate: restore if minimized, then SetForegroundWindow with verification
$ok = $false
for ($i = 0; $i -lt 10; $i++) {
  if ([W]::IsIconic($hwnd)) { [void][W]::ShowWindow($hwnd, $swRestore) }
  [void][W]::SetForegroundWindow($hwnd)
  Start-Sleep -Milliseconds 200
  if ([W]::GetForegroundWindow() -eq $hwnd) { $ok = $true; break }
}
if (-not $ok) { throw 'could not bring browicy window to foreground' }
Write-Output "foreground=ok"

$r = New-Object RECT
[void][W]::GetWindowRect($hwnd, [ref]$r)
$cx = [int](($r.Left + $r.Right) / 2)
$cy = $r.Top + 84   # Adressleiste laut Skill ~84 px unter Fensteroberkante
Write-Output ("rect=(" + $r.Left + "," + $r.Top + "," + $r.Right + "," + $r.Bottom + ") click=(" + $cx + "," + $cy + ")")

# Klick in die Adressleiste
[void][W]::SetCursorPos($cx, $cy)
Start-Sleep -Milliseconds 150
[void][W]::mouse_event($constLd, 0, 0, 0, [UIntPtr]::Zero)
[void][W]::mouse_event($constLu, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 400

# URL aus Clipboard einfuegen (SendKeys-Sonderzeichen sicher) und laden
Set-Clipboard -Value 'https://example.com'
Add-Type -AssemblyName System.Windows.Forms
[System.Windows.Forms.SendKeys]::SendWait('^v{ENTER}')
Write-Output 'url sent'

Start-Sleep -Seconds 5

# Screenshot des Fensters
Add-Type -AssemblyName System.Drawing
$wpx = $r.Right - $r.Left; $hpx = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap($wpx, $hpx)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($wpx, $hpx)))
$out = Join-Path (Get-Location) 'artifacts/desktop-verify.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output ("screenshot=" + $out)
