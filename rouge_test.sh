#/bin/bash

PEERS="eval/peers/1"

ROUGE_FILE="temp"
ROUGE_INPUT="$ROUGE_FILE.rouge.in"
ROUGE_OUTPUT="$ROUGE_FILE.rouge.out"

echo "Copy document summaries"
rm $PEERS/*
cp output/* $PEERS

echo "Construct ROUGE input file"
cd eval
./makeRougeScript.pl 1 > $ROUGE_INPUT

echo "Run ROUGE"
ROUGE/RELEASE-1.5.5/ROUGE-1.5.5.pl  \
  -e ROUGE/RELEASE-1.5.5/data       \
  -a -c 95 -b 75 -m -n 4 -w 1.2     \
  $ROUGE_INPUT > $ROUGE_OUTPUT

cd ..
