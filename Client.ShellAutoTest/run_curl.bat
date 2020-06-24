@echo off
:: POST
::curl --request POST --header @header.txt --data-binary @body.txt http://localhost:9999 > response_post.log.000
:: GET
curl --request GET --data-binary @body.txt http://localhost:9999/ _
 ?JMS-USR=ems_user&JMS-PSW=ems_password&JMS-URL=10.10.10.10:60010 _
 &JMS-QU1=$tmp$.queue.send&&JMS-QU2=$tmp$.queue.recv&JMS-TIM=3000 _
 > response_get.log.000

