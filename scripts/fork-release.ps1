# fork-release.ps1: Windows entry point for scripts/fork-release.sh. It finds Git Bash and
# passes every argument through, for example:  scripts\fork-release.ps1 --install
$ErrorActionPreference = 'Stop'
$candidates = @(
  "$env:ProgramFiles\Git\bin\bash.exe",
  "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
  "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
)
$bash = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $bash) { Write-Error 'Git Bash not found. Install Git for Windows: winget install Git.Git'; exit 1 }
$script = Join-Path $PSScriptRoot 'fork-release.sh'
& $bash $script @args
exit $LASTEXITCODE
