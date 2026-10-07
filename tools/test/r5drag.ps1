# Round-5 QA (test agent): one primed, scripted pinch-drag in ONE PowerShell process, inside the 60 fps period that
# `emu.sh waitfast` has just opened (this script never re-arms).
# Safety contract as emu.ps1: PostMessage only, only to windows of the qemu PID of -Avd; the real cursor, focus and
# window state are never touched.
#
# -Path: tokens separated by ';' (framebuffer = console-screenshot pixels):
#   c:X,Y              a click (press 150 ms, release) - the priming click on the calib catcher
#   p:X,Y              move the gaze there (two bursts, settle), press, hold -HoldMs
#   m:X,Y,STEPS,STEPMS glide (held button) from the current point to X,Y: STEPS moves, one every STEPMS (Stopwatch-timed)
#   w:MS               wait
#   s:NAME             console screenshot in the background (adb emu screenrecord screenshot -> -ShotDir\NAME\)
#   r                  release (also done at the end when the button is still down)
# Every event is printed as "EV t=<ms since start> host=<unix ms> ..." so that it can be matched with logcat.
param(
    [string]$Path,
    [int]$SettleMs = 400,
    [int]$HoldMs = 300,
    [string]$ShotDir = "",
    [string]$Avd = "PICO_6.1_Ruler",
    [string]$Serial = "emulator-5556"
)
$ErrorActionPreference = "Stop"
$src = @'
using System;
using System.Text;
using System.Diagnostics;
using System.Threading;
using System.Runtime.InteropServices;
public static class R5Win {
    public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder sb, int max);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr ctx);
    public static IntPtr Main = IntPtr.Zero;
    public static uint Pid = 0;
    public static Stopwatch Sw = Stopwatch.StartNew();
    public static void DpiAware() { try { SetThreadDpiAwarenessContext((IntPtr)(-4)); } catch (Exception) { } }
    public static uint PidOf(IntPtr h) { uint p; GetWindowThreadProcessId(h, out p); return p; }
    public static string Title(IntPtr h) { var t = new StringBuilder(256); GetWindowText(h, t, 256); return t.ToString(); }
    public static void Find(uint pid) {
        Pid = pid; Main = IntPtr.Zero;
        EnumWindows((h, l) => { if (PidOf(h) == pid && Title(h).StartsWith("PICO Emulator")) Main = h; return true; }, IntPtr.Zero);
    }
    public static IntPtr LParam(int x, int y) { return (IntPtr)(((y & 0xFFFF) << 16) | (x & 0xFFFF)); }
    static void Post(uint msg, long w, IntPtr l) {
        if (PidOf(Main) != Pid) throw new Exception("window not owned by the emulator pid");
        DpiAware(); PostMessage(Main, msg, (IntPtr)w, l);
    }
    public static void MoveBurst(int x, int y, long w) { for (int d = 4; d >= 0; d--) Post(0x0200, w, LParam(x - d, y)); }
    public static void Down(int x, int y) { Post(0x0201, 1, LParam(x, y)); }
    public static void Up(int x, int y) { Post(0x0202, 0, LParam(x, y)); }
    public static long Now() { return Sw.ElapsedMilliseconds; }
    public static void WaitUntil(double ms) {
        while (Sw.Elapsed.TotalMilliseconds < ms) {
            double left = ms - Sw.Elapsed.TotalMilliseconds;
            if (left > 3) Thread.Sleep(1); else Thread.SpinWait(200);
        }
    }
    // glide with the button held: STEPS moves, one every STEPMS, linear in window pixels
    public static void Glide(int x0, int y0, int x1, int y1, int steps, double stepMs) {
        double t0 = Sw.Elapsed.TotalMilliseconds;
        for (int i = 1; i <= steps; i++) {
            WaitUntil(t0 + i * stepMs);
            int x = (int)Math.Round(x0 + (x1 - x0) * (double)i / steps);
            int y = (int)Math.Round(y0 + (y1 - y0) * (double)i / steps);
            Post(0x0200, 1, LParam(x, y));
        }
    }
}
'@
if (-not ("R5Win" -as [type])) { Add-Type -TypeDefinition $src -Language CSharp }
[R5Win]::DpiAware()

$proc = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'qemu-system-x86_64.exe' -and $_.CommandLine -match ("-avd " + [regex]::Escape($Avd) + "( |$)") })
if ($proc.Count -ne 1) { Write-Output "ERROR: expected exactly one qemu process for AVD $Avd, found $($proc.Count)"; exit 2 }
[R5Win]::Find([uint32]$proc[0].ProcessId)
if ([R5Win]::Main -eq [IntPtr]::Zero) { Write-Output "ERROR: emulator window not found"; exit 3 }
$cal = Get-Content (Join-Path (Split-Path $PSScriptRoot -Parent) "harness\calib.json") -Raw | ConvertFrom-Json
function Map([double]$fx, [double]$fy) { return @([int][math]::Round($cal.ax * $fx + $cal.bx), [int][math]::Round($cal.ay * $fy + $cal.by)) }
function Ev([string]$what) { Write-Output ("EV t={0} host={1} {2}" -f [R5Win]::Now(), [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(), $what) }
function D([string]$s) { return [double]::Parse($s, [Globalization.CultureInfo]::InvariantCulture) }

$cur = $null; $down = $false
foreach ($tok in ($Path -split ';')) {
    $tok = $tok.Trim(); if ($tok -eq "") { continue }
    $kind = $tok.Substring(0, 1); $arg = if ($tok.Length -gt 2) { $tok.Substring(2) } else { "" }
    $v = @($arg -split ',')
    switch ($kind) {
        "c" {
            $p = Map (D $v[0]) (D $v[1])
            [R5Win]::MoveBurst($p[0], $p[1], 0); Start-Sleep -Milliseconds ([int]($SettleMs / 2))
            [R5Win]::MoveBurst($p[0], $p[1], 0); Start-Sleep -Milliseconds ([int]($SettleMs / 2))
            [R5Win]::Down($p[0], $p[1]); Ev ("click-down fb=({0},{1})" -f $v[0], $v[1]); Start-Sleep -Milliseconds 150
            [R5Win]::Up($p[0], $p[1]); Ev "click-up"; Start-Sleep -Milliseconds 250
        }
        "p" {
            $p = Map (D $v[0]) (D $v[1])
            [R5Win]::MoveBurst($p[0], $p[1], 0); Start-Sleep -Milliseconds ([int]($SettleMs / 2))
            [R5Win]::MoveBurst($p[0], $p[1], 0); Start-Sleep -Milliseconds ([int]($SettleMs / 2))
            [R5Win]::Down($p[0], $p[1]); $down = $true; $cur = $p
            Ev ("press fb=({0},{1}) client=({2},{3})" -f $v[0], $v[1], $p[0], $p[1])
            Start-Sleep -Milliseconds $HoldMs
        }
        "m" {
            $p = Map (D $v[0]) (D $v[1]); $n = [int]$v[2]; $ms = D $v[3]
            Ev ("glide-start fb_to=({0},{1}) steps={2} stepms={3}" -f $v[0], $v[1], $n, $ms)
            [R5Win]::Glide($cur[0], $cur[1], $p[0], $p[1], $n, $ms); $cur = $p
            Ev ("glide-end fb=({0},{1})" -f $v[0], $v[1])
        }
        "w" { [R5Win]::WaitUntil([R5Win]::Sw.Elapsed.TotalMilliseconds + (D $v[0])) }
        "s" {
            $dir = Join-Path $ShotDir $arg; New-Item -ItemType Directory -Force $dir | Out-Null
            Get-ChildItem $dir -Filter "Screenshot_*.png" -ErrorAction SilentlyContinue | Remove-Item -Force
            Start-Process -FilePath "adb" -ArgumentList @("-s", $Serial, "emu", "screenrecord", "screenshot", $dir) -NoNewWindow | Out-Null
            Ev ("shot {0}" -f $arg)
        }
        "r" { if ($down) { [R5Win]::Up($cur[0], $cur[1]); $down = $false; Ev "release" } }
        default { Write-Output "ERROR: bad token '$tok'"; exit 4 }
    }
}
if ($down) { [R5Win]::Up($cur[0], $cur[1]); Ev "release" }
