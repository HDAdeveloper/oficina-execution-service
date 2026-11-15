<#
.SYNOPSIS
  Baixa a narracao sintetica e junta ao video de slides (sem audio).
.DESCRIPTION
  Pre-requisito: ffmpeg (winget install Gyan.FFmpeg; feche e abra o PowerShell depois).
  Rode DENTRO da pasta docs\video:  powershell -ExecutionPolicy Bypass -File montar-video.ps1
  Saida: video-slides-com-voz.mp4 (cenas 1 a 8 com voz; cartoes dos blocos 4 a 7 e o resumo ficam em silencio).
  Para usar a SUA voz no lugar: grave um .wav/.mp3 com o texto de narracao-marcada.md e rode com -MinhaVoz arquivo.
#>
param([string]$MinhaVoz = "")
$ErrorActionPreference = "Stop"
$base = "https://assets.vidiq.com/voiceovers/75efb06f-e010-42d9-a8c2-781f33e10327"
$ids = @{ "a1.mp3" = "3f37a9e0-df99-4d4b-bccf-de4e7e99d3ac"; "a2.mp3" = "a76c131b-c31f-4854-a49d-6a577f8d6995"; "a3.mp3" = "5c4e0e94-aa92-481b-a1d0-7419319548e6" }
if ($MinhaVoz -ne "") {
  ffmpeg -y -i video-slides-sem-audio.mp4 -i $MinhaVoz -map 0:v -map 1:a -c:v copy -c:a aac -shortest video-slides-com-sua-voz.mp4
  Write-Host "Pronto: video-slides-com-sua-voz.mp4"; return
}
foreach ($k in $ids.Keys) {
  if (-not (Test-Path $k)) { Invoke-WebRequest "$base/$($ids[$k]).mp3" -OutFile $k }
}
$f = "[0:a]aresample=44100,aformat=channel_layouts=mono[a0];[1:a]aresample=44100,aformat=channel_layouts=mono[a1];[2:a]aresample=44100,aformat=channel_layouts=mono[a2];[3:a]aformat=channel_layouts=mono[a3];[a0][a1][a2][a3]concat=n=4:v=0:a=1[a]"
ffmpeg -y -i a1.mp3 -i a2.mp3 -i a3.mp3 -f lavfi -t 44 -i anullsrc=r=44100:cl=mono -filter_complex $f -map "[a]" -c:a aac narracao.m4a
ffmpeg -y -i video-slides-sem-audio.mp4 -i narracao.m4a -map 0:v -map 1:a -c:v copy -c:a aac -shortest video-slides-com-voz.mp4
Write-Host "Pronto: video-slides-com-voz.mp4"
