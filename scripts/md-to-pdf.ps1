# Converts a Markdown file to PDF using MS Word
# Usage: powershell -File md-to-pdf.ps1 -InputMd "path\to\file.md" -OutputPdf "path\to\file.pdf"

param(
    [Parameter(Mandatory=$true)][string]$InputMd,
    [Parameter(Mandatory=$true)][string]$OutputPdf
)

# Resolve to absolute paths (Word COM requires full paths)
$InputMd = (Resolve-Path $InputMd).Path

# Read markdown content
$md = Get-Content -Raw -Path $InputMd

# --- Minimal Markdown -> HTML conversion ---
$lines = $md -split "`n"
$html = New-Object System.Text.StringBuilder
[void]$html.Append(@"
<html><head><meta charset='utf-8'><style>
body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 11pt; line-height: 1.5; color: #222; }
h1 { font-size: 22pt; border-bottom: 2px solid #333; padding-bottom: 6px; }
h2 { font-size: 16pt; color: #1a5276; margin-top: 20px; }
h3 { font-size: 13pt; color: #2874a6; }
code { background: #f4f4f4; padding: 2px 5px; font-family: Consolas, monospace; font-size: 10pt; }
pre { background: #f4f4f4; padding: 10px; border-left: 3px solid #2874a6; font-family: Consolas, monospace; font-size: 9.5pt; white-space: pre-wrap; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; }
th, td { border: 1px solid #bbb; padding: 6px 10px; text-align: left; font-size: 10pt; }
th { background: #2874a6; color: white; }
blockquote { border-left: 4px solid #2874a6; margin-left: 0; padding-left: 15px; color: #555; font-style: italic; }
</style></head><body>
"@)

$inCode = $false
$inTable = $false

foreach ($line in $lines) {
    $line = $line.TrimEnd("`r")

    # Code fences
    if ($line -match '^```') {
        if ($inCode) { [void]$html.Append("</pre>"); $inCode = $false }
        else { [void]$html.Append("<pre>"); $inCode = $true }
        continue
    }
    if ($inCode) {
        $escaped = $line -replace '&','&amp;' -replace '<','&lt;' -replace '>','&gt;'
        [void]$html.Append($escaped + "`n")
        continue
    }

    # Tables
    if ($line -match '^\s*\|.*\|\s*$') {
        if ($line -match '^\s*\|[\s:\-\|]+\|\s*$') { continue } # separator row
        $cells = ($line.Trim().Trim('|') -split '\|')
        if (-not $inTable) { [void]$html.Append("<table>"); $inTable = $true; $tag = "th" }
        else { $tag = "td" }
        [void]$html.Append("<tr>")
        foreach ($c in $cells) {
            $cell = $c.Trim() -replace '&','&amp;' -replace '<','&lt;' -replace '>','&gt;'
            $cell = $cell -replace '\*\*(.+?)\*\*','<b>$1</b>' -replace '`(.+?)`','<code>$1</code>'
            [void]$html.Append("<$tag>$cell</$tag>")
        }
        [void]$html.Append("</tr>")
        continue
    } elseif ($inTable) {
        [void]$html.Append("</table>"); $inTable = $false
    }

    # Headers
    if ($line -match '^# (.+)') { [void]$html.Append("<h1>$($matches[1])</h1>"); continue }
    if ($line -match '^## (.+)') { [void]$html.Append("<h2>$($matches[1])</h2>"); continue }
    if ($line -match '^### (.+)') { [void]$html.Append("<h3>$($matches[1])</h3>"); continue }

    # Horizontal rule
    if ($line -match '^---+$') { [void]$html.Append("<hr>"); continue }

    # Blockquote
    if ($line -match '^> (.+)') { [void]$html.Append("<blockquote>$($matches[1])</blockquote>"); continue }

    # List items
    if ($line -match '^\s*[-*] (.+)') {
        $item = $matches[1] -replace '\*\*(.+?)\*\*','<b>$1</b>' -replace '`(.+?)`','<code>$1</code>'
        [void]$html.Append("<li>$item</li>"); continue
    }

    # Empty line
    if ($line.Trim() -eq '') { [void]$html.Append("<br>"); continue }

    # Regular paragraph
    $p = $line -replace '&','&amp;' -replace '\*\*(.+?)\*\*','<b>$1</b>' -replace '`(.+?)`','<code>$1</code>'
    [void]$html.Append("<p>$p</p>")
}

if ($inTable) { [void]$html.Append("</table>") }
if ($inCode) { [void]$html.Append("</pre>") }
[void]$html.Append("</body></html>")

# Write temp HTML file
$tempHtml = [System.IO.Path]::ChangeExtension($OutputPdf, ".temp.html")
$html.ToString() | Out-File -FilePath $tempHtml -Encoding UTF8

# Open in Word and export as PDF
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$doc = $word.Documents.Open($tempHtml)
# wdFormatPDF = 17
$doc.SaveAs([ref]$OutputPdf, [ref]17)
$doc.Close()
$word.Quit()

# Cleanup temp HTML
Remove-Item $tempHtml -ErrorAction SilentlyContinue

Write-Output "PDF created: $OutputPdf"
