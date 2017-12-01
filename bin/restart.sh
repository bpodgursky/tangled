#!/bin/bash

pkill -9 com.liveramp.tangled.WebServer

mkdir -p log
java -cp tangled.job.jar com.liveramp.tangled.WebServer config/config.json 2>> log/tangled.err 1>> log/tangled.out &