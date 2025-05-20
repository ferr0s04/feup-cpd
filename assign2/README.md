# Assignment 2 - Instructions

## Compiling
PowerShell:  
```
javac --enable-preview --release 23 -cp ".;lib/json-20250107.jar" Server.java Client.java data/*.java auth/*.java rooms/*.java ai/*.java 
```

Unix:
```
javac --enable-preview --release 21 -cp .:lib/json-20250107.jar Server.java Client.java data/*.java auth/*.java rooms/*.java ai/*.java
```

## Usage
### Client
- Windows:
```
java --enable-preview -cp ".;lib/json-20250107.jar" Client localhost <PORT> <USER> <PASS>
```

- Unix:
```
java --enable-preview -cp .:lib/json-20250107.jar Client localhost <PORT> <USER> <PASS>
```

### Server
- Windows:
```
java --enable-preview -cp ".;lib/json-20250107.jar" Server <PORT>
```

- Unix:
```
java --enable-preview -cp .:lib/json-20250107.jar Server <PORT>
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
