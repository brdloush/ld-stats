#!/bin/bash
if [ ! -d target ]; then
  mkdir target
fi
bb --uberscript target/ld-stats -cp src -m ld-stats.core
chmod +x target/ld-stats

