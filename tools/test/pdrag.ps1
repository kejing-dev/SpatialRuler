# Round 2 (test agent): primed actions in ONE PowerShell process, inside the 60 fps period that `emu.sh waitfast` opened.
# 1. a click on the calib catcher (the first press after a re-arm is taken at the REAL cursor position while the emulator
#    window is foreground and the real cursor rests on it; the catcher panel sits there and eats it),
# 2. then the real action with -NoRearm.
# usage: powershell -File pdrag.ps1 -CatchX 1297 -CatchY 1990 -Action drag -ArgLine "X1 Y1 X2 Y2 -Steps 12 -StepMs 60 -EndHoldMs 300"
param([int]$CatchX = 1297, [int]$CatchY = 1990, [string]$Action = "drag", [string]$ArgLine = "")
$emu = Join-Path (Split-Path $PSScriptRoot -Parent) "harness\emu.ps1"
& $emu click $CatchX $CatchY -NoRearm -Avd PICO_6.1_Ruler
Invoke-Expression ("& '{0}' {1} {2} -NoRearm -Avd PICO_6.1_Ruler" -f $emu, $Action, $ArgLine)
