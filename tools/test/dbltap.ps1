# Two harness clicks in ONE PowerShell process (the harness script emu.ps1 is invoked twice; input still goes only
# through its PostMessage path to the Ruler emulator). Gap between the two taps ~0.3-0.6 s instead of ~1.5 s.
param([string]$X1, [string]$Y1, [string]$X2, [string]$Y2, [int]$SettleMs = 80, [int]$HoldMs = 100)
$emu = Join-Path $PSScriptRoot "..\harness\emu.ps1"
$sw = [Diagnostics.Stopwatch]::StartNew()
& $emu click $X1 $Y1 -NoRearm -SettleMs $SettleMs -HoldMs $HoldMs -Avd PICO_6.1_Ruler
$t1 = $sw.ElapsedMilliseconds
& $emu click $X2 $Y2 -NoRearm -SettleMs $SettleMs -HoldMs $HoldMs -Avd PICO_6.1_Ruler
Write-Output ("first click done at {0} ms, second at {1} ms" -f $t1, $sw.ElapsedMilliseconds)
