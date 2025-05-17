# Assignment 2 - Instructions

## Compiling
PowerShell (using JAR file):  
```
$classpath = ".\lib\json-20250107.jar"                                                                   
$src = Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -cp $classpath $src 
```

Unix:
```
javac --enable-preview --release 21 Server.java Client.java data/*.java auth/*.java rooms/*.java
```

## Usage
### Client
```
java --enable-preview Client localhost <PORT> <USER> <PASS>
```

With JAR file:
```
java --enable-preview -cp ".;lib\json-20250107.jar" Client localhost <PORT> <USER> <PASS>
```

### Server
```
java --enable-preview Server <PORT>
```

With JAR file:
```
java --enable-preview -cp ".;lib\json-20250107.jar" Server <PORT>
```

## AI Setup
Just install Ollama on your machine:
```
sudo snap install ollama
```

And run the correct model:
```
ollama run llama3
```
