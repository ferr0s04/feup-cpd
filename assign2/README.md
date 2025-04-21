
Compiling:  
```
Get-ChildItem -Recurse -Filter *.java | ForEach-Object { javac -d ..\out $_.FullName }
```