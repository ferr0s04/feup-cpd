# Week 9 - Multithreaded TCP Socket Programming

### Introduction
The application is a client-server application, in which the server collects sensor readings sent by sensors
that play the role of clients, and provides statistics on these readings upon demand by other clients.  

Thus, the server supports two operations:  
- **put**, used by sensors to send their readings (one at a time);  
- **get**, used by other clients to retrieve the average of all values put by a given sensor.

Both the reading values reported by sensors and their averages are floating point numbers.
Sensors are identified using integer ids.  

The application will use TCP as the transport protocol, therefore the communicating
processes should use the Socket and ServerSocket classes. In this version, the server will
use multithreading (one thread per connection). The application should be able to handle multiple clients and
avoid race conditions by using appropriate synchronization mechanisms.

### Requirements
At least Java 21.

### Usage

Compiling:  
```
javac Server.java Client.java
```

Server (port 5000 and 10 sensors):  
```
java Server 5000 10
```

Client (write 25.4 to sensor 3):
```
java Client localhost 5000 put 3 25.4
```

Client (get average from sensor 3):
```
java Client localhost 5000 get 3
```
