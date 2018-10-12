#!/bin/sh
export CROWDIN_API_KEY=`cat ~/.appconfig/eu.darken.bluemusic/crowdin.key`
alias crowdin='crowdin-cli'
crowdin-cli "$@"