HJB = Http to Jms Bridge = v0.03 24 June 2020 = f552p69@gmail.com
=================================================================
Usage:
        java -jar hjbServer.jar \[port\]

By default hjbServer will be started at 9999 port:
        > java -jar hjbServer.jar
Server started on port: 9999
        > netstat -a
        Active Connections
          Proto  Local Address          Foreign Address        State
          TCP    0.0.0.0:9999           MYPC:0                 LISTENING

If the port will be occupied already you will receive the message and program will be termenated:
        > java -jar hjbServer.jar
I means port 9999 is blocked.
        > java -jar hjbServer.jar 9998
Server started on port: 9998

After start HJB:
* Listens localhost:port (by default port is 9999)
* Sends body of HTTP POST request to the queue JMS-QU1 at JMS-USR:JMS-PSW@JMS-URL
* Waits JMS-TIM (Timeout is optional parameter, by default 5000) milliseconds
* Sends back via HTTP responce from queue JMS-QU2 (response queue is optional parameter, if it not set temporary queue will be used)

All parameters must be provided thru HTTP header or thru URL:

<h5>HTTP header</h5>
        curl --request POST --header @header.txt --data-binary @body.txt http://localhost:9999 > response.log
header.txt:
    JMS-USR: ems_user
    JMS-PSW: ems_password
    JMS-URL: 10.10.10.10:60010
    JMS-QU1: $tmp$.queue.send
    JMS-QU2: $tmp$.queue.recv
    JMS-TIM: 3000

<h5>URL</h5>

        curl --request GET --data-binary @body.txt http://localhost:9999/?JMS-USR=ems_user&JMS-PSW=ems_password&JMS-URL=10.10.10.10:60010&JMS-QU1=$tmp$.queue.send&&JMS-QU2=$tmp$.queue.recv&JMS-TIM=3000 > response2.log

        

