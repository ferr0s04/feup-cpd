## Compiling
PowerShell:  
```
Get-ChildItem -Recurse -Filter *.java | ForEach-Object { javac -d ..\out $_.FullName }
```

Unix:
```
javac --enable-preview --release 21 Server.java Client.java data/*.java auth/*.java rooms/*.java
```

## Usage
Client:
```
java --enable-preview Client localhost <PORT> <USER> <PASS>
```

Server:
```
java --enable-preview Server <PORT>
```