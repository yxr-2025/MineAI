param(
    [string]$TaskFilter = '',
    [int]$TimeoutSeconds = 150,
    [int]$PollSeconds = 3,
    [string]$Report = 'D:\ximu\MineAI\tools\harness-report.md',
    [switch]$SkipSetup
)

$ErrorActionPreference = 'Continue'
$rcon = Join-Path $PSScriptRoot 'rcon.ps1'

function Invoke-Rcon {
    param([string[]]$Commands)
    & $rcon -Commands $Commands 2>&1
}

# ------------------------------------------------------------------ workshop

$WorkshopSetup = @(
    'gamerule doMobSpawning false',
    'gamerule doDaylightCycle false',
    'time set day',
    'weather clear',
    # oak tree next to spawn
    'fill 6 -60 0 6 -56 0 minecraft:oak_log',
    'fill 5 -56 -1 7 -55 1 minecraft:oak_leaves',
    # stone patch for cobblestone
    'fill 10 -61 0 10 -61 3 minecraft:stone',
    # ore wall at ground level so no exploration is needed
    'fill 12 -60 0 12 -60 3 minecraft:iron_ore',
    'fill 13 -60 0 13 -60 1 mekanism:osmium_ore',
    'fill 14 -60 0 14 -60 3 minecraft:redstone_ore',
    'fill 15 -60 0 15 -60 3 minecraft:coal_ore'
)

# -------------------------------------------------------------------- ladder

$Tasks = @(
    [pscustomobject]@{ Id='L1'; Item='minecraft:dirt'; Count=1;
        Goal='Mine the grass block beside you and pick up the drop.' }
    [pscustomobject]@{ Id='L2'; Item='minecraft:oak_log'; Count=4;
        Goal='Chop the oak tree and collect 4 logs.' }
    [pscustomobject]@{ Id='L3'; Item='minecraft:crafting_table'; Count=1;
        Goal='Craft a crafting table from planks.' }
    [pscustomobject]@{ Id='L4'; Item='minecraft:wooden_pickaxe'; Count=1;
        Goal='Craft a wooden pickaxe.' }
    [pscustomobject]@{ Id='L5'; Item='minecraft:cobblestone'; Count=3;
        Goal='Mine 3 stone and collect the cobblestone.' }
    [pscustomobject]@{ Id='L6'; Item='minecraft:furnace'; Count=1;
        Goal='Craft a furnace.' }
    [pscustomobject]@{ Id='L7'; Item='minecraft:iron_ingot'; Count=1;
        Goal='Mine iron ore, smelt it in a furnace, and obtain an iron ingot.' }
    [pscustomobject]@{ Id='L8'; Item='mekanism:metallurgic_infuser'; Count=1;
        Goal='Build a Mekanism metallurgic infuser from scratch.' }
)

# --------------------------------------------------------------------- run

$results = @()
$selected = if ($TaskFilter) { $Tasks | Where-Object { $_.Id -eq $TaskFilter } } else { $Tasks }

foreach ($task in $selected) {
    Write-Host "=== $($task.Id): $($task.Goal)" -ForegroundColor Cyan

    Invoke-Rcon @('npc remove all') | Out-Null
    if (-not $SkipSetup) {
        Invoke-Rcon $WorkshopSetup | Out-Null
    }
    Invoke-Rcon @('npc spawn') | Out-Null

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-Rcon @("npc agent obtain $($task.Item) $($task.Count) $($task.Goal)") | Out-Null

    $status = ''
    $last = ''
    $nextTick = 0
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Seconds $PollSeconds
        $out = (Invoke-Rcon @('npc agent status') | Out-String)
        $status = if ($out -match 'agent=(\w+)') { $Matches[1] } else { 'UNKNOWN' }
        $last = $out
        if ($sw.Elapsed.TotalSeconds -ge $nextTick) {
            $nextTick += 15
            $step = if ($out -match 'step=(\d+)') { $Matches[1] } else { '?' }
            Write-Host ("    [{0,4}s] {1} step={2}" -f [int]$sw.Elapsed.TotalSeconds, $status, $step) -ForegroundColor DarkGray
        }
        if ($status -eq 'FINISHED' -or $status -eq 'FAILED') { break }
    }

    $steps = if ($last -match 'step=(\d+)') { $Matches[1] } else { '?' }
    $plan = if ($last -match 'plan=(\d+/\d+)') { $Matches[1] } else { '?' }

    # verify independently instead of trusting the model
    $inv = (Invoke-Rcon @('data get entity AgentNPC_1 Inventory') | Out-String)
    $itemName = ($task.Item -split ':')[-1]
    $verified = $inv -match [regex]::Escape($itemName)

    $row = [pscustomobject]@{
        Task      = $task.Id
        Target    = "$($task.Count)x $($task.Item)"
        State     = $status
        Verified  = $verified
        Steps     = $steps
        Plan      = $plan
        Seconds   = [int]$sw.Elapsed.TotalSeconds
    }
    $results += $row
    Write-Host ("    -> {0} verified={1} steps={2} {3}s" -f $status, $verified, $steps, $row.Seconds) -ForegroundColor Green
    Invoke-Rcon @('npc agent stop') | Out-Null
}

# ------------------------------------------------------------------- report

$lines = @()
$lines += "# MineAI task harness report"
$lines += ""
$lines += "> $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$lines += ""
$lines += "| Task | Target | State | Verified | Steps | Plan | Seconds |"
$lines += "| --- | --- | --- | --- | --- | --- | --- |"
foreach ($r in $results) {
    $lines += "| $($r.Task) | $($r.Target) | $($r.State) | $($r.Verified) | $($r.Steps) | $($r.Plan) | $($r.Seconds) |"
}
$lines += ""
$passed = ($results | Where-Object { $_.Verified }).Count
$lines += "Passed: $passed / $($results.Count)"

$lines -join "`n" | Set-Content -LiteralPath $Report -Encoding UTF8
Write-Host ""
Write-Host "Report written to $Report"
$results | Format-Table -AutoSize
