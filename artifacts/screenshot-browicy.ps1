$ErrorActionPreference = 'Stop'
Add-Type @"
using System;
using System.Runtime.InteropServices;
public struct RECT3 { public int Left; public int Top; public int Right; public int Bottom; }
public class W3 {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT3 rect);
}
"@
$proc = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq 'browicy' } | Select-Object -First 1
if (-not $proc) { throw 'browicy window not found' }
$hwnd = $proc.MainWindowHandle
[void][W3]::SetForegroundWindow($hwnd)
Start-Sleep -Milliseconds 500
$r = New-Object RECT3
[void][W3]::GetWindowRect($hwnd, [ref]$r)
Add-Type -AssemblyName System.Drawing
$wpx = $r.Right - $r.Left; $hpx = $r.Bottom - $r.Top
$bmp = New-Object System.Drawing.Bitmap($wpx, $hpx)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($wpx, $hpx)))
$out = Join-Path (Get-Location) 'artifacts/desktop-example.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output ("screenshot=" + $out)
