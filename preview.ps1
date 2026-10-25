# Builds the whole site the way the workflow does (.github/workflows/site.yml), into _site, and serves it at
# http://127.0.0.1:8000: the home page at /, the documentation at /docs/. Run it again after a change, and refresh.
#
#   ./preview.ps1              build and serve
#   ./preview.ps1 -Port 8080   on another port
#   ./preview.ps1 -NoServe     only build: fails like the workflow on a broken link or a missing page
#   ./preview.ps1 -Offline     without the releases from GitHub: the home page then asks GitHub itself
#
# MkDocs runs from a virtual environment of its own (.venv), made the first time with the versions in
# docs/requirements.txt, and again whenever that file changes. Needs Python 3 (the workflow uses 3.12).
param(
    [int]$Port = 8000,
    [switch]$NoServe,
    [switch]$Offline
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$venv = Join-Path $PSScriptRoot ".venv"
$requirements = Join-Path $PSScriptRoot "docs/requirements.txt"
# What the venv was last installed from, so a change to the requirements installs again
$stamp = Join-Path $venv "requirements.installed"

function Get-VenvPython {
    foreach ($path in @("Scripts/python.exe", "bin/python")) {
        $python = Join-Path $venv $path
        if (Test-Path $python) { return $python }
    }
    return $null
}

# Stops on a command that failed, which $ErrorActionPreference doesn't do for programs
function Assert-Success([string]$what) {
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit code $LASTEXITCODE)." }
}

$python = Get-VenvPython
if (-not $python) {
    Write-Host "Making the virtual environment in .venv..."
    $system = (Get-Command python3, python -ErrorAction SilentlyContinue | Select-Object -First 1).Source
    if (-not $system) { throw "Python 3 isn't installed, or isn't on the PATH." }
    & $system -m venv $venv
    Assert-Success "Making the virtual environment"
    $python = Get-VenvPython
}
$wanted = Get-Content $requirements -Raw
if (-not (Test-Path $stamp) -or (Get-Content $stamp -Raw) -ne $wanted) {
    Write-Host "Installing MkDocs (docs/requirements.txt)..."
    & $python -m pip install --quiet --disable-pip-version-check -r $requirements
    Assert-Success "Installing the requirements"
    Set-Content -Path $stamp -Value $wanted -NoNewline
}

# The same two steps as the workflow's Build: the documentation under docs/, the home page over it
$site = Join-Path $PSScriptRoot "_site"
if (Test-Path $site) { Remove-Item $site -Recurse -Force }
& $python -m mkdocs build --strict --site-dir (Join-Path $site "docs")
Assert-Success "Building the documentation"
Copy-Item -Path (Join-Path $PSScriptRoot "website/*") -Destination $site -Recurse -Force

# The releases, as the server's sync serves them (docs/server-setup/), so the home page reads them as it does on the
# real site. Its "Next check in" counts down from the file's time, now; nothing writes it again here, so after 5 minutes it
# stays at "Checking GitHub…"
if (-not $Offline) {
    try {
        Invoke-WebRequest -Uri "https://api.github.com/repos/fidesosu/Materialdrain/releases?per_page=30" `
            -OutFile (Join-Path $site "releases.json") -UseBasicParsing | Out-Null
    } catch {
        Write-Warning "Couldn't read the releases from GitHub ($($_.Exception.Message)); the home page will ask GitHub itself."
    }
}

if ($NoServe) {
    Write-Host "Built into _site."
    return
}
Write-Host ""
Write-Host "Home page:     http://127.0.0.1:$Port/"
Write-Host "Documentation: http://127.0.0.1:$Port/docs/"
Write-Host "Ctrl+C stops it."
& $python -m http.server $Port --bind 127.0.0.1 --directory $site
