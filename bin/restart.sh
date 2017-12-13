#!/bin/bash

pkill -9 -f com.liveramp.tangled.WebServer

mkdir -p log
java -Djava.io.tmpdir=/var/www/tmp  -cp tangled.job.jar com.liveramp.tangled.WebServer config/config.json 2>> log/tangled.err 1>> log/tangled.out &