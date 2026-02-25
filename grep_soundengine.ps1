Get-Content "E:\Minecraft\MOD\topdown_view\build\createMcpToSrg\output.tsrg" | Where-Object { $_ -match "net/minecraft/client/sounds/SoundEngine" } | Out-File soundengine_tsrg.txt
