# Imprime los HTML de la entrega a PDF con Microsoft Edge en modo headless.
# Uso: powershell -ExecutionPolicy Bypass -File entrega\build_pdfs.ps1
$ErrorActionPreference = 'Stop'
$dir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
$prof = Join-Path $env:TEMP 'sigc-edge-pdf'
$jobs = @(
  @{ html = 'informe_final.html';  pdf = 'G3 SIGC Informe final.pdf' },
  @{ html = 'evidencias_3_1.html'; pdf = 'G3 SIGC Evidencias de funcionamiento.pdf' }
)
foreach ($j in $jobs) {
  $src = Join-Path $dir $j.html
  $out = Join-Path $dir $j.pdf
  if (Test-Path $out) { Remove-Item $out -Force }
  $uri = ([System.Uri]$src).AbsoluteUri
  $args = @('--headless', '--disable-gpu', '--no-first-run', "--user-data-dir=`"$prof`"",
            '--no-pdf-header-footer', "--print-to-pdf=`"$out`"", $uri)
  Start-Process -FilePath $edge -ArgumentList $args -Wait | Out-Null
  if (Test-Path $out) { "OK  $($j.pdf) ($((Get-Item $out).Length) bytes)" } else { "ERROR: no se generó $($j.pdf)" }
}
