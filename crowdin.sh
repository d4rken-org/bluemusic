#!/bin/sh
export CROWDIN_API_KEY=`cat ~/.appconfig/eu.darken.bluemusic/crowdin.key`
alias crowdin-cli='java -jar /usr/local/bin/crowdin-cli.jar'
crowdin-cli "$@"