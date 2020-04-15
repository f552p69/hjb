@echo off
:: POST
curl --request POST --header @header.txt --data-binary @body.txt http://localhost:9999 > response.log.000
