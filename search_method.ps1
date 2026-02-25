Get-ChildItem -Path "E:\Minecraft\MOD\topdown_view\.gradle\loom-cache" -Recurse -Filter "mappings*.tiny" | ForEach-Object { Select-String -Path $_.FullName -Pattern "updateListener" }
