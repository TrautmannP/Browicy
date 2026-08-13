$ErrorActionPreference = 'Stop'

Add-Type @"
using System;
using System.Runtime.InteropServices;
public struct RECT2 { public int Left; public int Top; public int Right; public int Bottom; }
public class W2 {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT2 rect);
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

$ok = $false
for ($i = 0; $i -lt 10; $i++) {
  if ([W2]::IsIconic($hwnd)) { [void][W2]::ShowWindow($hwnd, $swRestore) }
  [void][W2]::SetForegroundWindow($hwnd)
  Start-Sleep -Milliseconds 200
  if ([W2]::GetForegroundWindow() -eq $hwnd) { $ok = $true; break }
}
if (-not $ok) { throw 'could not activate browicy window' }

$r = New-Object RECT2
[void][W2]::GetWindowRect($hwnd, [ref]$r)
$cx = [int](($r.Left + $r.Right) / 2)
$cy = $r.Top + 84

[void][W2]::SetCursorPos($cx, $cy)
Start-Sleep -Milliseconds 150
[void][W2]::mouse_event($constLd, 0, 0, 0, [UIntPtr]::Zero)
[void][W2]::mouse_event($constLu, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 400

Add-Type -AssemblyName System.Windows.Forms
# Alles markieren, dann URL ersetzen
[System.Windows.Forms.SendKeys]::SendWait('^a')
Start-Sleep -Milliseconds 150
Set-Clipboard -Value 'https://example.com'
[System.Windows.Forms.SendKeys]::SendWait('^v{ENTER}')
Write-Output 'clean url sent'
Start-Sleep -Seconds 6
Write-Output 'done'
