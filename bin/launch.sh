#!/bin/bash

mkdir log
java -cp tangled-1.0-SNAPSHOT-uber.jar com.liveramp.tangled.WebServer config.json 2>> log/tangled.err 1>> log/tangled.out &