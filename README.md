By default hjbServer will be started at 9999 port:
>java -jar hjbServer.jar
Server started on port: 9999

>netstat -a
Active Connections
  Proto  Local Address          Foreign Address        State
[...]
  TCP    0.0.0.0:9999           MYPC:0                 LISTENING
[...]  


If the port will be occupied already you will receive the message and program will be termenated:
>java -jar hjbServer.jar
I means port 9999 is blocked.

>java -jar hjbServer.jar 9998
Server started on port: 9998
